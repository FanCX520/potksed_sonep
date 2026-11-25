package com.example.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.prefs.AiApiProfile
import com.example.data.remote.AiChatMessage
import com.example.data.remote.McpToolCallTrace
import com.example.data.remote.PenAiMcpClient
import com.example.ui.AiSessionState
import com.example.ui.PenOsUiState
import com.example.ui.components.MonoCard
import com.example.ui.components.MonoChip
import com.example.ui.components.MonoOutlineButton
import com.example.ui.components.MonoPrimaryButton
import com.example.ui.components.MonoSectionTitle
import com.example.ui.components.MonoTopBar
import com.example.ui.theme.PenIcons
import com.example.ui.theme.PenTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AiChatScreen(
    state: PenOsUiState,
    aiFlow: StateFlow<AiSessionState>,
    onBack: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onClearChat: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onUpsertProfile: (AiApiProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveExternalMcp: (String, String) -> Unit,
    onTestMcp: () -> Unit
) {
    BackHandler(onBack = onBack)

    val palette = PenTheme.palette
    val aiState by aiFlow.collectAsStateWithLifecycle()
    val activeProfile = state.config.activeAiProfile()

    var input by remember { mutableStateOf("") }
    var showConfigDialog by remember { mutableStateOf(false) }
    var configTab by remember { mutableStateOf(0) } // 0 = 模型预设, 1 = 外接 MCP
    val listState = rememberLazyListState()

    LaunchedEffect(aiState.messages.size) {
        if (aiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(aiState.messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .safeDrawingPadding()
            .imePadding()
    ) {
        MonoTopBar(
            title = activeProfile.name,
            subtitle = if (state.config.externalMcpUrl.isNotBlank()) {
                val cnt = aiState.discoveredMcpTools.size
                if (cnt > 0) "MCP 已连 · $cnt 工具" else "MCP 已配置"
            } else {
                activeProfile.model
            },
            onBack = onBack,
            trailing = {
                // Quick MCP button
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.hairline, RoundedCornerShape(8.dp))
                        .clickable {
                            configTab = 1
                            showConfigDialog = true
                        }
                        .testTag("ai_mcp_button")
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.config.externalMcpUrl.isNotBlank()) palette.success else palette.inkMuted
                            )
                    )
                    Text("MCP", style = MaterialTheme.typography.labelSmall, color = palette.ink)
                }

                IconButton(
                    onClick = {
                        configTab = 0
                        showConfigDialog = true
                    },
                    modifier = Modifier.size(44.dp).testTag("ai_model_config_button")
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "配置模型与MCP", tint = palette.ink)
                }

                if (aiState.messages.isNotEmpty()) {
                    IconButton(
                        onClick = onClearChat,
                        modifier = Modifier.size(44.dp).testTag("ai_clear_chat_button")
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "清空对话", tint = palette.inkMuted)
                    }
                }
            }
        )

        // Quick 1-tap model switcher strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.config.aiProfiles.forEach { profile ->
                MonoChip(
                    label = profile.name,
                    selected = profile.id == activeProfile.id,
                    onClick = { onSelectProfile(profile.id) },
                    testTag = "ai_profile_chip_${profile.id}"
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = palette.hairline)

        // Messages area
        if (aiState.messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = PenIcons.SparkleAi,
                    contentDescription = null,
                    tint = palette.ink,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "AI 极简对话 · 外接 MCP",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (activeProfile.apiKey.isBlank()) {
                        "请点右上角齿轮填写 API Key（支持 DeepSeek / GLM / 通义 / Ollama）"
                    } else {
                        "当前模型：${activeProfile.model}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "拆解这个英语句子结构",
                        "用英文写 3 句高频作文句型",
                        "列出当前可用的 MCP 工具"
                    ).forEach { sample ->
                        MonoOutlineButton(
                            text = sample,
                            onClick = {
                                if (activeProfile.apiKey.isBlank()) {
                                    configTab = 0
                                    showConfigDialog = true
                                } else {
                                    onSendPrompt(sample)
                                }
                            }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(aiState.messages, key = { it.id }) { msg ->
                    EditorialChatMessageItem(msg)
                }
                if (aiState.isLoading) {
                    item(key = "loading_indicator") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent)
                            )
                            Text(
                                text = "正在思考 / 调用工具…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.inkMuted
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = palette.hairline)

        // Bottom input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.canvas)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("提问或调用外部 MCP 工具…", color = palette.inkMuted) },
                maxLines = 3,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.ink,
                    unfocusedBorderColor = palette.hairline,
                    focusedTextColor = palette.ink,
                    unfocusedTextColor = palette.ink,
                    focusedContainerColor = palette.surface,
                    unfocusedContainerColor = palette.surface
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_prompt_input")
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (input.isNotBlank() && !aiState.isLoading) palette.invertedBg else palette.surfaceAlt)
                    .clickable(enabled = input.isNotBlank() && !aiState.isLoading) {
                        val text = input
                        input = ""
                        onSendPrompt(text)
                    }
                    .testTag("ai_send_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "发送",
                    tint = if (input.isNotBlank() && !aiState.isLoading) palette.invertedInk else palette.inkMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showConfigDialog) {
        AiAndMcpConfigDialog(
            initialTab = configTab,
            profiles = state.config.aiProfiles,
            activeProfile = activeProfile,
            externalMcpUrl = state.config.externalMcpUrl,
            externalMcpToken = state.config.externalMcpToken,
            aiState = aiState,
            onDismiss = { showConfigDialog = false },
            onUpsertProfile = onUpsertProfile,
            onDeleteProfile = onDeleteProfile,
            onSaveExternalMcp = onSaveExternalMcp,
            onTestMcp = onTestMcp
        )
    }
}

@Composable
private fun EditorialChatMessageItem(msg: AiChatMessage) {
    val palette = PenTheme.palette
    if (msg.role == "user") {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.invertedBg)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = msg.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.invertedInk
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (msg.toolTraces.isNotEmpty()) {
                msg.toolTraces.forEach { trace ->
                    McpTraceRow(trace)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(20.dp)
                        .background(palette.accent)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = msg.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.ink,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun McpTraceRow(trace: McpToolCallTrace) {
    val palette = PenTheme.palette
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.hairline, shape)
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = PenIcons.McpPlug,
                contentDescription = null,
                tint = if (trace.success) palette.success else palette.danger,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "MCP · ${trace.toolName}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = palette.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (expanded) "收起" else "详情",
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkMuted
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "参数：${trace.argumentsSummary}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "返回：${trace.resultSummary}",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.ink
            )
        }
    }
}

@Composable
private fun AiAndMcpConfigDialog(
    initialTab: Int,
    profiles: List<AiApiProfile>,
    activeProfile: AiApiProfile,
    externalMcpUrl: String,
    externalMcpToken: String,
    aiState: AiSessionState,
    onDismiss: () -> Unit,
    onUpsertProfile: (AiApiProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveExternalMcp: (String, String) -> Unit,
    onTestMcp: () -> Unit
) {
    val palette = PenTheme.palette
    var tab by remember { mutableStateOf(initialTab) }

    var editingId by remember(activeProfile.id) { mutableStateOf(activeProfile.id) }
    var name by remember(activeProfile.id) { mutableStateOf(activeProfile.name) }
    var baseUrl by remember(activeProfile.id) { mutableStateOf(activeProfile.baseUrl) }
    var apiKey by remember(activeProfile.id) { mutableStateOf(activeProfile.apiKey) }
    var model by remember(activeProfile.id) { mutableStateOf(activeProfile.model) }
    var enableMcp by remember(activeProfile.id) { mutableStateOf(activeProfile.enableExternalMcp) }

    var mcpUrl by remember(externalMcpUrl) { mutableStateOf(externalMcpUrl) }
    var mcpToken by remember(externalMcpToken) { mutableStateOf(externalMcpToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoChip(label = "模型预设", selected = tab == 0, onClick = { tab = 0 })
                MonoChip(label = "外接 MCP", selected = tab == 1, onClick = { tab = 1 })
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tab == 0) {
                    MonoSectionTitle("快速套用模板")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PenAiMcpClient.providerTemplates.forEach { tpl ->
                            MonoChip(
                                label = tpl.name,
                                selected = false,
                                onClick = {
                                    name = tpl.name
                                    baseUrl = tpl.baseUrl
                                    model = tpl.defaultModel
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("预设名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL (OpenAI 兼容)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("模型名称 (Model ID)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key（仅存本机）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("允许该模型调用外部 MCP", style = MaterialTheme.typography.bodyMedium, color = palette.ink)
                        Switch(checked = enableMcp, onCheckedChange = { enableMcp = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MonoOutlineButton(
                            text = "新建预设",
                            onClick = {
                                editingId = "custom_${System.currentTimeMillis()}"
                                name = "自定义模型"
                                apiKey = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (profiles.size > 1) {
                            MonoOutlineButton(
                                text = "删除当前",
                                onClick = {
                                    onDeleteProfile(editingId)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "填写支持 HTTP JSON-RPC 2.0（tools/list 与 tools/call）的外部 MCP 服务地址。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkMuted
                    )
                    OutlinedTextField(
                        value = mcpUrl,
                        onValueChange = { mcpUrl = it },
                        label = { Text("MCP Server URL") },
                        placeholder = { Text("https://example.com/mcp") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("mcp_url_input")
                    )
                    OutlinedTextField(
                        value = mcpToken,
                        onValueChange = { mcpToken = it },
                        label = { Text("可选 Bearer Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    MonoOutlineButton(
                        text = if (aiState.isTestingMcp) "正在探测工具…" else "保存并测试 MCP 连接",
                        enabled = !aiState.isTestingMcp,
                        onClick = {
                            onSaveExternalMcp(mcpUrl, mcpToken)
                            onTestMcp()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "mcp_test_button"
                    )
                    if (aiState.mcpStatusNote.isNotBlank()) {
                        Text(
                            text = aiState.mcpStatusNote,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.accent
                        )
                    }
                    if (aiState.discoveredMcpTools.isNotEmpty()) {
                        MonoSectionTitle("已发现工具 (${aiState.discoveredMcpTools.size})")
                        aiState.discoveredMcpTools.forEach { tool ->
                            MonoCard(
                                contentPadding = PaddingValues(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(
                                        text = tool.name,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = palette.ink
                                    )
                                    Text(
                                        text = tool.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.inkMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            MonoPrimaryButton(
                text = "保存",
                onClick = {
                    if (tab == 0) {
                        onUpsertProfile(
                            activeProfile.copy(
                                id = editingId,
                                name = name.ifBlank { "AI 模型" },
                                baseUrl = baseUrl.trim(),
                                apiKey = apiKey.trim(),
                                model = model.trim(),
                                enableExternalMcp = enableMcp
                            )
                        )
                    } else {
                        onSaveExternalMcp(mcpUrl, mcpToken)
                    }
                    onDismiss()
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = palette.inkMuted)
            }
        }
    )
}
