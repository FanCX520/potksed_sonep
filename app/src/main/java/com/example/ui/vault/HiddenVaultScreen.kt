package com.example.ui.vault

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.prefs.PenConfig
import com.example.ui.PenOsUiState
import com.example.ui.components.MonoCard
import com.example.ui.components.MonoChip
import com.example.ui.components.MonoOutlineButton
import com.example.ui.components.MonoPrimaryButton
import com.example.ui.components.MonoSectionTitle
import com.example.ui.components.MonoTopBar
import com.example.ui.theme.PenTheme

@Composable
fun HiddenVaultScreen(
    state: PenOsUiState,
    onCloseVault: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    onUpdatePin: (String) -> Unit,
    onToggleDarkTheme: () -> Unit,
    onSetShowSeconds: (Boolean) -> Unit,
    onSelectWallpaperMode: (String) -> Unit,
    onPickCustomWallpaper: () -> Unit,
    onImportWallpaperPath: (String) -> Unit,
    onToggleBypass60s: () -> Unit,
    onToggleKeepAlive: (Boolean) -> Unit,
    onSetBlockedHomePackage: (String) -> Unit,
    onToggleBlockedActivity: (String) -> Unit,
    onBindScanTarget: (String, String) -> Unit,
    onInspectActivities: (String) -> Unit,
    onToggleHiddenApp: (String) -> Unit,
    onLaunchApp: (String, String) -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onRootDisableHomeComponent: () -> Unit,
    onOpenAiScreen: () -> Unit
) {
    BackHandler(onBack = onCloseVault)

    val palette = PenTheme.palette

    if (!state.vaultUnlocked) {
        VaultPinGate(
            onBack = onCloseVault,
            onVerifyPin = onVerifyPin
        )
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("壁纸与主题", "防锁回", "应用与扫学", "安全与AI")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .safeDrawingPadding()
    ) {
        MonoTopBar(
            title = "隐藏密室",
            subtitle = "静态壁纸 · 久学网防锁回 · 扫学绑定",
            onBack = onCloseVault
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { idx, label ->
                MonoChip(
                    label = label,
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    testTag = "vault_tab_$idx"
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedTab) {
                0 -> VaultWallpaperTab(
                    state = state,
                    onToggleDarkTheme = onToggleDarkTheme,
                    onSetShowSeconds = onSetShowSeconds,
                    onSelectWallpaperMode = onSelectWallpaperMode,
                    onPickCustomWallpaper = onPickCustomWallpaper,
                    onImportWallpaperPath = onImportWallpaperPath
                )
                1 -> VaultTakeoverTab(
                    state = state,
                    onToggleBypass60s = onToggleBypass60s,
                    onToggleKeepAlive = onToggleKeepAlive,
                    onSetBlockedHomePackage = onSetBlockedHomePackage,
                    onToggleBlockedActivity = onToggleBlockedActivity,
                    onBindScanFromRadar = onBindScanTarget,
                    onRefreshDiagnostics = onRefreshDiagnostics,
                    onRootDisableHomeComponent = onRootDisableHomeComponent
                )
                2 -> VaultAppsAndScanTab(
                    state = state,
                    onBindScanTarget = onBindScanTarget,
                    onInspectActivities = onInspectActivities,
                    onToggleHiddenApp = onToggleHiddenApp,
                    onLaunchApp = onLaunchApp
                )
                3 -> VaultSecurityTab(
                    onUpdatePin = onUpdatePin,
                    onOpenAiScreen = onOpenAiScreen
                )
            }
        }
    }
}

@Composable
private fun VaultPinGate(
    onBack: () -> Unit,
    onVerifyPin: (String) -> Boolean
) {
    val palette = PenTheme.palette
    var entered by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .safeDrawingPadding()
            .testTag("vault_pin_gate")
    ) {
        MonoTopBar(
            title = "验证密室密码",
            subtitle = "默认初始密码 0000",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { idx ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (idx < entered.length) palette.accent else palette.surfaceAlt)
                            .border(1.dp, palette.hairline, CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.height(22.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("清空", "0", "退格")
            )
            Column(
                modifier = Modifier.widthIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { key ->
                            MonoOutlineButton(
                                text = key,
                                onClick = {
                                    when (key) {
                                        "清空" -> entered = ""
                                        "退格" -> if (entered.isNotEmpty()) entered = entered.dropLast(1)
                                        else -> {
                                            if (entered.length < 8) {
                                                entered += key
                                                if (entered.length == 4 && onVerifyPin(entered)) {
                                                    entered = ""
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                testTag = "pin_key_$key"
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                MonoPrimaryButton(
                    text = "确认解锁",
                    onClick = {
                        if (!onVerifyPin(entered)) entered = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "pin_submit_button"
                )
            }
        }
    }
}

@Composable
private fun VaultWallpaperTab(
    state: PenOsUiState,
    onToggleDarkTheme: () -> Unit,
    onSetShowSeconds: (Boolean) -> Unit,
    onSelectWallpaperMode: (String) -> Unit,
    onPickCustomWallpaper: () -> Unit,
    onImportWallpaperPath: (String) -> Unit
) {
    val palette = PenTheme.palette
    var localPath by remember { mutableStateOf("/sdcard/Pictures/bg.jpg") }

    MonoSectionTitle("黑白极简双主题")
    MonoCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.config.isDarkTheme) "当前：极简纯黑 (OLED)" else "当前：极简纸白 (Swiss)",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.ink
                    )
                    Text("主屏右上角随时可一键翻转黑白", style = MaterialTheme.typography.labelSmall, color = palette.inkMuted)
                }
                MonoPrimaryButton(
                    text = "切换主题",
                    onClick = onToggleDarkTheme,
                    testTag = "vault_theme_toggle"
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("主屏大时钟显示秒数", style = MaterialTheme.typography.bodyMedium, color = palette.ink)
                    Text("关闭时每分钟仅重绘一次，极度省电", style = MaterialTheme.typography.labelSmall, color = palette.inkMuted)
                }
                Switch(
                    checked = state.config.showSecondsOnHome,
                    onCheckedChange = onSetShowSeconds
                )
            }
        }
    }

    MonoSectionTitle("纯静态壁纸（已移除所有动态壁纸以杜绝掉帧）")
    MonoCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = listOf(
                PenConfig.WALLPAPER_SOLID to "纯色零过绘制（最流畅推荐）",
                PenConfig.WALLPAPER_INK to "静态 · 水墨深灰",
                PenConfig.WALLPAPER_AURORA to "静态 · 幽暗极光",
                PenConfig.WALLPAPER_GALLERY to "静态 · 极简画廊"
            )
            presets.forEach { (mode, label) ->
                MonoOutlineButton(
                    text = label,
                    selected = state.config.staticWallpaperMode == mode,
                    onClick = { onSelectWallpaperMode(mode) },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "wallpaper_preset_$mode"
                )
            }
            MonoPrimaryButton(
                text = "从相册 / MT管理器选择静态图片",
                onClick = onPickCustomWallpaper,
                modifier = Modifier.fillMaxWidth(),
                testTag = "wallpaper_pick_custom"
            )
            OutlinedTextField(
                value = localPath,
                onValueChange = { localPath = it },
                label = { Text("或直接输入本地图片路径") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            MonoOutlineButton(
                text = "按路径导入静态壁纸",
                onClick = { onImportWallpaperPath(localPath) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun VaultAppsAndScanTab(
    state: PenOsUiState,
    onBindScanTarget: (String, String) -> Unit,
    onInspectActivities: (String) -> Unit,
    onToggleHiddenApp: (String) -> Unit,
    onLaunchApp: (String, String) -> Unit
) {
    val palette = PenTheme.palette
    var inspectingPkg by remember { mutableStateOf("") }

    MonoSectionTitle("当前学王扫学入口")
    MonoCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.config.scanAppPackage.ifBlank { "未绑定（请在下方应用列表中指定）" },
                style = MaterialTheme.typography.titleMedium,
                color = palette.ink
            )
            if (state.config.scanAppActivity.isNotBlank()) {
                Text(
                    text = "Activity: ${state.config.scanAppActivity}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent
                )
            }
        }
    }

    MonoSectionTitle("已安装应用管理（隐藏 / 设扫学 / 启动）")
    state.installedApps.forEach { app ->
        val isHidden = state.config.hiddenPackages.contains(app.packageName)
        val isScan = state.config.scanAppPackage == app.packageName
        MonoCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isHidden) {
                        Text("已隐藏", style = MaterialTheme.typography.labelSmall, color = palette.accent)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonoOutlineButton(
                        text = "启动",
                        onClick = { onLaunchApp(app.packageName, app.launchActivityName) },
                        modifier = Modifier.weight(1f)
                    )
                    MonoOutlineButton(
                        text = if (isScan) "扫学 ✓" else "设扫学",
                        selected = isScan,
                        onClick = { onBindScanTarget(app.packageName, app.launchActivityName) },
                        modifier = Modifier.weight(1f)
                    )
                    MonoOutlineButton(
                        text = if (isHidden) "取消隐藏" else "隐藏",
                        onClick = { onToggleHiddenApp(app.packageName) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (app.isLikelyScanApp || app.isHomeLauncher) {
                    MonoOutlineButton(
                        text = "查看该包内部 Activity（精准绑定扫学页）",
                        onClick = {
                            inspectingPkg = app.packageName
                            onInspectActivities(app.packageName)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (inspectingPkg == app.packageName && state.inspectedActivities.isNotEmpty()) {
                        state.inspectedActivities.take(10).forEach { act ->
                            MonoOutlineButton(
                                text = "绑定：${act.substringAfterLast('.')}",
                                onClick = { onBindScanTarget(app.packageName, act) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultSecurityTab(
    onUpdatePin: (String) -> Unit,
    onOpenAiScreen: () -> Unit
) {
    val palette = PenTheme.palette
    var newPin by remember { mutableStateOf("") }

    MonoSectionTitle("AI 模型与外部 MCP 快捷入口")
    MonoCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "所有 OpenAI 兼容模型预设与外部 MCP 服务器均在 AI 页面内统一配置。",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted
            )
            MonoPrimaryButton(
                text = "前往 AI 与外接 MCP 页面",
                onClick = onOpenAiScreen,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    MonoSectionTitle("修改隐藏密室密码")
    MonoCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newPin,
                onValueChange = { if (it.length <= 8) newPin = it.filter { ch -> ch.isDigit() } },
                label = { Text("新密码（4~8位数字）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            MonoPrimaryButton(
                text = "更新密室密码",
                onClick = {
                    onUpdatePin(newPin)
                    newPin = ""
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
