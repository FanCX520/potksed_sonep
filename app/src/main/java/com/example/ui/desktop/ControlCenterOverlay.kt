package com.example.ui.desktop

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.prefs.PenConfig
import com.example.system.SystemTakeoverHelper
import com.example.system.WhiteNoiseType
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenMotionTier
import com.example.ui.theme.PenTheme
import com.example.ui.theme.springPressScale
import kotlin.math.roundToInt

/**
 * PenOS 4.0 Swipe-Down System Control Center tailored for 640x172 landscape screen.
 */
@Composable
fun ControlCenterOverlay(
    state: PenOsUiState,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onNightDimChange: (Float) -> Unit,
    onToggleWhiteNoise: (WhiteNoiseType) -> Unit,
    onToggleTheme: () -> Unit,
    onOpenAppleTvDrawer: () -> Unit,
    onOpenPomodoro: () -> Unit,
    onOpenMail: () -> Unit,
    onOpenLab: () -> Unit,
    onToggleLanServer: () -> Unit,
    onSetMotionTier: (String) -> Unit,
    onRequestAddNativeWidget: () -> Unit,
    onRollbackLatestSnapshot: () -> Unit = {},
    onRestoreDegradedWallpaper: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = PenTheme.colors
    val isLight = colors.isAppleLight
    val dev = state.deviceControl
    val wn = state.lab.whiteNoise
    val lan = state.lanServer

    val nextMotion = when (state.config.motionTierId) {
        PenMotionTier.FAST.id -> PenMotionTier.SPRING.id
        PenMotionTier.SPRING.id -> PenMotionTier.NONE.id
        else -> PenMotionTier.FAST.id
    }
    val motionLabel = when (state.config.motionTierId) {
        PenMotionTier.FAST.id -> "极速60fps"
        PenMotionTier.SPRING.id -> "轻盈弹簧"
        else -> "零动效"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.overlayScrim)
            .clickable(onClick = onDismiss)
            .padding(8.dp)
            .testTag("overlay_control_center"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(colors.cardCornerRadius),
            color = colors.glassCardElevated,
            border = if (colors.showCardBorder) BorderStroke(1.dp, colors.glassBorderBright) else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left: Brightness, Volume, Night Eye-Care Sliders
                Column(
                    modifier = Modifier
                        .weight(0.47f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("控制中心 · 亮度与音量", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        if (!dev.canWriteSystemBrightness) {
                            Text(
                                text = "授权系统亮度 ›",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.badgeEmber,
                                modifier = Modifier.clickable { SystemTakeoverHelper.openWriteSettingsPermission(context) }
                            )
                        }
                    }

                    CompactControlSlider(
                        icon = Icons.Rounded.Brightness6,
                        label = "亮度 ${(dev.brightnessFraction * 100).roundToInt()}%",
                        value = dev.brightnessFraction,
                        valueRange = 0.06f..1f,
                        tint = colors.standByAmber,
                        onValueChange = onBrightnessChange,
                        tag = "control_brightness_slider"
                    )

                    CompactControlSlider(
                        icon = Icons.Rounded.VolumeUp,
                        label = "音量 ${(dev.mediaVolumeFraction * 100).roundToInt()}%",
                        value = dev.mediaVolumeFraction,
                        valueRange = 0f..1f,
                        tint = colors.primaryAction,
                        onValueChange = onVolumeChange,
                        tag = "control_volume_slider"
                    )

                    CompactControlSlider(
                        icon = Icons.Rounded.NightlightRound,
                        label = "夜间护眼 ${(state.config.nightDimAlpha * 100).roundToInt()}%",
                        value = state.config.nightDimAlpha,
                        valueRange = 0f..0.60f,
                        tint = colors.vocabEmerald,
                        onValueChange = onNightDimChange,
                        tag = "control_night_dim_slider"
                    )
                }

                // Right: PenOS 4.0 Quick Tiles + White Noise Bar
                Column(
                    modifier = Modifier
                        .weight(0.53f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (lan.isRunning) "局域网：${lan.url} · PIN ${lan.pin}" else "快捷中心 · PenOS 5.0",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (lan.isRunning) colors.vocabEmerald else colors.linkAccent,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (state.memoryHealth.isDegraded) {
                                Surface(
                                    shape = RoundedCornerShape(980.dp),
                                    color = colors.standByAmber.copy(alpha = 0.22f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(980.dp))
                                        .clickable(onClick = onRestoreDegradedWallpaper)
                                        .testTag("control_restore_wallpaper_chip")
                                ) {
                                    Text(
                                        text = "恢复壁纸",
                                        fontSize = 9.sp,
                                        color = colors.standByAmber,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (state.recentSnapshots.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(980.dp),
                                    color = colors.dangerRose.copy(alpha = 0.18f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(980.dp))
                                        .clickable(onClick = onRollbackLatestSnapshot)
                                        .testTag("control_rollback_ai_chip")
                                ) {
                                    Text(
                                        text = "撤销AI修改",
                                        fontSize = 9.sp,
                                        color = colors.dangerRose,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "关闭控制中心", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ControlQuickTile(Icons.Rounded.Language, if (lan.isRunning) "局域网开" else "局域网后台", lan.isRunning, false, Modifier.weight(1f), onToggleLanServer)
                        ControlQuickTile(Icons.Rounded.ViewCarousel, "TV抽屉", false, false, Modifier.weight(1f), onOpenAppleTvDrawer)
                        ControlQuickTile(Icons.Rounded.Mail, if (state.mail.unreadCount > 0) "邮箱(${state.mail.unreadCount})" else "笔端邮箱", false, state.mail.unreadCount > 0, Modifier.weight(1f), onOpenMail)
                        ControlQuickTile(Icons.Rounded.Timer, "全屏番茄", state.pomodoro.isRunning, false, Modifier.weight(1f), onOpenPomodoro)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ControlQuickTile(Icons.Rounded.Speed, motionLabel, state.config.motionTierId == PenMotionTier.FAST.id, false, Modifier.weight(1f)) { onSetMotionTier(nextMotion) }
                        ControlQuickTile(if (isLight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, if (isLight) "切暗黑" else "切白画廊", isLight, false, Modifier.weight(1f), onToggleTheme)
                        ControlQuickTile(Icons.Rounded.Science, "AI实验室", false, false, Modifier.weight(1f), onOpenLab)
                        ControlQuickTile(Icons.Rounded.Widgets, "+原生件", state.config.nativeWidgetIds.isNotEmpty(), false, Modifier.weight(1f)) {
                            onDismiss()
                            onRequestAddNativeWidget()
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(WhiteNoiseType.entries) { type ->
                            val playing = wn.isPlaying && wn.activeType == type
                            Surface(
                                shape = RoundedCornerShape(980.dp),
                                color = if (playing) colors.primaryAction else colors.chipSurface,
                                modifier = Modifier
                                    .springPressScale { onToggleWhiteNoise(type) }
                                    .clip(RoundedCornerShape(980.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (playing) Icons.Rounded.GraphicEq else Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (playing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = type.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = if (playing) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactControlSlider(
    icon: ImageVector,
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    tint: Color,
    onValueChange: (Float) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(68.dp)
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = tint,
                activeTrackColor = tint,
                inactiveTrackColor = PenTheme.colors.chipSurface
            ),
            modifier = Modifier
                .weight(1f)
                .testTag(tag)
        )
    }
}

@Composable
private fun ControlQuickTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    showRedDot: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = PenTheme.colors
    Surface(
        modifier = modifier
            .height(30.dp)
            .springPressScale(onClick = onClick)
            .clip(RoundedCornerShape(9.dp)),
        shape = RoundedCornerShape(9.dp),
        color = if (active) colors.primaryAction else colors.chipSurface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            if (showRedDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 4.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .testTag("control_mail_unread_dot")
                )
            }
        }
    }
}
