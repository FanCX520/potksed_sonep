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
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StandByScreen(
    state: PenOsUiState,
    onWakeToDesktop: () -> Unit,
    onClockTap: () -> Unit,
    onClockLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clockColor = when (state.config.standByClockColor) {
        PenConfig.CLOCK_COLOR_AMBER -> PenTheme.colors.standByAmber
        PenConfig.CLOCK_COLOR_GLACIER -> PenTheme.colors.glacierBlue
        PenConfig.CLOCK_COLOR_IVORY -> Color(0xFFF3EFE6)
        else -> PenTheme.colors.standByCoral
    }

    // Subtle 1-2dp pixel shift based on minute digit to prevent LCD/OLED burn-in
    val minuteHash = state.clock.timeHHmm.lastOrNull()?.digitToIntOrNull() ?: 0
    val shiftX = ((minuteHash % 3) - 1).dp
    val shiftY = (((minuteHash / 3) % 3) - 1).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PenTheme.colors.pureBlack)
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
            // Left Pane: Oversized Apple StandBy Clock (also supports 5-tap + long-press hidden trigger!)
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
                        color = clockColor.copy(alpha = 0.78f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (state.clock.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                            contentDescription = "电池",
                            tint = clockColor.copy(alpha = 0.72f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${state.clock.batteryPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = clockColor.copy(alpha = 0.72f)
                        )
                    }
                    if (state.secretTrigger.isArmed) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(clockColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = state.clock.timeHHmm,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 68.sp,
                            lineHeight = 68.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = clockColor,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.clock.secondsStr,
                        style = MaterialTheme.typography.titleMedium,
                        color = clockColor.copy(alpha = 0.48f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }

            // Right Pane: Minimalist Low-Power StandBy Companion Widget (Poetry or Vocab)
            Surface(
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxHeight(0.86f),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0A0C10),
                border = BorderStroke(1.dp, clockColor.copy(alpha = 0.22f))
            ) {
                if (state.config.standByRightWidget == PenConfig.STANDBY_WIDGET_VOCAB) {
                    StandByVocabPane(state = state, accent = clockColor)
                } else {
                    StandByPoetryPane(state = state, accent = clockColor)
                }
            }
        }
    }
}

@Composable
private fun StandByPoetryPane(
    state: PenOsUiState,
    accent: Color
) {
    val poem = state.poem
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.AutoStories,
                contentDescription = "待机诗词",
                tint = accent.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "STANDBY · 今日诗词",
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.72f)
            )
        }

        Text(
            text = poem.content,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            color = Color(0xFFEAE6DF),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "〔${poem.dynasty}〕${poem.author}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7B818E),
                maxLines = 1
            )
            Text(
                text = "轻触唤醒",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF475061)
            )
        }
    }
}

@Composable
private fun StandByVocabPane(
    state: PenOsUiState,
    accent: Color
) {
    val current = state.vocabSession.currentWord?.word
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.School,
                    contentDescription = "待机单词",
                    tint = accent.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "STANDBY · 词卡",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.copy(alpha = 0.72f)
                )
            }
            Text(
                text = "已斩 ${state.vocabSession.masteredCount}",
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.65f)
            )
        }

        Column {
            Text(
                text = current?.word ?: "resilient",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                color = Color(0xFFF3EFE6),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = current?.phonetic ?: "/rɪˈzɪliənt/",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF7B818E)
            )
        }

        Text(
            text = current?.let { "${it.partOfSpeech} ${it.meaningZh}" } ?: "轻触唤醒桌面开始背词",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB5BAC6),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
