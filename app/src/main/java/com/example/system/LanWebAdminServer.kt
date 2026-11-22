package com.example.system

import android.content.Context
import com.example.data.local.CustomWidgetEntity
import com.example.data.local.DrawerSlotEntity
import com.example.data.local.MailAccountEntity
import com.example.data.local.PenDatabase
import com.example.data.prefs.AiApiProfile
import com.example.data.prefs.PenConfig
import com.example.data.prefs.PenPreferencesRepository
import com.example.ui.theme.FontSlotSpec
import com.example.widgets.CustomWidgetTemplates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class LanServerStatus(
    val isRunning: Boolean = false,
    val url: String = "",
    val ipAddress: String = "",
    val port: Int = 8765,
    val pin: String = "",
    val lastClientNote: String = ""
)

/**
 * Ultra-lightweight pure-Kotlin coroutine HTTP/1.1 LAN Web Admin Server running directly on the pen.
 * - Zero external server JARs (saves RAM & APK size on Android 5.0.2).
 * - Requires 6-digit PIN -> random 256-bit Bearer token held in memory (no localStorage, no wildcard CORS).
 * - Strictly confines file uploads to app-private `filesDir/penos_media` with extension allowlists.
 */
class LanWebAdminServer(
    private val context: Context,
    private val preferences: PenPreferencesRepository,
    private val database: PenDatabase,
    private val snapshotManager: PenDesktopSnapshotManager = PenDesktopSnapshotManager(preferences, database)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val activeTokens = ConcurrentHashMap.newKeySet<String>()

    var onRemoteAiPrompt: (suspend (String) -> Pair<String, String>)? = null
    var onMailAccountAdded: (suspend () -> Unit)? = null

    private val _status = MutableStateFlow(LanServerStatus())
    val status: StateFlow<LanServerStatus> = _status.asStateFlow()

    fun start(port: Int = 8765) {
        if (_status.value.isRunning) return
        scope.launch {
            val pin = preferences.ensureLanAdminPin()
            val ip = resolveLanIpv4()
            runCatching {
                val ss = ServerSocket(port)
                serverSocket = ss
                _status.value = LanServerStatus(
                    isRunning = true,
                    url = "http://$ip:$port",
                    ipAddress = ip,
                    port = port,
                    pin = pin,
                    lastClientNote = "局域网控制台已就绪"
                )
                acceptJob = scope.launch {
                    while (isActive && !ss.isClosed) {
                        val sock = runCatching { ss.accept() }.getOrNull() ?: break
                        launch { handleClient(sock) }
                    }
                }
            }.onFailure { err ->
                _status.value = _status.value.copy(
                    isRunning = false,
                    lastClientNote = "启动失败：${err.message}"
                )
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        activeTokens.clear()
        _status.value = _status.value.copy(isRunning = false, lastClientNote = "局域网服务已停止")
    }

    internal fun verifyPinAndIssueToken(submittedPin: String, expectedPin: String): String? {
        if (expectedPin.isBlank() || submittedPin.isBlank()) return null
        val match = MessageDigest.isEqual(
            submittedPin.trim().toByteArray(Charsets.UTF_8),
            expectedPin.trim().toByteArray(Charsets.UTF_8)
        )
        if (!match) return null
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        activeTokens.add(token)
        return token
    }

    internal fun isAuthorized(authHeader: String?): Boolean {
        if (authHeader.isNullOrBlank()) return false
        val token = authHeader.removePrefix("Bearer ").trim()
        return token.isNotEmpty() && activeTokens.contains(token)
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 15_000
            val input = BufferedInputStream(sock.getInputStream())
            val output = sock.getOutputStream()

            val requestLine = readLineCrlf(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val fullTarget = parts[1]
            val path = fullTarget.substringBefore("?")
            val query = parseQuery(fullTarget.substringAfter("?", ""))

            val headers = mutableMapOf<String, String>()
            while (true) {
                val hLine = readLineCrlf(input) ?: break
                if (hLine.isEmpty()) break
                val idx = hLine.indexOf(':')
                if (idx > 0) {
                    headers[hLine.substring(0, idx).trim().lowercase()] = hLine.substring(idx + 1).trim()
                }
            }

            val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 26 * 1024 * 1024) ?: 0
            val bodyBytes = if (contentLength > 0) readExact(input, contentLength) else ByteArray(0)

            try {
                if (method == "GET" && (path == "/" || path == "/index.html")) {
                    writeResponse(output, 200, "text/html; charset=utf-8", LanWebConsoleHtml.indexHtml.toByteArray(Charsets.UTF_8))
                    return
                }

                if (method == "GET" && path == "/api/ping") {
                    writeJson(
                        output,
                        200,
                        JSONObject()
                            .put("ok", true)
                            .put("name", "PenOS 5.0")
                            .put("status", "PenOS 5.0 在线")
                    )
                    return
                }

                if (method == "POST" && path == "/api/auth/login") {
                    val req = JSONObject(String(bodyBytes, Charsets.UTF_8))
                    val expected = preferences.ensureLanAdminPin()
                    val token = verifyPinAndIssueToken(req.optString("pin"), expected)
                    if (token == null) {
                        writeJson(output, 401, JSONObject().put("ok", false).put("message", "配对码错误"))
                    } else {
                        _status.value = _status.value.copy(lastClientNote = "已授权局域网设备访问")
                        writeJson(output, 200, JSONObject().put("ok", true).put("token", token))
                    }
                    return
                }

                // All other /api/* routes strictly require verified Bearer token!
                if (!isAuthorized(headers["authorization"])) {
                    writeJson(output, 401, JSONObject().put("ok", false).put("message", "未授权，请先输入 6 位配对码"))
                    return
                }

                when {
                    method == "GET" && path == "/api/state" -> {
                        writeJson(output, 200, buildMaskedStateJson())
                    }
                    method == "POST" && path == "/api/appearance" -> {
                        val req = JSONObject(String(bodyBytes, Charsets.UTF_8))
                        req.optString("designStyle").takeIf { it.isNotBlank() }?.let { preferences.setDesignStyle(it) }
                        req.optString("standByStyleMode").takeIf { it.isNotBlank() }?.let { preferences.setStandByStyleMode(it) }
                        req.optString("motionTierId").takeIf { it.isNotBlank() }?.let { preferences.setMotionTier(it) }
                        writeJson(output, 200, JSONObject().put("ok", true))
                    }
                    method == "POST" && path == "/api/wallpaper" -> {
                        val req = JSONObject(String(bodyBytes, Charsets.UTF_8))
                        preferences.setDynamicWallpaperConfig(
                            mode = req.optString("mode").takeIf { it.isNotBlank() },
                            proceduralStyle = req.optString("proceduralStyle").takeIf { it.isNotBlank() }
                        )
                        writeJson(output, 200, JSONObject().put("ok", true))
                    }
                    method == "POST" && path == "/api/clock" -> {
                        val req = JSONObject(String(bodyBytes, Charsets.UTF_8))
                        preferences.setMultiClockConfig(
                            style = req.optString("style").takeIf { it.isNotBlank() },
                            shape = req.optString("shape").takeIf { it.isNotBlank() }
                        )
                        writeJson(output, 200, JSONObject().put("ok", true))
                    }
                    method == "POST" && path == "/api/font" -> {
                        val req = JSONObject(String(bodyBytes, Charsets.UTF_8))
                        val slot = req.optString("slot", "body")
                        val existing = preferences.currentConfig().typographyProfile.slotByName(slot)
                        preferences.updateFontSlot(
                            slot,
                            FontSlotSpec(
                                familyKey = req.optString("familyKey", existing.familyKey),
                                scale = req.optDouble("scale", existing.scale.toDouble()).toFloat(),
                                weight = req.optInt("weight", existing.weight),
                                letterSpacingSp = req.optDouble("letterSpacingSp", existing.letterSpacingSp.toDouble()).toFloat(),
                                externalPath = existing.externalPath
                            )
                        )
                        writeJson(output, 200, JSONObject().put("ok", true))
                    }
                    method == "POST" && path == "/api/font/reset" -> {
                        preferences.resetTypographyProfile()
                        writeJson(output, 200, JSONObject().put("ok", true))
                    }
                    method == "POST" && path == "/api/ai/select" -> {
                        val req = JSONObject(String(bodyBytes, Charsets.UTF_8))
                        preferences.setActiveAiProfile(req.optString("id"))
                        writeJson(output, 200, JSONObject().put("ok", true))
                    }
                    method == "POST" && path == "/api/ai/upsert" -> {
                        val req = JSONObject(String(bodyBytes, Charsets.UTF_8))
                        val id = req.optString("id").ifBlank { "custom_${System.currentTimeMillis()}" }
                        val existing = preferences.currentConfig().aiProfiles.firstOrNull { it.id == id }
                        val newKey = req.optString("apiKey").trim().ifBlank { existing?.apiKey.orEmpty() }
                        preferences.upsertAiApiProfile(
                            AiApiProfile(
                                id = id,
                                name = req.optString("name", existing?.name ?: "自定义模型"),
                                baseUrl = req.optString("baseUrl", existing?.baseUrl ?: "https://api.deepseek.com/v1"),
                                apiKey = newKey,
                                model = req.optString("model", existing?.model ?: "deepseek-chat"),
                                systemPrompt = req.optString("systemPrompt").ifBlank { existing?.systemPrompt ?: PenConfig.DEFAULT_HARNESS_PROMPT },
                                enableMcpTools = true
                            )
                        )
                        writeJson(output, 200, JSONObject().put("ok", true))
                    }
                    method == "POST" && path == "/api/ai/run" -> {
                        val req = JSONObject(String(bodyBytes, Charsets.UTF_8))
                        val prompt = req.optString("prompt").trim()
                        val handler = onRemoteAiPrompt
                        if (prompt.isBlank() || handler == null) {
                            writeJson(output, 400, JSONObject().put("ok", false).put("message", "AI 执行器未就绪"))
                        } else {
                            val (reply, tools) = handler(prompt)
                            writeJson(output, 200, JSONObject().put("ok", true).put("reply", reply).put("tools", tools))
                        }
                    }
                    method == "POST" && path == "/api/widgets/upsert" -> {
                        val req = JSONObject(String(bodyBytes, Charsets.UTF_8))
                        val id = req.optString("id").ifBlank { "cw_${System.currentTimeMillis()}" }
                        val title = req.optString("title", "自定义卡片").take(28)
                        val placement = req.optString("placement", CustomWidgetEntity.PLACEMENT_BOTH)
                        val dslRaw = req.optString("dslJson", "[]")
                        // Validate JSON syntax before storing
                        JSONArray(dslRaw)
                        database.customWidgetDao().upsert(
                            CustomWidgetEntity(
                                id = id,
                                title = title,
                                placement = placement,
                                widthDp = 196,
                                accentHex = 0xFF38BDF8,
                                dslJson = dslRaw
                            )
                        )
                        writeJson(output, 200, JSONObject().put("ok", true))
                    }
                    method == "POST" && path == "/api/widgets/delete" -> {
                        val req = JSONObject(String(bodyBytes, Charsets.UTF_8))
                        database.customWidgetDao().deleteById(req.optString("id"))
                        database.drawerSlotDao().deleteById("slot_cw_${req.optString("id")}")
                        writeJson(output, 200, JSONObject().put("ok", true))
                    }
                    method == "POST" && path == "/api/widgets/template" -> {
                        val req = JSONObject(String(bodyBytes, Charsets.UTF_8))
                        val tpl = CustomWidgetTemplates.findById(req.optString("templateId"))
                        if (tpl == null) {
                            writeJson(output, 404, JSONObject().put("ok", false).put("message", "未找到对应组件模板"))
                        } else {
                            snapshotManager.captureSnapshot("安装模板「${tpl.title}」前", "LAN")
                            database.customWidgetDao().upsert(tpl.toEntity())
                            val existingSlots = database.drawerSlotDao().getAllSnapshot()
                            if (existingSlots.none { it.type == DrawerSlotEntity.TYPE_CUSTOM_WIDGET && it.refId == tpl.id }) {
                                database.drawerSlotDao().upsert(
                                    DrawerSlotEntity(
                                        id = "slot_cw_${tpl.id}",
                                        type = DrawerSlotEntity.TYPE_CUSTOM_WIDGET,
                                        refId = tpl.id,
                                        title = tpl.title,
                                        sortOrder = existingSlots.size
                                    )
                                )
                            }
                            writeJson(output, 200, JSONObject().put("ok", true).put("message", "已安装模板「${tpl.title}」"))
                        }
                    }
                    method == "GET" && path == "/api/snapshots" -> {
                        val list = database.desktopSnapshotDao().getLatestSnapshot()
                        val recent = buildSnapshotsJson()
                        writeJson(output, 200, JSONObject().put("ok", true).put("hasLatest", list != null).put("snapshots", recent))
                    }
                    method == "POST" && path == "/api/snapshots/rollback" -> {
                        val req = runCatching { JSONObject(String(bodyBytes, Charsets.UTF_8)) }.getOrDefault(JSONObject())
                        val snapId = req.optLong("id", -1L)
                        val res = if (snapId > 0L) snapshotManager.restoreSnapshot(snapId) else snapshotManager.restoreLatestSnapshot()
                        res.fold(
                            onSuccess = { msg -> writeJson(output, 200, JSONObject().put("ok", true).put("message", msg)) },
                            onFailure = { err -> writeJson(output, 400, JSONObject().put("ok", false).put("message", err.message ?: "回滚失败")) }
                        )
                    }
                    method == "GET" && path == "/api/config/export" -> {
                        val jsonPayload = snapshotManager.exportFullConfigJson(includeSecrets = false)
                        writeResponse(output, 200, "application/json; charset=utf-8", jsonPayload.toByteArray(Charsets.UTF_8))
                    }
                    method == "POST" && path == "/api/config/import" -> {
                        val rawJson = String(bodyBytes, Charsets.UTF_8)
                        snapshotManager.importFullConfigJson(rawJson).fold(
                            onSuccess = { msg -> writeJson(output, 200, JSONObject().put("ok", true).put("message", msg)) },
                            onFailure = { err -> writeJson(output, 400, JSONObject().put("ok", false).put("message", err.message ?: "导入失败")) }
                        )
                    }
                    method == "POST" && path == "/api/mail/upsert" -> {
                        val req = JSONObject(String(bodyBytes, Charsets.UTF_8))
                        val addr = req.optString("emailAddress").trim()
                        val pass = req.optString("password")
                        if (!addr.contains("@") || pass.isBlank()) {
                            writeJson(output, 400, JSONObject().put("ok", false).put("message", "邮箱地址或授权码不能为空"))
                        } else {
                            val account = MailAccountEntity(
                                id = "mail_${addr.lowercase().hashCode().toUInt()}",
                                label = addr.substringBefore("@"),
                                emailAddress = addr,
                                username = addr,
                                passwordOrAuthCode = pass,
                                imapHost = req.optString("imapHost", "imap.qq.com").trim(),
                                imapPort = req.optInt("imapPort", 993),
                                smtpHost = req.optString("smtpHost", "smtp.qq.com").trim(),
                                smtpPort = req.optInt("smtpPort", 465),
                                useSsl = true,
                                isDefault = true
                            )
                            database.mailDao().upsertAccount(account)
                            onMailAccountAdded?.invoke()
                            writeJson(output, 200, JSONObject().put("ok", true))
                        }
                    }
                    method == "POST" && path == "/api/mail/delete" -> {
                        val req = JSONObject(String(bodyBytes, Charsets.UTF_8))
                        val id = req.optString("id")
                        database.mailDao().deleteAccount(id)
                        database.mailDao().deleteMessagesForAccount(id)
                        writeJson(output, 200, JSONObject().put("ok", true))
                    }
                    method == "POST" && path == "/api/upload" -> {
                        val kind = query["kind"].orEmpty()
                        val rawName = query["name"].orEmpty()
                        val slot = query["slot"].orEmpty().ifBlank { "poetry" }
                        val resMsg = handleSafeUpload(kind, slot, rawName, bodyBytes)
                        writeJson(output, 200, JSONObject().put("ok", true).put("message", resMsg))
                    }
                    else -> {
                        writeJson(output, 404, JSONObject().put("ok", false).put("message", "Not Found"))
                    }
                }
            } catch (e: Exception) {
                writeJson(output, 400, JSONObject().put("ok", false).put("message", e.message ?: "请求异常"))
            }
        }
    }

    private suspend fun handleSafeUpload(kind: String, slot: String, rawName: String, bytes: ByteArray): String {
        if (bytes.isEmpty()) throw IllegalArgumentException("上传文件为空")
        val ext = rawName.substringAfterLast('.', "").lowercase()
        val allowedExt = when (kind) {
            "font" -> setOf("ttf", "otf")
            else -> setOf("jpg", "jpeg", "png", "webp", "gif", "mp4")
        }
        if (ext !in allowedExt) throw IllegalArgumentException("不支持的文件类型 .$ext")
        val maxBytes = if (ext == "mp4") 24 * 1024 * 1024 else 12 * 1024 * 1024
        if (bytes.size > maxBytes) throw IllegalArgumentException("文件体积超过限制")

        val baseDir = File(context.filesDir, "penos_media").apply { mkdirs() }
        val safeFile = File(baseDir, "${kind}_${System.currentTimeMillis()}.$ext")
        if (!safeFile.canonicalPath.startsWith(baseDir.canonicalPath + File.separator)) {
            throw SecurityException("非法路径")
        }
        safeFile.writeBytes(bytes)

        return when {
            kind == "font" -> {
                val probe = SafeMediaImporter.validateAndProbeFontFile(safeFile)
                if (probe.isFailure) {
                    safeFile.delete()
                    throw IllegalArgumentException(probe.exceptionOrNull()?.message ?: "字体渲染校验失败")
                }
                val cur = preferences.currentConfig().typographyProfile.slotByName(slot)
                preferences.updateFontSlot(slot, cur.copy(familyKey = "external", externalPath = safeFile.absolutePath))
                "字体已通过渲染检测并无线推送至 $slot 通道"
            }
            ext == "gif" -> {
                preferences.setDynamicWallpaperConfig(mode = PenConfig.DYN_WALLPAPER_GIF, mediaPath = safeFile.absolutePath)
                "GIF 动态壁纸已无线应用"
            }
            ext == "mp4" -> {
                preferences.setDynamicWallpaperConfig(mode = PenConfig.DYN_WALLPAPER_MP4, mediaPath = safeFile.absolutePath)
                "MP4 视频壁纸已无线应用"
            }
            else -> {
                val isLight = preferences.currentConfig().isAppleLightDesktop()
                preferences.setWallpaperForTheme(isLight, PenConfig.WALLPAPER_CUSTOM, safeFile.absolutePath)
                preferences.setDynamicWallpaperConfig(mode = PenConfig.DYN_WALLPAPER_STATIC)
                BitmapDecodeCache.clear()
                "自定义壁纸已无线应用"
            }
        }
    }

    private suspend fun buildMaskedStateJson(): JSONObject {
        val cfg = preferences.currentConfig()
        val widgets = database.customWidgetDao().getAllSnapshot()
        val accounts = database.mailDao().getAccountsSnapshot()

        val typoObj = JSONObject()
        listOf("clock", "title", "body", "poetry", "metric").forEach { slot ->
            val s = cfg.typographyProfile.slotByName(slot)
            typoObj.put(
                slot,
                JSONObject()
                    .put("familyKey", s.familyKey)
                    .put("scale", s.scale.toDouble())
                    .put("weight", s.weight)
                    .put("letterSpacingSp", s.letterSpacingSp.toDouble())
            )
        }

        val profilesArr = JSONArray()
        cfg.aiProfiles.forEach { p ->
            profilesArr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("baseUrl", p.baseUrl)
                    .put("model", p.model)
                    .put("systemPrompt", p.systemPrompt)
                    .put("hasKey", p.apiKey.isNotBlank()) // Never echo raw API key!
            )
        }

        val widgetsArr = JSONArray()
        widgets.forEach { w ->
            widgetsArr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("title", w.title)
                    .put("placement", w.placement)
            )
        }

        val mailArr = JSONArray()
        accounts.forEach { a ->
            mailArr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("emailAddress", a.emailAddress)
                    .put("imapHost", a.imapHost)
                    .put("imapPort", a.imapPort)
            )
        }

        return JSONObject()
            .put("designStyle", cfg.designStyle)
            .put("standByStyleMode", cfg.standByStyleMode)
            .put("motionTierId", cfg.motionTierId)
            .put("dynamicWallpaperMode", cfg.dynamicWallpaperMode)
            .put("proceduralWallpaperStyle", cfg.proceduralWallpaperStyle)
            .put(
                "multiClock",
                JSONObject()
                    .put("style", cfg.multiClock.style)
                    .put("shape", cfg.multiClock.shape)
                    .put("showSeconds", cfg.multiClock.showSeconds)
            )
            .put("typography", typoObj)
            .put("activeAiProfileId", cfg.activeAiProfileId)
            .put("aiProfiles", profilesArr)
            .put("customWidgets", widgetsArr)
            .put("mailAccounts", mailArr)
            .put("snapshots", buildSnapshotsJson())
    }

    private suspend fun buildSnapshotsJson(): JSONArray {
        val arr = JSONArray()
        val latest = database.desktopSnapshotDao().getLatestSnapshot()
        if (latest != null) {
            arr.put(
                JSONObject()
                    .put("id", latest.id)
                    .put("label", latest.label)
                    .put("source", latest.source)
                    .put("createdAt", latest.createdAt)
            )
        }
        return arr
    }

    private fun resolveLanIpv4(): String {
        return runCatching {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in Collections.list(nif.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress.orEmpty()
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return@runCatching ip
                        }
                    }
                }
            }
            "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null else {
                val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                k to v
            }
        }.toMap()
    }

    private fun readLineCrlf(input: BufferedInputStream): String? {
        val baos = ByteArrayOutputStream()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) return if (baos.size() == 0) null else baos.toString("UTF-8")
            if (b == '\n'.code) {
                val bytes = baos.toByteArray()
                val len = if (prev == '\r'.code && bytes.isNotEmpty()) bytes.size - 1 else bytes.size
                return String(bytes, 0, len, Charsets.UTF_8)
            }
            baos.write(b)
            prev = b
        }
    }

    private fun readExact(input: BufferedInputStream, len: Int): ByteArray {
        val buf = ByteArray(len)
        var readTotal = 0
        while (readTotal < len) {
            val n = input.read(buf, readTotal, len - readTotal)
            if (n <= 0) break
            readTotal += n
        }
        return if (readTotal == len) buf else buf.copyOf(readTotal)
    }

    private fun writeJson(out: OutputStream, status: Int, obj: JSONObject) {
        writeResponse(out, status, "application/json; charset=utf-8", obj.toString().toByteArray(Charsets.UTF_8))
    }

    private fun writeResponse(out: OutputStream, status: Int, contentType: String, payload: ByteArray) {
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Error"
        }
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${payload.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.write(payload)
        out.flush()
    }
}
