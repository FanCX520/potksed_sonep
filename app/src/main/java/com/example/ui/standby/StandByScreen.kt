package com.example.ui.standby

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.prefs.PenConfig
import com.example.data.remote.WmoWeatherMapper
import com.example.ui.PenOsUiState
import com.example.ui.theme.AppleBlue
import com.example.ui.theme.AppleEmber
import com.example.ui.theme.AppleFogMist
import com.example.ui.theme.AppleGraphite
import com.example.ui.theme.AppleNearBlack
import com.example.ui.theme.ApplePureWhite
import com.example.ui.theme.CarPlayGlacier
import com.example.ui.theme.StandByAmber
import com.example.ui.theme.StandByCoral
import com.example.widgets.wmoWeatherIcon
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StandByScreen(
    state: PenOsUiState,
    onWakeToDesktop: () -> Unit,
    onClockTap: () -> Unit,
    onClockLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLightStandBy = state.config.isAppleLightStandBy()

    val bgColor = if (isLightStandBy) ApplePureWhite else Color.Black
    val cardColor = if (isLightStandBy) AppleFogMist else Color(0xFF0A0C10)
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
    val badgeInk = if (isLightStandBy) AppleEmber else clockColor.copy(alpha = 0.78f)

    // Subtle 1-2dp pixel shift tied to minute digit
    val minuteHash = state.clock.timeHHmm.lastOrNull()?.digitToIntOrNull() ?: 0
    val shiftX = ((minuteHash % 3) - 1).dp
    val shiftY = (((minuteHash / 3) % 3) - 1).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .clickable(onClick = onWakeToDesktop)
            .testTag("standby_root")
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = shiftX, y = shiftY),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Left Pane: Oversized StandBy Clock (5-tap + long-press hidden trigger also works here!)
            Column(
                modifier = Modifier
                    .weight(0.52f)
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

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = state.clock.timeHHmm,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 66.sp,
                            lineHeight = 66.sp,
                            fontWeight = if (isLightStandBy) FontWeight.SemiBold else FontWeight.Bold
                        ),
                        color = clockColor,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.clock.secondsStr,
                        style = MaterialTheme.typography.titleMedium,
                        color = secondaryInk.copy(alpha = 0.65f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }

            // Right Pane: Configurable StandBy Companion Card (Poetry / Vocab / Weather / Todo)
            Surface(
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxHeight(0.86f),
                shape = RoundedCornerShape(if (isLightStandBy) 24.dp else 18.dp),
                color = cardColor,
                border = if (isLightStandBy) null else BorderStroke(1.dp, clockColor.copy(alpha = 0.22f))
            ) {
                when (state.config.standByRightWidget) {
                    PenConfig.STANDBY_WIDGET_VOCAB -> StandByVocabPane(state, badgeInk, bodyInk, secondaryInk)
                    PenConfig.STANDBY_WIDGET_WEATHER -> StandByWeatherPane(state, accent, bodyInk, secondaryInk)
                    PenConfig.STANDBY_WIDGET_TODO -> StandByTodoPane(state, accent, bodyInk, secondaryInk)
                    else -> StandByPoetryPane(state, badgeInk, bodyInk, secondaryInk)
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
    secondaryInk: Color
) {
    val poem = state.poem
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoStories, contentDescription = "待机诗词", tint = accent, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("STANDBY · 今日诗词", style = MaterialTheme.typography.labelSmall, color = accent)
        }
        Text(
            text = poem.content,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = bodyInk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("〔${poem.dynasty}〕${poem.author}", style = MaterialTheme.typography.bodySmall, color = secondaryInk, maxLines = 1)
            Text("轻触唤醒", style = MaterialTheme.typography.labelSmall, color = secondaryInk.copy(alpha = 0.7f))
        }
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 23.sp),
                color = bodyInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(current?.phonetic ?: "/rɪˈzɪliənt/", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
        }
        Text(
            text = current?.let { "${it.partOfSpeech} ${it.meaningZh}" } ?: "轻触唤醒桌面开始背词",
            style = MaterialTheme.typography.bodySmall,
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
    secondaryInk: Color
) {
    val w = state.weather
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 38.sp, lineHeight = 38.sp),
                color = bodyInk
            )
            Text(
                text = "高 ${w.tempMaxC.roundToInt()}° / 低 ${w.tempMinC.roundToInt()}°",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryInk,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Text("湿度 ${w.humidity}% · 轻触唤醒", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TaskAlt, contentDescription = "待机待办", tint = accent, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("STANDBY · 待办聚焦", style = MaterialTheme.typography.labelSmall, color = accent)
            }
            Text("${pending.size} 项待完成", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val top = pending.take(2)
            if (top.isEmpty()) {
                Text("今日待办已全部完成", style = MaterialTheme.typography.titleSmall, color = bodyInk)
            } else {
                top.forEach { item ->
                    Text(
                        text = "• ${item.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = bodyInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Text("轻触唤醒桌面打卡", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
    }
}
