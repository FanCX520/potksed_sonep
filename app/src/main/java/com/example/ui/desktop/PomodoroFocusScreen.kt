package com.example.ui.desktop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.system.PenDynamicWallpaperBackdrop
import com.example.system.WhiteNoiseType
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme
import com.example.ui.theme.springPressScale
import com.example.widgets.LocalClockTickFlow

/**
 * Full-Screen Wallpaper Pomodoro Focus Mode (triggered by Swiping Up on Desktop / StandBy):
 * - Full-bleed independent theme clock wallpaper (Dark StandBy vs Apple White Gallery)
 * - Huge centered unified clock (PenTheme.clockFont)
 * - Compact bottom-left Pomodoro countdown capsule (play/pause, duration preset cycle, break toggle, reset)
 * - Compact bottom-right White Noise quick toggle
 * - Swipe Down anywhere or tap top-right close to exit back to Desktop
 */
@Composable
fun PomodoroFocusScreen(
    state: PenOsUiState,
    onToggleRunning: () -> Unit,
    onReset: () -> Unit,
    onCycleDuration: () -> Unit,
    onToggleBreak: () -> Unit,
    onToggleWhiteNoise: (WhiteNoiseType) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onExit)

    val colors = PenTheme.colors
    val isLight = colors.isAppleLight
    val pomo = state.pomodoro
    val wn = state.lab.whiteNoise
    val clockFont = PenTheme.clockFont
    val liveClock by LocalClockTickFlow.current.collectAsStateWithLifecycle()
    var dragAccum by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onVerticalDrag = { _, dragAmount -> dragAccum += dragAmount },
                    onDragEnd = {
                        if (dragAccum > 36f) onExit()
                        dragAccum = 0f
                    }
                )
            }
            .testTag("pomodoro_focus_screen")
    ) {
        // 1. Full-bleed 4-Mode Dynamic / Theme Wallpaper Backdrop
        PenDynamicWallpaperBackdrop(
            config = state.config,
            isAppleLight = isLight,
            forFullScreenClock = true
        )

        // Subtle readability scrim so centered clock is always crisp over any custom photo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isLight) {
                        Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color(0x55FFFFFF), Color(0x44F3F6F6)))
                    } else {
                        Brush.verticalGradient(listOf(Color(0x55000000), Color(0x33000000), Color(0x88000000)))
                    }
                )
        )

        // 2. Top-left subtle mode badge + Top-right exit button
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(980.dp),
                color = if (isLight) Color(0xCCFFFFFF) else Color(0x77000000)
            ) {
                Text(
                    text = if (pomo.isBreakMode) "休息放松 · 下滑返回" else "番茄专注 · 已完成 ${pomo.completedSessions} 轮",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isLight) Color(0xFF1D1D1F) else Color(0xFFE5E7EB),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(28.dp)
                .springPressScale(onClick = onExit)
                .clip(CircleShape)
                .testTag("pomodoro_exit_button"),
            shape = CircleShape,
            color = if (isLight) Color(0xCCFFFFFF) else Color(0x77000000)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "退出专注模式",
                    tint = if (isLight) Color(0xFF1D1D1F) else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 3. Giant Centered Unified Clock (decoupled 1s clock tick)
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = liveClock.timeHHmm,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = clockFont,
                    fontSize = 64.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp
                ),
                color = if (isLight) Color(0xFF1D1D1F) else Color(0xFFF9FAFB)
            )
            Text(
                text = "${liveClock.dateLabel} · 电量 ${liveClock.batteryPercent}%",
                style = MaterialTheme.typography.labelMedium,
                color = if (isLight) Color(0xFF424245) else Color(0xFFD1D5DB)
            )
        }

        // 4. Bottom-Left Compact Pomodoro Countdown Pill
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 8.dp)
                .testTag("pomodoro_bottom_left_pill"),
            shape = RoundedCornerShape(16.dp),
            color = if (isLight) Color(0xE8FFFFFF) else Color(0xB80F141E),
            border = BorderStroke(
                1.dp,
                if (pomo.isRunning) colors.primaryAction.copy(alpha = 0.65f) else colors.glassBorder
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = if (pomo.isBreakMode) colors.vocabEmerald else colors.primaryAction,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = pomo.formattedRemaining,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = clockFont,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isLight) Color(0xFF1D1D1F) else Color.White,
                        modifier = Modifier.clickable(onClick = onCycleDuration)
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (pomo.isRunning) colors.badgeEmber else colors.primaryAction,
                        modifier = Modifier
                            .size(26.dp)
                            .springPressScale(onClick = onToggleRunning)
                            .clip(CircleShape)
                            .testTag("pomodoro_play_pause")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (pomo.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (pomo.isRunning) "暂停" else "开始",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colors.chipSurface)
                            .clickable(onClick = onReset),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "重置番茄钟",
                            tint = if (isLight) Color(0xFF1D1D1F) else Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = if (pomo.isBreakMode) "专注" else "小憩",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.linkAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onToggleBreak)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                LinearProgressIndicator(
                    progress = { pomo.progressFraction },
                    modifier = Modifier
                        .width(172.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(980.dp)),
                    color = if (pomo.isBreakMode) colors.vocabEmerald else colors.primaryAction,
                    trackColor = if (isLight) Color(0xFFE5E7EB) else Color.White.copy(alpha = 0.14f)
                )
            }
        }

        // 5. Bottom-Right Compact White Noise Pill
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 10.dp)
                .springPressScale {
                    if (wn.isPlaying) {
                        val all = WhiteNoiseType.entries
                        val next = all[(all.indexOf(wn.activeType) + 1) % all.size]
                        onToggleWhiteNoise(next)
                    } else {
                        onToggleWhiteNoise(wn.activeType)
                    }
                }
                .clip(RoundedCornerShape(980.dp)),
            shape = RoundedCornerShape(980.dp),
            color = if (wn.isPlaying) colors.primaryAction else if (isLight) Color(0xDDFFFFFF) else Color(0xAA0F141E),
            border = BorderStroke(1.dp, if (wn.isPlaying) Color.Transparent else colors.glassBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "白噪音",
                    tint = if (wn.isPlaying) Color.White else if (isLight) Color(0xFF1D1D1F) else Color(0xFFD1D5DB),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (wn.isPlaying) "${wn.activeType.label} · 切音" else "开启白噪音",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (wn.isPlaying) Color.White else if (isLight) Color(0xFF1D1D1F) else Color(0xFFD1D5DB)
                )
            }
        }
    }
}
