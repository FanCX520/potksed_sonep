package com.example.ui

import com.example.data.local.CalendarEventEntity
import com.example.data.local.CustomWidgetEntity
import com.example.data.local.DrawerSlotEntity
import com.example.data.local.PenDatabase
import com.example.data.local.TodoItemEntity
import com.example.data.local.VocabWordEntity
import com.example.data.prefs.PenConfig
import com.example.data.prefs.PenPreferencesRepository
import com.example.data.remote.PenMcpToolResult
import com.example.data.remote.WeatherCityOption
import com.example.data.remote.WeatherRepository
import com.example.data.remote.WmoWeatherMapper
import com.example.system.InstalledAppInfo
import com.example.system.WhiteNoiseEngine
import com.example.system.WhiteNoiseType
import com.example.ui.theme.FontSlotSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * PenOS 4.0 DeepSeek Harness-Grade Full-Control MCP Tool Executor.
 * Decoupled from PenOsViewModel to keep both files clean and maintainable.
 * Strictly declarative — zero dynamic code loading, and email drafting ALWAYS requires
 * explicit user confirmation in MailScreen before SMTP transmission.
 */
class PenHarnessExecutor(
    private val prefs: PenPreferencesRepository,
    private val database: PenDatabase,
    private val weatherRepo: WeatherRepository,
    private val whiteNoise: WhiteNoiseEngine,
    private val getIsoDate: () -> String,
    private val getInstalledApps: () -> List<InstalledAppInfo>,
    private val onSetWeatherSource: (String) -> Unit,
    private val onStartPomodoro: (Int) -> Unit,
    private val onSetBrightness: (Float) -> Unit,
    private val onSetVolume: (Float) -> Unit,
    private val onLaunchApp: (String) -> Unit,
    private val onPresentMailDraft: (to: String, subject: String, body: String) -> Unit
) {

    fun isReadOnlyTool(toolName: String): Boolean = toolName in setOf(
        "inspect_penos_state",
        "list_recent_emails",
        "read_email",
        "draft_email"
    )

    fun describeChange(toolName: String, args: JSONObject): String = when (toolName) {
        "create_custom_widget" -> {
            val title = args.optString("title", "自定义小组件").take(18)
            val place = args.optString("placement", "BOTH")
            val count = args.optJSONArray("blocks")?.length() ?: 0
            "新建/更新小组件「$title」($place · ${count}个块)"
        }
        "delete_custom_widget" -> "删除自定义小组件 ID=${args.optString("id")}"
        "manage_drawer" -> {
            val act = if (args.optString("action", "add") == "remove") "移除" else "添加到"
            "${act}上滑抽屉：「${args.optString("title").ifBlank { args.optString("ref_id") }}」"
        }
        "set_font_slot" -> {
            val slot = args.optString("slot", "body")
            val fam = args.optString("family").ifBlank { "保持" }
            val scale = if (args.has("scale")) " ${"%.2f".format(args.optDouble("scale"))}x" else ""
            "调整 $slot 字体 → $fam$scale"
        }
        "switch_theme" -> {
            val parts = listOfNotNull(
                args.optString("desktop_style").takeIf { it.isNotBlank() }?.let { "主题=$it" },
                args.optString("standby_style").takeIf { it.isNotBlank() }?.let { "StandBy=$it" },
                args.optString("motion_tier").takeIf { it.isNotBlank() }?.let { "动效=$it" }
            )
            "切换外观：${parts.joinToString(" · ").ifBlank { "默认" }}"
        }
        "set_wallpaper_mode" -> "切换动态壁纸：${args.optString("mode")} ${args.optString("procedural_style")}".trim()
        "set_clock_widget_style" -> "切换时钟表盘：${args.optString("style")} / ${args.optString("shape").ifBlank { "默认轮廓" }}"
        "manage_widgets" -> "${if (args.optBoolean("enable", true)) "开启" else "隐藏"}内置组件 ${args.optString("widget_id")}"
        "switch_ai_profile" -> "切换 AI 模型预设 → ${args.optString("profile_name_or_id")}"
        "add_todo" -> "新增待办：「${args.optString("title").take(20)}」"
        "add_calendar_event" -> "新增日程：${args.optString("time_hhmm", "08:30")} ${args.optString("title").take(18)}"
        "switch_weather_city" -> "切换天气城市 → ${args.optString("city_name")}"
        "start_pomodoro" -> "开启 ${args.optInt("minutes", 25)} 分钟番茄专注"
        "control_white_noise" -> "白噪音：${args.optString("action")} ${args.optString("sound")}".trim()
        "adjust_device" -> "调节硬件亮度/音量/护眼"
        "add_vocab_word" -> "收录生词「${args.optString("word")}」"
        "launch_app" -> "启动应用「${args.optString("app_query")}」"
        else -> "执行 MCP 工具 $toolName"
    }

    suspend fun execute(toolName: String, args: JSONObject): PenMcpToolResult {
        return when (toolName) {
            "inspect_penos_state" -> inspectState()
            "create_custom_widget" -> createCustomWidget(args)
            "delete_custom_widget" -> deleteCustomWidget(args)
            "manage_drawer" -> manageDrawer(args)
            "set_font_slot" -> setFontSlot(args)
            "switch_theme" -> switchTheme(args)
            "set_wallpaper_mode" -> setWallpaperMode(args)
            "set_clock_widget_style" -> setClockWidgetStyle(args)
            "manage_widgets" -> manageBuiltinWidgets(args)
            "switch_ai_profile" -> switchAiProfile(args)
            "add_todo" -> addTodo(args)
            "add_calendar_event" -> addCalendarEvent(args)
            "switch_weather_city" -> switchWeatherCity(args)
            "start_pomodoro" -> {
                val mins = args.optInt("minutes", 25).coerceIn(1, 120)
                onStartPomodoro(mins)
                PenMcpToolResult(toolName, "已开启 $mins 分钟全屏番茄专注", true)
            }
            "control_white_noise" -> controlWhiteNoise(args)
            "adjust_device" -> adjustDevice(args)
            "add_vocab_word" -> addVocabWord(args)
            "launch_app" -> launchAppByQuery(args)
            "list_recent_emails" -> listRecentEmails(args)
            "read_email" -> readEmail(args)
            "draft_email" -> draftEmail(args)
            else -> PenMcpToolResult(toolName, "未知工具：$toolName", false)
        }
    }

    private suspend fun inspectState(): PenMcpToolResult {
        val cfg = prefs.currentConfig()
        val widgets = database.customWidgetDao().getAllSnapshot()
        val slots = database.drawerSlotDao().getAllSnapshot()
        val mails = database.mailDao().getRecentMessagesSnapshot(10)
        val unread = mails.count { it.isUnread }
        val apps = getInstalledApps().take(12).joinToString(",") { "${it.label}(${it.packageName})" }
        val profiles = cfg.aiProfiles.joinToString(",") { it.name }
        val summary = buildString {
            append("主题=${cfg.designStyle}, StandBy=${cfg.standByStyleMode}, 动效=${cfg.motionTierId}; ")
            append("动态壁纸=${cfg.dynamicWallpaperMode}/${cfg.proceduralWallpaperStyle}; ")
            append("多样式时钟=${cfg.multiClock.style}/${cfg.multiClock.shape}; ")
            append("字体缩放(时/标/正/诗/数)=${cfg.typographyProfile.clock.scale}/${cfg.typographyProfile.title.scale}/${cfg.typographyProfile.body.scale}/${cfg.typographyProfile.poetry.scale}/${cfg.typographyProfile.metric.scale}; ")
            append("自定义小组件(${widgets.size})=${widgets.joinToString(",") { "${it.id}:${it.title}[${it.placement}]" }}; ")
            append("上滑抽屉槽位=${slots.size}; 未读邮件=$unread/${mails.size}; ")
            append("AI模型预设=[$profiles](当前:${cfg.activeAiProfile().name}); 应用示例=[$apps]")
        }
        return PenMcpToolResult("inspect_penos_state", summary, true)
    }

    private suspend fun createCustomWidget(args: JSONObject): PenMcpToolResult {
        val title = args.optString("title").trim().ifBlank { "AI 自定义卡片" }.take(28)
        val rawId = args.optString("id").trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val id = rawId.ifBlank { "ai_cw_${System.currentTimeMillis() % 100000}" }
        val placement = args.optString("placement", CustomWidgetEntity.PLACEMENT_BOTH).uppercase().let {
            if (it in setOf(CustomWidgetEntity.PLACEMENT_DESKTOP, CustomWidgetEntity.PLACEMENT_DRAWER, CustomWidgetEntity.PLACEMENT_BOTH)) {
                it
            } else {
                CustomWidgetEntity.PLACEMENT_BOTH
            }
        }
        val widthDp = args.optInt("width_dp", 196).coerceIn(150, 280)
        val accentHex = parseHexColor(args.optString("accent_hex", "#38BDF8"))
        val blocks = args.optJSONArray("blocks") ?: JSONArray().apply {
            put(JSONObject().put("type", "header").put("title", title).put("badge", "AI"))
            put(JSONObject().put("type", "text").put("content", "由 PenOS Harness MCP 实时生成"))
        }

        val entity = CustomWidgetEntity(
            id = id,
            title = title,
            placement = placement,
            widthDp = widthDp,
            accentHex = accentHex,
            dslJson = blocks.toString(),
            updatedAt = System.currentTimeMillis()
        )
        database.customWidgetDao().upsert(entity)

        // Ensure it also appears in Apple TV drawer if placement includes DRAWER
        if (placement != CustomWidgetEntity.PLACEMENT_DESKTOP) {
            val existingSlots = database.drawerSlotDao().getAllSnapshot()
            if (existingSlots.none { it.type == DrawerSlotEntity.TYPE_CUSTOM_WIDGET && it.refId == id }) {
                database.drawerSlotDao().upsert(
                    DrawerSlotEntity(
                        id = "slot_cw_$id",
                        type = DrawerSlotEntity.TYPE_CUSTOM_WIDGET,
                        refId = id,
                        title = title,
                        sortOrder = existingSlots.size
                    )
                )
            }
        }
        return PenMcpToolResult("create_custom_widget", "已创建并应用自定义小组件「$title」(id=$id, 位置=$placement)", true)
    }

    private suspend fun deleteCustomWidget(args: JSONObject): PenMcpToolResult {
        val id = args.optString("id").trim()
        if (id.isBlank()) return PenMcpToolResult("delete_custom_widget", "缺少小组件 ID", false)
        database.customWidgetDao().deleteById(id)
        database.drawerSlotDao().deleteById("slot_cw_$id")
        return PenMcpToolResult("delete_custom_widget", "已删除自定义小组件 $id", true)
    }

    private suspend fun manageDrawer(args: JSONObject): PenMcpToolResult {
        val action = args.optString("action", "add").lowercase()
        val type = args.optString("type", DrawerSlotEntity.TYPE_ACTION_TILE).uppercase()
        val refId = args.optString("ref_id").trim()
        val title = args.optString("title").trim().ifBlank { refId }
        val existing = database.drawerSlotDao().getAllSnapshot()

        return if (action == "remove") {
            val target = existing.firstOrNull { it.refId == refId || it.id == refId || it.title == title }
            if (target != null) {
                database.drawerSlotDao().deleteById(target.id)
                PenMcpToolResult("manage_drawer", "已从 Apple TV 抽屉移除「${target.title}」", true)
            } else {
                PenMcpToolResult("manage_drawer", "未找到对应抽屉项 $refId", false)
            }
        } else {
            val slotId = "slot_${type.lowercase()}_${refId.ifBlank { System.currentTimeMillis().toString() }}"
            database.drawerSlotDao().upsert(
                DrawerSlotEntity(
                    id = slotId,
                    type = type,
                    refId = refId,
                    title = title,
                    sortOrder = existing.size
                )
            )
            PenMcpToolResult("manage_drawer", "已添加「$title」到 Apple TV 上滑抽屉", true)
        }
    }

    private suspend fun setFontSlot(args: JSONObject): PenMcpToolResult {
        val slot = args.optString("slot", "body").lowercase()
        val existing = prefs.currentConfig().typographyProfile.slotByName(slot)
        val next = FontSlotSpec(
            familyKey = args.optString("family").takeIf { it.isNotBlank() } ?: existing.familyKey,
            scale = if (args.has("scale")) args.optDouble("scale", existing.scale.toDouble()).toFloat() else existing.scale,
            weight = if (args.has("weight")) args.optInt("weight", existing.weight) else existing.weight,
            letterSpacingSp = if (args.has("letter_spacing")) {
                args.optDouble("letter_spacing", existing.letterSpacingSp.toDouble()).toFloat()
            } else existing.letterSpacingSp,
            externalPath = existing.externalPath
        )
        prefs.updateFontSlot(slot, next)
        return PenMcpToolResult("set_font_slot", "已更新 $slot 字体(${next.familyKey}, ${"%.2f".format(next.scale)}x, 字重${next.weight})", true)
    }

    private suspend fun switchTheme(args: JSONObject): PenMcpToolResult {
        val notes = mutableListOf<String>()
        val style = args.optString("desktop_style")
        if (style.isNotBlank()) {
            val normalized = if (style.contains("light", ignoreCase = true)) PenConfig.STYLE_APPLE_LIGHT else PenConfig.STYLE_DARK_STANDBY
            prefs.setDesignStyle(normalized)
            notes.add(if (normalized == PenConfig.STYLE_APPLE_LIGHT) "Apple白画廊" else "暗黑StandBy")
        }
        val standBy = args.optString("standby_style")
        if (standBy.isNotBlank()) {
            prefs.setStandByStyleMode(standBy)
            notes.add("StandBy=$standBy")
        }
        val motion = args.optString("motion_tier")
        if (motion.isNotBlank()) {
            prefs.setMotionTier(motion)
            notes.add("动效=$motion")
        }
        return PenMcpToolResult("switch_theme", "已更新外观：${notes.joinToString(" · ").ifBlank { "无变更" }}", true)
    }

    private suspend fun setWallpaperMode(args: JSONObject): PenMcpToolResult {
        val mode = args.optString("mode", PenConfig.DYN_WALLPAPER_PROCEDURAL).lowercase()
        val proc = args.optString("procedural_style").takeIf { it.isNotBlank() }
        val media = args.optString("media_path").takeIf { it.isNotBlank() }
        prefs.setDynamicWallpaperConfig(mode = mode, proceduralStyle = proc, mediaPath = media)
        return PenMcpToolResult("set_wallpaper_mode", "动态壁纸已切换为 $mode${if (proc != null) "($proc)" else ""}", true)
    }

    private suspend fun setClockWidgetStyle(args: JSONObject): PenMcpToolResult {
        val style = args.optString("style").takeIf { it.isNotBlank() }
        val shape = args.optString("shape").takeIf { it.isNotBlank() }
        val showSec = if (args.has("show_seconds")) args.optBoolean("show_seconds") else null
        prefs.setMultiClockConfig(style = style, shape = shape, showSeconds = showSec)
        return PenMcpToolResult("set_clock_widget_style", "时钟表盘已切换为 ${style ?: "保持"} / ${shape ?: "保持"}", true)
    }

    private suspend fun manageBuiltinWidgets(args: JSONObject): PenMcpToolResult {
        val wid = args.optString("widget_id")
        val enable = args.optBoolean("enable", true)
        val cur = prefs.currentConfig().enabledWidgetIds.toMutableList()
        if (enable && !cur.contains(wid) && PenConfig.ALL_WIDGET_IDS.contains(wid)) {
            cur.add(wid)
            prefs.setEnabledWidgets(cur)
        } else if (!enable && cur.contains(wid) && cur.size > 1) {
            cur.remove(wid)
            prefs.setEnabledWidgets(cur)
        }
        return PenMcpToolResult("manage_widgets", "已${if (enable) "开启" else "隐藏"}内置组件 $wid", true)
    }

    private suspend fun switchAiProfile(args: JSONObject): PenMcpToolResult {
        val q = args.optString("profile_name_or_id").trim()
        val profiles = prefs.currentConfig().aiProfiles
        val match = profiles.firstOrNull {
            it.id.equals(q, true) || it.name.contains(q, true) || it.model.contains(q, true)
        } ?: return PenMcpToolResult("switch_ai_profile", "未找到名为「$q」的 AI 模型预设", false)
        prefs.setActiveAiProfile(match.id)
        return PenMcpToolResult("switch_ai_profile", "已切换 AI 模型预设为「${match.name}」(${match.model})", true)
    }

    private suspend fun addTodo(args: JSONObject): PenMcpToolResult {
        val title = args.optString("title").trim()
        val due = args.optString("due_date").ifBlank { getIsoDate() }
        if (title.isBlank()) return PenMcpToolResult("add_todo", "待办标题为空", false)
        database.todoDao().insertTodo(TodoItemEntity(title = title, dueDateIso = due, priority = 1))
        return PenMcpToolResult("add_todo", "已添加待办「$title」", true)
    }

    private suspend fun addCalendarEvent(args: JSONObject): PenMcpToolResult {
        val title = args.optString("title").trim()
        val dateIso = args.optString("date_iso").ifBlank { getIsoDate() }
        val parts = args.optString("time_hhmm", "08:30").trim().replace("：", ":").split(":")
        val time = "%02d:%02d".format(
            parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8,
            parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        )
        if (title.isBlank()) return PenMcpToolResult("add_calendar_event", "日程标题为空", false)
        database.calendarEventDao().insertEvent(CalendarEventEntity(dateIso = dateIso, timeHHmm = time, title = title))
        return PenMcpToolResult("add_calendar_event", "已添加日程 $dateIso $time「$title」", true)
    }

    private suspend fun switchWeatherCity(args: JSONObject): PenMcpToolResult {
        val cityName = args.optString("city_name").trim()
        val found = WmoWeatherMapper.findBestCity(cityName) ?: WeatherCityOption(cityName, "自定义", "", 35.0, 114.0)
        val res = weatherRepo.selectCityAndRefresh(found)
        onSetWeatherSource(res.sourceLabel)
        return PenMcpToolResult("switch_weather_city", "天气已切换为 ${found.displayName}（${res.sourceLabel}）", true)
    }

    private fun controlWhiteNoise(args: JSONObject): PenMcpToolResult {
        val action = args.optString("action", "play")
        return if (action.equals("stop", ignoreCase = true)) {
            whiteNoise.stop()
            PenMcpToolResult("control_white_noise", "已停止白噪音", true)
        } else {
            val soundId = args.optString("sound", "rain")
            val type = WhiteNoiseType.entries.firstOrNull { it.id.equals(soundId, ignoreCase = true) } ?: WhiteNoiseType.RAIN
            whiteNoise.start(type)
            PenMcpToolResult("control_white_noise", "已开始播放白噪音：${type.label}", true)
        }
    }

    private suspend fun adjustDevice(args: JSONObject): PenMcpToolResult {
        val changes = mutableListOf<String>()
        if (args.has("brightness_percent")) {
            val b = (args.optInt("brightness_percent", 60) / 100f).coerceIn(0.08f, 1f)
            onSetBrightness(b)
            changes.add("亮度${(b * 100).toInt()}%")
        }
        if (args.has("volume_percent")) {
            val v = (args.optInt("volume_percent", 50) / 100f).coerceIn(0f, 1f)
            onSetVolume(v)
            changes.add("音量${(v * 100).toInt()}%")
        }
        if (args.has("night_eye_care")) {
            val night = args.optBoolean("night_eye_care", false)
            prefs.setNightDimAlpha(if (night) 0.35f else 0f)
            changes.add(if (night) "护眼暗屏开" else "护眼暗屏关")
        }
        return PenMcpToolResult("adjust_device", "设备已调节：${changes.joinToString(" / ").ifBlank { "无变更" }}", true)
    }

    private suspend fun addVocabWord(args: JSONObject): PenMcpToolResult {
        val word = args.optString("word").trim()
        val meaning = args.optString("meaning_zh").trim()
        if (word.isBlank() || meaning.isBlank()) return PenMcpToolResult("add_vocab_word", "单词或释义为空", false)
        val entity = VocabWordEntity(
            id = "ai_${word.lowercase().replace(Regex("[^a-z0-9]"), "_")}",
            word = word,
            phonetic = args.optString("phonetic", "/$word/"),
            partOfSpeech = "ai.",
            meaningZh = meaning,
            mnemonicTag = "AI 收录",
            rootBreakdown = args.optString("root_breakdown", "AI 词根助记"),
            visualCue = "来自 AI Harness 生词卡片",
            exampleEn = args.optString("example_en", "Remember \"$word\" in context."),
            exampleZh = meaning,
            bookId = "custom",
            accentHex = 0xFF38BDF8
        )
        database.vocabDao().upsertWords(listOf(entity))
        return PenMcpToolResult("add_vocab_word", "已收录生词「$word」到自定义词书", true)
    }

    private fun launchAppByQuery(args: JSONObject): PenMcpToolResult {
        val query = args.optString("app_query").trim()
        val match = getInstalledApps().firstOrNull {
            it.packageName.equals(query, true) || it.label.contains(query, true) || it.packageName.contains(query, true)
        } ?: return PenMcpToolResult("launch_app", "未找到应用「$query」", false)
        onLaunchApp(match.packageName)
        return PenMcpToolResult("launch_app", "已启动应用「${match.label}」", true)
    }

    private suspend fun listRecentEmails(args: JSONObject): PenMcpToolResult {
        val limit = args.optInt("limit", 8).coerceIn(1, 20)
        val mails = database.mailDao().getRecentMessagesSnapshot(limit)
        if (mails.isEmpty()) {
            return PenMcpToolResult("list_recent_emails", "本地邮箱暂无缓存邮件，请先在邮箱页面绑定 IMAP 账号并同步", true)
        }
        val lines = mails.mapIndexed { idx, m ->
            "[${idx + 1}] id=${m.id} | ${if (m.isUnread) "未读" else "已读"} | 发件:${m.fromAddress} | 主题:${m.subject} | 摘要:${m.snippet.take(60)}"
        }.joinToString("\n")
        return PenMcpToolResult("list_recent_emails", lines, true)
    }

    private suspend fun readEmail(args: JSONObject): PenMcpToolResult {
        val key = args.optString("email_id_or_index").trim()
        val mails = database.mailDao().getRecentMessagesSnapshot(20)
        val idx = key.toIntOrNull()
        val mail = when {
            idx != null && idx in 1..mails.size -> mails[idx - 1]
            else -> mails.firstOrNull { it.id == key }
        } ?: return PenMcpToolResult("read_email", "未找到编号或 ID 为 $key 的邮件", false)
        return PenMcpToolResult(
            "read_email",
            "发件人: ${mail.fromAddress}\n主题: ${mail.subject}\n正文:\n${mail.bodyText.take(600)}",
            true
        )
    }

    private fun draftEmail(args: JSONObject): PenMcpToolResult {
        val to = args.optString("to").trim()
        val subject = args.optString("subject").trim().ifBlank { "来自 PenOS 的回复" }
        val body = args.optString("body").trim()
        if (to.isBlank() || body.isBlank()) {
            return PenMcpToolResult("draft_email", "收件人或邮件正文为空", false)
        }
        onPresentMailDraft(to, subject, body)
        return PenMcpToolResult("draft_email", "已起草发往 $to 的邮件「$subject」，并在邮箱界面弹出发送确认弹窗（等待用户点击确认发送）", true)
    }

    private fun parseHexColor(hex: String): Long {
        val clean = hex.trim().removePrefix("#")
        val rgb = clean.toLongOrNull(16) ?: 0x38BDF8L
        return if (clean.length <= 6) (0xFF000000L or rgb) else rgb
    }
}
