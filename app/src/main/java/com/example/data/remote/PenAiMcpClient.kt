package com.example.data.remote

import com.example.data.prefs.AiApiProfile
import com.example.data.prefs.AiPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class AiProviderTemplate(
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val note: String
)

data class AiChatMessage(
    val id: String,
    val role: String, // "user" | "assistant" | "tool"
    val content: String,
    val toolActionsSummary: String = ""
)

data class PenMcpToolResult(
    val toolName: String,
    val summary: String,
    val success: Boolean
)

/**
 * PenOS 4.0 DeepSeek Harness-Grade Full-Control MCP Client.
 * Supports multi-round tool loops (inspect -> plan -> mutate -> verify), 22 built-in PenOS Harness tools
 * (declarative custom widget creation, 5-slot global typography, 4-mode dynamic wallpapers, 6-style clock,
 * Apple TV drawer management, multi-AI profile switching, email read/summarize/draft, productivity),
 * plus external HTTP JSON-RPC MCP servers.
 */
class PenAiMcpClient {

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        val icpProviderTemplates: List<AiProviderTemplate> = listOf(
            AiProviderTemplate("DeepSeek V3", "https://api.deepseek.com/v1", "deepseek-chat", "深度求索 · 推荐 Harness"),
            AiProviderTemplate("DeepSeek R1", "https://api.deepseek.com/v1", "deepseek-reasoner", "深度推理 · 国内备案"),
            AiProviderTemplate("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", "智谱清言 · 免费额度"),
            AiProviderTemplate("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-turbo", "阿里云百炼 · 国内备案"),
            AiProviderTemplate("硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct", "SiliconFlow · 国内备案"),
            AiProviderTemplate("Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k", "月之暗面 · 国内备案")
        )
    }

    private val client by lazy {
        Api21NetworkFactory.createClient(connectTimeoutSec = 15, readTimeoutSec = 55)
    }

    suspend fun sendChatWithMcp(
        profile: AiApiProfile,
        history: List<AiChatMessage>,
        userPrompt: String,
        externalMcpUrl: String,
        desktopToolExecutor: suspend (toolName: String, args: JSONObject) -> PenMcpToolResult
    ): Result<AiChatMessage> = sendChatWithMcp(
        baseUrl = profile.baseUrl,
        apiKey = profile.apiKey,
        model = profile.model,
        preset = profile.toAiPreset(),
        history = history,
        userPrompt = userPrompt,
        externalMcpUrl = externalMcpUrl,
        temperature = profile.temperature,
        desktopToolExecutor = desktopToolExecutor
    )

    suspend fun sendChatWithMcp(
        baseUrl: String,
        apiKey: String,
        model: String,
        preset: AiPreset,
        history: List<AiChatMessage>,
        userPrompt: String,
        externalMcpUrl: String,
        temperature: Float = 0.6f,
        desktopToolExecutor: suspend (toolName: String, args: JSONObject) -> PenMcpToolResult
    ): Result<AiChatMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase = baseUrl.trim().trimEnd('/')
            if (cleanBase.isBlank()) throw IllegalArgumentException("请先在实验室或局域网后台填写 AI 接口地址")
            if (apiKey.isBlank()) throw IllegalArgumentException("请先配置当前模型预设的 API Key（仅存本地）")

            val endpoint = if (cleanBase.endsWith("/chat/completions")) cleanBase else "$cleanBase/chat/completions"

            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", preset.systemPrompt))
                history.takeLast(10).forEach { msg ->
                    if (msg.role == "user" || msg.role == "assistant") {
                        put(JSONObject().put("role", msg.role).put("content", msg.content))
                    }
                }
                put(JSONObject().put("role", "user").put("content", userPrompt))
            }

            val externalTools = if (preset.enableMcpTools && externalMcpUrl.isNotBlank()) {
                fetchExternalMcpTools(externalMcpUrl).getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val externalToolNames = externalTools.map { it.first }.toSet()
            val toolsPayload = if (preset.enableMcpTools) buildCombinedToolsSchema(externalTools) else null

            val executedLogs = mutableListOf<String>()
            var finalAssistantText = ""

            // DeepSeek Harness multi-round agentic tool loop (up to 4 rounds: inspect -> create -> configure -> confirm)
            for (round in 0 until 4) {
                val reqObj = JSONObject().apply {
                    put("model", model.ifBlank { "deepseek-chat" })
                    put("messages", messages)
                    put("temperature", temperature.toDouble().coerceIn(0.0, 1.5))
                    if (toolsPayload != null && round < 3) {
                        put("tools", toolsPayload)
                        put("tool_choice", "auto")
                    }
                }
                val respJson = postJson(endpoint, reqObj, apiKey)
                val choices = respJson.optJSONArray("choices")
                    ?: throw IllegalStateException("模型未返回 choices：${respJson.toString().take(160)}")
                if (choices.length() == 0) throw IllegalStateException("模型返回空结果")

                val assistantMsg = choices.getJSONObject(0).getJSONObject("message")
                val toolCalls = assistantMsg.optJSONArray("tool_calls")

                if (toolCalls != null && toolCalls.length() > 0 && round < 3) {
                    messages.put(assistantMsg)
                    for (i in 0 until toolCalls.length()) {
                        val call = toolCalls.getJSONObject(i)
                        val callId = call.optString("id", "call_${round}_$i")
                        val fn = call.getJSONObject("function")
                        val fnName = fn.optString("name")
                        val argsStr = fn.optString("arguments", "{}")
                        val argsObj = runCatching { JSONObject(argsStr) }.getOrDefault(JSONObject())

                        val toolResult = if (externalToolNames.contains(fnName) && externalMcpUrl.isNotBlank()) {
                            invokeExternalMcpTool(externalMcpUrl, fnName, argsObj)
                        } else {
                            desktopToolExecutor(fnName, argsObj)
                        }

                        executedLogs.add("${if (toolResult.success) "✓" else "✗"} ${toolResult.summary}")
                        messages.put(
                            JSONObject()
                                .put("role", "tool")
                                .put("tool_call_id", callId)
                                .put("name", fnName)
                                .put("content", toolResult.summary)
                        )
                    }
                    continue
                }

                finalAssistantText = assistantMsg.optString("content", "").trim()
                break
            }

            if (finalAssistantText.isBlank() && executedLogs.isNotEmpty()) {
                finalAssistantText = "已通过 PenOS Harness MCP 完成以下变更：\n${executedLogs.joinToString("\n")}"
            }
            if (finalAssistantText.isBlank()) {
                finalAssistantText = "已收到指令，但模型未返回文字内容。"
            }

            AiChatMessage(
                id = "ai_${System.currentTimeMillis()}",
                role = "assistant",
                content = finalAssistantText,
                toolActionsSummary = executedLogs.joinToString(" · ")
            )
        }
    }

    suspend fun sendQuickTextPrompt(
        profile: AiApiProfile,
        systemPrompt: String,
        userPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase = profile.baseUrl.trim().trimEnd('/')
            if (cleanBase.isBlank() || profile.apiKey.isBlank()) {
                throw IllegalArgumentException("请先配置当前 AI 模型预设的 API Key")
            }
            val endpoint = if (cleanBase.endsWith("/chat/completions")) cleanBase else "$cleanBase/chat/completions"
            val req = JSONObject()
                .put("model", profile.model.ifBlank { "deepseek-chat" })
                .put("temperature", 0.4)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", userPrompt))
                )
            val resp = postJson(endpoint, req, profile.apiKey)
            resp.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()
                .ifBlank { "模型未返回内容" }
        }
    }

    private fun postJson(url: String, body: JSONObject, apiKey: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val errMsg = runCatching {
                    JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("")
                throw IllegalStateException("HTTP ${resp.code} ${errMsg.ifBlank { raw.take(120) }}")
            }
            return JSONObject(raw)
        }
    }

    private fun fetchExternalMcpTools(mcpUrl: String): Result<List<Pair<String, JSONObject>>> = runCatching {
        val rpc = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "tools/list")
            .put("params", JSONObject())
        val req = Request.Builder()
            .url(mcpUrl.trim())
            .header("Content-Type", "application/json")
            .post(rpc.toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching emptyList()
            val root = JSONObject(resp.body?.string().orEmpty())
            val tools = root.optJSONObject("result")?.optJSONArray("tools") ?: return@runCatching emptyList()
            (0 until tools.length()).mapNotNull { i ->
                val t = tools.optJSONObject(i) ?: return@mapNotNull null
                val name = t.optString("name")
                if (name.isBlank()) null else name to t
            }
        }
    }

    private fun invokeExternalMcpTool(mcpUrl: String, name: String, args: JSONObject): PenMcpToolResult {
        return try {
            val rpc = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 2)
                .put("method", "tools/call")
                .put("params", JSONObject().put("name", name).put("arguments", args))
            val req = Request.Builder()
                .url(mcpUrl.trim())
                .header("Content-Type", "application/json")
                .post(rpc.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return PenMcpToolResult(name, "外部 MCP 调用失败 HTTP ${resp.code}", false)
                }
                val root = JSONObject(resp.body?.string().orEmpty())
                val text = root.optJSONObject("result")
                    ?.optJSONArray("content")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    .orEmpty()
                    .ifBlank { "外部 MCP 工具 $name 执行完成" }
                PenMcpToolResult(name, text.take(160), true)
            }
        } catch (e: Exception) {
            PenMcpToolResult(name, "外部 MCP 异常: ${e.message}", false)
        }
    }

    private fun buildCombinedToolsSchema(externalTools: List<Pair<String, JSONObject>>): JSONArray {
        val arr = JSONArray()
        fun addTool(name: String, desc: String, props: JSONObject, required: List<String> = emptyList()) {
            val params = JSONObject()
                .put("type", "object")
                .put("properties", props)
                .put("required", JSONArray(required))
            arr.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", name)
                            .put("description", desc)
                            .put("parameters", params)
                    )
            )
        }

        // 1. Harness Introspection
        addTool(
            "inspect_penos_state",
            "读取 PenOS 当前完整桌面状态（主题、壁纸模式、5通道字体、多样式时钟、现有自定义小组件、Apple TV抽屉内容、已安装应用、未读邮件数）以便精准修改",
            JSONObject()
        )

        // 2. Declarative Custom Widget Authoring (Harness Killer Feature!)
        addTool(
            "create_custom_widget",
            "编写并立即安装/更新一个 PenOS 5.0 声明式自定义小组件到主屏或 Apple TV 上滑抽屉。blocks 数组支持 17 类声明式组件块: header(title,badge), metric(label,value,unit), text(content), progress(label,percent), countdown(label,target_iso如2027-06-07), checklist(items数组[{text,done}]), quote(content,author), sparkline(label,values数值数组), action_button(label,action,target), stopwatch(label), habit_counter(label,value,goal,unit), mood_picker(label,options数组,selected), calc_pad(label), http_json(label,url仅http/https,json_path,unit), macro_button(label,steps数组如[\"night_eye_on\",\"white_noise:rain\",\"pomodoro:45\"]), timetable(title,slots数组[{time:\"08:00-09:30\",name:\"英语\"}]), flashcard_mini(front,back,tag)",
            JSONObject()
                .put("id", JSONObject().put("type", "string").put("description", "唯一ID，如 gaokao_countdown；相同ID则覆盖更新"))
                .put("title", JSONObject().put("type", "string").put("description", "小组件标题"))
                .put("placement", JSONObject().put("type", "string").put("description", "DESKTOP / DRAWER / BOTH，默认 BOTH"))
                .put("width_dp", JSONObject().put("type", "integer").put("description", "宽度 150~280，默认 196"))
                .put("accent_hex", JSONObject().put("type", "string").put("description", "十六进制强调色如 #38BDF8 或 #FF6B4A"))
                .put("blocks", JSONObject().put("type", "array").put("items", JSONObject().put("type", "object")).put("description", "声明式内容块数组（最多8块，优先精简适配172dp高横屏）")),
            listOf("title", "blocks")
        )
        addTool(
            "delete_custom_widget",
            "删除指定 ID 的自定义 MCP 小组件",
            JSONObject().put("id", JSONObject().put("type", "string")),
            listOf("id")
        )

        // 3. Apple TV Up-Swipe Drawer Management
        addTool(
            "manage_drawer",
            "管理主屏上滑呼出的 Apple TV 风格自定义抽屉内容（添加/移除自定义小组件、时钟、邮箱、AI切换卡或快捷动作）",
            JSONObject()
                .put("action", JSONObject().put("type", "string").put("description", "add 或 remove"))
                .put("type", JSONObject().put("type", "string").put("description", "CUSTOM_WIDGET / BUILTIN_CLOCK / BUILTIN_MAIL / AI_SWITCHER / APP_SHORTCUT / ACTION_TILE"))
                .put("ref_id", JSONObject().put("type", "string").put("description", "自定义组件ID、应用包名或动作名"))
                .put("title", JSONObject().put("type", "string")),
            listOf("action", "type", "ref_id")
        )

        // 4. Granular 5-Slot Global Typography
        addTool(
            "set_font_slot",
            "精细调整 PenOS 5 通道全局动态字体之一（时钟/标题/正文/诗词/徽章）的字体族、缩放倍率、字重与字距",
            JSONObject()
                .put("slot", JSONObject().put("type", "string").put("description", "clock / title / body / poetry / metric"))
                .put("family", JSONObject().put("type", "string").put("description", "outfit / inter / space_grotesk / jetbrains_mono / noto_serif_sc / playfair / system_sans / system_serif"))
                .put("scale", JSONObject().put("type", "number").put("description", "0.75 到 1.45"))
                .put("weight", JSONObject().put("type", "integer").put("description", "300 到 800"))
                .put("letter_spacing", JSONObject().put("type", "number").put("description", "-2.0 到 3.0")),
            listOf("slot")
        )

        // 5. 4-Mode Dynamic Wallpaper & Theme & Multi-Style Clock & Motion Tier
        addTool(
            "switch_theme",
            "切换 PenOS 桌面主题、StandBy 主题或动画流畅度档位",
            JSONObject()
                .put("desktop_style", JSONObject().put("type", "string").put("description", "dark_standby 或 apple_light"))
                .put("standby_style", JSONObject().put("type", "string").put("description", "follow / oled_dark / apple_light"))
                .put("motion_tier", JSONObject().put("type", "string").put("description", "fast(极速流畅) / spring(轻盈弹簧) / none(零动效)"))
        )
        addTool(
            "set_wallpaper_mode",
            "切换动态壁纸模式（static静态 / procedural代码动效 / gif / mp4 / live系统原生动态壁纸）与代码动效样式",
            JSONObject()
                .put("mode", JSONObject().put("type", "string").put("description", "static / procedural / gif / mp4 / live"))
                .put("procedural_style", JSONObject().put("type", "string").put("description", "aurora_flow / star_trails / soft_rain / gallery_breeze"))
                .put("media_path", JSONObject().put("type", "string").put("description", "可选本地 GIF/MP4 路径")),
            listOf("mode")
        )
        addTool(
            "set_clock_widget_style",
            "切换多样式时钟小组件表盘样式（M3花瓣/瑞士铁路/复古翻页/KDE极简/罗马经典/点阵LED）",
            JSONObject()
                .put("style", JSONObject().put("type", "string").put("description", "bloom_analog / railway_swiss / retro_flip / kde_minimal / roman_classic / dot_matrix"))
                .put("shape", JSONObject().put("type", "string").put("description", "circle / squircle / scallop / pill"))
                .put("show_seconds", JSONObject().put("type", "boolean")),
            listOf("style")
        )

        // 6. Built-in Widgets, Productivity, Device & Multi-AI Switcher
        addTool(
            "manage_widgets",
            "开启或关闭 PenOS 内置桌面小组件",
            JSONObject()
                .put("widget_id", JSONObject().put("type", "string").put("description", "quick_scan, multi_clock, weather, mail_inbox, calendar, todo, jinrishici, vocab_card, pinned_apps, pen_status"))
                .put("enable", JSONObject().put("type", "boolean")),
            listOf("widget_id", "enable")
        )
        addTool(
            "switch_ai_profile",
            "切换到已保存的其他 AI API 模型预设",
            JSONObject().put("profile_name_or_id", JSONObject().put("type", "string")),
            listOf("profile_name_or_id")
        )
        addTool(
            "add_todo",
            "向桌面待办事项添加新任务",
            JSONObject()
                .put("title", JSONObject().put("type", "string"))
                .put("due_date", JSONObject().put("type", "string").put("description", "可选 yyyy-MM-dd")),
            listOf("title")
        )
        addTool(
            "add_calendar_event",
            "向日历添加带有自定义时间(HH:mm)的日程安排",
            JSONObject()
                .put("title", JSONObject().put("type", "string"))
                .put("date_iso", JSONObject().put("type", "string").put("description", "yyyy-MM-dd，留空默认今天"))
                .put("time_hhmm", JSONObject().put("type", "string").put("description", "如 08:30 或 19:00")),
            listOf("title", "time_hhmm")
        )
        addTool(
            "switch_weather_city",
            "切换 PenOS 天气小组件显示的城市",
            JSONObject().put("city_name", JSONObject().put("type", "string")),
            listOf("city_name")
        )
        addTool(
            "start_pomodoro",
            "启动全屏壁纸番茄专注倒计时模式",
            JSONObject().put("minutes", JSONObject().put("type", "integer")),
            listOf("minutes")
        )
        addTool(
            "control_white_noise",
            "开启、切换或关闭离线合成白噪音",
            JSONObject()
                .put("action", JSONObject().put("type", "string").put("description", "play 或 stop"))
                .put("sound", JSONObject().put("type", "string").put("description", "rain / wind / campfire / brown / ocean")),
            listOf("action")
        )
        addTool(
            "adjust_device",
            "调节词典笔屏幕亮度、媒体音量或夜间护眼遮罩",
            JSONObject()
                .put("brightness_percent", JSONObject().put("type", "integer"))
                .put("volume_percent", JSONObject().put("type", "integer"))
                .put("night_eye_care", JSONObject().put("type", "boolean"))
        )
        addTool(
            "add_vocab_word",
            "将一个英语单词加入百词斩离线词库中",
            JSONObject()
                .put("word", JSONObject().put("type", "string"))
                .put("phonetic", JSONObject().put("type", "string"))
                .put("meaning_zh", JSONObject().put("type", "string"))
                .put("root_breakdown", JSONObject().put("type", "string"))
                .put("example_en", JSONObject().put("type", "string")),
            listOf("word", "meaning_zh")
        )
        addTool(
            "launch_app",
            "按包名或名称启动词典笔上的指定应用",
            JSONObject().put("app_query", JSONObject().put("type", "string")),
            listOf("app_query")
        )

        // 7. Email Integration (Read + Draft with user confirmation before SMTP send!)
        addTool(
            "list_recent_emails",
            "查看笔端邮箱缓存中的最近邮件（含发件人、主题、摘要与未读状态），用于帮用户总结收件箱",
            JSONObject().put("limit", JSONObject().put("type", "integer").put("description", "默认 8"))
        )
        addTool(
            "read_email",
            "按邮件 ID 或序号读取某封邮件的完整正文",
            JSONObject().put("email_id_or_index", JSONObject().put("type", "string")),
            listOf("email_id_or_index")
        )
        addTool(
            "draft_email",
            "为用户起草一封新邮件或回复草稿并弹出邮箱发送确认页（不会未经确认自动发送）",
            JSONObject()
                .put("to", JSONObject().put("type", "string").put("description", "收件人邮箱"))
                .put("subject", JSONObject().put("type", "string"))
                .put("body", JSONObject().put("type", "string")),
            listOf("to", "subject", "body")
        )

        externalTools.forEach { (name, schemaObj) ->
            val desc = schemaObj.optString("description", "外部 MCP 工具")
            val inputSchema = schemaObj.optJSONObject("inputSchema") ?: JSONObject().put("type", "object").put("properties", JSONObject())
            arr.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", name)
                            .put("description", desc)
                            .put("parameters", inputSchema)
                    )
            )
        }
        return arr
    }
}
