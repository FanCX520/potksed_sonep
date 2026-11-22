package com.example.data.prefs

import com.example.ui.theme.FontSlotSpec
import com.example.ui.theme.GranularTypographyProfile
import com.example.ui.theme.PenFontCatalog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JSON serialization helpers for PenOS 4.0 multi-AI API profiles,
 * 5-slot granular typography profiles, and legacy DataStore fields.
 */
object PenPrefsCodec {

    fun encodeAiApiProfiles(list: List<AiApiProfile>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("baseUrl", p.baseUrl)
                    .put("apiKey", p.apiKey)
                    .put("model", p.model)
                    .put("systemPrompt", p.systemPrompt)
                    .put("enableMcpTools", p.enableMcpTools)
                    .put("temperature", p.temperature.toDouble())
            )
        }
        return arr.toString()
    }

    fun decodeAiApiProfiles(
        raw: String?,
        fallbackBaseUrl: String,
        fallbackApiKey: String,
        fallbackModel: String
    ): List<AiApiProfile> {
        if (!raw.isNullOrBlank()) {
            val parsed = runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optString("id").trim()
                    if (id.isBlank()) return@mapNotNull null
                    AiApiProfile(
                        id = id,
                        name = o.optString("name", id),
                        baseUrl = o.optString("baseUrl", "https://api.deepseek.com/v1"),
                        apiKey = o.optString("apiKey", ""),
                        model = o.optString("model", "deepseek-chat"),
                        systemPrompt = o.optString("systemPrompt", PenConfig.DEFAULT_HARNESS_PROMPT),
                        enableMcpTools = o.optBoolean("enableMcpTools", true),
                        temperature = o.optDouble("temperature", 0.6).toFloat().coerceIn(0f, 1.5f)
                    )
                }
            }.getOrNull()
            if (!parsed.isNullOrEmpty()) return parsed
        }
        return PenConfig.defaultAiApiProfiles(fallbackBaseUrl, fallbackApiKey, fallbackModel)
    }

    fun encodeTypographyProfile(profile: GranularTypographyProfile): String {
        fun slotToJson(s: FontSlotSpec): JSONObject = JSONObject()
            .put("familyKey", s.familyKey)
            .put("scale", s.scale.toDouble())
            .put("weight", s.weight)
            .put("letterSpacingSp", s.letterSpacingSp.toDouble())
            .put("externalPath", s.externalPath)

        return JSONObject()
            .put("clock", slotToJson(profile.clock))
            .put("title", slotToJson(profile.title))
            .put("body", slotToJson(profile.body))
            .put("poetry", slotToJson(profile.poetry))
            .put("metric", slotToJson(profile.metric))
            .toString()
    }

    fun decodeTypographyProfile(raw: String?, legacyPoetryFontPath: String = ""): GranularTypographyProfile {
        val defaultProfile = GranularTypographyProfile()
        if (raw.isNullOrBlank()) {
            return if (legacyPoetryFontPath.isNotBlank()) {
                defaultProfile.copy(
                    poetry = defaultProfile.poetry.copy(
                        familyKey = PenFontCatalog.FAMILY_EXTERNAL,
                        externalPath = legacyPoetryFontPath
                    )
                )
            } else {
                defaultProfile
            }
        }
        return runCatching {
            val root = JSONObject(raw)
            fun parseSlot(key: String, fallback: FontSlotSpec): FontSlotSpec {
                val o = root.optJSONObject(key) ?: return fallback
                return FontSlotSpec(
                    familyKey = o.optString("familyKey", fallback.familyKey),
                    scale = o.optDouble("scale", fallback.scale.toDouble()).toFloat(),
                    weight = o.optInt("weight", fallback.weight),
                    letterSpacingSp = o.optDouble("letterSpacingSp", fallback.letterSpacingSp.toDouble()).toFloat(),
                    externalPath = o.optString("externalPath", fallback.externalPath)
                ).clamped()
            }
            GranularTypographyProfile(
                clock = parseSlot("clock", defaultProfile.clock),
                title = parseSlot("title", defaultProfile.title),
                body = parseSlot("body", defaultProfile.body),
                poetry = parseSlot("poetry", defaultProfile.poetry),
                metric = parseSlot("metric", defaultProfile.metric)
            )
        }.getOrDefault(defaultProfile)
    }
}
