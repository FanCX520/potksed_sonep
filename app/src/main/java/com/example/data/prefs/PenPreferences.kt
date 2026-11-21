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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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

data class PenConfig(
    val designStyle: String = STYLE_DARK_STANDBY,
    val standByStyleMode: String = STANDBY_STYLE_OLED_DARK,
    val hiddenPackages: Set<String> = emptySet(),
    val pinnedDockPackages: List<String> = emptyList(),
    val enabledWidgetIds: List<String> = DEFAULT_WIDGET_ORDER,
    val desktopDeckMode: String = DECK_MODE_MIXED, // mixed | native_only
    val nativeWidgetIds: List<Int> = emptyList(),
    val nativeWidgetWidths: Map<Int, Int> = emptyMap(),
    // Jiuxuewang component-level scan & anti-lock settings
    val scanAppPackage: String = "",
    val scanActivityClass: String = "",
    val blockedStockLauncherPackage: String = "",
    val blockedHomeActivities: Set<String> = emptySet(),
    // Independent Wallpapers for Dark StandBy vs Apple White Gallery
    val darkWallpaperPresetId: String = WALLPAPER_AURORA,
    val darkCustomWallpaperPath: String = "",
    val lightWallpaperPresetId: String = WALLPAPER_GALLERY_LIGHT,
    val lightCustomWallpaperPath: String = "",
    // Custom App Icons (pkg -> filePath or "color:#RRGGBB")
    val customAppIcons: Map<String, String> = emptyMap(),
    // External Noto Serif SC / custom poetry font path override
    val customPoetryFontPath: String = "",
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
    // Vocab
    val activeVocabBook: String = "highschool",
    val vocabDailyGoal: Int = 20,
    // Poetry
    val jinrishiciToken: String = "",
    val lastPoemFetchAt: Long = 0L,
    // Weather (China ICP-filed multi-city)
    val weatherCityName: String = "北京",
    val weatherCityCode: String = "101010100",
    val weatherLat: Double = 39.9042,
    val weatherLon: Double = 116.4074,
    val savedCities: List<SavedCityEntry> = DEFAULT_SAVED_CITIES,
    val weatherQWeatherKey: String = "",
    val weatherAmapKey: String = "",
    val lastWeatherFetchAt: Long = 0L,
    // WebDAV Sync (China ICP-filed Nutstore default)
    val webDavUrl: String = "https://dav.jianguoyun.com/dav/PenOS/",
    val webDavUser: String = "",
    val webDavPass: String = "",
    val lastSyncTime: Long = 0L,
    // Lab AI + Custom Presets + MCP (China ICP-filed OpenAI-compatible defaults)
    val aiBaseUrl: String = "https://api.deepseek.com/v1",
    val aiApiKey: String = "",
    val aiModel: String = "deepseek-chat",
    val aiPresets: List<AiPreset> = DEFAULT_AI_PRESETS,
    val activeAiPresetId: String = "penos_mcp",
    val externalMcpUrl: String = ""
) {
    companion object {
        const val STYLE_DARK_STANDBY = "dark_standby"
        const val STYLE_APPLE_LIGHT = "apple_light"

        const val STANDBY_STYLE_FOLLOW = "follow"
        const val STANDBY_STYLE_OLED_DARK = "oled_dark"
        const val STANDBY_STYLE_APPLE_LIGHT = "apple_light"

        const val DECK_MODE_MIXED = "mixed"
        const val DECK_MODE_NATIVE_ONLY = "native_only"

        // Dark style wallpapers
        const val WALLPAPER_AURORA = "aurora"
        const val WALLPAPER_INK = "ink"
        const val WALLPAPER_OBSIDIAN = "obsidian"

        // Apple White Gallery independent light clock wallpapers
        const val WALLPAPER_GALLERY_LIGHT = "gallery_light"
        const val WALLPAPER_PURE_MIST = "pure_mist"
        const val WALLPAPER_WARM_PAPER = "warm_paper"

        const val WALLPAPER_CUSTOM = "custom"

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
            "weather",
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

        val DEFAULT_AI_PRESETS = listOf(
            AiPreset(
                id = "penos_mcp",
                name = "桌面管家 · MCP",
                systemPrompt = "你是 PenOS 词典笔智能桌面管家。你可以直接调用 MCP 工具帮助用户切换桌面主题、开关小组件、添加待办和日历定时日程、切换天气城市、开启全屏番茄钟、播放专注白噪音、调节亮度音量或收录英语生词。回答简明精炼，适合 640x172 词典笔屏幕阅读。",
                enableMcpTools = true
            ),
            AiPreset(
                id = "english_tutor",
                name = "英语词典外教",
                systemPrompt = "你是专业简洁的英语学习私教，擅长用词根词缀、地道例句和易混词对比讲解英语，并可在用户要求时通过工具把生词加入百词斩生词本。回复简明易读。",
                enableMcpTools = true
            ),
            AiPreset(
                id = "poetry_scholar",
                name = "古诗词赏析师",
                systemPrompt = "你是温润儒雅的中国古典诗词赏析老师，擅长简洁点拨意象、典故与名句默写技巧，适合在词典笔小屏快速阅读。",
                enableMcpTools = false
            ),
            AiPreset(
                id = "focus_coach",
                name = "番茄学习督导",
                systemPrompt = "你是高效自律的学习督导，帮助用户拆解学习计划并调用工具创建日程、待办、启动番茄专注倒计时和舒缓白噪音。",
                enableMcpTools = true
            )
        )
    }

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

        val DARK_WALLPAPER_PRESET = stringPreferencesKey("wallpaper_preset") // keeps backward compat
        val DARK_CUSTOM_WALLPAPER = stringPreferencesKey("custom_wallpaper_uri")
        val LIGHT_WALLPAPER_PRESET = stringPreferencesKey("light_wallpaper_preset")
        val LIGHT_CUSTOM_WALLPAPER = stringPreferencesKey("light_custom_wallpaper_path")

        val CUSTOM_APP_ICONS = stringPreferencesKey("custom_app_icons_kv")
        val CUSTOM_POETRY_FONT_PATH = stringPreferencesKey("custom_poetry_font_path")

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
        val AI_PRESETS_ENCODED = stringPreferencesKey("ai_presets_encoded")
        val ACTIVE_AI_PRESET_ID = stringPreferencesKey("active_ai_preset_id")
        val EXTERNAL_MCP_URL = stringPreferencesKey("external_mcp_url")
    }

    val configFlow: Flow<PenConfig> = context.penDataStore.data.map { prefs ->
        val pinnedRaw = prefs[Keys.PINNED_DOCK_CSV].orEmpty()
        val widgetsRaw = prefs[Keys.ENABLED_WIDGETS_CSV]
        val nativeIdsRaw = prefs[Keys.NATIVE_WIDGET_IDS].orEmpty()
        val nativeWidthsRaw = prefs[Keys.NATIVE_WIDGET_WIDTHS].orEmpty()

        PenConfig(
            designStyle = prefs[Keys.DESIGN_STYLE] ?: PenConfig.STYLE_DARK_STANDBY,
            standByStyleMode = prefs[Keys.STANDBY_STYLE_MODE] ?: PenConfig.STANDBY_STYLE_OLED_DARK,
            hiddenPackages = prefs[Keys.HIDDEN_PACKAGES] ?: emptySet(),
            pinnedDockPackages = if (pinnedRaw.isBlank()) emptyList() else pinnedRaw.split(",").filter { it.isNotBlank() },
            enabledWidgetIds = if (widgetsRaw == null) {
                PenConfig.DEFAULT_WIDGET_ORDER
            } else {
                widgetsRaw.split(",").filter { it.isNotBlank() }
            },
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
            customAppIcons = decodeStringMap(prefs[Keys.CUSTOM_APP_ICONS].orEmpty()),
            customPoetryFontPath = prefs[Keys.CUSTOM_POETRY_FONT_PATH].orEmpty(),
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
            aiBaseUrl = prefs[Keys.AI_BASE_URL] ?: "https://api.deepseek.com/v1",
            aiApiKey = prefs[Keys.AI_API_KEY].orEmpty(),
            aiModel = prefs[Keys.AI_MODEL] ?: "deepseek-chat",
            aiPresets = decodeAiPresets(prefs[Keys.AI_PRESETS_ENCODED]),
            activeAiPresetId = prefs[Keys.ACTIVE_AI_PRESET_ID] ?: "penos_mcp",
            externalMcpUrl = prefs[Keys.EXTERNAL_MCP_URL].orEmpty()
        )
    }

    suspend fun currentConfig(): PenConfig = configFlow.first()

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
            if (current.none { it.name == city.name }) {
                current.add(0, city)
            }
            prefs[Keys.SAVED_WEATHER_CITIES] = encodeSavedCities(current.take(16))
        }
    }

    suspend fun removeWeatherCityFromFavorites(cityName: String) {
        context.penDataStore.edit { prefs ->
            val current = decodeSavedCities(prefs[Keys.SAVED_WEATHER_CITIES]).filter { it.name != cityName }
            if (current.isNotEmpty()) {
                prefs[Keys.SAVED_WEATHER_CITIES] = encodeSavedCities(current)
            }
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
            if (current.contains(packageName)) {
                current.remove(packageName)
            } else {
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
        context.penDataStore.edit { it[Keys.CUSTOM_POETRY_FONT_PATH] = path.trim() }
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
        context.penDataStore.edit {
            it[Keys.AI_BASE_URL] = baseUrl.trim()
            it[Keys.AI_API_KEY] = apiKey.trim()
            it[Keys.AI_MODEL] = model.trim()
            it[Keys.EXTERNAL_MCP_URL] = externalMcpUrl.trim()
        }
    }

    suspend fun setActiveAiPreset(presetId: String) {
        context.penDataStore.edit { it[Keys.ACTIVE_AI_PRESET_ID] = presetId }
    }

    suspend fun upsertAiPreset(preset: AiPreset) {
        context.penDataStore.edit { prefs ->
            val list = decodeAiPresets(prefs[Keys.AI_PRESETS_ENCODED]).toMutableList()
            val idx = list.indexOfFirst { it.id == preset.id }
            if (idx >= 0) list[idx] = preset else list.add(preset)
            prefs[Keys.AI_PRESETS_ENCODED] = encodeAiPresets(list)
            prefs[Keys.ACTIVE_AI_PRESET_ID] = preset.id
        }
    }

    suspend fun deleteAiPreset(presetId: String) {
        context.penDataStore.edit { prefs ->
            val list = decodeAiPresets(prefs[Keys.AI_PRESETS_ENCODED]).filter { it.id != presetId }
            val safe = list.ifEmpty { PenConfig.DEFAULT_AI_PRESETS }
            prefs[Keys.AI_PRESETS_ENCODED] = encodeAiPresets(safe)
            if (prefs[Keys.ACTIVE_AI_PRESET_ID] == presetId) {
                prefs[Keys.ACTIVE_AI_PRESET_ID] = safe.first().id
            }
        }
    }

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

    private fun encodeAiPresets(list: List<AiPreset>): String =
        list.joinToString("\u001E") { preset ->
            listOf(
                preset.id.replace("\u001F", ""),
                preset.name.replace("\u001F", ""),
                preset.systemPrompt.replace("\u001F", " "),
                if (preset.enableMcpTools) "1" else "0"
            ).joinToString("\u001F")
        }

    private fun decodeAiPresets(raw: String?): List<AiPreset> {
        if (raw.isNullOrBlank()) return PenConfig.DEFAULT_AI_PRESETS
        val parsed = raw.split("\u001E").mapNotNull { entry ->
            val p = entry.split("\u001F")
            if (p.size >= 4) {
                AiPreset(p[0], p[1], p[2], p[3] == "1")
            } else null
        }
        return parsed.ifEmpty { PenConfig.DEFAULT_AI_PRESETS }
    }
}
