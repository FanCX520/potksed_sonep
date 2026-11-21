package com.example.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.prefs.PenConfig
import com.example.system.SystemTakeoverHelper
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme
import com.example.widgets.WidgetRegistry

private enum class VaultTab(val label: String, val icon: ImageVector) {
    HIDDEN_APPS("隐藏应用", Icons.Rounded.Apps),
    WIDGETS_THEME("风格组件", Icons.Rounded.DashboardCustomize),
    TAKEOVER_SCAN("扫学防锁", Icons.Rounded.Shield),
    SECURITY_SYNC("安全同步", Icons.Rounded.Security)
}

@Composable
fun HiddenVaultScreen(
    state: PenOsUiState,
    onCloseVault: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    onLaunchApp: (String) -> Unit,
    onToggleHiddenApp: (String) -> Unit,
    onTogglePinnedApp: (String) -> Unit,
    onSelectDesignStyle: (String) -> Unit,
    onSelectStandByStyleMode: (String) -> Unit,
    onToggleWidget: (String) -> Unit,
    onMoveWidget: (String, Int) -> Unit,
    onSelectWallpaperPreset: (String) -> Unit,
    onPickCustomWallpaper: () -> Unit,
    onUpdateStandBy: (Int?, Float?, String?, String?) -> Unit,
    onSetPenFramePreview: (Boolean) -> Unit,
    onBindScanApp: (String) -> Unit,
    onBindBlockedLauncher: (String) -> Unit,
    onToggleKeepAlive: (Boolean) -> Unit,
    onRootSetHome: () -> Unit,
    onRootFreezeStock: (Boolean) -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onSetVaultPin: (String) -> Unit,
    onSaveWebDav: (String, String, String) -> Unit,
    onSyncWebDav: () -> Unit,
    onGenerateSyncCode: () -> Unit,
    onImportSyncCode: (String) -> Unit,
    onExportJsonFile: () -> Unit,
    onImportJsonFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isVaultUnlocked) {
        VaultPinGate(onBack = onCloseVault, onVerifyPin = onVerifyPin, modifier = modifier)
        return
    }

    val colors = PenTheme.colors
    var activeTab by remember { mutableStateOf(VaultTab.HIDDEN_APPS) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(colors.cardCornerRadius),
                color = colors.glassCardElevated,
                border = if (colors.showCardBorder) BorderStroke(1.dp, colors.glassBorder) else null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onCloseVault)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .testTag("vault_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "退出密室",
                            tint = colors.primaryAction,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "退出密室",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primaryAction,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        VaultTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            Surface(
                                shape = RoundedCornerShape(980.dp),
                                color = if (selected) colors.primaryAction.copy(alpha = 0.16f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(980.dp))
                                    .clickable { activeTab = tab }
                                    .testTag("vault_tab_${tab.name.lowercase()}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = if (selected) colors.primaryAction else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(colors.cardCornerRadius),
                color = colors.glassCard,
                border = if (colors.showCardBorder) BorderStroke(1.dp, colors.glassBorder) else null
            ) {
                Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    when (activeTab) {
                        VaultTab.HIDDEN_APPS -> HiddenAppsTab(
                            state = state,
                            onLaunchApp = onLaunchApp,
                            onToggleHiddenApp = onToggleHiddenApp,
                            onTogglePinnedApp = onTogglePinnedApp
                        )
                        VaultTab.WIDGETS_THEME -> WidgetsAndThemeTab(
                            state = state,
                            onSelectDesignStyle = onSelectDesignStyle,
                            onSelectStandByStyleMode = onSelectStandByStyleMode,
                            onToggleWidget = onToggleWidget,
                            onMoveWidget = onMoveWidget,
                            onSelectWallpaperPreset = onSelectWallpaperPreset,
                            onPickCustomWallpaper = onPickCustomWallpaper,
                            onUpdateStandBy = onUpdateStandBy,
                            onSetPenFramePreview = onSetPenFramePreview
                        )
                        VaultTab.TAKEOVER_SCAN -> TakeoverAndScanTab(
                            state = state,
                            onBindScanApp = onBindScanApp,
                            onBindBlockedLauncher = onBindBlockedLauncher,
                            onToggleKeepAlive = onToggleKeepAlive,
                            onRootSetHome = onRootSetHome,
                            onRootFreezeStock = onRootFreezeStock,
                            onRefreshDiagnostics = onRefreshDiagnostics
                        )
                        VaultTab.SECURITY_SYNC -> SecurityAndSyncTab(
                            state = state,
                            onSetVaultPin = onSetVaultPin,
                            onSaveWebDav = onSaveWebDav,
                            onSyncWebDav = onSyncWebDav,
                            onGenerateSyncCode = onGenerateSyncCode,
                            onImportSyncCode = onImportSyncCode,
                            onExportJsonFile = onExportJsonFile,
                            onImportJsonFile = onImportJsonFile
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultPinGate(
    onBack: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val colors = PenTheme.colors
    var pinInput by remember { mutableStateOf("") }
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(0.4f), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.primaryAction, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("密室解锁", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "●".repeat(pinInput.length) + "○".repeat((4 - pinInput.length).coerceAtLeast(0)),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.primaryAction,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Column(modifier = Modifier.weight(0.6f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val rows = listOf(listOf("1", "2", "3", "4", "5", "C"), listOf("6", "7", "8", "9", "0", "OK"))
            rows.forEach { rowKeys ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowKeys.forEach { key ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.glassCardElevated,
                            border = if (colors.showCardBorder) BorderStroke(1.dp, colors.glassBorder) else null,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    when (key) {
                                        "C" -> pinInput = ""
                                        "OK" -> if (!onVerifyPin(pinInput)) pinInput = ""
                                        else -> {
                                            if (pinInput.length < 4) {
                                                pinInput += key
                                                if (pinInput.length == 4 && !onVerifyPin(pinInput)) pinInput = ""
                                            }
                                        }
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (key == "OK") colors.primaryAction else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenAppsTab(
    state: PenOsUiState,
    onLaunchApp: (String) -> Unit,
    onToggleHiddenApp: (String) -> Unit,
    onTogglePinnedApp: (String) -> Unit
) {
    val colors = PenTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "全部应用与隐私隐藏（共 ${state.installedApps.size} 个，已隐藏 ${state.config.hiddenPackages.size} 个）",
            style = MaterialTheme.typography.labelMedium,
            color = colors.linkAccent
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.installedApps, key = { it.packageName }) { app ->
                val isHidden = state.config.hiddenPackages.contains(app.packageName)
                val isPinned = state.config.pinnedDockPackages.contains(app.packageName)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isHidden) colors.dangerRose.copy(alpha = 0.12f) else colors.chipSurface.copy(alpha = 0.55f),
                    border = if (colors.showCardBorder) BorderStroke(
                        1.dp,
                        if (isHidden) colors.dangerRose.copy(alpha = 0.45f) else colors.glassBorder
                    ) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bmp = app.iconBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(7.dp))
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.label + if (isHidden) " [已隐藏]" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onLaunchApp(app.packageName) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.Launch, contentDescription = "启动应用", tint = colors.primaryAction, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onTogglePinnedApp(app.packageName) }, enabled = !isHidden, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.PushPin, contentDescription = "钉选到桌面", tint = if (isPinned) colors.linkAccent else colors.textMuted, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onToggleHiddenApp(app.packageName) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (isHidden) "取消隐藏" else "隐藏应用",
                                tint = if (isHidden) colors.dangerRose else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetsAndThemeTab(
    state: PenOsUiState,
    onSelectDesignStyle: (String) -> Unit,
    onSelectStandByStyleMode: (String) -> Unit,
    onToggleWidget: (String) -> Unit,
    onMoveWidget: (String, Int) -> Unit,
    onSelectWallpaperPreset: (String) -> Unit,
    onPickCustomWallpaper: () -> Unit,
    onUpdateStandBy: (Int?, Float?, String?, String?) -> Unit,
    onSetPenFramePreview: (Boolean) -> Unit
) {
    val colors = PenTheme.colors
    val cfg = state.config

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Built-in Dual Design System Switcher
        Text("内置双套设计方案", style = MaterialTheme.typography.labelMedium, color = colors.primaryAction)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val themes = listOf(
                Triple(PenConfig.STYLE_DARK_STANDBY, "方案一 · 暗黑 CarPlay", "曜石玻璃 / 暖珊瑚低功耗"),
                Triple(PenConfig.STYLE_APPLE_LIGHT, "方案二 · Apple 极简白画廊", "纯白无框 / Apple Blue 胶囊")
            )
            themes.forEach { (id, title, desc) ->
                val selected = cfg.designStyle == id
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) colors.primaryAction.copy(alpha = 0.16f) else colors.chipSurface.copy(alpha = 0.55f),
                    border = if (selected) BorderStroke(1.5.dp, colors.primaryAction) else null,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectDesignStyle(id) }
                        .testTag("theme_option_$id")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(title, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.primaryAction else MaterialTheme.colorScheme.onSurface)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                }
            }
        }

        // 2. Independent StandBy Theme Override
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("StandBy 独立风格:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            listOf(
                PenConfig.STANDBY_STYLE_OLED_DARK to "始终纯黑省电",
                PenConfig.STANDBY_STYLE_APPLE_LIGHT to "极简白画廊",
                PenConfig.STANDBY_STYLE_FOLLOW to "跟随主屏"
            ).forEach { (mode, label) ->
                val selected = cfg.standByStyleMode == mode
                Surface(
                    shape = RoundedCornerShape(980.dp),
                    color = if (selected) colors.primaryAction else colors.chipSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(980.dp))
                        .clickable { onSelectStandByStyleMode(mode) }
                        .testTag("standby_style_$mode")
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 3. Modular Widgets Manager (all 8 plugins including Weather, Calendar, Todo)
        Text("模块化小组件管理（已内置天气/日历/待办等 8 个插件）", style = MaterialTheme.typography.labelMedium, color = colors.linkAccent)
        WidgetRegistry.allPlugins.forEach { plugin ->
            val enabled = cfg.enabledWidgetIds.contains(plugin.id)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.chipSurface.copy(alpha = 0.48f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${plugin.title} · ${plugin.lowPowerNote}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(plugin.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    if (enabled) {
                        IconButton(onClick = { onMoveWidget(plugin.id, -1) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "前移", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp))
                        }
                        IconButton(onClick = { onMoveWidget(plugin.id, 1) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "后移", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp))
                        }
                    }
                    Switch(checked = enabled, onCheckedChange = { onToggleWidget(plugin.id) })
                }
            }
        }

        // 4. Wallpaper Presets & Custom Picker
        Text("桌面壁纸设置", style = MaterialTheme.typography.labelMedium, color = colors.badgeEmber)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                PenConfig.WALLPAPER_AURORA to "极光 CarPlay",
                PenConfig.WALLPAPER_INK to "夜墨山水",
                PenConfig.WALLPAPER_OBSIDIAN to "纯黑省电"
            ).forEach { (id, label) ->
                val selected = cfg.wallpaperPresetId == id
                OutlinedButton(
                    onClick = { onSelectWallpaperPreset(id) },
                    shape = RoundedCornerShape(980.dp),
                    border = BorderStroke(1.dp, if (selected) colors.primaryAction else colors.glassBorder),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) colors.primaryAction else MaterialTheme.colorScheme.onSurface)
                }
            }
            Button(
                onClick = onPickCustomWallpaper,
                shape = RoundedCornerShape(980.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAction),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("pick_custom_wallpaper_button")
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("相册自定义", style = MaterialTheme.typography.labelSmall)
            }
        }

        // 5. StandBy Timeout, Companion Widget (4 options), Clock Color, Brightness
        Text("StandBy 待机参数", style = MaterialTheme.typography.labelMedium, color = colors.badgeEmber)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("自动进入:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            listOf(0 to "关闭", 15 to "15秒", 30 to "30秒", 60 to "60秒").forEach { (sec, label) ->
                val selected = cfg.standByTimeoutSec == sec
                Surface(
                    shape = RoundedCornerShape(980.dp),
                    color = if (selected) colors.primaryAction else colors.chipSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(980.dp))
                        .clickable { onUpdateStandBy(sec, null, null, null) }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("右侧卡片:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            listOf(
                PenConfig.STANDBY_WIDGET_POETRY to "诗词",
                PenConfig.STANDBY_WIDGET_WEATHER to "天气",
                PenConfig.STANDBY_WIDGET_TODO to "待办",
                PenConfig.STANDBY_WIDGET_VOCAB to "词卡"
            ).forEach { (id, label) ->
                val selected = cfg.standByRightWidget == id
                Surface(
                    shape = RoundedCornerShape(980.dp),
                    color = if (selected) colors.primaryAction else colors.chipSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(980.dp))
                        .clickable { onUpdateStandBy(null, null, id, null) }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("待机亮度 ${(cfg.standByBrightness * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = cfg.standByBrightness,
                onValueChange = { onUpdateStandBy(null, it, null, null) },
                valueRange = 0.04f..0.45f,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("大屏设备显示 640×172 词典笔模拟框（真笔自动全屏）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = cfg.penFramePreviewEnabled, onCheckedChange = onSetPenFramePreview)
        }
    }
}

@Composable
private fun TakeoverAndScanTab(
    state: PenOsUiState,
    onBindScanApp: (String) -> Unit,
    onBindBlockedLauncher: (String) -> Unit,
    onToggleKeepAlive: (Boolean) -> Unit,
    onRootSetHome: () -> Unit,
    onRootFreezeStock: (Boolean) -> Unit,
    onRefreshDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    val colors = PenTheme.colors
    val diag = state.diagnostics
    val cfg = state.config
    var showAppPickerFor by remember { mutableIntStateOf(0) }

    val adbScript = remember(context.packageName, cfg.blockedStockLauncherPackage) {
        SystemTakeoverHelper.buildAdbTakeoverScript(context.packageName, cfg.blockedStockLauncherPackage)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = colors.chipSurface.copy(alpha = 0.55f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "默认桌面: ${if (diag.isPenOsDefaultHome) "PenOS(已接管)" else diag.currentDefaultHomePackage} | 无障碍: ${if (diag.isAccessibilityGuardActive) "运行中" else "未开"} | Root: ${if (diag.isRootAvailable) "已检测" else "无"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "刷新",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.linkAccent,
                    modifier = Modifier.clickable(onClick = onRefreshDiagnostics).padding(horizontal = 6.dp)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { showAppPickerFor = if (showAppPickerFor == 1) 0 else 1 },
                shape = RoundedCornerShape(980.dp),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Text("绑定扫学: ${cfg.scanAppPackage.ifBlank { "未绑定" }}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = { showAppPickerFor = if (showAppPickerFor == 2) 0 else 2 },
                shape = RoundedCornerShape(980.dp),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Text("防锁拦截: ${cfg.blockedStockLauncherPackage.ifBlank { "未选择" }}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (showAppPickerFor != 0) {
            Surface(shape = RoundedCornerShape(10.dp), color = colors.chipSurface.copy(alpha = 0.65f)) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (showAppPickerFor == 1) "选择「学王扫学」对应应用：" else "选择防锁定拦截的原厂桌面：",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primaryAction
                    )
                    state.installedApps.take(12).forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    if (showAppPickerFor == 1) onBindScanApp(app.packageName)
                                    else onBindBlockedLauncher(app.packageName)
                                    showAppPickerFor = 0
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(app.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        }
                    }
                }
            }
        }

        Text("方案 A：无 Root 系统接管与低功耗守护", style = MaterialTheme.typography.labelMedium, color = colors.primaryAction)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { SystemTakeoverHelper.openHomeSettings(context) }, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(34.dp)) {
                Text("设为主屏幕", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = { SystemTakeoverHelper.openAccessibilitySettings(context) }, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(34.dp)) {
                Text("开启无障碍防锁", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("后台常驻", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = cfg.keepAliveEnabled, onCheckedChange = onToggleKeepAlive)
        }

        Text("方案 B：Root 一键接管 / PC 端 ADB 防锁命令", style = MaterialTheme.typography.labelMedium, color = colors.badgeEmber)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onRootSetHome, enabled = diag.isRootAvailable, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(34.dp)) {
                Text("Root 默认桌面", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = { onRootFreezeStock(true) }, enabled = diag.isRootAvailable, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(34.dp)) {
                Text("Root 冻结原厂", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("PenOS ADB", adbScript))
                },
                shape = RoundedCornerShape(980.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("复制 ADB", style = MaterialTheme.typography.labelSmall)
            }
        }

        Surface(shape = RoundedCornerShape(10.dp), color = colors.chipSurface.copy(alpha = 0.6f)) {
            Text(
                text = adbScript,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun SecurityAndSyncTab(
    state: PenOsUiState,
    onSetVaultPin: (String) -> Unit,
    onSaveWebDav: (String, String, String) -> Unit,
    onSyncWebDav: () -> Unit,
    onGenerateSyncCode: () -> Unit,
    onImportSyncCode: (String) -> Unit,
    onExportJsonFile: () -> Unit,
    onImportJsonFile: () -> Unit
) {
    val context = LocalContext.current
    val colors = PenTheme.colors
    val cfg = state.config
    var pinText by remember(cfg.vaultPin) { mutableStateOf(cfg.vaultPin) }
    var davUrl by remember(cfg.webDavUrl) { mutableStateOf(cfg.webDavUrl) }
    var davUser by remember(cfg.webDavUser) { mutableStateOf(cfg.webDavUser) }
    var davPass by remember(cfg.webDavPass) { mutableStateOf(cfg.webDavPass) }
    var importCodeText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("密室安全锁（4 位数字密码）", style = MaterialTheme.typography.labelMedium, color = colors.primaryAction)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = pinText,
                onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) pinText = it },
                label = { Text("4位PIN", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                modifier = Modifier.width(125.dp)
            )
            Button(onClick = { onSetVaultPin(pinText) }, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(36.dp)) {
                Text("保存密码", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = { pinText = ""; onSetVaultPin("") }, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(36.dp)) {
                Text("清除密码", style = MaterialTheme.typography.labelSmall)
            }
        }

        Text("背单词云同步 1：WebDAV（兼容安卓 5.0 无谷歌框架，支持坚果云）", style = MaterialTheme.typography.labelMedium, color = colors.linkAccent)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = davUrl, onValueChange = { davUrl = it }, label = { Text("WebDAV 地址", style = MaterialTheme.typography.labelSmall) }, singleLine = true, modifier = Modifier.weight(1.4f))
            OutlinedTextField(value = davUser, onValueChange = { davUser = it }, label = { Text("账号", style = MaterialTheme.typography.labelSmall) }, singleLine = true, modifier = Modifier.weight(0.8f))
            OutlinedTextField(value = davPass, onValueChange = { davPass = it }, label = { Text("应用密码", style = MaterialTheme.typography.labelSmall) }, singleLine = true, modifier = Modifier.weight(0.8f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { davUrl = "https://dav.jianguoyun.com/dav/PenOS/" }, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(34.dp)) {
                Text("坚果云预设", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = { onSaveWebDav(davUrl, davUser, davPass) }, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(34.dp)) {
                Text("保存配置", style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = { onSaveWebDav(davUrl, davUser, davPass); onSyncWebDav() },
                shape = RoundedCornerShape(980.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("立即云同步", style = MaterialTheme.typography.labelSmall)
            }
        }

        Text("背单词云同步 2：离线同步码 & 本地 JSON 文件", style = MaterialTheme.typography.labelMedium, color = colors.badgeEmber)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onGenerateSyncCode, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(34.dp)) {
                Text("生成同步码", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = onExportJsonFile, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(34.dp)) {
                Text("导出 JSON", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = onImportJsonFile, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(34.dp)) {
                Text("导入 JSON", style = MaterialTheme.typography.labelSmall)
            }
        }

        state.generatedSyncCode?.let { code ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.chipSurface.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("PenOS SyncCode", code))
                    }
            ) {
                Text(
                    text = "点击复制同步码: $code",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primaryAction,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = importCodeText,
                onValueChange = { importCodeText = it },
                label = { Text("粘贴 PENOS1:... 同步码导入", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (importCodeText.isNotBlank()) {
                        onImportSyncCode(importCodeText)
                        importCodeText = ""
                    }
                },
                shape = RoundedCornerShape(980.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text("恢复进度", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
