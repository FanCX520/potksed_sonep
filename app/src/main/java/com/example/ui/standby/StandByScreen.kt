package com.example.ui.standby

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.FocusSubMode
import com.example.ui.LocalClockFlow
import com.example.ui.PenOsUiState
import com.example.ui.PomodoroState
import com.example.ui.components.MonoChip
import com.example.ui.components.MonoOutlineButton
import com.example.ui.components.MonoPrimaryButton
import com.example.ui.components.MonoTopBar
import com.example.ui.theme.ClockDisplayFont
import com.example.ui.theme.PenTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * PenOS 6.0 Unified StandBy Giant Clock & Pomodoro Focus Screen.
 * Zero 60fps animation loops — redraws strictly once per second on isolated leaf composables.
 */
@Composable
fun StandByScreen(
    state: PenOsUiState,
    pomodoroFlow: StateFlow<PomodoroState>,
    onBack: () -> Unit,
    onSelectSubMode: (FocusSubMode) -> Unit,
    onTogglePomodoro: () -> Unit,
    onResetPomodoro: () -> Unit,
    onCyclePomodoroDuration: () -> Unit,
    onCycleStandbyBrightness: () -> Unit
) {
    BackHandler(onBack = onBack)

    val palette = PenTheme.palette
    val pomo by pomodoroFlow.collectAsStateWithLifecycle()
    val isCompactStrip = LocalConfiguration.current.screenHeightDp in 1..280

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .safeDrawingPadding()
    ) {
        MonoTopBar(
            title = if (pomo.subMode == FocusSubMode.STANDBY) "待机大时钟" else "番茄专注",
            subtitle = if (pomo.subMode == FocusSubMode.STANDBY) {
                "低功耗常亮 · 亮度 ${(state.config.standbyBrightness * 100).toInt()}%"
            } else {
                "今日完成 ${pomo.completedSessions} 轮"
            },
            onBack = onBack,
            trailing = {
                MonoChip(
                    label = "待机",
                    selected = pomo.subMode == FocusSubMode.STANDBY,
                    onClick = { onSelectSubMode(FocusSubMode.STANDBY) },
                    testTag = "focus_tab_standby"
                )
                MonoChip(
                    label = "番茄",
                    selected = pomo.subMode == FocusSubMode.POMODORO,
                    onClick = { onSelectSubMode(FocusSubMode.POMODORO) },
                    testTag = "focus_tab_pomodoro"
                )
            }
        )

        if (pomo.subMode == FocusSubMode.STANDBY) {
            StandByClockStage(
                batteryPercent = state.batteryPercent,
                isCharging = state.isCharging,
                isCompactStrip = isCompactStrip,
                onCycleBrightness = onCycleStandbyBrightness,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else {
            PomodoroStage(
                pomo = pomo,
                isCompactStrip = isCompactStrip,
                onToggle = onTogglePomodoro,
                onReset = onResetPomodoro,
                onCycleDuration = onCyclePomodoroDuration,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StandByClockStage(
    batteryPercent: Int,
    isCharging: Boolean,
    isCompactStrip: Boolean,
    onCycleBrightness: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = PenTheme.palette
    val clock by LocalClockFlow.current.collectAsStateWithLifecycle()
    val widthDp = LocalConfiguration.current.screenWidthDp
    val sizeSp = when {
        isCompactStrip -> 64.sp
        widthDp in 1..360 -> 68.sp
        else -> 92.sp
    }

    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .clickable(onClick = onCycleBrightness)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = clock.hour,
                fontFamily = ClockDisplayFont,
                fontWeight = FontWeight.Light,
                fontSize = sizeSp,
                lineHeight = sizeSp,
                letterSpacing = (-2.5).sp,
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                color = palette.ink
            )
            Text(
                text = ":",
                fontFamily = ClockDisplayFont,
                fontWeight = FontWeight.Normal,
                fontSize = sizeSp,
                lineHeight = sizeSp,
                color = palette.accent,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            Text(
                text = clock.minute,
                fontFamily = ClockDisplayFont,
                fontWeight = FontWeight.Light,
                fontSize = sizeSp,
                lineHeight = sizeSp,
                letterSpacing = (-2.5).sp,
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                color = palette.ink
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = clock.second,
                fontFamily = ClockDisplayFont,
                fontWeight = FontWeight.Normal,
                fontSize = if (isCompactStrip || widthDp in 1..360) 20.sp else 26.sp,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = palette.inkMuted,
                modifier = Modifier.padding(bottom = if (isCompactStrip) 8.dp else 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(if (isCompactStrip) 4.dp else 12.dp))
        Text(
            text = "${clock.dateLine} · ${if (isCharging) "充电 " else ""}$batteryPercent% · 轻点调亮度",
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = palette.inkMuted,
            modifier = Modifier.testTag("standby_status_line")
        )
    }
}

@Composable
private fun PomodoroStage(
    pomo: PomodoroState,
    isCompactStrip: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onCycleDuration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = PenTheme.palette
    val mins = (pomo.remainingSeconds / 60).toString().padStart(2, '0')
    val secs = (pomo.remainingSeconds % 60).toString().padStart(2, '0')
    val progress = if (pomo.totalSeconds > 0) {
        pomo.remainingSeconds.toFloat() / pomo.totalSeconds.toFloat()
    } else 1f

    if (isCompactStrip) {
        // Ultra-clean horizontal layout for 640x172 dictionary pen strip
        Row(
            modifier = modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (pomo.isBreak) "短休中" else "深度专注",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent
                )
                Text(
                    text = "$mins:$secs",
                    fontFamily = ClockDisplayFont,
                    fontWeight = FontWeight.Light,
                    fontSize = 56.sp,
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                    color = palette.ink,
                    modifier = Modifier.testTag("pomo_timer_text")
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoPrimaryButton(
                    text = if (pomo.isRunning) "暂停" else "开始",
                    useAccent = !pomo.isRunning,
                    onClick = onToggle,
                    testTag = "pomo_toggle"
                )
                MonoOutlineButton(text = "${pomo.totalSeconds / 60}m", onClick = onCycleDuration, testTag = "pomo_duration")
                MonoOutlineButton(text = "重置", onClick = onReset, testTag = "pomo_reset")
            }
        }
    } else {
        Column(
            modifier = modifier
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(184.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 4.dp.toPx()
                    val diameter = size.minDimension - strokePx
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arcSize = Size(diameter, diameter)
                    drawArc(
                        color = palette.hairline,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx)
                    )
                    drawArc(
                        color = palette.accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (pomo.isBreak) "休息时间" else "专注倒计时",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accent
                    )
                    Text(
                        text = "$mins:$secs",
                        fontFamily = ClockDisplayFont,
                        fontWeight = FontWeight.Light,
                        fontSize = 46.sp,
                        lineHeight = 52.sp,
                        style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                        color = palette.ink,
                        modifier = Modifier.testTag("pomo_timer_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoOutlineButton(
                    text = "${pomo.totalSeconds / 60}m",
                    onClick = onCycleDuration,
                    testTag = "pomo_duration"
                )
                MonoPrimaryButton(
                    text = if (pomo.isRunning) "暂停专注" else "开始专注",
                    useAccent = !pomo.isRunning,
                    onClick = onToggle,
                    testTag = "pomo_toggle"
                )
                MonoOutlineButton(
                    text = "重置",
                    onClick = onReset,
                    testTag = "pomo_reset"
                )
            }
        }
    }
}
