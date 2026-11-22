package com.example.data.prefs

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.FontSlotSpec
import com.example.ui.theme.GranularTypographyProfile
import com.example.ui.theme.PenMotionTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

private val Context.penDataStore: DataStore<Preferences> by preferencesDataStore(name = "penos_prefs")

data class SavedCityEntry(
    val name: String,
    val region: String,
    val cmaCode: String,
    val lat: Double,
    val lon: Double
)

data class AiPreset(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val enableMcpTools: Boolean = true
)

/**
 * PenOS 4.0 Multi-API AI Profile: lets the user store & switch between multiple AI providers/models
 * (DeepSeek-V3, DeepSeek-R1, GLM-4, Qwen, Kimi, SiliconFlow, Custom LAN Ollama) with 1 tap.
 */
data class AiApiProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String = PenConfig.DEFAULT_HARNESS_PROMPT,
    val enableMcpTools: Boolean = true,
    val temperature: Float = 0.6f
) {
    fun toAiPreset(): AiPreset = AiPreset(id, name, systemPrompt, enableMcpTools)
}

data class MultiClockConfig(
    val style: String = STYLE_BLOOM_ANALOG,
    val shape: String = SHAPE_SCALLOP,
    val showSeconds: Boolean = true,
    val accentHex: Long = 0xFFFF6B4A
) {
    companion object {
        const val STYLE_BLOOM_ANALOG = "bloom_analog"
        const val STYLE_RAILWAY_SWISS = "railway_swiss"
        const val STYLE_RETRO_FLIP = "retro_flip"
        const val STYLE_KDE_MINIMAL = "kde_minimal"
        const val STYLE_ROMAN_CLASSIC = "roman_classic"
        const val STYLE_DOT_MATRIX = "dot_matrix"

        const val SHAPE_CIRCLE = "circle"
        const val SHAPE_SQUIRCLE = "squircle"
        const val SHAPE_SCALLOP = "scallop"
        const val SHAPE_PILL = "pill"

        val allStyles: List<Pair<String, String>> = listOf(
            STYLE_BLOOM_ANALOG to "M3 花瓣指针",
            STYLE_RAILWAY_SWISS to "瑞士铁路表",
            STYLE_RETRO_FLIP to "复古翻页钟",
            STYLE_KDE_MINIMAL to "KDE 极简叠层",
            STYLE_ROMAN_CLASSIC to "罗马典雅盘",
            STYLE_DOT_MATRIX to "点阵 LED"
        )
    }
}

data class PenConfig(
    val designStyle: String = STYLE_DARK_STANDBY,
    val standByStyleMode: String = STANDBY_STYLE_OLED_DARK,
    val hiddenPackages: Set<String> = emptySet(),
    val pinnedDockPackages: List<String> = emptyList(),
    val enabledWidgetIds: List<String> = DEFAULT_WIDGET_ORDER,
    val desktopDeckMode: String = DECK_MODE_MIXED,
    val nativeWidgetIds: List<Int> = emptyList(),
    val nativeWidgetWidths: Map<Int, Int> = emptyMap(),
    // Jiuxuewang component-level scan & anti-lock settings
    val scanAppPackage: String = "",
    val scanActivityClass: String = "",
    val blockedStockLauncherPackage: String = "",
    val blockedHomeActivities: Set<String> = emptySet(),
    // Independent Wallpapers + 4-mode Dynamic Wallpapers (static | procedural | gif | mp4 | live)
    val darkWallpaperPresetId: String = WALLPAPER_AURORA,
    val darkCustomWallpaperPath: String = "",
    val lightWallpaperPresetId: String = WALLPAPER_GALLERY_LIGHT,
    val lightCustomWallpaperPath: String = "",
    val dynamicWallpaperMode: String = DYN_WALLPAPER_STATIC,
    val proceduralWallpaperStyle: String = PROC_AURORA_FLOW,
    val dynamicWallpaperMediaPath: String = "",
    // Custom App Icons & Granular 5-Slot Global Dynamic Typography
    val customAppIcons: Map<String, String> = emptyMap(),
    val customPoetryFontPath: String = "",
    val typographyProfile: GranularTypographyProfile = GranularTypographyProfile(),
    // Multi-Style Clock & 60fps Motion Tier & Setup Assistant
    val multiClock: MultiClockConfig = MultiClockConfig(),
    val motionTierId: String = PenMotionTier.FAST.id,
    val setupAssistantCompleted: Boolean = false,
    // LAN Web Admin Console
    val lanAdminPort: Int = 8765,
    val lanAdminPin: String = "",
    // StandBy & Focus
    val standByTimeoutSec: Int = 30,
    val standByBrightness: Float = 0.08f,
    val standByRightWidget: String = STANDBY_WIDGET_POETRY,
    val standByClockColor: String = CLOCK_COLOR_CORAL,
    val pomodoroMinutes: Int = 25,
    val nightDimAlpha: Float = 0f,
    val lowMemMode: Boolean = false,
    // Security & Preview
    val vaultPin: String = "",
    val keepAliveEnabled: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O,
    val penFramePreviewEnabled: Boolean = true,
    // Vocab & Poetry
    val activeVocabBook: String = "highschool",
    val vocabDailyGoal: Int = 20,
    val jinrishiciToken: String = "",
    val lastPoemFetchAt: Long = 0L,
    // Weather
    val weatherCityName: String = "北京",
    val weatherCityCode: String = "101010100",
    val weatherLat: Double = 39.9042,
    val weatherLon: Double = 116.4074,
    val savedCities: List<SavedCityEntry> = DEFAULT_SAVED_CITIES,
    val weatherQWeatherKey: String = "",
    val weatherAmapKey: String = "",
    val lastWeatherFetchAt: Long = 0L,
    // WebDAV Sync
    val webDavUrl: String = "https://dav.jianguoyun.com/dav/PenOS/",
    val webDavUser: String = "",
    val webDavPass: String = "",
    val lastSyncTime: Long = 0L,
    // AI Harness Multi-API Profiles + External MCP
    val aiBaseUrl: String = "https://api.deepseek.com/v1",
    val aiApiKey: String = "",
    val aiModel: String = "deepseek-chat",
    val aiProfiles: List<AiApiProfile> = defaultAiApiProfiles("https://api.deepseek.com/v1", "", "deepseek-chat"),
    val activeAiProfileId: String = "deepseek_harness",
    val aiPresets: List<AiPreset> = DEFAULT_AI_PRESETS,
    val activeAiPresetId: String = "deepseek_harness",
    val externalMcpUrl: String = "",
    // PenOS 5.0 Guardrails, Gesture Tuning & Auto-Sync
    val mcpPreviewBeforeApply: Boolean = true,
    val swipeSensitivityDp: Float = 34f,
    val gestureHapticsEnabled: Boolean = true,
    val mailAutoSyncEnabled: Boolean = true,
    val lastMailAutoSyncAt: Long = 0L
) {
    companion object {
        const val STYLE_DARK_STANDBY = "dark_standby"
        const val STYLE_APPLE_LIGHT = "apple_light"

        const val STANDBY_STYLE_FOLLOW = "follow"
        const val STANDBY_STYLE_OLED_DARK = "oled_dark"
        const val STANDBY_STYLE_APPLE_LIGHT = "apple_light"

        const val DECK_MODE_MIXED = "mixed"
        const val DECK_MODE_NATIVE_ONLY = "native_only"

        // Static wallpapers
        const val WALLPAPER_AURORA = "aurora"
        const val WALLPAPER_INK = "ink"
        const val WALLPAPER_OBSIDIAN = "obsidian"
        const val WALLPAPER_GALLERY_LIGHT = "gallery_light"
        const val WALLPAPER_PURE_MIST = "pure_mist"
        const val WALLPAPER_WARM_PAPER = "warm_paper"
        const val WALLPAPER_CUSTOM = "custom"

        // 4 Dynamic Wallpaper Modes + Static
        const val DYN_WALLPAPER_STATIC = "static"
        const val DYN_WALLPAPER_PROCEDURAL = "procedural"
        const val DYN_WALLPAPER_GIF = "gif"
        const val DYN_WALLPAPER_MP4 = "mp4"
        const val DYN_WALLPAPER_LIVE = "live"

        const val PROC_AURORA_FLOW = "aurora_flow"
        const val PROC_STAR_TRAILS = "star_trails"
        const val PROC_SOFT_RAIN = "soft_rain"
        const val PROC_GALLERY_BREEZE = "gallery_breeze"

        const val STANDBY_WIDGET_POETRY = "poetry"
        const val STANDBY_WIDGET_WEATHER = "weather"
        const val STANDBY_WIDGET_CALENDAR = "calendar"
        const val STANDBY_WIDGET_TODO = "todo"
        const val STANDBY_WIDGET_VOCAB = "vocab"
        const val STANDBY_WIDGET_POMODORO = "pomodoro"

        val STANDBY_WIDGET_ORDER = listOf(
            STANDBY_WIDGET_POETRY,
            STANDBY_WIDGET_WEATHER,
            STANDBY_WIDGET_CALENDAR,
            STANDBY_WIDGET_TODO,
            STANDBY_WIDGET_VOCAB,
            STANDBY_WIDGET_POMODORO
        )

        const val CLOCK_COLOR_CORAL = "coral"
        const val CLOCK_COLOR_AMBER = "amber"
        const val CLOCK_COLOR_GLACIER = "glacier"
        const val CLOCK_COLOR_IVORY = "ivory"

        val DEFAULT_WIDGET_ORDER = listOf(
            "quick_scan",
            "multi_clock",
            "weather",
            "mail_inbox",
            "calendar",
            "todo",
            "jinrishici",
            "vocab_card",
            "pinned_apps",
            "pen_status"
        )

        val DEFAULT_SAVED_CITIES = listOf(
            SavedCityEntry("北京", "北京", "101010100", 39.9042, 116.4074),
            SavedCityEntry("上海", "上海", "101020100", 31.2304, 121.4737),
            SavedCityEntry("广州", "广东", "101280101", 23.1291, 113.2644),
            SavedCityEntry("深圳", "广东", "101280601", 22.5431, 114.0579),
            SavedCityEntry("杭州", "浙江", "101210101", 30.2741, 120.1551),
            SavedCityEntry("成都", "四川", "101270101", 30.5728, 104.0668)
        )

        const val DEFAULT_HARNESS_PROMPT =
            "你是 PenOS 4.0 全权 Harness 智能桌面管家。你拥有完整的桌面编排工具：先可调用 inspect_penos_state 查看桌面、字体、抽屉与邮箱状态；可以直接用 create_custom_widget 编写声明式小组件并安装到主屏或 Apple TV 上滑抽屉；可以精细调整 5 通道全局字体、4 模式动态壁纸、6 样式时钟表盘、动画流畅度、日程待办、番茄钟、白噪音，也可以总结未读邮件并起草回复。回答精炼直接。"

        fun defaultAiApiProfiles(
            baseUrl: String = "https://api.deepseek.com/v1",
            apiKey: String = "",
            model: String = "deepseek-chat"
        ): List<AiApiProfile> = listOf(
            AiApiProfile(
                id = "deepseek_harness",
                name = "DeepSeek Harness 全权管家",
                baseUrl = baseUrl.ifBlank { "https://api.deepseek.com/v1" },
                apiKey = apiKey,
                model = model.ifBlank { "deepseek-chat" },
                systemPrompt = DEFAULT_HARNESS_PROMPT,
                enableMcpTools = true
            ),
            AiApiProfile(
                id = "glm_flash",
                name = "智谱 GLM-4 Flash",
                baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                apiKey = "",
                model = "glm-4-flash",
                systemPrompt = DEFAULT_HARNESS_PROMPT,
                enableMcpTools = true
            ),
            AiApiProfile(
                id = "qwen_turbo",
                name = "通义千问 Qwen",
                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                apiKey = "",
                model = "qwen-turbo",
                systemPrompt = "你是高效简洁的英语与学习助教，擅长词根拆解、邮件起草与学习计划编排，支持调用 PenOS MCP 工具。",
                enableMcpTools = true
            ),
            AiApiProfile(
                id = "siliconflow_qwen",
                name = "硅基流动 SiliconFlow",
                baseUrl = "https://api.siliconflow.cn/v1",
                apiKey = "",
                model = "Qwen/Qwen2.5-7B-Instruct",
                systemPrompt = DEFAULT_HARNESS_PROMPT,
                enableMcpTools = true
            ),
            AiApiProfile(
                id = "kimi_moonshot",
                name = "Kimi 长文本助手",
                baseUrl = "https://api.moonshot.cn/v1",
                apiKey = "",
                model = "moonshot-v1-8k",
                systemPrompt = "你是擅长总结长邮件与古诗词赏析的轻量助手，回复简明清晰。",
                enableMcpTools = true
            )
        )

        val DEFAULT_AI_PRESETS = defaultAiApiProfiles().map { it.toAiPreset() }
    }

    fun activeAiProfile(): AiApiProfile =
        aiProfiles.firstOrNull { it.id == activeAiProfileId } ?: aiProfiles.firstOrNull() ?: defaultAiApiProfiles().first()

    fun motionTier(): PenMotionTier = PenMotionTier.fromId(motionTierId)

    fun isAppleLightDesktop(): Boolean = designStyle == STYLE_APPLE_LIGHT

    fun isAppleLightStandBy(): Boolean = when (standByStyleMode) {
        STANDBY_STYLE_APPLE_LIGHT -> true
        STANDBY_STYLE_OLED_DARK -> false
        else -> isAppleLightDesktop()
    }

    fun activeWallpaperPresetFor(isLight: Boolean): String =
        if (isLight) lightWallpaperPresetId else darkWallpaperPresetId

    fun activeCustomWallpaperPathFor(isLight: Boolean): String =
        if (isLight) lightCustomWallpaperPath else darkCustomWallpaperPath
}

class PenPreferencesRepository(private val context: Context) {

    private object Keys {
        val DESIGN_STYLE = stringPreferencesKey("design_style")
        val STANDBY_STYLE_MODE = stringPreferencesKey("standby_style_mode")
        val HIDDEN_PACKAGES = stringSetPreferencesKey("hidden_packages")
        val PINNED_DOCK_CSV = stringPreferencesKey("pinned_dock_csv")
        val ENABLED_WIDGETS_CSV = stringPreferencesKey("enabled_widgets_csv")
        val DESKTOP_DECK_MODE = stringPreferencesKey("desktop_deck_mode")
        val NATIVE_WIDGET_IDS = stringPreferencesKey("native_widget_ids")
        val NATIVE_WIDGET_WIDTHS = stringPreferencesKey("native_widget_widths")

        val SCAN_APP_PACKAGE = stringPreferencesKey("scan_app_package")
        val SCAN_ACTIVITY_CLASS = stringPreferencesKey("scan_activity_class")
        val BLOCKED_LAUNCHER_PACKAGE = stringPreferencesKey("blocked_launcher_package")
        val BLOCKED_HOME_ACTIVITIES = stringSetPreferencesKey("blocked_home_activities")

        val DARK_WALLPAPER_PRESET = stringPreferencesKey("wallpaper_preset")
        val DARK_CUSTOM_WALLPAPER = stringPreferencesKey("custom_wallpaper_uri")
        val LIGHT_WALLPAPER_PRESET = stringPreferencesKey("light_wallpaper_preset")
        val LIGHT_CUSTOM_WALLPAPER = stringPreferencesKey("light_custom_wallpaper_path")
        val DYNAMIC_WALLPAPER_MODE = stringPreferencesKey("dynamic_wallpaper_mode")
        val PROCEDURAL_WALLPAPER_STYLE = stringPreferencesKey("procedural_wallpaper_style")
        val DYNAMIC_WALLPAPER_MEDIA = stringPreferencesKey("dynamic_wallpaper_media_path")

        val CUSTOM_APP_ICONS = stringPreferencesKey("custom_app_icons_kv")
        val CUSTOM_POETRY_FONT_PATH = stringPreferencesKey("custom_poetry_font_path")
        val TYPOGRAPHY_PROFILE_JSON = stringPreferencesKey("typography_profile_json")

        val MULTI_CLOCK_STYLE = stringPreferencesKey("multi_clock_style")
        val MULTI_CLOCK_SHAPE = stringPreferencesKey("multi_clock_shape")
        val MULTI_CLOCK_SECONDS = booleanPreferencesKey("multi_clock_seconds")
        val MULTI_CLOCK_ACCENT = longPreferencesKey("multi_clock_accent")

        val MOTION_TIER_ID = stringPreferencesKey("motion_tier_id")
        val SETUP_COMPLETED = booleanPreferencesKey("setup_assistant_completed")
        val LAN_ADMIN_PORT = intPreferencesKey("lan_admin_port")
        val LAN_ADMIN_PIN = stringPreferencesKey("lan_admin_pin")

        val STANDBY_TIMEOUT_SEC = intPreferencesKey("standby_timeout_sec")
        val STANDBY_BRIGHTNESS = floatPreferencesKey("standby_brightness")
        val STANDBY_RIGHT_WIDGET = stringPreferencesKey("standby_right_widget")
        val STANDBY_CLOCK_COLOR = stringPreferencesKey("standby_clock_color")
        val POMODORO_MINUTES = intPreferencesKey("pomodoro_minutes")
        val NIGHT_DIM_ALPHA = floatPreferencesKey("night_dim_alpha")
        val LOW_MEM_MODE = booleanPreferencesKey("low_mem_mode")

        val VAULT_PIN = stringPreferencesKey("vault_pin")
        val KEEP_ALIVE_ENABLED = booleanPreferencesKey("keep_alive_enabled")
        val PEN_FRAME_PREVIEW = booleanPreferencesKey("pen_frame_preview")
        val ACTIVE_VOCAB_BOOK = stringPreferencesKey("active_vocab_book")
        val VOCAB_DAILY_GOAL = intPreferencesKey("vocab_daily_goal")
        val JINRISHICI_TOKEN = stringPreferencesKey("jinrishici_token")
        val LAST_POEM_FETCH_AT = longPreferencesKey("last_poem_fetch_at")

        val WEATHER_CITY_NAME = stringPreferencesKey("weather_city_name")
        val WEATHER_CITY_CODE = stringPreferencesKey("weather_city_code")
        val WEATHER_LAT = doublePreferencesKey("weather_lat")
        val WEATHER_LON = doublePreferencesKey("weather_lon")
        val SAVED_WEATHER_CITIES = stringPreferencesKey("saved_weather_cities")
        val WEATHER_QWEATHER_KEY = stringPreferencesKey("weather_qweather_key")
        val WEATHER_AMAP_KEY = stringPreferencesKey("weather_amap_key")
        val LAST_WEATHER_FETCH_AT = longPreferencesKey("last_weather_fetch_at")

        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USER = stringPreferencesKey("webdav_user")
        val WEBDAV_PASS = stringPreferencesKey("webdav_pass")
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")

        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_PROFILES_JSON = stringPreferencesKey("ai_profiles_json")
        val ACTIVE_AI_PROFILE_ID = stringPreferencesKey("active_ai_profile_id")
        val EXTERNAL_MCP_URL = stringPreferencesKey("external_mcp_url")
        val MCP_PREVIEW_BEFORE_APPLY = booleanPreferencesKey("mcp_preview_before_apply")
        val SWIPE_SENSITIVITY_DP = floatPreferencesKey("swipe_sensitivity_dp")
        val GESTURE_HAPTICS_ENABLED = booleanPreferencesKey("gesture_haptics_enabled")
        val MAIL_AUTO_SYNC_ENABLED = booleanPreferencesKey("mail_auto_sync_enabled")
        val LAST_MAIL_AUTO_SYNC_AT = longPreferencesKey("last_mail_auto_sync_at")
    }

    val configFlow: Flow<PenConfig> = context.penDataStore.data.map { prefs ->
        val pinnedRaw = prefs[Keys.PINNED_DOCK_CSV].orEmpty()
        val widgetsRaw = prefs[Keys.ENABLED_WIDGETS_CSV]
        val nativeIdsRaw = prefs[Keys.NATIVE_WIDGET_IDS].orEmpty()
        val nativeWidthsRaw = prefs[Keys.NATIVE_WIDGET_WIDTHS].orEmpty()
        val legacyBaseUrl = prefs[Keys.AI_BASE_URL] ?: "https://api.deepseek.com/v1"
        val legacyApiKey = prefs[Keys.AI_API_KEY].orEmpty()
        val legacyModel = prefs[Keys.AI_MODEL] ?: "deepseek-chat"
        val profiles = PenPrefsCodec.decodeAiApiProfiles(
            prefs[Keys.AI_PROFILES_JSON], legacyBaseUrl, legacyApiKey, legacyModel
        )
        val activeProfileId = prefs[Keys.ACTIVE_AI_PROFILE_ID] ?: profiles.first().id
        val activeProfile = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()
        val legacyPoetryFont = prefs[Keys.CUSTOM_POETRY_FONT_PATH].orEmpty()
        val typoProfile = PenPrefsCodec.decodeTypographyProfile(
            prefs[Keys.TYPOGRAPHY_PROFILE_JSON], legacyPoetryFont
        )

        PenConfig(
            designStyle = prefs[Keys.DESIGN_STYLE] ?: PenConfig.STYLE_DARK_STANDBY,
            standByStyleMode = prefs[Keys.STANDBY_STYLE_MODE] ?: PenConfig.STANDBY_STYLE_OLED_DARK,
            hiddenPackages = prefs[Keys.HIDDEN_PACKAGES] ?: emptySet(),
            pinnedDockPackages = if (pinnedRaw.isBlank()) emptyList() else pinnedRaw.split(",").filter { it.isNotBlank() },
            enabledWidgetIds = if (widgetsRaw == null) PenConfig.DEFAULT_WIDGET_ORDER else widgetsRaw.split(",").filter { it.isNotBlank() },
            desktopDeckMode = prefs[Keys.DESKTOP_DECK_MODE] ?: PenConfig.DECK_MODE_MIXED,
            nativeWidgetIds = nativeIdsRaw.split(",").mapNotNull { it.trim().toIntOrNull() },
            nativeWidgetWidths = decodeIntMap(nativeWidthsRaw),
            scanAppPackage = prefs[Keys.SCAN_APP_PACKAGE].orEmpty(),
            scanActivityClass = prefs[Keys.SCAN_ACTIVITY_CLASS].orEmpty(),
            blockedStockLauncherPackage = prefs[Keys.BLOCKED_LAUNCHER_PACKAGE].orEmpty(),
            blockedHomeActivities = prefs[Keys.BLOCKED_HOME_ACTIVITIES] ?: emptySet(),
            darkWallpaperPresetId = prefs[Keys.DARK_WALLPAPER_PRESET] ?: PenConfig.WALLPAPER_AURORA,
            darkCustomWallpaperPath = prefs[Keys.DARK_CUSTOM_WALLPAPER].orEmpty(),
            lightWallpaperPresetId = prefs[Keys.LIGHT_WALLPAPER_PRESET] ?: PenConfig.WALLPAPER_GALLERY_LIGHT,
            lightCustomWallpaperPath = prefs[Keys.LIGHT_CUSTOM_WALLPAPER].orEmpty(),
            dynamicWallpaperMode = prefs[Keys.DYNAMIC_WALLPAPER_MODE] ?: PenConfig.DYN_WALLPAPER_STATIC,
            proceduralWallpaperStyle = prefs[Keys.PROCEDURAL_WALLPAPER_STYLE] ?: PenConfig.PROC_AURORA_FLOW,
            dynamicWallpaperMediaPath = prefs[Keys.DYNAMIC_WALLPAPER_MEDIA].orEmpty(),
            customAppIcons = decodeStringMap(prefs[Keys.CUSTOM_APP_ICONS].orEmpty()),
            customPoetryFontPath = typoProfile.poetry.externalPath.ifBlank { legacyPoetryFont },
            typographyProfile = typoProfile,
            multiClock = MultiClockConfig(
                style = prefs[Keys.MULTI_CLOCK_STYLE] ?: MultiClockConfig.STYLE_BLOOM_ANALOG,
                shape = prefs[Keys.MULTI_CLOCK_SHAPE] ?: MultiClockConfig.SHAPE_SCALLOP,
                showSeconds = prefs[Keys.MULTI_CLOCK_SECONDS] ?: true,
                accentHex = prefs[Keys.MULTI_CLOCK_ACCENT] ?: 0xFFFF6B4AL
            ),
            motionTierId = prefs[Keys.MOTION_TIER_ID] ?: PenMotionTier.FAST.id,
            setupAssistantCompleted = prefs[Keys.SETUP_COMPLETED] ?: false,
            lanAdminPort = prefs[Keys.LAN_ADMIN_PORT] ?: 8765,
            lanAdminPin = prefs[Keys.LAN_ADMIN_PIN].orEmpty(),
            standByTimeoutSec = prefs[Keys.STANDBY_TIMEOUT_SEC] ?: 30,
            standByBrightness = prefs[Keys.STANDBY_BRIGHTNESS] ?: 0.08f,
            standByRightWidget = prefs[Keys.STANDBY_RIGHT_WIDGET] ?: PenConfig.STANDBY_WIDGET_POETRY,
            standByClockColor = prefs[Keys.STANDBY_CLOCK_COLOR] ?: PenConfig.CLOCK_COLOR_CORAL,
            pomodoroMinutes = prefs[Keys.POMODORO_MINUTES] ?: 25,
            nightDimAlpha = prefs[Keys.NIGHT_DIM_ALPHA] ?: 0f,
            lowMemMode = prefs[Keys.LOW_MEM_MODE] ?: false,
            vaultPin = prefs[Keys.VAULT_PIN].orEmpty(),
            keepAliveEnabled = prefs[Keys.KEEP_ALIVE_ENABLED] ?: (Build.VERSION.SDK_INT < Build.VERSION_CODES.O),
            penFramePreviewEnabled = prefs[Keys.PEN_FRAME_PREVIEW] ?: true,
            activeVocabBook = prefs[Keys.ACTIVE_VOCAB_BOOK] ?: "highschool",
            vocabDailyGoal = prefs[Keys.VOCAB_DAILY_GOAL] ?: 20,
            jinrishiciToken = prefs[Keys.JINRISHICI_TOKEN].orEmpty(),
            lastPoemFetchAt = prefs[Keys.LAST_POEM_FETCH_AT] ?: 0L,
            weatherCityName = prefs[Keys.WEATHER_CITY_NAME] ?: "北京",
            weatherCityCode = prefs[Keys.WEATHER_CITY_CODE] ?: "101010100",
            weatherLat = prefs[Keys.WEATHER_LAT] ?: 39.9042,
            weatherLon = prefs[Keys.WEATHER_LON] ?: 116.4074,
            savedCities = decodeSavedCities(prefs[Keys.SAVED_WEATHER_CITIES]),
            weatherQWeatherKey = prefs[Keys.WEATHER_QWEATHER_KEY].orEmpty(),
            weatherAmapKey = prefs[Keys.WEATHER_AMAP_KEY].orEmpty(),
            lastWeatherFetchAt = prefs[Keys.LAST_WEATHER_FETCH_AT] ?: 0L,
            webDavUrl = prefs[Keys.WEBDAV_URL] ?: "https://dav.jianguoyun.com/dav/PenOS/",
            webDavUser = prefs[Keys.WEBDAV_USER].orEmpty(),
            webDavPass = prefs[Keys.WEBDAV_PASS].orEmpty(),
            lastSyncTime = prefs[Keys.LAST_SYNC_TIME] ?: 0L,
            aiBaseUrl = activeProfile.baseUrl,
            aiApiKey = activeProfile.apiKey,
            aiModel = activeProfile.model,
            aiProfiles = profiles,
            activeAiProfileId = activeProfile.id,
            aiPresets = profiles.map { it.toAiPreset() },
            activeAiPresetId = activeProfile.id,
            externalMcpUrl = prefs[Keys.EXTERNAL_MCP_URL].orEmpty(),
            mcpPreviewBeforeApply = prefs[Keys.MCP_PREVIEW_BEFORE_APPLY] ?: true,
            swipeSensitivityDp = (prefs[Keys.SWIPE_SENSITIVITY_DP] ?: 34f).coerceIn(20f, 72f),
            gestureHapticsEnabled = prefs[Keys.GESTURE_HAPTICS_ENABLED] ?: true,
            mailAutoSyncEnabled = prefs[Keys.MAIL_AUTO_SYNC_ENABLED] ?: true,
            lastMailAutoSyncAt = prefs[Keys.LAST_MAIL_AUTO_SYNC_AT] ?: 0L
        )
    }

    suspend fun currentConfig(): PenConfig = configFlow.first()

    suspend fun ensureLanAdminPin(): String {
        val existing = currentConfig().lanAdminPin
        if (existing.length == 6 && existing.all { it.isDigit() }) return existing
        return regenerateLanAdminPin()
    }

    suspend fun regenerateLanAdminPin(): String {
        val randomPin = "%06d".format(SecureRandom().nextInt(1_000_000))
        context.penDataStore.edit { it[Keys.LAN_ADMIN_PIN] = randomPin }
        return randomPin
    }

    suspend fun setLanAdminPin(pin: String) {
        val clean = pin.filter { it.isDigit() }.take(8)
        if (clean.length >= 4) {
            context.penDataStore.edit { it[Keys.LAN_ADMIN_PIN] = clean }
        }
    }

    suspend fun setSetupAssistantCompleted(completed: Boolean) {
        context.penDataStore.edit { it[Keys.SETUP_COMPLETED] = completed }
    }

    suspend fun setMotionTier(tierId: String) {
        context.penDataStore.edit { it[Keys.MOTION_TIER_ID] = PenMotionTier.fromId(tierId).id }
    }

    suspend fun setMcpPreviewBeforeApply(enabled: Boolean) {
        context.penDataStore.edit { it[Keys.MCP_PREVIEW_BEFORE_APPLY] = enabled }
    }

    suspend fun setSwipeSensitivityDp(dp: Float) {
        context.penDataStore.edit { it[Keys.SWIPE_SENSITIVITY_DP] = dp.coerceIn(20f, 72f) }
    }

    suspend fun setGestureHapticsEnabled(enabled: Boolean) {
        context.penDataStore.edit { it[Keys.GESTURE_HAPTICS_ENABLED] = enabled }
    }

    suspend fun setMailAutoSyncEnabled(enabled: Boolean) {
        context.penDataStore.edit { it[Keys.MAIL_AUTO_SYNC_ENABLED] = enabled }
    }

    suspend fun setLastMailAutoSyncAt(timestampMs: Long) {
        context.penDataStore.edit { it[Keys.LAST_MAIL_AUTO_SYNC_AT] = timestampMs }
    }

    suspend fun setDynamicWallpaperConfig(
        mode: String? = null,
        proceduralStyle: String? = null,
        mediaPath: String? = null
    ) {
        context.penDataStore.edit { prefs ->
            mode?.let { prefs[Keys.DYNAMIC_WALLPAPER_MODE] = it }
            proceduralStyle?.let { prefs[Keys.PROCEDURAL_WALLPAPER_STYLE] = it }
            mediaPath?.let { prefs[Keys.DYNAMIC_WALLPAPER_MEDIA] = it }
        }
    }

    suspend fun updateFontSlot(slot: String, spec: FontSlotSpec) {
        context.penDataStore.edit { prefs ->
            val current = PenPrefsCodec.decodeTypographyProfile(
                prefs[Keys.TYPOGRAPHY_PROFILE_JSON],
                prefs[Keys.CUSTOM_POETRY_FONT_PATH].orEmpty()
            )
            val updated = current.withUpdatedSlot(slot, spec)
            prefs[Keys.TYPOGRAPHY_PROFILE_JSON] = PenPrefsCodec.encodeTypographyProfile(updated)
            if (slot == GranularTypographyProfile.SLOT_POETRY) {
                prefs[Keys.CUSTOM_POETRY_FONT_PATH] = spec.externalPath
            }
        }
    }

    suspend fun resetTypographyProfile() {
        context.penDataStore.edit { prefs ->
            prefs[Keys.TYPOGRAPHY_PROFILE_JSON] = PenPrefsCodec.encodeTypographyProfile(GranularTypographyProfile())
            prefs[Keys.CUSTOM_POETRY_FONT_PATH] = ""
        }
    }

    suspend fun setMultiClockConfig(
        style: String? = null,
        shape: String? = null,
        showSeconds: Boolean? = null,
        accentHex: Long? = null
    ) {
        context.penDataStore.edit { prefs ->
            style?.let { prefs[Keys.MULTI_CLOCK_STYLE] = it }
            shape?.let { prefs[Keys.MULTI_CLOCK_SHAPE] = it }
            showSeconds?.let { prefs[Keys.MULTI_CLOCK_SECONDS] = it }
            accentHex?.let { prefs[Keys.MULTI_CLOCK_ACCENT] = it }
        }
    }

    suspend fun setDesignStyle(style: String) {
        context.penDataStore.edit { it[Keys.DESIGN_STYLE] = style }
    }

    suspend fun setStandByStyleMode(mode: String) {
        context.penDataStore.edit { it[Keys.STANDBY_STYLE_MODE] = mode }
    }

    suspend fun setDesktopDeckMode(mode: String) {
        context.penDataStore.edit { it[Keys.DESKTOP_DECK_MODE] = mode }
    }

    suspend fun addNativeWidgetId(widgetId: Int, widthDp: Int = 196) {
        context.penDataStore.edit { prefs ->
            val current = prefs[Keys.NATIVE_WIDGET_IDS].orEmpty()
                .split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableList()
            if (!current.contains(widgetId)) current.add(widgetId)
            prefs[Keys.NATIVE_WIDGET_IDS] = current.joinToString(",")
            val widths = decodeIntMap(prefs[Keys.NATIVE_WIDGET_WIDTHS].orEmpty()).toMutableMap()
            widths[widgetId] = widthDp.coerceIn(130, 340)
            prefs[Keys.NATIVE_WIDGET_WIDTHS] = encodeIntMap(widths)
        }
    }

    suspend fun removeNativeWidgetId(widgetId: Int) {
        context.penDataStore.edit { prefs ->
            val current = prefs[Keys.NATIVE_WIDGET_IDS].orEmpty()
                .split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it != widgetId }
            prefs[Keys.NATIVE_WIDGET_IDS] = current.joinToString(",")
            val widths = decodeIntMap(prefs[Keys.NATIVE_WIDGET_WIDTHS].orEmpty()).toMutableMap()
            widths.remove(widgetId)
            prefs[Keys.NATIVE_WIDGET_WIDTHS] = encodeIntMap(widths)
        }
    }

    suspend fun setNativeWidgetWidth(widgetId: Int, widthDp: Int) {
        context.penDataStore.edit { prefs ->
            val widths = decodeIntMap(prefs[Keys.NATIVE_WIDGET_WIDTHS].orEmpty()).toMutableMap()
            widths[widgetId] = widthDp.coerceIn(130, 340)
            prefs[Keys.NATIVE_WIDGET_WIDTHS] = encodeIntMap(widths)
        }
    }

    suspend fun setWeatherCity(name: String, cmaCode: String, lat: Double, lon: Double) {
        context.penDataStore.edit {
            it[Keys.WEATHER_CITY_NAME] = name
            it[Keys.WEATHER_CITY_CODE] = cmaCode
            it[Keys.WEATHER_LAT] = lat
            it[Keys.WEATHER_LON] = lon
            it[Keys.LAST_WEATHER_FETCH_AT] = 0L
        }
    }

    suspend fun saveWeatherCityToFavorites(city: SavedCityEntry) {
        context.penDataStore.edit { prefs ->
            val current = decodeSavedCities(prefs[Keys.SAVED_WEATHER_CITIES]).toMutableList()
            if (current.none { it.name == city.name }) current.add(0, city)
            prefs[Keys.SAVED_WEATHER_CITIES] = encodeSavedCities(current.take(16))
        }
    }

    suspend fun removeWeatherCityFromFavorites(cityName: String) {
        context.penDataStore.edit { prefs ->
            val current = decodeSavedCities(prefs[Keys.SAVED_WEATHER_CITIES]).filter { it.name != cityName }
            if (current.isNotEmpty()) prefs[Keys.SAVED_WEATHER_CITIES] = encodeSavedCities(current)
        }
    }

    suspend fun saveWeatherApiKeys(qWeatherKey: String, amapKey: String) {
        context.penDataStore.edit {
            it[Keys.WEATHER_QWEATHER_KEY] = qWeatherKey.trim()
            it[Keys.WEATHER_AMAP_KEY] = amapKey.trim()
            it[Keys.LAST_WEATHER_FETCH_AT] = 0L
        }
    }

    suspend fun saveLastWeatherFetchAt(timestamp: Long) {
        context.penDataStore.edit { it[Keys.LAST_WEATHER_FETCH_AT] = timestamp }
    }

    suspend fun toggleHiddenPackage(packageName: String) {
        context.penDataStore.edit { prefs ->
            val current = (prefs[Keys.HIDDEN_PACKAGES] ?: emptySet()).toMutableSet()
            if (current.contains(packageName)) {
                current.remove(packageName)
            } else {
                current.add(packageName)
                val pinned = prefs[Keys.PINNED_DOCK_CSV].orEmpty()
                    .split(",").filter { it.isNotBlank() && it != packageName }
                prefs[Keys.PINNED_DOCK_CSV] = pinned.joinToString(",")
            }
            prefs[Keys.HIDDEN_PACKAGES] = current
        }
    }

    suspend fun togglePinnedPackage(packageName: String) {
        context.penDataStore.edit { prefs ->
            val current = prefs[Keys.PINNED_DOCK_CSV].orEmpty()
                .split(",").filter { it.isNotBlank() }.toMutableList()
            if (current.contains(packageName)) current.remove(packageName)
            else {
                if (current.size >= 8) current.removeAt(0)
                current.add(packageName)
            }
            prefs[Keys.PINNED_DOCK_CSV] = current.joinToString(",")
        }
    }

    suspend fun setEnabledWidgets(widgetIds: List<String>) {
        context.penDataStore.edit { prefs ->
            prefs[Keys.ENABLED_WIDGETS_CSV] = widgetIds.joinToString(",")
        }
    }

    suspend fun setScanTarget(packageName: String, activityClass: String = "") {
        context.penDataStore.edit {
            it[Keys.SCAN_APP_PACKAGE] = packageName.trim()
            it[Keys.SCAN_ACTIVITY_CLASS] = activityClass.trim()
        }
    }

    suspend fun setBlockedStockLauncherPackage(packageName: String) {
        context.penDataStore.edit { it[Keys.BLOCKED_LAUNCHER_PACKAGE] = packageName.trim() }
    }

    suspend fun toggleBlockedHomeActivity(componentFlat: String) {
        val clean = componentFlat.trim()
        if (clean.isBlank()) return
        context.penDataStore.edit { prefs ->
            val current = (prefs[Keys.BLOCKED_HOME_ACTIVITIES] ?: emptySet()).toMutableSet()
            if (current.contains(clean)) current.remove(clean) else current.add(clean)
            prefs[Keys.BLOCKED_HOME_ACTIVITIES] = current
        }
    }

    suspend fun addBlockedHomeActivity(componentFlat: String) {
        val clean = componentFlat.trim()
        if (clean.isBlank()) return
        context.penDataStore.edit { prefs ->
            val current = (prefs[Keys.BLOCKED_HOME_ACTIVITIES] ?: emptySet()).toMutableSet()
            current.add(clean)
            prefs[Keys.BLOCKED_HOME_ACTIVITIES] = current
        }
    }

    suspend fun setWallpaperForTheme(isLight: Boolean, presetId: String, customPath: String = "") {
        context.penDataStore.edit {
            if (isLight) {
                it[Keys.LIGHT_WALLPAPER_PRESET] = presetId
                if (customPath.isNotBlank()) it[Keys.LIGHT_CUSTOM_WALLPAPER] = customPath
            } else {
                it[Keys.DARK_WALLPAPER_PRESET] = presetId
                if (customPath.isNotBlank()) it[Keys.DARK_CUSTOM_WALLPAPER] = customPath
            }
        }
    }

    suspend fun setCustomAppIcon(packageName: String, iconSpec: String) {
        context.penDataStore.edit { prefs ->
            val map = decodeStringMap(prefs[Keys.CUSTOM_APP_ICONS].orEmpty()).toMutableMap()
            if (iconSpec.isBlank()) map.remove(packageName) else map[packageName] = iconSpec
            prefs[Keys.CUSTOM_APP_ICONS] = encodeStringMap(map)
        }
    }

    suspend fun setCustomPoetryFontPath(path: String) {
        val cur = currentConfig().typographyProfile.poetry
        updateFontSlot(
            GranularTypographyProfile.SLOT_POETRY,
            cur.copy(
                familyKey = if (path.isBlank()) "noto_serif_sc" else "external",
                externalPath = path.trim()
            )
        )
    }

    suspend fun setStandByConfig(
        timeoutSec: Int? = null,
        brightness: Float? = null,
        rightWidget: String? = null,
        clockColor: String? = null
    ) {
        context.penDataStore.edit { prefs ->
            timeoutSec?.let { prefs[Keys.STANDBY_TIMEOUT_SEC] = it }
            brightness?.let { prefs[Keys.STANDBY_BRIGHTNESS] = it.coerceIn(0.03f, 0.6f) }
            rightWidget?.let { prefs[Keys.STANDBY_RIGHT_WIDGET] = it }
            clockColor?.let { prefs[Keys.STANDBY_CLOCK_COLOR] = it }
        }
    }

    suspend fun setPomodoroMinutes(minutes: Int) {
        context.penDataStore.edit { it[Keys.POMODORO_MINUTES] = minutes.coerceIn(1, 120) }
    }

    suspend fun setNightDimAlpha(alpha: Float) {
        context.penDataStore.edit { it[Keys.NIGHT_DIM_ALPHA] = alpha.coerceIn(0f, 0.65f) }
    }

    suspend fun setLowMemMode(enabled: Boolean) {
        context.penDataStore.edit { it[Keys.LOW_MEM_MODE] = enabled }
    }

    suspend fun setVaultPin(pin: String) {
        context.penDataStore.edit { it[Keys.VAULT_PIN] = pin }
    }

    suspend fun setKeepAliveEnabled(enabled: Boolean) {
        context.penDataStore.edit { it[Keys.KEEP_ALIVE_ENABLED] = enabled }
    }

    suspend fun setPenFramePreviewEnabled(enabled: Boolean) {
        context.penDataStore.edit { it[Keys.PEN_FRAME_PREVIEW] = enabled }
    }

    suspend fun setActiveVocabBook(bookId: String) {
        context.penDataStore.edit { it[Keys.ACTIVE_VOCAB_BOOK] = bookId }
    }

    suspend fun setVocabDailyGoal(goal: Int) {
        context.penDataStore.edit { it[Keys.VOCAB_DAILY_GOAL] = goal.coerceIn(5, 200) }
    }

    suspend fun saveJinrishiciToken(token: String) {
        context.penDataStore.edit { it[Keys.JINRISHICI_TOKEN] = token }
    }

    suspend fun saveLastPoemFetchAt(timestamp: Long) {
        context.penDataStore.edit { it[Keys.LAST_POEM_FETCH_AT] = timestamp }
    }

    suspend fun saveWebDavConfig(url: String, user: String, pass: String) {
        context.penDataStore.edit {
            it[Keys.WEBDAV_URL] = url.trim()
            it[Keys.WEBDAV_USER] = user.trim()
            it[Keys.WEBDAV_PASS] = pass
        }
    }

    suspend fun saveLastSyncTime(timestamp: Long) {
        context.penDataStore.edit { it[Keys.LAST_SYNC_TIME] = timestamp }
    }

    suspend fun saveAiConfig(baseUrl: String, apiKey: String, model: String, externalMcpUrl: String) {
        val current = currentConfig()
        val active = current.activeAiProfile().copy(
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim()
        )
        upsertAiApiProfile(active)
        context.penDataStore.edit { it[Keys.EXTERNAL_MCP_URL] = externalMcpUrl.trim() }
    }

    suspend fun saveExternalMcpUrl(url: String) {
        context.penDataStore.edit { it[Keys.EXTERNAL_MCP_URL] = url.trim() }
    }

    suspend fun setActiveAiProfile(profileId: String) {
        context.penDataStore.edit { it[Keys.ACTIVE_AI_PROFILE_ID] = profileId }
    }

    suspend fun upsertAiApiProfile(profile: AiApiProfile) {
        context.penDataStore.edit { prefs ->
            val current = PenPrefsCodec.decodeAiApiProfiles(
                prefs[Keys.AI_PROFILES_JSON],
                prefs[Keys.AI_BASE_URL] ?: "https://api.deepseek.com/v1",
                prefs[Keys.AI_API_KEY].orEmpty(),
                prefs[Keys.AI_MODEL] ?: "deepseek-chat"
            ).toMutableList()
            val idx = current.indexOfFirst { it.id == profile.id }
            if (idx >= 0) current[idx] = profile else current.add(profile)
            prefs[Keys.AI_PROFILES_JSON] = PenPrefsCodec.encodeAiApiProfiles(current)
            prefs[Keys.ACTIVE_AI_PROFILE_ID] = profile.id
            prefs[Keys.AI_BASE_URL] = profile.baseUrl
            prefs[Keys.AI_API_KEY] = profile.apiKey
            prefs[Keys.AI_MODEL] = profile.model
        }
    }

    suspend fun deleteAiApiProfile(profileId: String) {
        context.penDataStore.edit { prefs ->
            val current = PenPrefsCodec.decodeAiApiProfiles(
                prefs[Keys.AI_PROFILES_JSON],
                prefs[Keys.AI_BASE_URL] ?: "https://api.deepseek.com/v1",
                prefs[Keys.AI_API_KEY].orEmpty(),
                prefs[Keys.AI_MODEL] ?: "deepseek-chat"
            ).filter { it.id != profileId }
            val safe = current.ifEmpty { PenConfig.defaultAiApiProfiles() }
            prefs[Keys.AI_PROFILES_JSON] = PenPrefsCodec.encodeAiApiProfiles(safe)
            if (prefs[Keys.ACTIVE_AI_PROFILE_ID] == profileId) {
                prefs[Keys.ACTIVE_AI_PROFILE_ID] = safe.first().id
            }
        }
    }

    suspend fun setActiveAiPreset(presetId: String) = setActiveAiProfile(presetId)

    suspend fun upsertAiPreset(preset: AiPreset) {
        val current = currentConfig()
        val existing = current.aiProfiles.firstOrNull { it.id == preset.id } ?: current.activeAiProfile()
        upsertAiApiProfile(
            existing.copy(
                id = preset.id,
                name = preset.name,
                systemPrompt = preset.systemPrompt,
                enableMcpTools = preset.enableMcpTools
            )
        )
    }

    suspend fun deleteAiPreset(presetId: String) = deleteAiApiProfile(presetId)

    private fun encodeStringMap(map: Map<String, String>): String =
        map.entries.joinToString("\n") { "${it.key}=${it.value}" }

    private fun decodeStringMap(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.lines().mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
        }.toMap()
    }

    private fun encodeIntMap(map: Map<Int, Int>): String =
        map.entries.joinToString(",") { "${it.key}:${it.value}" }

    private fun decodeIntMap(raw: String): Map<Int, Int> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",").mapNotNull { pair ->
            val p = pair.split(":")
            if (p.size == 2) {
                val k = p[0].toIntOrNull()
                val v = p[1].toIntOrNull()
                if (k != null && v != null) k to v else null
            } else null
        }.toMap()
    }

    private fun encodeSavedCities(list: List<SavedCityEntry>): String =
        list.joinToString("\n") { "${it.name}|${it.region}|${it.cmaCode}|${it.lat}|${it.lon}" }

    private fun decodeSavedCities(raw: String?): List<SavedCityEntry> {
        if (raw.isNullOrBlank()) return PenConfig.DEFAULT_SAVED_CITIES
        val parsed = raw.lines().mapNotNull { line ->
            val p = line.split("|")
            if (p.size >= 5) {
                val lat = p[3].toDoubleOrNull() ?: return@mapNotNull null
                val lon = p[4].toDoubleOrNull() ?: return@mapNotNull null
                SavedCityEntry(p[0], p[1], p[2], lat, lon)
            } else null
        }
        return parsed.ifEmpty { PenConfig.DEFAULT_SAVED_CITIES }
    }
}
