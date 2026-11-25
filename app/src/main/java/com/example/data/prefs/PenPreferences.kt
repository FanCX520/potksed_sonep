package com.example.data.prefs

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.penDataStore: DataStore<Preferences> by preferencesDataStore(name = "penos_v6_prefs")

@Immutable
data class AiApiProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val temperature: Float = 0.6f,
    val enableExternalMcp: Boolean = true
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "你是 PenOS 极简学习助手。回答简洁准确、结构清晰，适合小屏阅读；当已连接外部 MCP 工具且需要查资料或执行外部操作时，优先调用合适的 MCP 工具并给出清晰结论。"

        fun defaultProfiles(): List<AiApiProfile> = listOf(
            AiApiProfile(
                id = "deepseek_v3",
                name = "DeepSeek V3",
                baseUrl = "https://api.deepseek.com/v1",
                apiKey = "",
                model = "deepseek-chat",
                temperature = 0.55f,
                enableExternalMcp = true
            ),
            AiApiProfile(
                id = "glm_flash",
                name = "智谱 GLM-4-Flash",
                baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                apiKey = "",
                model = "glm-4-flash",
                temperature = 0.6f,
                enableExternalMcp = true
            ),
            AiApiProfile(
                id = "qwen_turbo",
                name = "通义千问 Turbo",
                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                apiKey = "",
                model = "qwen-turbo",
                temperature = 0.6f,
                enableExternalMcp = true
            )
        )
    }
}

@Immutable
data class PenConfig(
    val isDarkTheme: Boolean = true,
    val staticWallpaperMode: String = WALLPAPER_SOLID,
    val customWallpaperPath: String = "",
    val showSecondsOnHome: Boolean = false,
    val scanAppPackage: String = "",
    val scanAppActivity: String = "",
    val blockedHomePackage: String = "",
    val blockedHomeActivities: Set<String> = emptySet(),
    val hiddenPackages: Set<String> = emptySet(),
    val vaultPin: String = "0000",
    val keepAliveEnabled: Boolean = false,
    val aiProfiles: List<AiApiProfile> = AiApiProfile.defaultProfiles(),
    val activeAiProfileId: String = "deepseek_v3",
    val externalMcpUrl: String = "",
    val externalMcpToken: String = "",
    val activeVocabBook: String = "highschool",
    val vocabDailyGoal: Int = 20,
    val pomodoroMinutes: Int = 25,
    val standbyBrightness: Float = 0.25f
) {
    fun activeAiProfile(): AiApiProfile =
        aiProfiles.firstOrNull { it.id == activeAiProfileId }
            ?: aiProfiles.firstOrNull()
            ?: AiApiProfile.defaultProfiles().first()

    companion object {
        const val WALLPAPER_SOLID = "solid"
        const val WALLPAPER_INK = "preset_ink"
        const val WALLPAPER_AURORA = "preset_aurora"
        const val WALLPAPER_GALLERY = "preset_gallery"
        const val WALLPAPER_CUSTOM = "custom_file"
    }
}

class PenPreferencesRepository(private val context: Context) {

    private object Keys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val STATIC_WALLPAPER_MODE = stringPreferencesKey("static_wallpaper_mode")
        val CUSTOM_WALLPAPER_PATH = stringPreferencesKey("custom_wallpaper_path")
        val SHOW_SECONDS_HOME = booleanPreferencesKey("show_seconds_home")
        val SCAN_APP_PACKAGE = stringPreferencesKey("scan_app_package")
        val SCAN_APP_ACTIVITY = stringPreferencesKey("scan_app_activity")
        val BLOCKED_HOME_PACKAGE = stringPreferencesKey("blocked_home_package")
        val BLOCKED_HOME_ACTIVITIES = stringSetPreferencesKey("blocked_home_activities")
        val HIDDEN_PACKAGES = stringSetPreferencesKey("hidden_packages")
        val VAULT_PIN = stringPreferencesKey("vault_pin")
        val KEEP_ALIVE_ENABLED = booleanPreferencesKey("keep_alive_enabled")
        val AI_PROFILES_JSON = stringPreferencesKey("ai_profiles_json")
        val ACTIVE_AI_PROFILE_ID = stringPreferencesKey("active_ai_profile_id")
        val EXTERNAL_MCP_URL = stringPreferencesKey("external_mcp_url")
        val EXTERNAL_MCP_TOKEN = stringPreferencesKey("external_mcp_token")
        val ACTIVE_VOCAB_BOOK = stringPreferencesKey("active_vocab_book")
        val VOCAB_DAILY_GOAL = intPreferencesKey("vocab_daily_goal")
        val POMODORO_MINUTES = intPreferencesKey("pomodoro_minutes")
        val STANDBY_BRIGHTNESS = floatPreferencesKey("standby_brightness")
    }

    val configFlow: Flow<PenConfig> = context.penDataStore.data.map { p ->
        val profiles = decodeAiProfiles(p[Keys.AI_PROFILES_JSON])
        val activeId = p[Keys.ACTIVE_AI_PROFILE_ID]
            ?.takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.first().id

        PenConfig(
            isDarkTheme = p[Keys.IS_DARK_THEME] ?: true,
            staticWallpaperMode = p[Keys.STATIC_WALLPAPER_MODE] ?: PenConfig.WALLPAPER_SOLID,
            customWallpaperPath = p[Keys.CUSTOM_WALLPAPER_PATH].orEmpty(),
            showSecondsOnHome = p[Keys.SHOW_SECONDS_HOME] ?: false,
            scanAppPackage = p[Keys.SCAN_APP_PACKAGE].orEmpty(),
            scanAppActivity = p[Keys.SCAN_APP_ACTIVITY].orEmpty(),
            blockedHomePackage = p[Keys.BLOCKED_HOME_PACKAGE].orEmpty(),
            blockedHomeActivities = p[Keys.BLOCKED_HOME_ACTIVITIES] ?: emptySet(),
            hiddenPackages = p[Keys.HIDDEN_PACKAGES] ?: emptySet(),
            vaultPin = p[Keys.VAULT_PIN] ?: "0000",
            keepAliveEnabled = p[Keys.KEEP_ALIVE_ENABLED] ?: false,
            aiProfiles = profiles,
            activeAiProfileId = activeId,
            externalMcpUrl = p[Keys.EXTERNAL_MCP_URL].orEmpty(),
            externalMcpToken = p[Keys.EXTERNAL_MCP_TOKEN].orEmpty(),
            activeVocabBook = p[Keys.ACTIVE_VOCAB_BOOK] ?: "highschool",
            vocabDailyGoal = p[Keys.VOCAB_DAILY_GOAL] ?: 20,
            pomodoroMinutes = p[Keys.POMODORO_MINUTES] ?: 25,
            standbyBrightness = p[Keys.STANDBY_BRIGHTNESS] ?: 0.25f
        )
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.penDataStore.edit { it[Keys.IS_DARK_THEME] = isDark }
    }

    suspend fun setStaticWallpaper(mode: String, customPath: String? = null) {
        context.penDataStore.edit {
            it[Keys.STATIC_WALLPAPER_MODE] = mode
            if (customPath != null) it[Keys.CUSTOM_WALLPAPER_PATH] = customPath
        }
    }

    suspend fun setShowSecondsOnHome(show: Boolean) {
        context.penDataStore.edit { it[Keys.SHOW_SECONDS_HOME] = show }
    }

    suspend fun setScanApp(pkg: String, activity: String = "") {
        context.penDataStore.edit {
            it[Keys.SCAN_APP_PACKAGE] = pkg.trim()
            it[Keys.SCAN_APP_ACTIVITY] = activity.trim()
        }
    }

    suspend fun setBlockedHomePackage(pkg: String) {
        context.penDataStore.edit { it[Keys.BLOCKED_HOME_PACKAGE] = pkg.trim() }
    }

    suspend fun setBlockedHomeActivities(activities: Set<String>) {
        context.penDataStore.edit { it[Keys.BLOCKED_HOME_ACTIVITIES] = activities }
    }

    suspend fun setHiddenPackages(packages: Set<String>) {
        context.penDataStore.edit { it[Keys.HIDDEN_PACKAGES] = packages }
    }

    suspend fun setVaultPin(pin: String) {
        if (pin.length in 4..8) {
            context.penDataStore.edit { it[Keys.VAULT_PIN] = pin }
        }
    }

    suspend fun setKeepAliveEnabled(enabled: Boolean) {
        context.penDataStore.edit { it[Keys.KEEP_ALIVE_ENABLED] = enabled }
    }

    suspend fun saveAiProfiles(profiles: List<AiApiProfile>, activeId: String) {
        val safe = profiles.ifEmpty { AiApiProfile.defaultProfiles() }
        val resolvedActive = activeId.takeIf { id -> safe.any { it.id == id } } ?: safe.first().id
        context.penDataStore.edit {
            it[Keys.AI_PROFILES_JSON] = encodeAiProfiles(safe)
            it[Keys.ACTIVE_AI_PROFILE_ID] = resolvedActive
        }
    }

    suspend fun selectAiProfile(profileId: String) {
        context.penDataStore.edit { it[Keys.ACTIVE_AI_PROFILE_ID] = profileId }
    }

    suspend fun saveExternalMcp(url: String, token: String) {
        context.penDataStore.edit {
            it[Keys.EXTERNAL_MCP_URL] = url.trim()
            it[Keys.EXTERNAL_MCP_TOKEN] = token.trim()
        }
    }

    suspend fun setActiveVocabBook(bookId: String) {
        context.penDataStore.edit { it[Keys.ACTIVE_VOCAB_BOOK] = bookId }
    }

    suspend fun setPomodoroMinutes(minutes: Int) {
        context.penDataStore.edit { it[Keys.POMODORO_MINUTES] = minutes.coerceIn(5, 90) }
    }

    suspend fun setStandbyBrightness(brightness: Float) {
        context.penDataStore.edit { it[Keys.STANDBY_BRIGHTNESS] = brightness.coerceIn(0.05f, 1.0f) }
    }

    companion object {
        fun encodeAiProfiles(profiles: List<AiApiProfile>): String {
            val arr = JSONArray()
            profiles.forEach { p ->
                arr.put(
                    JSONObject()
                        .put("id", p.id)
                        .put("name", p.name)
                        .put("baseUrl", p.baseUrl)
                        .put("apiKey", p.apiKey)
                        .put("model", p.model)
                        .put("systemPrompt", p.systemPrompt)
                        .put("temperature", p.temperature.toDouble())
                        .put("enableExternalMcp", p.enableExternalMcp)
                )
            }
            return arr.toString()
        }

        fun decodeAiProfiles(raw: String?): List<AiApiProfile> {
            if (raw.isNullOrBlank()) return AiApiProfile.defaultProfiles()
            return runCatching {
                val arr = JSONArray(raw)
                val out = mutableListOf<AiApiProfile>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").ifBlank { "ai_$i" }
                    out.add(
                        AiApiProfile(
                            id = id,
                            name = o.optString("name").ifBlank { "自定义模型" },
                            baseUrl = o.optString("baseUrl"),
                            apiKey = o.optString("apiKey"),
                            model = o.optString("model"),
                            systemPrompt = o.optString("systemPrompt").ifBlank { AiApiProfile.DEFAULT_SYSTEM_PROMPT },
                            temperature = o.optDouble("temperature", 0.6).toFloat().coerceIn(0f, 1.5f),
                            enableExternalMcp = o.optBoolean("enableExternalMcp", true)
                        )
                    )
                }
                out.ifEmpty { AiApiProfile.defaultProfiles() }
            }.getOrDefault(AiApiProfile.defaultProfiles())
        }
    }
}
