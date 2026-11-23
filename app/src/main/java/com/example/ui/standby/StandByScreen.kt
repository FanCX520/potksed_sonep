package com.example.ui.standby

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.prefs.PenConfig
import com.example.data.remote.WmoWeatherMapper
import com.example.ui.PenOsUiState
import com.example.ui.desktop.SharedThemeWallpaperBackdrop
import com.example.ui.desktop.SwipeDirectionClassifier
import com.example.ui.desktop.SwipeDirectionDecision
import com.example.ui.theme.AppleBlue
import com.example.ui.theme.AppleEmber
import com.example.ui.theme.AppleFogMist
import com.example.ui.theme.AppleGraphite
import com.example.ui.theme.AppleNearBlack
import com.example.ui.theme.CarPlayGlacier
import com.example.ui.theme.LocalPenMotionTier
import com.example.ui.theme.PenMotion
import com.example.ui.theme.PenTheme
import com.example.ui.theme.StandByAmber
import com.example.ui.theme.StandByCoral
import com.example.widgets.LocalClockTickFlow
import com.example.widgets.wmoWeatherIcon
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StandByScreen(
    state: PenOsUiState,
    onWakeToDesktop: () -> Unit,
    onSwipeUpToPomodoro: () -> Unit,
    onCycleRightWidget: (Int) -> Unit,
    onTogglePomodoro: () -> Unit,
    onClockTap: () -> Unit,
    onClockLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onWakeToDesktop)

    val isLightStandBy = state.config.isAppleLightStandBy()
    val clockFont = PenTheme.clockFont
    val poetryFont = PenTheme.poetryFont

    val cardColor = if (isLightStandBy) Color(0xDDF3F6F6) else Color(0xCC0A0C10)
    val clockColor = if (isLightStandBy) {
        AppleNearBlack
    } else {
        when (state.config.standByClockColor) {
            PenConfig.CLOCK_COLOR_AMBER -> StandByAmber
            PenConfig.CLOCK_COLOR_GLACIER -> CarPlayGlacier
            PenConfig.CLOCK_COLOR_IVORY -> Color(0xFFF3EFE6)
            else -> StandByCoral
        }
    }
    val secondaryInk = if (isLightStandBy) AppleGraphite else Color(0xFF7B818E)
    val bodyInk = if (isLightStandBy) AppleNearBlack else Color(0xFFEAE6DF)
    val accent = if (isLightStandBy) AppleBlue else clockColor
    val badgeInk = if (isLightStandBy) AppleEmber else clockColor.copy(alpha = 0.85f)

    val motionTier = LocalPenMotionTier.current
    // Subtle burn-in shift computed from slow state.clock (updates once per minute, NOT every second!)
    val minuteHash = state.clock.timeHHmm.lastOrNull()?.digitToIntOrNull() ?: 0
    val shiftX = ((minuteHash % 3) - 1).dp
    val shiftY = (((minuteHash / 3) % 3) - 1).dp

    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    var lastSlideDir by remember { mutableIntStateOf(1) }

    val activePaneKey = state.config.standByRightWidget
    val paneIndex = PenConfig.STANDBY_WIDGET_ORDER.indexOf(activePaneKey).coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    val slop = viewConfiguration.touchSlop.coerceAtLeast(10f)
                    var lockedVertical = false
                    var lockedHorizontal = false
                    var fired = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!lockedVertical && !lockedHorizontal) {
                            when (SwipeDirectionClassifier.classify(change.position.x - startX, change.position.y - startY, slop)) {
                                SwipeDirectionDecision.HORIZONTAL_SCROLL -> lockedHorizontal = true
                                SwipeDirectionDecision.VERTICAL_SWIPE_UP,
                                SwipeDirectionDecision.VERTICAL_SWIPE_DOWN -> lockedVertical = true
                                SwipeDirectionDecision.NONE -> Unit
                            }
                        }
                        if (lockedVertical && !fired) {
                            val dy = change.position.y - startY
                            if (dy < -34f) {
                                fired = true
                                change.consume()
                                onSwipeUpToPomodoro()
                            } else if (dy > 42f) {
                                fired = true
                                change.consume()
                                onWakeToDesktop()
                            }
                        }
                    }
                }
            }
            .clickable(onClick = onWakeToDesktop)
            .testTag("standby_root")
    ) {
        // Independent StandBy Clock Background (Light Gallery Wallpaper vs Pure OLED Black)
        if (isLightStandBy) {
            SharedThemeWallpaperBackdrop(
                isAppleLight = true,
                presetId = state.config.lightWallpaperPresetId,
                customPath = state.config.lightCustomWallpaperPath,
                forFullScreenClock = true
            )
            Box(modifier = Modifier.fillMaxSize().background(Color(0x55FFFFFF)))
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .offset(x = shiftX, y = shiftY),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Pane: Oversized Unified-Font StandBy Clock (5-tap + long-press Vault trigger works here too!)
            Column(
                modifier = Modifier
                    .weight(0.50f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = onClockTap,
                        onLongClick = onClockLongPress
                    )
                    .padding(horizontal = 6.dp)
                    .testTag("standby_clock_trigger"),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = state.clock.dateLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isLightStandBy) AppleGraphite else clockColor.copy(alpha = 0.78f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (state.clock.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                            contentDescription = "电池",
                            tint = secondaryInk,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${state.clock.batteryPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryInk
                        )
                    }
                    if (state.secretTrigger.isArmed) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                StandByLiveClockDigits(
                    clockFont = clockFont,
                    clockColor = clockColor,
                    secondaryInk = secondaryInk
                )

                Text(
                    text = "上滑进番茄专注 · 右卡左右滑切组件",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = secondaryInk.copy(alpha = 0.72f)
                )
            }

            // Right Pane: Horizontally Swipeable 6-Widget StandBy Companion Deck
            Surface(
                modifier = Modifier
                    .weight(0.50f)
                    .fillMaxHeight(0.88f)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { horizontalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount -> horizontalDrag += dragAmount },
                            onDragEnd = {
                                if (horizontalDrag < -28f) {
                                    lastSlideDir = 1
                                    onCycleRightWidget(1)
                                } else if (horizontalDrag > 28f) {
                                    lastSlideDir = -1
                                    onCycleRightWidget(-1)
                                }
                                horizontalDrag = 0f
                            }
                        )
                    }
                    .testTag("standby_swipe_card"),
                shape = RoundedCornerShape(if (isLightStandBy) 24.dp else 18.dp),
                color = cardColor,
                border = if (isLightStandBy) null else BorderStroke(1.dp, clockColor.copy(alpha = 0.22f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = activePaneKey,
                            transitionSpec = { PenMotion.horizontalPaneTransition(lastSlideDir, motionTier) },
                            label = "standby_pane_anim"
                        ) { targetKey ->
                            when (targetKey) {
                                PenConfig.STANDBY_WIDGET_WEATHER -> StandByWeatherPane(state, accent, bodyInk, secondaryInk, clockFont)
                                PenConfig.STANDBY_WIDGET_CALENDAR -> StandByCalendarPane(state, badgeInk, bodyInk, secondaryInk, clockFont)
                                PenConfig.STANDBY_WIDGET_TODO -> StandByTodoPane(state, accent, bodyInk, secondaryInk)
                                PenConfig.STANDBY_WIDGET_VOCAB -> StandByVocabPane(state, badgeInk, bodyInk, secondaryInk)
                                PenConfig.STANDBY_WIDGET_POMODORO -> StandByPomodoroPane(state, accent, bodyInk, secondaryInk, clockFont, onTogglePomodoro)
                                else -> StandByPoetryPane(state, badgeInk, bodyInk, secondaryInk, poetryFont)
                            }
                        }
                    }

                    // Bottom Apple-style Page Indicator Dots (also tappable to advance!)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                lastSlideDir = 1
                                onCycleRightWidget(1)
                            }
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PenConfig.STANDBY_WIDGET_ORDER.forEachIndexed { idx, _ ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(width = if (idx == paneIndex) 14.dp else 5.dp, height = 5.dp)
                                    .clip(RoundedCornerShape(980.dp))
                                    .background(if (idx == paneIndex) accent else secondaryInk.copy(alpha = 0.35f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandByPoetryPane(
    state: PenOsUiState,
    accent: Color,
    bodyInk: Color,
    secondaryInk: Color,
    poetryFont: androidx.compose.ui.text.font.FontFamily
) {
    val poem = state.poem
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoStories, contentDescription = "待机诗词", tint = accent, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("STANDBY · 今日诗词", style = MaterialTheme.typography.labelSmall, color = accent)
        }
        Text(
            text = poem.content,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = poetryFont,
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            color = bodyInk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "〔${poem.dynasty}〕${poem.author} · 《${poem.title}》",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = poetryFont),
            color = secondaryInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StandByWeatherPane(
    state: PenOsUiState,
    accent: Color,
    bodyInk: Color,
    secondaryInk: Color,
    clockFont: androidx.compose.ui.text.font.FontFamily
) {
    val w = state.weather
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(wmoWeatherIcon(w.weatherCode), contentDescription = "待机天气", tint = accent, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("STANDBY · ${w.cityName}", style = MaterialTheme.typography.labelSmall, color = accent)
            }
            Text(WmoWeatherMapper.describe(w.weatherCode), style = MaterialTheme.typography.labelSmall, color = secondaryInk)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "${w.tempC.roundToInt()}°",
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = clockFont, fontSize = 36.sp, lineHeight = 36.sp),
                color = bodyInk
            )
            Text(
                text = "高 ${w.tempMaxC.roundToInt()}° / 低 ${w.tempMinC.roundToInt()}°",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryInk,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }
        Text("${state.weatherSourceStatus} · 湿度 ${w.humidity}%", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
    }
}

@Composable
private fun StandByCalendarPane(
    state: PenOsUiState,
    accent: Color,
    bodyInk: Color,
    secondaryInk: Color,
    clockFont: androidx.compose.ui.text.font.FontFamily
) {
    val todayEvents = state.calendarEvents.filter { it.dateIso == state.clock.isoDate }.sortedBy { it.timeHHmm }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = "待机日程", tint = accent, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("STANDBY · 今日日程", style = MaterialTheme.typography.labelSmall, color = accent)
            }
            Text("${todayEvents.size} 项定时", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (todayEvents.isEmpty()) {
                Text("今日暂无定时日程", style = MaterialTheme.typography.titleSmall, color = bodyInk)
            } else {
                todayEvents.take(2).forEach { ev ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(ev.timeHHmm, style = MaterialTheme.typography.labelMedium.copy(fontFamily = clockFont, fontWeight = FontWeight.Bold), color = accent)
                        Text(ev.title, style = MaterialTheme.typography.bodyMedium, color = bodyInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Text(state.clock.isoDate, style = MaterialTheme.typography.labelSmall, color = secondaryInk)
    }
}

@Composable
private fun StandByTodoPane(
    state: PenOsUiState,
    accent: Color,
    bodyInk: Color,
    secondaryInk: Color
) {
    val pending = state.todos.filter { !it.isDone }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TaskAlt, contentDescription = "待机待办", tint = accent, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("STANDBY · 待办聚焦", style = MaterialTheme.typography.labelSmall, color = accent)
            }
            Text("${pending.size} 项未完成", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (pending.isEmpty()) {
                Text("今日待办已全部完成", style = MaterialTheme.typography.titleSmall, color = bodyInk)
            } else {
                pending.take(2).forEach { item ->
                    Text("• ${item.title}", style = MaterialTheme.typography.bodyMedium, color = bodyInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text("左右滑动切换 StandBy 组件", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
    }
}

@Composable
private fun StandByVocabPane(
    state: PenOsUiState,
    accent: Color,
    bodyInk: Color,
    secondaryInk: Color
) {
    val current = state.vocabSession.currentWord?.word
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.School, contentDescription = "待机单词", tint = accent, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("STANDBY · 词卡", style = MaterialTheme.typography.labelSmall, color = accent)
            }
            Text("已斩 ${state.vocabSession.masteredCount}", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
        }
        Column {
            Text(
                text = current?.word ?: "resilient",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
                color = bodyInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = current?.rootBreakdown?.ifBlank { current.phonetic } ?: "/rɪˈzɪliənt/",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = current?.let { "${it.partOfSpeech} ${it.meaningZh}" } ?: "轻触唤醒开始背词",
            style = MaterialTheme.typography.bodySmall,
            color = secondaryInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StandByPomodoroPane(
    state: PenOsUiState,
    accent: Color,
    bodyInk: Color,
    secondaryInk: Color,
    clockFont: androidx.compose.ui.text.font.FontFamily,
    onTogglePomodoro: () -> Unit
) {
    val pomo = state.pomodoro
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, contentDescription = "番茄钟", tint = accent, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("STANDBY · 番茄钟", style = MaterialTheme.typography.labelSmall, color = accent)
            }
            Text(if (pomo.isRunning) "专注中" else "已暂停", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = pomo.formattedRemaining,
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = clockFont, fontSize = 34.sp, lineHeight = 34.sp),
                color = bodyInk
            )
            Surface(
                shape = CircleShape,
                color = accent,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTogglePomodoro)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (pomo.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (pomo.isRunning) "暂停" else "开始",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Text("上滑进入全屏壁纸番茄专注模式", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
    }
}

@Composable
private fun StandByLiveClockDigits(
    clockFont: FontFamily,
    clockColor: Color,
    secondaryInk: Color
) {
    val liveClock by LocalClockTickFlow.current.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = liveClock.timeHHmm,
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = clockFont,
                fontSize = 66.sp,
                lineHeight = 66.sp,
                fontWeight = FontWeight.Bold
            ),
            color = clockColor,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = liveClock.secondsStr,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = clockFont),
            color = secondaryInk.copy(alpha = 0.70f),
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }
}

