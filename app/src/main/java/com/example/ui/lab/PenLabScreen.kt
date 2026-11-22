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
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import com.example.data.prefs.AiApiProfile
import com.example.data.prefs.PenConfig
import com.example.data.remote.PenAiMcpClient
import com.example.system.WhiteNoiseType
import com.example.ui.PenOsUiState
import com.example.ui.theme.FontSlotSpec
import com.example.ui.theme.PenTheme
import com.example.ui.theme.springPressScale
import com.example.widgets.CustomWidgetTemplates
import kotlin.math.roundToInt

/**
 * PenOS 4.0 Laboratory:
 * 0: DeepSeek Harness AI + Multi-AI API Profile Switcher + 22-Tool MCP Engine
 * 1: Built-in LAN Web Admin Console (QR + IP + PIN)
 * 2: 5-Slot Global Dynamic Typography Studio
 * 3: 4-Mode Dynamic Wallpaper + 6-Style Clock + 60fps Motion Tier Studio
 * 4: Offline Real-Time DSP White Noise Synthesizer
 * 5: Jiuxuewang Activity Live Radar
 */
@Composable
fun PenLabScreen(
    state: PenOsUiState,
    onBack: () -> Unit,
    onSendAiPrompt: (String) -> Unit,
    onSelectAiProfile: (String) -> Unit,
    onUpsertAiProfile: (AiApiProfile) -> Unit,
    onDeleteAiProfile: (String) -> Unit,
    onSaveExternalMcpUrl: (String) -> Unit,
    onClearAiChat: () -> Unit,
    onToggleLanServer: () -> Unit,
    onRegenerateLanPin: () -> Unit,
    onReopenSetupAssistant: () -> Unit,
    onUpdateFontSlot: (String, FontSlotSpec) -> Unit,
    onPickFontForSlot: (String) -> Unit,
    onResetFonts: () -> Unit,
    onSetDynamicWallpaperMode: (String, String?) -> Unit,
    onPickGifWallpaper: () -> Unit,
    onPickMp4Wallpaper: () -> Unit,
    onOpenSystemLiveWallpaperChooser: () -> Unit,
    onSetClockStyle: (String?, String?, Boolean?) -> Unit,
    onCycleClockStyle: () -> Unit,
    onSetMotionTier: (String) -> Unit,
    onToggleWhiteNoise: (WhiteNoiseType) -> Unit,
    onSetWhiteNoiseVolume: (Float) -> Unit,
    onBindScanFromRadar: (String, String) -> Unit,
    onBlockHomeFromRadar: (String) -> Unit,
    onSetNightDimAlpha: (Float) -> Unit,
    onSetLowMemMode: (Boolean) -> Unit,
    onToggleMcpPreview: (Boolean) -> Unit = {},
    onTogglePendingMcpChange: (String) -> Unit = {},
    onApplyPendingMcpChanges: () -> Unit = {},
    onDiscardPendingMcpChanges: () -> Unit = {},
    onRollbackLatestSnapshot: () -> Unit = {},
    onInstallWidgetTemplate: (String) -> Unit = {},
    onExportFullConfigJson: () -> Unit = {},
    onImportFullConfigJson: () -> Unit = {},
    onSyncFullConfigWebDav: () -> Unit = {},
    onSetSwipeSensitivityDp: (Float) -> Unit = {},
    onSetGestureHapticsEnabled: (Boolean) -> Unit = {},
    onSetMailAutoSyncEnabled: (Boolean) -> Unit = {},
    onRestoreDegradedWallpaper: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val colors = PenTheme.colors
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        Triple(0, "AI · MCP", Icons.Rounded.AutoAwesome),
        Triple(1, "局域网", Icons.Rounded.Language),
        Triple(2, "全局字体", Icons.Rounded.TextFields),
        Triple(3, "壁纸时钟", Icons.Rounded.Wallpaper),
        Triple(4, "白噪音", Icons.Rounded.GraphicEq),
        Triple(5, "久学雷达", Icons.Rounded.Radar)
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("pen_lab_screen"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left scrollable Lab rail
        Surface(
            modifier = Modifier
                .width(98.dp)
                .fillMaxHeight(),
            shape = RoundedCornerShape(colors.cardCornerRadius),
            color = colors.glassCardElevated,
            border = if (colors.showCardBorder) BorderStroke(1.dp, colors.glassBorder) else null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 5.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
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
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回桌面", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("实验室", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    tabs.forEach { (idx, label, icon) ->
                        val active = selectedTab == idx
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = if (active) colors.primaryAction else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { selectedTab = idx }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(icon, contentDescription = label, tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(label, fontSize = 10.sp, color = if (active) Color.White else MaterialTheme.colorScheme.onSurface, maxLines = 1)
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
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                when (selectedTab) {
                    0 -> LabAiHarnessTab(
                        state = state,
                        onSendPrompt = onSendAiPrompt,
                        onSelectProfile = onSelectAiProfile,
                        onUpsertProfile = onUpsertAiProfile,
                        onDeleteProfile = onDeleteAiProfile,
                        onSaveExternalMcpUrl = onSaveExternalMcpUrl,
                        onClearChat = onClearAiChat,
                        onToggleMcpPreview = onToggleMcpPreview,
                        onTogglePendingMcpChange = onTogglePendingMcpChange,
                        onApplyPendingMcpChanges = onApplyPendingMcpChanges,
                        onDiscardPendingMcpChanges = onDiscardPendingMcpChanges,
                        onRollbackLatestSnapshot = onRollbackLatestSnapshot,
                        onInstallWidgetTemplate = onInstallWidgetTemplate
                    )
                    1 -> LabLanAdminTab(
                        state = state,
                        onToggleLanServer = onToggleLanServer,
                        onRegeneratePin = onRegenerateLanPin,
                        onReopenSetupAssistant = onReopenSetupAssistant,
                        onExportFullConfigJson = onExportFullConfigJson,
                        onImportFullConfigJson = onImportFullConfigJson,
                        onSyncFullConfigWebDav = onSyncFullConfigWebDav
                    )
                    2 -> LabFontStudioTab(
                        state = state,
                        onUpdateFontSlot = onUpdateFontSlot,
                        onPickFontForSlot = onPickFontForSlot,
                        onResetFonts = onResetFonts,
                        onSetNightDimAlpha = onSetNightDimAlpha,
                        onSetLowMemMode = onSetLowMemMode
                    )
                    3 -> LabWallpaperClockTab(
                        state = state,
                        onSetDynamicWallpaperMode = onSetDynamicWallpaperMode,
                        onPickGifWallpaper = onPickGifWallpaper,
                        onPickMp4Wallpaper = onPickMp4Wallpaper,
                        onOpenSystemLiveWallpaperChooser = onOpenSystemLiveWallpaperChooser,
                        onSetClockStyle = onSetClockStyle,
                        onCycleClockStyle = onCycleClockStyle,
                        onSetMotionTier = onSetMotionTier,
                        onSetSwipeSensitivityDp = onSetSwipeSensitivityDp,
                        onSetGestureHapticsEnabled = onSetGestureHapticsEnabled,
                        onSetMailAutoSyncEnabled = onSetMailAutoSyncEnabled,
                        onRestoreDegradedWallpaper = onRestoreDegradedWallpaper
                    )
                    4 -> LabWhiteNoiseTab(
                        state = state,
                        onToggle = onToggleWhiteNoise,
                        onVolumeChange = onSetWhiteNoiseVolume
                    )
                    else -> LabJiuxueRadarTab(
                        state = state,
                        onBindScan = onBindScanFromRadar,
                        onBlockHome = onBlockHomeFromRadar
                    )
                }
            }
        }
    }
}

@Composable
private fun LabAiHarnessTab(
    state: PenOsUiState,
    onSendPrompt: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onUpsertProfile: (AiApiProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveExternalMcpUrl: (String) -> Unit,
    onClearChat: () -> Unit,
    onToggleMcpPreview: (Boolean) -> Unit,
    onTogglePendingMcpChange: (String) -> Unit,
    onApplyPendingMcpChanges: () -> Unit,
    onDiscardPendingMcpChanges: () -> Unit,
    onRollbackLatestSnapshot: () -> Unit,
    onInstallWidgetTemplate: (String) -> Unit
) {
    val colors = PenTheme.colors
    val activeProfile = state.config.activeAiProfile()
    var showProfileEditor by remember { mutableStateOf(false) }
    var inputPrompt by remember { mutableStateOf("") }

    var editId by remember(activeProfile.id, showProfileEditor) { mutableStateOf(activeProfile.id) }
    var editName by remember(activeProfile.id, showProfileEditor) { mutableStateOf(activeProfile.name) }
    var editBaseUrl by remember(activeProfile.id, showProfileEditor) { mutableStateOf(activeProfile.baseUrl) }
    var editApiKey by remember(activeProfile.id, showProfileEditor) { mutableStateOf(activeProfile.apiKey) }
    var editModel by remember(activeProfile.id, showProfileEditor) { mutableStateOf(activeProfile.model) }
    var editExtMcp by remember(state.config.externalMcpUrl) { mutableStateOf(state.config.externalMcpUrl) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        // Multi-AI Profile Switcher Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.config.aiProfiles, key = { it.id }) { profile ->
                    val active = profile.id == state.config.activeAiProfileId && !showProfileEditor
                    Surface(
                        shape = RoundedCornerShape(980.dp),
                        color = if (active) colors.primaryAction else colors.chipSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(980.dp))
                            .clickable {
                                showProfileEditor = false
                                onSelectProfile(profile.id)
                            }
                    ) {
                        Text(
                            text = profile.name + if (profile.apiKey.isNotBlank()) " ✓" else "",
                            fontSize = 10.sp,
                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(980.dp),
                color = colors.chipSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(980.dp))
                    .clickable {
                        editId = "profile_${System.currentTimeMillis()}"
                        editName = "新模型预设"
                        editBaseUrl = "https://api.deepseek.com/v1"
                        editApiKey = ""
                        editModel = "deepseek-chat"
                        showProfileEditor = true
                    }
            ) {
                Text("+模型", fontSize = 10.sp, color = colors.linkAccent, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
            }
            IconButton(onClick = { showProfileEditor = !showProfileEditor }, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Rounded.Settings, contentDescription = "编辑当前模型API", tint = if (showProfileEditor) colors.primaryAction else colors.textMuted, modifier = Modifier.size(15.dp))
            }
        }

        if (showProfileEditor) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(PenAiMcpClient.icpProviderTemplates) { tpl ->
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = colors.chipSurface,
                            modifier = Modifier.clickable {
                                editName = tpl.name
                                editBaseUrl = tpl.baseUrl
                                editModel = tpl.defaultModel
                            }
                        ) {
                            Text(tpl.name, fontSize = 9.sp, color = colors.linkAccent, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, placeholder = { Text("名称", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(0.7f))
                    OutlinedTextField(value = editBaseUrl, onValueChange = { editBaseUrl = it }, placeholder = { Text("Base URL", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(1.3f))
                    OutlinedTextField(value = editModel, onValueChange = { editModel = it }, placeholder = { Text("Model", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(0.8f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = editApiKey, onValueChange = { editApiKey = it }, placeholder = { Text("API Key（仅存本机）", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = editExtMcp, onValueChange = { editExtMcp = it }, placeholder = { Text("外部 MCP URL", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(0.9f))
                    Button(
                        onClick = {
                            onUpsertProfile(
                                AiApiProfile(
                                    id = editId,
                                    name = editName.trim().ifBlank { "自定义模型" },
                                    baseUrl = editBaseUrl.trim(),
                                    apiKey = editApiKey.trim(),
                                    model = editModel.trim(),
                                    systemPrompt = activeProfile.systemPrompt.ifBlank { PenConfig.DEFAULT_HARNESS_PROMPT },
                                    enableMcpTools = true
                                )
                            )
                            onSaveExternalMcpUrl(editExtMcp)
                            showProfileEditor = false
                        },
                        shape = RoundedCornerShape(980.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAction),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("保存", fontSize = 10.sp)
                    }
                    if (state.config.aiProfiles.size > 1) {
                        Text("删", fontSize = 10.sp, color = colors.dangerRose, modifier = Modifier.clickable {
                            onDeleteProfile(editId)
                            showProfileEditor = false
                        })
                    }
                }
            }
        } else {
            // PenOS 5.0 Guardrail + Rollback + Offline 17-Block Template Gallery Strip
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "guardrail_preview_toggle") {
                    val previewOn = state.config.mcpPreviewBeforeApply
                    Surface(
                        shape = RoundedCornerShape(980.dp),
                        color = if (previewOn) colors.vocabEmerald.copy(alpha = 0.2f) else colors.standByAmber.copy(alpha = 0.2f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(980.dp))
                            .clickable { onToggleMcpPreview(!previewOn) }
                            .testTag("lab_mcp_preview_toggle")
                    ) {
                        Text(
                            text = if (previewOn) "护栏:预览确认✓" else "护栏:直执(自动快照)",
                            fontSize = 9.sp,
                            color = if (previewOn) colors.vocabEmerald else colors.standByAmber,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                item(key = "rollback_snapshot_chip") {
                    val hasSnap = state.recentSnapshots.isNotEmpty()
                    Surface(
                        shape = RoundedCornerShape(980.dp),
                        color = if (hasSnap) colors.dangerRose.copy(alpha = 0.18f) else colors.chipSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(980.dp))
                            .clickable(enabled = hasSnap, onClick = onRollbackLatestSnapshot)
                            .testTag("lab_rollback_snapshot_btn")
                    ) {
                        Text(
                            text = if (hasSnap) "撤销修改(${state.recentSnapshots.size})" else "暂无快照",
                            fontSize = 9.sp,
                            color = if (hasSnap) colors.dangerRose else colors.textMuted,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                items(CustomWidgetTemplates.all, key = { "tpl_${it.id}" }) { tpl ->
                    Surface(
                        shape = RoundedCornerShape(980.dp),
                        color = colors.chipSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(980.dp))
                            .clickable { onInstallWidgetTemplate(tpl.id) }
                    ) {
                        Text(
                            text = "+${tpl.title}",
                            fontSize = 9.sp,
                            color = colors.linkAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // PenOS 5.0 Pending MCP Diff Preview Card
            if (state.pendingMcpChanges.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.standByAmber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, colors.standByAmber.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp)
                        .testTag("lab_mcp_diff_card")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "待确认 AI 变更（${state.pendingMcpChanges.count { it.selected }}/${state.pendingMcpChanges.size} 项）",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.standByAmber
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(980.dp),
                                    color = colors.vocabEmerald,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(980.dp))
                                        .clickable(onClick = onApplyPendingMcpChanges)
                                        .testTag("lab_mcp_apply_diff_btn")
                                ) {
                                    Text(
                                        text = "应用并存档",
                                        fontSize = 9.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(980.dp),
                                    color = colors.chipSurface,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(980.dp))
                                        .clickable(onClick = onDiscardPendingMcpChanges)
                                        .testTag("lab_mcp_discard_diff_btn")
                                ) {
                                    Text(
                                        text = "放弃",
                                        fontSize = 9.sp,
                                        color = colors.dangerRose,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(state.pendingMcpChanges, key = { it.id }) { chg ->
                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = if (chg.selected) colors.primaryAction.copy(alpha = 0.2f) else colors.chipSurface,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .clickable { onTogglePendingMcpChange(chg.id) }
                                ) {
                                    Text(
                                        text = (if (chg.selected) "☑ " else "☐ ") + chg.humanSummary,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
                                    text = "Harness MCP：${msg.toolActionsSummary}",
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
                            if (activeProfile.apiKey.isBlank()) "点右上齿轮或局域网后台配置 API Key" else "例：帮我写一个倒计时小组件放到上滑抽屉",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lab_ai_input")
                )
                if (state.lab.isAiLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.primaryAction)
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
                            .height(34.dp)
                            .testTag("lab_ai_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "发送", modifier = Modifier.size(13.dp))
                    }
                }
                Text("清空", fontSize = 10.sp, color = colors.textMuted, modifier = Modifier.clickable(onClick = onClearChat))
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
            Text("离线 DSP 合成白噪音（零流量 · 兼容番茄专注）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
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
                        .width(120.dp)
                        .height(62.dp)
                        .springPressScale { onToggle(type) }
                        .clip(RoundedCornerShape(14.dp))
                        .testTag("whitenoise_${type.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(type.label, style = MaterialTheme.typography.titleSmall, color = if (playing) Color.White else MaterialTheme.colorScheme.onSurface)
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = if (playing) Color.White else colors.linkAccent, modifier = Modifier.size(15.dp))
                        }
                        Text(type.desc, fontSize = 9.sp, color = if (playing) Color.White.copy(alpha = 0.85f) else colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("音量 ${(wn.volume * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
            Slider(value = wn.volume, onValueChange = onVolumeChange, valueRange = 0.05f..1f, modifier = Modifier.weight(1f))
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
        Text("久学网同包名多 Activity 实时雷达", style = MaterialTheme.typography.labelMedium, color = colors.badgeEmber)

        if (radar.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无捕获记录：开启无障碍防锁回守护后，打开扫学再返回此处即可一键绑定。", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(radar) { entry ->
                    Surface(shape = RoundedCornerShape(10.dp), color = colors.chipSurface.copy(alpha = 0.72f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${entry.timestamp}  ${entry.className.substringAfterLast('.')}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (entry.wasIntercepted) colors.dangerRose else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(entry.flatComponent, fontSize = 9.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Surface(shape = RoundedCornerShape(980.dp), color = colors.vocabEmerald, modifier = Modifier.clickable { onBindScan(entry.packageName, entry.className) }) {
                                Text("设为扫学", fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                            }
                            Surface(shape = RoundedCornerShape(980.dp), color = colors.dangerRose, modifier = Modifier.clickable { onBlockHome(entry.flatComponent) }) {
                                Text("拉黑桌面", fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
