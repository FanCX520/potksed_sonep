package com.example.data.remote

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
 * China ICP-filed OpenAI-compatible AI + Built-in PenOS Desktop MCP Tool Bridge + External HTTP MCP Client.
 * Fully compatible with Android 5.0.2 (API 21 TLS 1.2) and campus ICP-only firewalls.
 */
class PenAiMcpClient {

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        // All default templates are China ICP-filed AI platforms accessible inside school networks
        val icpProviderTemplates: List<AiProviderTemplate> = listOf(
            AiProviderTemplate("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "深度求索 · 国内备案"),
            AiProviderTemplate("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", "智谱清言 · 免费额度"),
            AiProviderTemplate("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-turbo", "阿里云百炼 · 国内备案"),
            AiProviderTemplate("硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct", "SiliconFlow · 国内备案"),
            AiProviderTemplate("Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k", "月之暗面 · 国内备案")
        )
    }

    private val client by lazy {
        Api21NetworkFactory.createClient(connectTimeoutSec = 15, readTimeoutSec = 45)
    }

    /**
     * Sends a conversation turn to any ICP-filed OpenAI-compatible endpoint.
     * When MCP tools are enabled on the active preset, registers all built-in PenOS Desktop MCP tools
     * (plus any discovered tools from externalMcpUrl), executes tool calls locally/remotely, and returns
     * the final assistant message along with executed tool badges.
     */
    suspend fun sendChatWithMcp(
        baseUrl: String,
        apiKey: String,
        model: String,
        preset: AiPreset,
        history: List<AiChatMessage>,
        userPrompt: String,
        externalMcpUrl: String,
        desktopToolExecutor: suspend (toolName: String, args: JSONObject) -> PenMcpToolResult
    ): Result<AiChatMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase = baseUrl.trim().trimEnd('/')
            if (cleanBase.isBlank()) throw IllegalArgumentException("请先在实验室填写国内已备案的 AI 接口地址")
            if (apiKey.isBlank()) throw IllegalArgumentException("请先在实验室配置您的 API Key（仅保存在笔端本地）")

            val endpoint = if (cleanBase.endsWith("/chat/completions")) cleanBase else "$cleanBase/chat/completions"

            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", preset.systemPrompt))
                history.takeLast(8).forEach { msg ->
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

            // Round 1 (and Round 2 if model requested MCP tool calls)
            var finalAssistantText = ""
            for (round in 0 until 2) {
                val reqObj = JSONObject().apply {
                    put("model", model.ifBlank { "deepseek-chat" })
                    put("messages", messages)
                    put("temperature", 0.6)
                    if (toolsPayload != null && round == 0) {
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

                if (toolCalls != null && toolCalls.length() > 0 && round == 0) {
                    messages.put(assistantMsg)
                    for (i in 0 until toolCalls.length()) {
                        val call = toolCalls.getJSONObject(i)
                        val callId = call.optString("id", "call_$i")
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
                finalAssistantText = "已通过 MCP 工具完成操作：\n${executedLogs.joinToString("\n")}"
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

        addTool(
            "switch_theme",
            "切换 PenOS 桌面主题或 StandBy 息屏主题",
            JSONObject()
                .put("desktop_style", JSONObject().put("type", "string").put("description", "dark_standby 或 apple_light"))
                .put("standby_style", JSONObject().put("type", "string").put("description", "可选：follow / oled_dark / apple_light")),
            listOf("desktop_style")
        )
        addTool(
            "manage_widgets",
            "开启或关闭 PenOS 桌面小组件",
            JSONObject()
                .put("widget_id", JSONObject().put("type", "string").put("description", "quick_scan, weather, calendar, todo, jinrishici, vocab_card, pinned_apps, pen_status"))
                .put("enable", JSONObject().put("type", "boolean")),
            listOf("widget_id", "enable")
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
            JSONObject().put("city_name", JSONObject().put("type", "string").put("description", "中国城市名如 北京/上海/南京/成都")),
            listOf("city_name")
        )
        addTool(
            "start_pomodoro",
            "启动全屏壁纸番茄专注倒计时模式",
            JSONObject().put("minutes", JSONObject().put("type", "integer").put("description", "专注分钟数，如 15/25/45")),
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
                .put("brightness_percent", JSONObject().put("type", "integer").put("description", "10 到 100"))
                .put("volume_percent", JSONObject().put("type", "integer").put("description", "0 到 100"))
                .put("night_eye_care", JSONObject().put("type", "boolean"))
        )
        addTool(
            "add_vocab_word",
            "将一个英语单词加入百词斩离线词库中方便复习",
            JSONObject()
                .put("word", JSONObject().put("type", "string"))
                .put("phonetic", JSONObject().put("type", "string"))
                .put("meaning_zh", JSONObject().put("type", "string"))
                .put("root_breakdown", JSONObject().put("type", "string"))
                .put("example_en", JSONObject().put("type", "string")),
            listOf("word", "meaning_zh")
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
