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

data class PenConfig(
    val designStyle: String = STYLE_DARK_STANDBY,
    val standByStyleMode: String = STANDBY_STYLE_OLED_DARK,
    val hiddenPackages: Set<String> = emptySet(),
    val pinnedDockPackages: List<String> = emptyList(),
    val enabledWidgetIds: List<String> = DEFAULT_WIDGET_ORDER,
    val scanAppPackage: String = "",
    val blockedStockLauncherPackage: String = "",
    val wallpaperPresetId: String = WALLPAPER_AURORA,
    val customWallpaperUri: String = "",
    val standByTimeoutSec: Int = 30,
    val standByBrightness: Float = 0.08f,
    val standByRightWidget: String = STANDBY_WIDGET_POETRY,
    val standByClockColor: String = CLOCK_COLOR_CORAL,
    val vaultPin: String = "",
    val keepAliveEnabled: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O,
    val penFramePreviewEnabled: Boolean = true,
    val activeVocabBook: String = "highschool",
    val jinrishiciToken: String = "",
    val lastPoemFetchAt: Long = 0L,
    val weatherCityName: String = "北京",
    val weatherLat: Double = 39.9042,
    val weatherLon: Double = 116.4074,
    val lastWeatherFetchAt: Long = 0L,
    val webDavUrl: String = "https://dav.jianguoyun.com/dav/PenOS/",
    val webDavUser: String = "",
    val webDavPass: String = "",
    val lastSyncTime: Long = 0L
) {
    companion object {
        const val STYLE_DARK_STANDBY = "dark_standby"
        const val STYLE_APPLE_LIGHT = "apple_light"

        const val STANDBY_STYLE_FOLLOW = "follow"
        const val STANDBY_STYLE_OLED_DARK = "oled_dark"
        const val STANDBY_STYLE_APPLE_LIGHT = "apple_light"

        const val WALLPAPER_AURORA = "aurora"
        const val WALLPAPER_INK = "ink"
        const val WALLPAPER_OBSIDIAN = "obsidian"
        const val WALLPAPER_CUSTOM = "custom"

        const val STANDBY_WIDGET_POETRY = "poetry"
        const val STANDBY_WIDGET_VOCAB = "vocab"
        const val STANDBY_WIDGET_WEATHER = "weather"
        const val STANDBY_WIDGET_TODO = "todo"

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
    }

    fun isAppleLightDesktop(): Boolean = designStyle == STYLE_APPLE_LIGHT

    fun isAppleLightStandBy(): Boolean = when (standByStyleMode) {
        STANDBY_STYLE_APPLE_LIGHT -> true
        STANDBY_STYLE_OLED_DARK -> false
        else -> isAppleLightDesktop()
    }
}

class PenPreferencesRepository(private val context: Context) {

    private object Keys {
        val DESIGN_STYLE = stringPreferencesKey("design_style")
        val STANDBY_STYLE_MODE = stringPreferencesKey("standby_style_mode")
        val HIDDEN_PACKAGES = stringSetPreferencesKey("hidden_packages")
        val PINNED_DOCK_CSV = stringPreferencesKey("pinned_dock_csv")
        val ENABLED_WIDGETS_CSV = stringPreferencesKey("enabled_widgets_csv")
        val SCAN_APP_PACKAGE = stringPreferencesKey("scan_app_package")
        val BLOCKED_LAUNCHER_PACKAGE = stringPreferencesKey("blocked_launcher_package")
        val WALLPAPER_PRESET = stringPreferencesKey("wallpaper_preset")
        val CUSTOM_WALLPAPER_URI = stringPreferencesKey("custom_wallpaper_uri")
        val STANDBY_TIMEOUT_SEC = intPreferencesKey("standby_timeout_sec")
        val STANDBY_BRIGHTNESS = floatPreferencesKey("standby_brightness")
        val STANDBY_RIGHT_WIDGET = stringPreferencesKey("standby_right_widget")
        val STANDBY_CLOCK_COLOR = stringPreferencesKey("standby_clock_color")
        val VAULT_PIN = stringPreferencesKey("vault_pin")
        val KEEP_ALIVE_ENABLED = booleanPreferencesKey("keep_alive_enabled")
        val PEN_FRAME_PREVIEW = booleanPreferencesKey("pen_frame_preview")
        val ACTIVE_VOCAB_BOOK = stringPreferencesKey("active_vocab_book")
        val JINRISHICI_TOKEN = stringPreferencesKey("jinrishici_token")
        val LAST_POEM_FETCH_AT = longPreferencesKey("last_poem_fetch_at")
        val WEATHER_CITY_NAME = stringPreferencesKey("weather_city_name")
        val WEATHER_LAT = doublePreferencesKey("weather_lat")
        val WEATHER_LON = doublePreferencesKey("weather_lon")
        val LAST_WEATHER_FETCH_AT = longPreferencesKey("last_weather_fetch_at")
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USER = stringPreferencesKey("webdav_user")
        val WEBDAV_PASS = stringPreferencesKey("webdav_pass")
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
    }

    val configFlow: Flow<PenConfig> = context.penDataStore.data.map { prefs ->
        val pinnedRaw = prefs[Keys.PINNED_DOCK_CSV].orEmpty()
        val widgetsRaw = prefs[Keys.ENABLED_WIDGETS_CSV]
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
            scanAppPackage = prefs[Keys.SCAN_APP_PACKAGE].orEmpty(),
            blockedStockLauncherPackage = prefs[Keys.BLOCKED_LAUNCHER_PACKAGE].orEmpty(),
            wallpaperPresetId = prefs[Keys.WALLPAPER_PRESET] ?: PenConfig.WALLPAPER_AURORA,
            customWallpaperUri = prefs[Keys.CUSTOM_WALLPAPER_URI].orEmpty(),
            standByTimeoutSec = prefs[Keys.STANDBY_TIMEOUT_SEC] ?: 30,
            standByBrightness = prefs[Keys.STANDBY_BRIGHTNESS] ?: 0.08f,
            standByRightWidget = prefs[Keys.STANDBY_RIGHT_WIDGET] ?: PenConfig.STANDBY_WIDGET_POETRY,
            standByClockColor = prefs[Keys.STANDBY_CLOCK_COLOR] ?: PenConfig.CLOCK_COLOR_CORAL,
            vaultPin = prefs[Keys.VAULT_PIN].orEmpty(),
            keepAliveEnabled = prefs[Keys.KEEP_ALIVE_ENABLED] ?: (Build.VERSION.SDK_INT < Build.VERSION_CODES.O),
            penFramePreviewEnabled = prefs[Keys.PEN_FRAME_PREVIEW] ?: true,
            activeVocabBook = prefs[Keys.ACTIVE_VOCAB_BOOK] ?: "highschool",
            jinrishiciToken = prefs[Keys.JINRISHICI_TOKEN].orEmpty(),
            lastPoemFetchAt = prefs[Keys.LAST_POEM_FETCH_AT] ?: 0L,
            weatherCityName = prefs[Keys.WEATHER_CITY_NAME] ?: "北京",
            weatherLat = prefs[Keys.WEATHER_LAT] ?: 39.9042,
            weatherLon = prefs[Keys.WEATHER_LON] ?: 116.4074,
            lastWeatherFetchAt = prefs[Keys.LAST_WEATHER_FETCH_AT] ?: 0L,
            webDavUrl = prefs[Keys.WEBDAV_URL] ?: "https://dav.jianguoyun.com/dav/PenOS/",
            webDavUser = prefs[Keys.WEBDAV_USER].orEmpty(),
            webDavPass = prefs[Keys.WEBDAV_PASS].orEmpty(),
            lastSyncTime = prefs[Keys.LAST_SYNC_TIME] ?: 0L
        )
    }

    suspend fun currentConfig(): PenConfig = configFlow.first()

    suspend fun setDesignStyle(style: String) {
        context.penDataStore.edit { it[Keys.DESIGN_STYLE] = style }
    }

    suspend fun setStandByStyleMode(mode: String) {
        context.penDataStore.edit { it[Keys.STANDBY_STYLE_MODE] = mode }
    }

    suspend fun setWeatherCity(name: String, lat: Double, lon: Double) {
        context.penDataStore.edit {
            it[Keys.WEATHER_CITY_NAME] = name
            it[Keys.WEATHER_LAT] = lat
            it[Keys.WEATHER_LON] = lon
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
                if (current.size >= 6) current.removeAt(0)
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

    suspend fun setScanAppPackage(packageName: String) {
        context.penDataStore.edit { it[Keys.SCAN_APP_PACKAGE] = packageName }
    }

    suspend fun setBlockedStockLauncherPackage(packageName: String) {
        context.penDataStore.edit { it[Keys.BLOCKED_LAUNCHER_PACKAGE] = packageName }
    }

    suspend fun setWallpaper(presetId: String, customUri: String = "") {
        context.penDataStore.edit {
            it[Keys.WALLPAPER_PRESET] = presetId
            if (customUri.isNotBlank()) {
                it[Keys.CUSTOM_WALLPAPER_URI] = customUri
            }
        }
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
}
