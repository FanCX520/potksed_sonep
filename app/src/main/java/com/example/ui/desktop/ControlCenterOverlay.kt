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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Wifi
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
import com.example.system.SystemTakeoverHelper
import com.example.system.WhiteNoiseType
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme
import com.example.ui.theme.springPressScale
import kotlin.math.roundToInt

/**
 * Swipe-Down System Control Center tailored for 640x172 dictionary pen screen:
 * Left: Brightness + Media Volume + Night Eye-Care sliders
 * Right: White Noise quick toggles + Wi-Fi / System Settings / Lab AI / Pomodoro / Theme / Native Widget shortcuts
 */
@Composable
fun ControlCenterOverlay(
    state: PenOsUiState,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onNightDimChange: (Float) -> Unit,
    onToggleWhiteNoise: (WhiteNoiseType) -> Unit,
    onToggleTheme: () -> Unit,
    onOpenPomodoro: () -> Unit,
    onOpenLab: () -> Unit,
    onRequestAddNativeWidget: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = PenTheme.colors
    val isLight = colors.isAppleLight
    val dev = state.deviceControl
    val wn = state.lab.whiteNoise

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
                // Left: Real Brightness, Volume, Night Eye-Care Sliders
                Column(
                    modifier = Modifier
                        .weight(0.50f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "控制中心 · 亮度与音量",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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

                // Right: Quick Tiles + White Noise Bar
                Column(
                    modifier = Modifier
                        .weight(0.50f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "快捷开关 & 白噪音",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.linkAccent
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "关闭控制中心", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }

                    // 2 rows x 3 quick tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ControlQuickTile(
                            icon = Icons.Rounded.Wifi,
                            label = if (state.clock.isWifiConnected) "Wi-Fi 已连" else "Wi-Fi 设置",
                            active = state.clock.isWifiConnected,
                            modifier = Modifier.weight(1f),
                            onClick = { SystemTakeoverHelper.openWifiSettings(context) }
                        )
                        ControlQuickTile(
                            icon = Icons.Rounded.Timer,
                            label = "全屏番茄",
                            active = state.pomodoro.isRunning,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenPomodoro
                        )
                        ControlQuickTile(
                            icon = Icons.Rounded.Science,
                            label = "AI 实验室",
                            active = false,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenLab
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ControlQuickTile(
                            icon = if (isLight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                            label = if (isLight) "切暗黑" else "切白画廊",
                            active = isLight,
                            modifier = Modifier.weight(1f),
                            onClick = onToggleTheme
                        )
                        ControlQuickTile(
                            icon = Icons.Rounded.Widgets,
                            label = "+原生组件",
                            active = state.config.nativeWidgetIds.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onDismiss()
                                onRequestAddNativeWidget()
                            }
                        )
                        ControlQuickTile(
                            icon = Icons.Rounded.Settings,
                            label = "系统设置",
                            active = false,
                            modifier = Modifier.weight(1f),
                            onClick = { SystemTakeoverHelper.openSystemSettings(context) }
                        )
                    }

                    // Offline DSP White Noise quick chips
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = PenTheme.colors
    Surface(
        modifier = modifier
            .height(32.dp)
            .springPressScale(onClick = onClick)
            .clip(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = if (active) colors.primaryAction else colors.chipSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
