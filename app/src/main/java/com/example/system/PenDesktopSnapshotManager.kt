package com.example.system

import com.example.data.local.CustomWidgetEntity
import com.example.data.local.DesktopSnapshotEntity
import com.example.data.local.DrawerSlotEntity
import com.example.data.local.PenDatabase
import com.example.data.prefs.PenConfig
import com.example.data.prefs.PenPreferencesRepository
import com.example.data.prefs.PenPrefsCodec
import com.example.data.remote.Api21NetworkFactory
import com.example.ui.theme.GranularTypographyProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * PenOS 5.0 Snapshot, Full-Config Backup/Restore & WebDAV Cloud Config Sync Manager.
 *
 * Security invariants:
 * - Never includes raw mail passwords in any export or snapshot.
 * - Redacts AI API keys by default on manual JSON export and ALWAYS redacts secrets on WebDAV upload.
 * - Restores desktop appearance, typography, clock, gesture tuning, custom widgets, and Apple TV drawer slots cleanly.
 */
class PenDesktopSnapshotManager(
    private val prefs: PenPreferencesRepository,
    private val database: PenDatabase
) {
    private val httpClient by lazy {
        Api21NetworkFactory.createClient(connectTimeoutSec = 10, readTimeoutSec = 14)
    }

    suspend fun captureSnapshot(label: String, source: String = "MCP"): DesktopSnapshotEntity =
        withContext(Dispatchers.IO) {
            val payload = buildConfigJson(includeSecrets = false, includeAiProfiles = false)
            val entity = DesktopSnapshotEntity(
                label = label.trim().ifBlank { "桌面快照" }.take(48),
                source = source,
                payloadJson = payload,
                createdAt = System.currentTimeMillis()
            )
            val id = database.desktopSnapshotDao().insert(entity)
            database.desktopSnapshotDao().trimToLatestTen()
            entity.copy(id = id)
        }

    suspend fun restoreLatestSnapshot(): Result<String> = withContext(Dispatchers.IO) {
        val latest = database.desktopSnapshotDao().getLatestSnapshot()
            ?: return@withContext Result.failure(IllegalStateException("暂无可回滚的桌面快照"))
        restoreSnapshot(latest.id)
    }

    suspend fun restoreSnapshot(snapshotId: Long): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = database.desktopSnapshotDao().getById(snapshotId)
                ?: throw IllegalArgumentException("未找到编号为 #$snapshotId 的快照")
            applyConfigJson(snapshot.payloadJson)
            "已回滚至快照「${snapshot.label}」"
        }
    }

    suspend fun exportFullConfigJson(includeSecrets: Boolean = false): String =
        withContext(Dispatchers.IO) {
            buildConfigJson(includeSecrets = includeSecrets, includeAiProfiles = true)
        }

    suspend fun importFullConfigJson(json: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Auto-capture safety snapshot before overwriting config
            captureSnapshot(label = "导入全量配置前自动备份", source = "MANUAL")
            applyConfigJson(json)
            "全量配置导入成功（已自动保存导入前快照）"
        }
    }

    suspend fun syncFullConfigWithWebDav(uploadOnly: Boolean = false): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cfg = prefs.currentConfig()
                val baseUrl = cfg.webDavUrl.trim()
                if (baseUrl.isBlank() || (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://"))) {
                    throw IllegalArgumentException("请先填写有效的坚果云 / WebDAV 地址")
                }
                if (cfg.webDavUser.isBlank() || cfg.webDavPass.isBlank()) {
                    throw IllegalArgumentException("请先填写 WebDAV 账号与应用密码")
                }

                val targetUrl = if (baseUrl.endsWith(".json")) {
                    baseUrl.substringBeforeLast("/") + "/penos_full_config.json"
                } else {
                    baseUrl.trimEnd('/') + "/penos_full_config.json"
                }
                val authHeader = Credentials.basic(cfg.webDavUser, cfg.webDavPass)

                if (!uploadOnly) {
                    val getReq = Request.Builder()
                        .url(targetUrl)
                        .header("Authorization", authHeader)
                        .get()
                        .build()
                    httpClient.newCall(getReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val remoteJson = resp.body?.string().orEmpty()
                            if (remoteJson.isNotBlank() && remoteJson.trimStart().startsWith("{")) {
                                captureSnapshot(label = "WebDAV 拉取覆盖前备份", source = "WEBDAV")
                                applyConfigJson(remoteJson)
                                return@runCatching "已从 WebDAV 同步并应用远端配置（本地旧配置已快照备份）"
                            }
                        } else if (resp.code != 404) {
                            throw IllegalStateException("WebDAV 拉取失败 HTTP ${resp.code}")
                        }
                    }
                }

                // Always redact raw API keys & passwords before cloud upload
                val redactedPayload = buildConfigJson(includeSecrets = false, includeAiProfiles = true)
                val putReq = Request.Builder()
                    .url(targetUrl)
                    .header("Authorization", authHeader)
                    .put(redactedPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(putReq).execute().use { resp ->
                    if (!resp.isSuccessful && resp.code !in listOf(200, 201, 204)) {
                        throw IllegalStateException("WebDAV 上传失败 HTTP ${resp.code}")
                    }
                }
                "已将脱敏全量配置上传至 WebDAV（penos_full_config.json）"
            }
        }

    private suspend fun buildConfigJson(
        includeSecrets: Boolean,
        includeAiProfiles: Boolean
    ): String {
        val cfg = prefs.currentConfig()
        val widgets = database.customWidgetDao().getAllSnapshot()
        val slots = database.drawerSlotDao().getAllSnapshot()

        val root = JSONObject()
        root.put("schemaVersion", 5)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("secretsIncluded", includeSecrets)

        val appearance = JSONObject()
            .put("designStyle", cfg.designStyle)
            .put("standByStyleMode", cfg.standByStyleMode)
            .put("desktopDeckMode", cfg.desktopDeckMode)
            .put("desktopEngineMode", cfg.desktopEngineMode)
            .put("zeroOverdrawMode", cfg.zeroOverdrawMode)
            .put("darkWallpaperPresetId", cfg.darkWallpaperPresetId)
            .put("lightWallpaperPresetId", cfg.lightWallpaperPresetId)
            .put("dynamicWallpaperMode", cfg.dynamicWallpaperMode)
            .put("proceduralWallpaperStyle", cfg.proceduralWallpaperStyle)
            .put("dynamicWallpaperMediaPath", cfg.dynamicWallpaperMediaPath)
            .put("motionTierId", cfg.motionTierId)
            .put("standByTimeoutSec", cfg.standByTimeoutSec)
            .put("standByBrightness", cfg.standByBrightness.toDouble())
            .put("standByRightWidget", cfg.standByRightWidget)
            .put("standByClockColor", cfg.standByClockColor)
            .put("pomodoroMinutes", cfg.pomodoroMinutes)
            .put("nightDimAlpha", cfg.nightDimAlpha.toDouble())
            .put("enabledWidgetIds", JSONArray(cfg.enabledWidgetIds))
        root.put("appearance", appearance)

        val clock = JSONObject()
            .put("style", cfg.multiClock.style)
            .put("shape", cfg.multiClock.shape)
            .put("showSeconds", cfg.multiClock.showSeconds)
            .put("accentHex", cfg.multiClock.accentHex)
        root.put("multiClock", clock)

        root.put("typography", JSONObject(PenPrefsCodec.encodeTypographyProfile(cfg.typographyProfile)))

        val gestures = JSONObject()
            .put("swipeSensitivityDp", cfg.swipeSensitivityDp.toDouble())
            .put("gestureHapticsEnabled", cfg.gestureHapticsEnabled)
            .put("mcpPreviewBeforeApply", cfg.mcpPreviewBeforeApply)
            .put("mailAutoSyncEnabled", cfg.mailAutoSyncEnabled)
        root.put("controls", gestures)

        val widgetsArr = JSONArray()
        widgets.forEach { w ->
            widgetsArr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("title", w.title)
                    .put("placement", w.placement)
                    .put("widthDp", w.widthDp)
                    .put("accentHex", w.accentHex)
                    .put("dslJson", w.dslJson)
                    .put("sortOrder", w.sortOrder)
            )
        }
        root.put("customWidgets", widgetsArr)

        val slotsArr = JSONArray()
        slots.forEach { s ->
            slotsArr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("type", s.type)
                    .put("refId", s.refId)
                    .put("title", s.title)
                    .put("subtitle", s.subtitle)
                    .put("accentHex", s.accentHex)
                    .put("sortOrder", s.sortOrder)
            )
        }
        root.put("drawerSlots", slotsArr)

        if (includeAiProfiles) {
            val profilesToSave = cfg.aiProfiles.map { p ->
                if (includeSecrets) p else p.copy(apiKey = "")
            }
            root.put("activeAiProfileId", cfg.activeAiProfileId)
            root.put("aiProfiles", JSONArray(PenPrefsCodec.encodeAiApiProfiles(profilesToSave)))
            root.put("externalMcpUrl", cfg.externalMcpUrl)
        }

        return root.toString(2)
    }

    private suspend fun applyConfigJson(rawJson: String) {
        val root = JSONObject(rawJson.trim())

        root.optJSONObject("appearance")?.let { app ->
            app.optString("designStyle").takeIf { it.isNotBlank() }?.let { prefs.setDesignStyle(it) }
            app.optString("standByStyleMode").takeIf { it.isNotBlank() }?.let { prefs.setStandByStyleMode(it) }
            app.optString("desktopDeckMode").takeIf { it.isNotBlank() }?.let { prefs.setDesktopDeckMode(it) }
            app.optString("desktopEngineMode").takeIf { it.isNotBlank() }?.let { prefs.setDesktopEngineMode(it) }
            if (app.has("zeroOverdrawMode")) {
                prefs.setZeroOverdrawMode(app.optBoolean("zeroOverdrawMode", true))
            }
            app.optString("darkWallpaperPresetId").takeIf { it.isNotBlank() }?.let {
                prefs.setWallpaperForTheme(isLight = false, presetId = it)
            }
            app.optString("lightWallpaperPresetId").takeIf { it.isNotBlank() }?.let {
                prefs.setWallpaperForTheme(isLight = true, presetId = it)
            }
            val dynMode = app.optString("dynamicWallpaperMode").takeIf { it.isNotBlank() }
            val procStyle = app.optString("proceduralWallpaperStyle").takeIf { it.isNotBlank() }
            val mediaPath = if (app.has("dynamicWallpaperMediaPath")) app.optString("dynamicWallpaperMediaPath") else null
            if (dynMode != null || procStyle != null || mediaPath != null) {
                prefs.setDynamicWallpaperConfig(mode = dynMode, proceduralStyle = procStyle, mediaPath = mediaPath)
            }
            app.optString("motionTierId").takeIf { it.isNotBlank() }?.let { prefs.setMotionTier(it) }
            prefs.setStandByConfig(
                timeoutSec = if (app.has("standByTimeoutSec")) app.optInt("standByTimeoutSec", 30) else null,
                brightness = if (app.has("standByBrightness")) app.optDouble("standByBrightness", 0.08).toFloat() else null,
                rightWidget = app.optString("standByRightWidget").takeIf { it.isNotBlank() },
                clockColor = app.optString("standByClockColor").takeIf { it.isNotBlank() }
            )
            if (app.has("pomodoroMinutes")) {
                prefs.setPomodoroMinutes(app.optInt("pomodoroMinutes", 25))
            }
            if (app.has("nightDimAlpha")) {
                prefs.setNightDimAlpha(app.optDouble("nightDimAlpha", 0.0).toFloat())
            }
            app.optJSONArray("enabledWidgetIds")?.let { arr ->
                val list = (0 until arr.length()).mapNotNull { i ->
                    arr.optString(i).takeIf { it.isNotBlank() && PenConfig.ALL_WIDGET_IDS.contains(it) }
                }
                if (list.isNotEmpty()) prefs.setEnabledWidgets(list)
            }
        }

        root.optJSONObject("multiClock")?.let { clk ->
            prefs.setMultiClockConfig(
                style = clk.optString("style").takeIf { it.isNotBlank() },
                shape = clk.optString("shape").takeIf { it.isNotBlank() },
                showSeconds = if (clk.has("showSeconds")) clk.optBoolean("showSeconds") else null,
                accentHex = if (clk.has("accentHex")) clk.optLong("accentHex") else null
            )
        }

        root.optJSONObject("typography")?.let { typoObj ->
            val decoded = PenPrefsCodec.decodeTypographyProfile(typoObj.toString())
            prefs.updateFontSlot(GranularTypographyProfile.SLOT_CLOCK, decoded.clock)
            prefs.updateFontSlot(GranularTypographyProfile.SLOT_TITLE, decoded.title)
            prefs.updateFontSlot(GranularTypographyProfile.SLOT_BODY, decoded.body)
            prefs.updateFontSlot(GranularTypographyProfile.SLOT_POETRY, decoded.poetry)
            prefs.updateFontSlot(GranularTypographyProfile.SLOT_METRIC, decoded.metric)
        }

        root.optJSONObject("controls")?.let { ctrl ->
            if (ctrl.has("swipeSensitivityDp")) {
                prefs.setSwipeSensitivityDp(ctrl.optDouble("swipeSensitivityDp", 34.0).toFloat())
            }
            if (ctrl.has("gestureHapticsEnabled")) {
                prefs.setGestureHapticsEnabled(ctrl.optBoolean("gestureHapticsEnabled", true))
            }
            if (ctrl.has("mcpPreviewBeforeApply")) {
                prefs.setMcpPreviewBeforeApply(ctrl.optBoolean("mcpPreviewBeforeApply", true))
            }
            if (ctrl.has("mailAutoSyncEnabled")) {
                prefs.setMailAutoSyncEnabled(ctrl.optBoolean("mailAutoSyncEnabled", true))
            }
        }

        root.optJSONArray("customWidgets")?.let { arr ->
            val existing = database.customWidgetDao().getAllSnapshot()
            val incomingIds = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                if (id.isBlank()) continue
                incomingIds.add(id)
                database.customWidgetDao().upsert(
                    CustomWidgetEntity(
                        id = id,
                        title = o.optString("title", "自定义小组件"),
                        placement = o.optString("placement", CustomWidgetEntity.PLACEMENT_BOTH),
                        widthDp = o.optInt("widthDp", 196).coerceIn(150, 280),
                        accentHex = o.optLong("accentHex", 0xFF38BDF8L),
                        dslJson = o.optString("dslJson", "[]"),
                        sortOrder = o.optInt("sortOrder", i),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            existing.forEach { old ->
                if (!incomingIds.contains(old.id)) {
                    database.customWidgetDao().deleteById(old.id)
                }
            }
        }

        root.optJSONArray("drawerSlots")?.let { arr ->
            val existing = database.drawerSlotDao().getAllSnapshot()
            val incomingIds = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                if (id.isBlank()) continue
                incomingIds.add(id)
                database.drawerSlotDao().upsert(
                    DrawerSlotEntity(
                        id = id,
                        type = o.optString("type", DrawerSlotEntity.TYPE_ACTION_TILE),
                        refId = o.optString("refId"),
                        title = o.optString("title", id),
                        subtitle = o.optString("subtitle"),
                        accentHex = o.optLong("accentHex", 0xFF38BDF8L),
                        sortOrder = o.optInt("sortOrder", i)
                    )
                )
            }
            existing.forEach { old ->
                if (!incomingIds.contains(old.id)) {
                    database.drawerSlotDao().deleteById(old.id)
                }
            }
        }

        // Preserve existing local API keys when importing a redacted config!
        root.optJSONArray("aiProfiles")?.let { arr ->
            val existingById = prefs.currentConfig().aiProfiles.associateBy { it.id }
            val imported = PenPrefsCodec.decodeAiApiProfiles(
                arr.toString(),
                "https://api.deepseek.com/v1",
                "",
                "deepseek-chat"
            )
            imported.forEach { p ->
                val preservedKey = p.apiKey.ifBlank { existingById[p.id]?.apiKey.orEmpty() }
                prefs.upsertAiApiProfile(p.copy(apiKey = preservedKey))
            }
            root.optString("activeAiProfileId").takeIf { it.isNotBlank() }?.let {
                prefs.setActiveAiProfile(it)
            }
        }
        if (root.has("externalMcpUrl")) {
            prefs.saveExternalMcpUrl(root.optString("externalMcpUrl"))
        }
    }
}
