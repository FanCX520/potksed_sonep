package com.example.ui.lab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.prefs.AiPreset
import com.example.data.remote.PenAiMcpClient
import com.example.system.WhiteNoiseType
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme
import com.example.ui.theme.springPressScale
import kotlin.math.roundToInt

/**
 * PenOS 3.0 Laboratory:
 * 0: AI Assistant + Custom Presets + Built-in PenOS Desktop MCP Tools + ICP-filed Providers
 * 1: Offline Real-Time DSP White Noise Synthesizer
 * 2: Jiuxuewang Activity Live Radar (1-tap Bind Scan / Block Stock HomeActivity)
 * 3: Night Eye-Care Dimmer, 1GB Low-Mem Mode & External Noto Serif SC Font Path
 */
@Composable
fun PenLabScreen(
    state: PenOsUiState,
    onBack: () -> Unit,
    onSendAiPrompt: (String) -> Unit,
    onSaveAiConfig: (baseUrl: String, apiKey: String, model: String, externalMcpUrl: String) -> Unit,
    onSelectAiPreset: (String) -> Unit,
    onUpsertAiPreset: (AiPreset) -> Unit,
    onDeleteAiPreset: (String) -> Unit,
    onClearAiChat: () -> Unit,
    onToggleWhiteNoise: (WhiteNoiseType) -> Unit,
    onSetWhiteNoiseVolume: (Float) -> Unit,
    onBindScanFromRadar: (String, String) -> Unit,
    onBlockHomeFromRadar: (String) -> Unit,
    onSetNightDimAlpha: (Float) -> Unit,
    onSetLowMemMode: (Boolean) -> Unit,
    onPickPoetryFontFile: () -> Unit,
    onImportPoetryFontPath: (String) -> Unit,
    onClearPoetryFont: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val colors = PenTheme.colors
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        Triple(0, "AI · MCP", Icons.Rounded.AutoAwesome),
        Triple(1, "白噪音", Icons.Rounded.GraphicEq),
        Triple(2, "久学雷达", Icons.Rounded.Radar),
        Triple(3, "护眼字体", Icons.Rounded.NightlightRound)
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .testTag("pen_lab_screen"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left compact Lab rail
        Surface(
            modifier = Modifier
                .width(102.dp)
                .fillMaxHeight(),
            shape = RoundedCornerShape(colors.cardCornerRadius),
            color = colors.glassCardElevated,
            border = if (colors.showCardBorder) BorderStroke(1.dp, colors.glassBorder) else null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onBack)
                        .padding(vertical = 2.dp)
                        .testTag("lab_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回桌面", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("实验室", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    tabs.forEach { (idx, label, icon) ->
                        val active = selectedTab == idx
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) colors.primaryAction else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedTab = idx }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                            ) {
                                Icon(icon, contentDescription = label, tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

        // Right Content Area
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(colors.cardCornerRadius),
            color = colors.glassCardElevated,
            border = if (colors.showCardBorder) BorderStroke(1.dp, colors.glassBorder) else null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                when (selectedTab) {
                    0 -> LabAiMcpTab(
                        state = state,
                        onSendPrompt = onSendAiPrompt,
                        onSaveAiConfig = onSaveAiConfig,
                        onSelectPreset = onSelectAiPreset,
                        onUpsertPreset = onUpsertAiPreset,
                        onDeletePreset = onDeleteAiPreset,
                        onClearChat = onClearAiChat
                    )
                    1 -> LabWhiteNoiseTab(
                        state = state,
                        onToggle = onToggleWhiteNoise,
                        onVolumeChange = onSetWhiteNoiseVolume
                    )
                    2 -> LabJiuxueRadarTab(
                        state = state,
                        onBindScan = onBindScanFromRadar,
                        onBlockHome = onBlockHomeFromRadar
                    )
                    else -> LabEyeCareFontTab(
                        state = state,
                        onSetNightDimAlpha = onSetNightDimAlpha,
                        onSetLowMemMode = onSetLowMemMode,
                        onPickPoetryFontFile = onPickPoetryFontFile,
                        onImportPoetryFontPath = onImportPoetryFontPath,
                        onClearPoetryFont = onClearPoetryFont
                    )
                }
            }
        }
    }
}

@Composable
private fun LabAiMcpTab(
    state: PenOsUiState,
    onSendPrompt: (String) -> Unit,
    onSaveAiConfig: (String, String, String, String) -> Unit,
    onSelectPreset: (String) -> Unit,
    onUpsertPreset: (AiPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onClearChat: () -> Unit
) {
    val colors = PenTheme.colors
    var subMode by remember { mutableIntStateOf(0) } // 0=Chat, 1=Provider/MCP Config, 2=Custom Preset Editor
    var inputPrompt by remember { mutableStateOf("") }

    var baseUrl by remember(state.config.aiBaseUrl) { mutableStateOf(state.config.aiBaseUrl) }
    var apiKey by remember(state.config.aiApiKey) { mutableStateOf(state.config.aiApiKey) }
    var model by remember(state.config.aiModel) { mutableStateOf(state.config.aiModel) }
    var extMcpUrl by remember(state.config.externalMcpUrl) { mutableStateOf(state.config.externalMcpUrl) }

    var presetName by remember { mutableStateOf("") }
    var presetPrompt by remember { mutableStateOf("") }
    var presetMcp by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        // Header: Presets Bar + Config/Preset Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(state.config.aiPresets, key = { it.id }) { preset ->
                    val active = preset.id == state.config.activeAiPresetId && subMode == 0
                    Surface(
                        shape = RoundedCornerShape(980.dp),
                        color = if (active) colors.primaryAction else colors.chipSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(980.dp))
                            .clickable {
                                subMode = 0
                                onSelectPreset(preset.id)
                            }
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(980.dp),
                color = if (subMode == 2) colors.vocabEmerald else colors.chipSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(980.dp))
                    .clickable { subMode = if (subMode == 2) 0 else 2 }
            ) {
                Text("+预设", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = if (subMode == 2) Color.White else colors.linkAccent, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
            }
            IconButton(onClick = { subMode = if (subMode == 1) 0 else 1 }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Settings, contentDescription = "AI与MCP接口配置", tint = if (subMode == 1) colors.primaryAction else colors.textMuted, modifier = Modifier.size(16.dp))
            }
        }

        when (subMode) {
            1 -> {
                // China ICP-filed AI Provider & External MCP Server Config
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(PenAiMcpClient.icpProviderTemplates) { tpl ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.chipSurface,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        baseUrl = tpl.baseUrl
                                        model = tpl.defaultModel
                                    }
                            ) {
                                Text(tpl.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.linkAccent, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, placeholder = { Text("国内备案 Base URL", style = MaterialTheme.typography.labelSmall) }, singleLine = true, modifier = Modifier.weight(1.3f))
                        OutlinedTextField(value = model, onValueChange = { model = it }, placeholder = { Text("模型名", style = MaterialTheme.typography.labelSmall) }, singleLine = true, modifier = Modifier.weight(0.7f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, placeholder = { Text("个人 API Key（仅存本机）", style = MaterialTheme.typography.labelSmall) }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = extMcpUrl, onValueChange = { extMcpUrl = it }, placeholder = { Text("外部 MCP URL（选填）", style = MaterialTheme.typography.labelSmall) }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                onSaveAiConfig(baseUrl, apiKey, model, extMcpUrl)
                                subMode = 0
                            },
                            shape = RoundedCornerShape(980.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAction),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("保存", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            2 -> {
                // Custom AI Preset Creator
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = presetName, onValueChange = { presetName = it }, placeholder = { Text("预设名称（如 物理老师）", style = MaterialTheme.typography.labelSmall) }, singleLine = true, modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("允许MCP改桌面", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                            Switch(checked = presetMcp, onCheckedChange = { presetMcp = it })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = presetPrompt, onValueChange = { presetPrompt = it }, placeholder = { Text("系统提示词 System Prompt...", style = MaterialTheme.typography.labelSmall) }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                if (presetName.isNotBlank() && presetPrompt.isNotBlank()) {
                                    onUpsertPreset(AiPreset("custom_${System.currentTimeMillis()}", presetName.trim(), presetPrompt.trim(), presetMcp))
                                    presetName = ""
                                    presetPrompt = ""
                                    subMode = 0
                                }
                            },
                            shape = RoundedCornerShape(980.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.vocabEmerald),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("创建", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    // Allow deleting custom presets
                    val customPresets = state.config.aiPresets.filter { it.id.startsWith("custom_") }
                    if (customPresets.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            items(customPresets) { p ->
                                Surface(shape = RoundedCornerShape(8.dp), color = colors.dangerRose.copy(alpha = 0.16f), modifier = Modifier.clickable { onDeletePreset(p.id) }) {
                                    Text("删「${p.name}」", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.dangerRose, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                // Live AI + MCP Chat Stream
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.lab.messages, key = { it.id }) { msg ->
                        val isUser = msg.role == "user"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isUser) colors.primaryAction.copy(alpha = 0.18f) else colors.chipSurface.copy(alpha = 0.72f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                if (msg.toolActionsSummary.isNotBlank()) {
                                    Text(
                                        text = "MCP 已执行：${msg.toolActionsSummary}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = colors.vocabEmerald
                                    )
                                }
                                Text(
                                    text = (if (isUser) "我：" else "AI：") + msg.content,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = inputPrompt,
                        onValueChange = { inputPrompt = it },
                        placeholder = {
                            Text(
                                if (state.config.aiApiKey.isBlank()) "点右上齿轮填国内AI Key即可对话/MCP改桌面" else "指令：切白主题 / 08:30早读 / 25分番茄",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lab_ai_input")
                    )
                    if (state.lab.isAiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.primaryAction)
                    } else {
                        Button(
                            onClick = {
                                if (inputPrompt.isNotBlank()) {
                                    onSendPrompt(inputPrompt)
                                    inputPrompt = ""
                                }
                            },
                            shape = RoundedCornerShape(980.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAction),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("lab_ai_send_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "发送", modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(
                        text = "清空",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        modifier = Modifier.clickable(onClick = onClearChat)
                    )
                }
            }
        }
    }
}

@Composable
private fun LabWhiteNoiseTab(
    state: PenOsUiState,
    onToggle: (WhiteNoiseType) -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    val colors = PenTheme.colors
    val wn = state.lab.whiteNoise

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("离线 DSP 合成白噪音（零流量 · 零体积 · 兼容番茄专注）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = if (wn.isPlaying) "播放中：${wn.activeType.label}" else "已静音",
                style = MaterialTheme.typography.labelSmall,
                color = if (wn.isPlaying) colors.vocabEmerald else colors.textMuted
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(WhiteNoiseType.entries) { type ->
                val playing = wn.isPlaying && wn.activeType == type
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (playing) colors.primaryAction else colors.chipSurface,
                    modifier = Modifier
                        .width(126.dp)
                        .height(66.dp)
                        .springPressScale { onToggle(type) }
                        .clip(RoundedCornerShape(14.dp))
                        .testTag("whitenoise_${type.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (playing) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = if (playing) Color.White else colors.linkAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = type.desc,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (playing) Color.White.copy(alpha = 0.85f) else colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("白噪音音量 ${(wn.volume * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Slider(
                value = wn.volume,
                onValueChange = onVolumeChange,
                valueRange = 0.05f..1f,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LabJiuxueRadarTab(
    state: PenOsUiState,
    onBindScan: (String, String) -> Unit,
    onBlockHome: (String) -> Unit
) {
    val colors = PenTheme.colors
    val radar = state.lab.activityRadar

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "久学网同包名多 Activity 实时雷达（打开一次扫学和原厂桌面后回来点选绑定）",
            style = MaterialTheme.typography.labelMedium,
            color = colors.badgeEmber
        )

        if (radar.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "暂无捕获记录：请先在密室开启「无障碍防锁定守护」，再打开扫学后返回此处即可看到精确类名。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(radar) { entry ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.chipSurface.copy(alpha = 0.72f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${entry.timestamp}  ${entry.className.substringAfterLast('.')}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (entry.wasIntercepted) colors.dangerRose else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = entry.flatComponent,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(980.dp),
                                color = colors.vocabEmerald,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(980.dp))
                                    .clickable { onBindScan(entry.packageName, entry.className) }
                            ) {
                                Text("设为扫学", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                            }
                            Surface(
                                shape = RoundedCornerShape(980.dp),
                                color = colors.dangerRose,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(980.dp))
                                    .clickable { onBlockHome(entry.flatComponent) }
                            ) {
                                Text("拉黑桌面", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabEyeCareFontTab(
    state: PenOsUiState,
    onSetNightDimAlpha: (Float) -> Unit,
    onSetLowMemMode: (Boolean) -> Unit,
    onPickPoetryFontFile: () -> Unit,
    onImportPoetryFontPath: (String) -> Unit,
    onClearPoetryFont: () -> Unit
) {
    val colors = PenTheme.colors
    var fontPath by remember(state.config.customPoetryFontPath) {
        mutableStateOf(state.config.customPoetryFontPath.ifBlank { "/sdcard/Fonts/NotoSerifSC.ttf" })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("夜间护眼暗屏 ${(state.config.nightDimAlpha * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Slider(
                value = state.config.nightDimAlpha,
                onValueChange = onSetNightDimAlpha,
                valueRange = 0f..0.60f,
                modifier = Modifier.weight(1f)
            )
            Text("1GB极简内存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = state.config.lowMemMode, onCheckedChange = onSetLowMemMode)
        }

        Text(
            text = "古诗词字体（已内置思源宋体 Noto Serif SC，也可替换为本地外部 TTF/OTF）",
            style = MaterialTheme.typography.labelSmall,
            color = colors.poetryGold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = fontPath,
                onValueChange = { fontPath = it },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onImportPoetryFontPath(fontPath) },
                shape = RoundedCornerShape(980.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAction),
                modifier = Modifier.height(34.dp)
            ) {
                Text("路径加载", style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = onPickPoetryFontFile,
                shape = RoundedCornerShape(980.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.vocabEmerald),
                modifier = Modifier.height(34.dp)
            ) {
                Text("MT选择", style = MaterialTheme.typography.labelSmall)
            }
            if (state.config.customPoetryFontPath.isNotBlank()) {
                Text("恢复内置", style = MaterialTheme.typography.labelSmall, color = colors.dangerRose, modifier = Modifier.clickable(onClick = onClearPoetryFont))
            }
        }
    }
}
