package com.example.data.remote

import androidx.compose.runtime.Immutable
import com.example.data.prefs.AiApiProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class AiProviderTemplate(
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val note: String
)

@Immutable
data class McpToolInfo(
    val name: String,
    val description: String,
    val inputSchemaJson: String = "{}"
)

@Immutable
data class McpToolCallTrace(
    val toolName: String,
    val argumentsSummary: String,
    val resultSummary: String,
    val success: Boolean
)

@Immutable
data class AiChatMessage(
    val id: String,
    val role: String, // "user" | "assistant"
    val content: String,
    val toolTraces: List<McpToolCallTrace> = emptyList()
)

/**
 * PenOS 6.0 Ultra-Lean AI Chat + External MCP JSON-RPC 2.0 Client.
 * Supports OpenAI-compatible multi-model endpoints and external HTTP MCP servers
 * (`tools/list` discovery + `tools/call` execution loop).
 */
class PenAiMcpClient {

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        val providerTemplates: List<AiProviderTemplate> = listOf(
            AiProviderTemplate("DeepSeek V3", "https://api.deepseek.com/v1", "deepseek-chat", "深度求索 · 性价比高"),
            AiProviderTemplate("DeepSeek R1", "https://api.deepseek.com/v1", "deepseek-reasoner", "深度推理模型"),
            AiProviderTemplate("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", "智谱清言 · 免费额度"),
            AiProviderTemplate("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-turbo", "阿里云百炼"),
            AiProviderTemplate("硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct", "SiliconFlow 聚合"),
            AiProviderTemplate("Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k", "月之暗面")
        )
    }

    private val client by lazy {
        Api21NetworkFactory.createClient(connectTimeoutSec = 15, readTimeoutSec = 55)
    }

    suspend fun discoverExternalMcpTools(
        mcpUrl: String,
        authToken: String = ""
    ): Result<List<McpToolInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = mcpUrl.trim()
            if (url.isBlank()) throw IllegalArgumentException("未填写外部 MCP 地址")
            val pairs = fetchExternalMcpToolsRaw(url, authToken.trim())
            pairs.map { (name, obj) ->
                McpToolInfo(
                    name = name,
                    description = obj.optString("description", "外部 MCP 工具"),
                    inputSchemaJson = obj.optJSONObject("inputSchema")?.toString() ?: "{}"
                )
            }
        }
    }

    suspend fun sendChat(
        profile: AiApiProfile,
        history: List<AiChatMessage>,
        userPrompt: String,
        externalMcpUrl: String,
        externalMcpToken: String = ""
    ): Result<AiChatMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase = profile.baseUrl.trim().trimEnd('/')
            if (cleanBase.isBlank()) throw IllegalArgumentException("请先配置 AI 模型接口地址")
            if (profile.apiKey.isBlank()) throw IllegalArgumentException("请先在模型设置中填写 API Key")

            val endpoint = if (cleanBase.endsWith("/chat/completions")) cleanBase else "$cleanBase/chat/completions"

            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", profile.systemPrompt))
                history.takeLast(12).forEach { msg ->
                    put(JSONObject().put("role", msg.role).put("content", msg.content))
                }
                put(JSONObject().put("role", "user").put("content", userPrompt))
            }

            val cleanMcpUrl = externalMcpUrl.trim()
            val cleanMcpToken = externalMcpToken.trim()
            val externalTools = if (profile.enableExternalMcp && cleanMcpUrl.isNotBlank()) {
                runCatching { fetchExternalMcpToolsRaw(cleanMcpUrl, cleanMcpToken) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val toolsPayload = buildExternalToolsPayload(externalTools)

            val traces = mutableListOf<McpToolCallTrace>()
            var finalAssistantText = ""

            for (round in 0 until 4) {
                val reqObj = JSONObject().apply {
                    put("model", profile.model.ifBlank { "deepseek-chat" })
                    put("messages", messages)
                    put("temperature", profile.temperature.toDouble().coerceIn(0.0, 1.5))
                    if (toolsPayload != null && round < 3) {
                        put("tools", toolsPayload)
                        put("tool_choice", "auto")
                    }
                }
                val respJson = postJson(endpoint, reqObj, profile.apiKey)
                val choices = respJson.optJSONArray("choices")
                    ?: throw IllegalStateException("模型未返回 choices：${respJson.toString().take(160)}")
                if (choices.length() == 0) throw IllegalStateException("模型返回空结果")

                val assistantMsg = choices.getJSONObject(0).getJSONObject("message")
                val toolCalls = assistantMsg.optJSONArray("tool_calls")

                if (toolCalls != null && toolCalls.length() > 0 && round < 3 && cleanMcpUrl.isNotBlank()) {
                    messages.put(assistantMsg)
                    for (i in 0 until toolCalls.length()) {
                        val call = toolCalls.getJSONObject(i)
                        val callId = call.optString("id", "call_${round}_$i")
                        val fn = call.getJSONObject("function")
                        val fnName = fn.optString("name")
                        val argsStr = fn.optString("arguments", "{}")
                        val argsObj = runCatching { JSONObject(argsStr) }.getOrDefault(JSONObject())

                        val trace = invokeExternalMcpTool(cleanMcpUrl, cleanMcpToken, fnName, argsObj)
                        traces.add(trace)
                        messages.put(
                            JSONObject()
                                .put("role", "tool")
                                .put("tool_call_id", callId)
                                .put("name", fnName)
                                .put("content", trace.resultSummary)
                        )
                    }
                    continue
                }

                finalAssistantText = assistantMsg.optString("content", "").trim()
                break
            }

            if (finalAssistantText.isBlank() && traces.isNotEmpty()) {
                finalAssistantText = traces.joinToString("\n") {
                    "${if (it.success) "✓" else "✗"} ${it.toolName}: ${it.resultSummary}"
                }
            }
            if (finalAssistantText.isBlank()) {
                finalAssistantText = "已收到请求，但模型未返回文本内容。"
            }

            AiChatMessage(
                id = "ai_${System.currentTimeMillis()}",
                role = "assistant",
                content = finalAssistantText,
                toolTraces = traces
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

    private fun fetchExternalMcpToolsRaw(mcpUrl: String, authToken: String): List<Pair<String, JSONObject>> {
        val rpc = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "tools/list")
            .put("params", JSONObject())
        val reqBuilder = Request.Builder()
            .url(mcpUrl)
            .header("Content-Type", "application/json")
            .post(rpc.toString().toRequestBody(JSON_MEDIA))
        if (authToken.isNotBlank()) {
            val headerValue = if (authToken.startsWith("Bearer ", ignoreCase = true)) authToken else "Bearer $authToken"
            reqBuilder.header("Authorization", headerValue)
        }
        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("MCP HTTP ${resp.code}")
            val root = JSONObject(resp.body?.string().orEmpty())
            val tools = root.optJSONObject("result")?.optJSONArray("tools") ?: return emptyList()
            return (0 until tools.length()).mapNotNull { i ->
                val t = tools.optJSONObject(i) ?: return@mapNotNull null
                val name = t.optString("name")
                if (name.isBlank()) null else name to t
            }
        }
    }

    private fun invokeExternalMcpTool(
        mcpUrl: String,
        authToken: String,
        name: String,
        args: JSONObject
    ): McpToolCallTrace {
        val argSummary = args.toString().take(120)
        return try {
            val rpc = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 2)
                .put("method", "tools/call")
                .put("params", JSONObject().put("name", name).put("arguments", args))
            val reqBuilder = Request.Builder()
                .url(mcpUrl)
                .header("Content-Type", "application/json")
                .post(rpc.toString().toRequestBody(JSON_MEDIA))
            if (authToken.isNotBlank()) {
                val headerValue = if (authToken.startsWith("Bearer ", ignoreCase = true)) authToken else "Bearer $authToken"
                reqBuilder.header("Authorization", headerValue)
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return McpToolCallTrace(name, argSummary, "MCP 调用失败 HTTP ${resp.code}", false)
                }
                val root = JSONObject(resp.body?.string().orEmpty())
                val errorObj = root.optJSONObject("error")
                if (errorObj != null) {
                    return McpToolCallTrace(name, argSummary, errorObj.optString("message", "MCP 工具返回错误"), false)
                }
                val contentArr = root.optJSONObject("result")?.optJSONArray("content")
                val text = buildString {
                    if (contentArr != null) {
                        for (i in 0 until contentArr.length()) {
                            val item = contentArr.optJSONObject(i)
                            val part = item?.optString("text").orEmpty()
                            if (part.isNotBlank()) {
                                if (isNotEmpty()) append('\n')
                                append(part)
                            }
                        }
                    }
                }.ifBlank { "外部 MCP 工具 $name 执行完成" }
                McpToolCallTrace(name, argSummary, text.take(400), true)
            }
        } catch (e: Exception) {
            McpToolCallTrace(name, argSummary, "MCP 异常: ${e.message}", false)
        }
    }

    private fun buildExternalToolsPayload(externalTools: List<Pair<String, JSONObject>>): JSONArray? {
        if (externalTools.isEmpty()) return null
        val arr = JSONArray()
        externalTools.forEach { (name, schemaObj) ->
            val desc = schemaObj.optString("description", "外部 MCP 工具")
            val inputSchema = schemaObj.optJSONObject("inputSchema")
                ?: JSONObject().put("type", "object").put("properties", JSONObject())
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
