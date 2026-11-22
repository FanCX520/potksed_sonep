package com.example.ui.lab

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.prefs.MultiClockConfig
import com.example.data.prefs.PenConfig
import com.example.system.QrCodeCanvas
import com.example.ui.PenOsUiState
import com.example.ui.theme.FontSlotSpec
import com.example.ui.theme.PenFontCatalog
import com.example.ui.theme.PenMotionTier
import com.example.ui.theme.PenTheme
import com.example.widgets.MultiStyleClockCard
import kotlin.math.roundToInt

/**
 * Tab: Built-In LAN Web Admin Console (QR + IP + 6-digit PIN + Start/Stop).
 */
@Composable
fun LabLanAdminTab(
    state: PenOsUiState,
    onToggleLanServer: () -> Unit,
    onRegeneratePin: () -> Unit,
    onReopenSetupAssistant: () -> Unit,
    onExportFullConfigJson: () -> Unit = {},
    onImportFullConfigJson: () -> Unit = {},
    onSyncFullConfigWebDav: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = PenTheme.colors
    val lan = state.lanServer
    val adbCmd = "adb forward tcp:${lan.port} tcp:${lan.port}"

    Row(
        modifier = Modifier
            .fillMaxSize()
            .testTag("lab_lan_admin_tab"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (lan.isRunning && lan.url.isNotBlank()) {
            QrCodeCanvas(
                content = lan.url,
                modifier = Modifier.size(116.dp)
            )
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.chipSurface,
                modifier = Modifier.size(104.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Language, contentDescription = null, tint = colors.primaryAction, modifier = Modifier.size(38.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (lan.isRunning) "局域网网页控制台已运行" else "局域网网页控制台（未开启）",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (lan.isRunning) colors.vocabEmerald else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (lan.isRunning) "浏览器打开：${lan.url}" else "同 Wi-Fi 扫码配置 AI/邮箱/壁纸/字体/MCP，学校隔离时可用下方 3 重兜底",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 2
                )
                if (lan.isRunning) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp), color = colors.primaryAction.copy(alpha = 0.16f)) {
                            Text(
                                text = "6 位配对码：${lan.pin}",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = PenTheme.clockFont),
                                color = colors.primaryAction,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.chipSurface,
                            modifier = Modifier.clickable(onClick = onRegeneratePin)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "刷新配对码", modifier = Modifier.size(11.dp))
                                Text("刷新PIN", fontSize = 9.sp)
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onToggleLanServer,
                    shape = RoundedCornerShape(980.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (lan.isRunning) colors.dangerRose else colors.primaryAction
                    ),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("lab_lan_toggle_button")
                ) {
                    Text(if (lan.isRunning) "关闭服务" else "一键启动局域网", fontSize = 10.sp)
                }
                Surface(
                    shape = RoundedCornerShape(980.dp),
                    color = colors.chipSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(980.dp))
                        .clickable(onClick = onReopenSetupAssistant)
                ) {
                    Text(
                        "重跑设置向导",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            // PenOS 5.0 Campus Wi-Fi AP-Isolation Triple Fallback Bar
            Text(
                text = "校园 Wi-Fi 隔离 3 重兜底（热点直连 · USB 转发 · 脱敏备份/WebDAV）",
                fontSize = 9.sp,
                color = colors.linkAccent,
                fontWeight = FontWeight.SemiBold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item(key = "fallback_hotspot") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.chipSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                    ) {
                        Text("1·连手机热点", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                }
                item(key = "fallback_adb_usb") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.primaryAction.copy(alpha = 0.16f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("PenOS ADB Forward", "$adbCmd  # 电脑访问 http://127.0.0.1:${lan.port}"))
                            }
                            .testTag("lab_copy_adb_forward_btn")
                    ) {
                        Text("2·复制ADB USB转发", fontSize = 9.sp, color = colors.primaryAction, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                }
                item(key = "fallback_export_cfg") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.chipSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onExportFullConfigJson)
                            .testTag("lab_export_full_config_btn")
                    ) {
                        Text("3·导出配置JSON", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                }
                item(key = "fallback_import_cfg") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.chipSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onImportFullConfigJson)
                            .testTag("lab_import_full_config_btn")
                    ) {
                        Text("导入配置JSON", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                }
                item(key = "fallback_webdav_cfg") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.vocabEmerald.copy(alpha = 0.18f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onSyncFullConfigWebDav)
                            .testTag("lab_webdav_full_config_btn")
                    ) {
                        Text("WebDAV全量同步", fontSize = 9.sp, color = colors.vocabEmerald, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                }
            }
            Text(
                text = "USB 命令：$adbCmd → 电脑打开 http://127.0.0.1:${lan.port}",
                fontSize = 8.sp,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Tab: 5-Slot Granular Global Dynamic Typography Studio.
 */
@Composable
fun LabFontStudioTab(
    state: PenOsUiState,
    onUpdateFontSlot: (String, FontSlotSpec) -> Unit,
    onPickFontForSlot: (String) -> Unit,
    onResetFonts: () -> Unit,
    onSetNightDimAlpha: (Float) -> Unit,
    onSetLowMemMode: (Boolean) -> Unit
) {
    val colors = PenTheme.colors
    var selectedSlot by remember { mutableStateOf("clock") }
    val slotLabels = listOf(
        "clock" to "时钟数字",
        "title" to "标题卡片",
        "body" to "正文列表",
        "poetry" to "古诗词",
        "metric" to "标签徽章"
    )
    val currentSpec = state.config.typographyProfile.slotByName(selectedSlot)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("lab_font_studio_tab"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Slot selector + Reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(slotLabels) { (key, label) ->
                    val active = selectedSlot == key
                    Surface(
                        shape = RoundedCornerShape(980.dp),
                        color = if (active) colors.primaryAction else colors.chipSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(980.dp))
                            .clickable { selectedSlot = key }
                    ) {
                        Text(
                            label,
                            fontSize = 10.sp,
                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.chipSurface,
                modifier = Modifier.clickable { onPickFontForSlot(selectedSlot) }
            ) {
                Text("导入TTF", fontSize = 9.sp, color = colors.linkAccent, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.chipSurface,
                modifier = Modifier.clickable(onClick = onResetFonts)
            ) {
                Icon(Icons.Rounded.Restore, contentDescription = "重置字体", modifier = Modifier.padding(3.dp).size(13.dp))
            }
        }

        // Built-in font family picker for active slot
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(PenFontCatalog.allFamilies) { (familyKey, familyLabel) ->
                val active = currentSpec.familyKey == familyKey
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (active) colors.vocabEmerald else colors.chipSurface,
                    modifier = Modifier.clickable {
                        onUpdateFontSlot(selectedSlot, currentSpec.copy(familyKey = familyKey))
                    }
                ) {
                    Text(
                        familyLabel,
                        fontSize = 9.sp,
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Scale + LetterSpacing + Weight compact controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("缩放 ${"%.2f".format(currentSpec.scale)}x", fontSize = 9.sp, modifier = Modifier.width(56.dp))
            Slider(
                value = currentSpec.scale,
                onValueChange = { onUpdateFontSlot(selectedSlot, currentSpec.copy(scale = it)) },
                valueRange = 0.75f..1.45f,
                modifier = Modifier.weight(1f).height(22.dp)
            )
            listOf(400 to "常规", 600 to "中粗", 700 to "加粗").forEach { (w, lbl) ->
                val act = currentSpec.weight == w
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (act) colors.primaryAction else colors.chipSurface,
                    modifier = Modifier.clickable { onUpdateFontSlot(selectedSlot, currentSpec.copy(weight = w)) }
                ) {
                    Text(lbl, fontSize = 9.sp, color = if (act) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("字距 ${"%.1f".format(currentSpec.letterSpacingSp)}", fontSize = 9.sp, modifier = Modifier.width(56.dp))
            Slider(
                value = currentSpec.letterSpacingSp,
                onValueChange = { onUpdateFontSlot(selectedSlot, currentSpec.copy(letterSpacingSp = it)) },
                valueRange = -1.5f..2.5f,
                modifier = Modifier.weight(1f).height(22.dp)
            )
            Text("护眼 ${(state.config.nightDimAlpha * 100).roundToInt()}%", fontSize = 9.sp)
            Slider(
                value = state.config.nightDimAlpha,
                onValueChange = onSetNightDimAlpha,
                valueRange = 0f..0.60f,
                modifier = Modifier.width(76.dp).height(22.dp)
            )
            Switch(checked = state.config.lowMemMode, onCheckedChange = onSetLowMemMode, modifier = Modifier.height(22.dp))
        }
    }
}

/**
 * Tab: 4-Mode Switchable Dynamic Wallpaper + 6-Style Multi-Clock + 60fps Motion Tier Studio.
 */
@Composable
fun LabWallpaperClockTab(
    state: PenOsUiState,
    onSetDynamicWallpaperMode: (String, String?) -> Unit,
    onPickGifWallpaper: () -> Unit,
    onPickMp4Wallpaper: () -> Unit,
    onOpenSystemLiveWallpaperChooser: () -> Unit,
    onSetClockStyle: (String?, String?, Boolean?) -> Unit,
    onCycleClockStyle: () -> Unit,
    onSetMotionTier: (String) -> Unit,
    onSetSwipeSensitivityDp: (Float) -> Unit = {},
    onSetGestureHapticsEnabled: (Boolean) -> Unit = {},
    onSetMailAutoSyncEnabled: (Boolean) -> Unit = {},
    onRestoreDegradedWallpaper: () -> Unit = {}
) {
    val colors = PenTheme.colors
    val cfg = state.config

    Row(
        modifier = Modifier
            .fillMaxSize()
            .testTag("lab_wallpaper_clock_tab"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left: Live Multi-Style Clock Preview
        MultiStyleClockCard(
            clockConfig = cfg.multiClock,
            onCycleStyle = onCycleClockStyle,
            modifier = Modifier
                .width(142.dp)
                .fillMaxHeight()
        )

        // Right: 4-Mode Dynamic Wallpaper + 6-Style Clock + Motion Tier
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 4-Mode Dynamic Wallpaper Row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Wallpaper, contentDescription = null, tint = colors.primaryAction, modifier = Modifier.size(13.dp))
                Text("动态壁纸 4 模式自由切换：", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val modes = listOf(
                    PenConfig.DYN_WALLPAPER_STATIC to "静态壁纸",
                    PenConfig.DYN_WALLPAPER_PROCEDURAL to "1·代码极光",
                    PenConfig.DYN_WALLPAPER_GIF to "2·本地GIF",
                    PenConfig.DYN_WALLPAPER_MP4 to "3·本地MP4",
                    PenConfig.DYN_WALLPAPER_LIVE to "4·系统动态"
                )
                items(modes) { (mode, label) ->
                    val act = cfg.dynamicWallpaperMode == mode
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (act) colors.primaryAction else colors.chipSurface,
                        modifier = Modifier.clickable {
                            when (mode) {
                                PenConfig.DYN_WALLPAPER_GIF -> {
                                    onSetDynamicWallpaperMode(mode, null)
                                    onPickGifWallpaper()
                                }
                                PenConfig.DYN_WALLPAPER_MP4 -> {
                                    onSetDynamicWallpaperMode(mode, null)
                                    onPickMp4Wallpaper()
                                }
                                PenConfig.DYN_WALLPAPER_LIVE -> {
                                    onSetDynamicWallpaperMode(mode, null)
                                    onOpenSystemLiveWallpaperChooser()
                                }
                                else -> onSetDynamicWallpaperMode(mode, null)
                            }
                        }
                    ) {
                        Text(label, fontSize = 9.sp, color = if (act) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                }
            }

            if (cfg.dynamicWallpaperMode == PenConfig.DYN_WALLPAPER_PROCEDURAL) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val procList = listOf(
                        PenConfig.PROC_AURORA_FLOW to "极光星云",
                        PenConfig.PROC_STAR_TRAILS to "深空星轨",
                        PenConfig.PROC_SOFT_RAIN to "细雨斜丝",
                        PenConfig.PROC_GALLERY_BREEZE to "柔光微风"
                    )
                    items(procList) { (id, label) ->
                        val act = cfg.proceduralWallpaperStyle == id
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (act) colors.vocabEmerald else colors.chipSurface,
                            modifier = Modifier.clickable { onSetDynamicWallpaperMode(PenConfig.DYN_WALLPAPER_PROCEDURAL, id) }
                        ) {
                            Text(label, fontSize = 9.sp, color = if (act) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                    }
                }
            }

            // 6-Style Clock Selector
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(MultiClockConfig.allStyles) { (id, label) ->
                    val act = cfg.multiClock.style == id
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = if (act) colors.primaryAction else colors.chipSurface,
                        modifier = Modifier.clickable { onSetClockStyle(id, null, null) }
                    ) {
                        Text(label, fontSize = 9.sp, color = if (act) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                    }
                }
            }

            // Motion Tier 60fps Row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("动效流畅度：", fontSize = 9.sp, color = colors.textMuted)
                PenMotionTier.entries.forEach { tier ->
                    val act = cfg.motionTierId == tier.id
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = if (act) colors.vocabEmerald else colors.chipSurface,
                        modifier = Modifier.clickable { onSetMotionTier(tier.id) }
                    ) {
                        Text(tier.label, fontSize = 9.sp, color = if (act) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
            }

            // PenOS 5.0 Low-RAM Auto-Downgrade Banner + 1-Tap Restore
            if (state.memoryHealth.isDegraded) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.standByAmber.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, colors.standByAmber.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "低内存降级(${state.memoryHealth.availRamMb}MB)：已暂切静态，原为 ${state.memoryHealth.previousWallpaperMode.ifBlank { "动态壁纸" }}",
                            fontSize = 9.sp,
                            color = colors.standByAmber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(980.dp),
                            color = colors.standByAmber,
                            modifier = Modifier
                                .clip(RoundedCornerShape(980.dp))
                                .clickable(onClick = onRestoreDegradedWallpaper)
                                .testTag("lab_restore_degraded_wallpaper_btn")
                        ) {
                            Text("一键恢复", fontSize = 9.sp, color = Color(0xFF1A1405), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            // PenOS 5.0 Two-Stage Swipe Sensitivity + Haptic Pulse + 15m Wi-Fi Mail Sync
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("上滑阈值 ${cfg.swipeSensitivityDp.roundToInt()}dp", fontSize = 9.sp, color = colors.textMuted)
                Slider(
                    value = cfg.swipeSensitivityDp.coerceIn(20f, 72f),
                    onValueChange = onSetSwipeSensitivityDp,
                    valueRange = 20f..72f,
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .testTag("lab_swipe_sensitivity_slider")
                )
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = if (cfg.gestureHapticsEnabled) colors.primaryAction else colors.chipSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .clickable { onSetGestureHapticsEnabled(!cfg.gestureHapticsEnabled) }
                        .testTag("lab_gesture_haptics_toggle")
                ) {
                    Text(
                        text = if (cfg.gestureHapticsEnabled) "震动开" else "震动关",
                        fontSize = 9.sp,
                        color = if (cfg.gestureHapticsEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = if (cfg.mailAutoSyncEnabled) colors.vocabEmerald else colors.chipSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .clickable { onSetMailAutoSyncEnabled(!cfg.mailAutoSyncEnabled) }
                        .testTag("lab_mail_autosync_toggle")
                ) {
                    Text(
                        text = if (cfg.mailAutoSyncEnabled) "15m收信开" else "自动收信关",
                        fontSize = 9.sp,
                        color = if (cfg.mailAutoSyncEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
