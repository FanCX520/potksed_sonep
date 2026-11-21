package com.example.widgets

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.WmoWeatherMapper
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

fun wmoWeatherIcon(code: Int): ImageVector = when (code) {
    0, 1 -> Icons.Rounded.WbSunny
    2, 3, 45, 48 -> Icons.Rounded.Cloud
    71, 73, 75, 77, 85, 86 -> Icons.Rounded.AcUnit
    95, 96, 99 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.WaterDrop
}

/**
 * New Plugin 6: Weather Widget (Open-Meteo manual city + offline cache)
 */
object WeatherWidgetPlugin : PenWidgetPlugin {
    override val id = "weather"
    override val title = "天气预报"
    override val subtitle = "手动城市搜索 · 零 GPS 低功耗缓存"
    override val preferredWidth: Dp = 184.dp
    override val lowPowerNote = "每小时缓存刷新"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        val colors = PenTheme.colors
        val isLight = colors.isAppleLight
        val weather = state.weather
        val accent = if (isLight) colors.primaryAction else colors.glacierBlue

        AdaptiveWidgetCard(
            modifier = modifier.testTag("widget_weather"),
            darkBorderColor = accent.copy(alpha = 0.35f),
            darkAccentBrush = Brush.linearGradient(
                colors = listOf(accent.copy(alpha = 0.20f), Color.Transparent)
            ),
            onClick = actions.onOpenWeather
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = wmoWeatherIcon(weather.weatherCode),
                            contentDescription = "天气图标",
                            tint = if (isLight) MaterialTheme.colorScheme.onSurface else accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = weather.cityName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isLight) MaterialTheme.colorScheme.onSurface else accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = actions.onRefreshWeather,
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("refresh_weather_button")
                    ) {
                        if (state.isWeatherLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.5.dp,
                                color = accent
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新天气",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${weather.tempC.roundToInt()}°",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 36.sp,
                            lineHeight = 36.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = WmoWeatherMapper.describe(weather.weatherCode),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${weather.tempMaxC.roundToInt()}° / ${weather.tempMinC.roundToInt()}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = if (isLight) "湿度 ${weather.humidity}% · 切换城市 ›" else "湿度 ${weather.humidity}% · 风速 ${weather.windKmh.roundToInt()}km/h",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLight) colors.linkAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * New Plugin 7: Calendar Week-Strip & Today Hero Widget
 */
object CalendarWidgetPlugin : PenWidgetPlugin {
    override val id = "calendar"
    override val title = "日历视图"
    override val subtitle = "今日大日期 + 本周进度条 + 月历联动"
    override val preferredWidth: Dp = 204.dp
    override val lowPowerNote = "本地日历零联网"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        val colors = PenTheme.colors
        val isLight = colors.isAppleLight
        val accent = if (isLight) colors.primaryAction else colors.standByCoral

        val calData = remember(state.clock.isoDate, state.todos) {
            val cal = Calendar.getInstance()
            val todayDay = cal.get(Calendar.DAY_OF_MONTH)
            val yearMonth = SimpleDateFormat("yyyy年M月", Locale.CHINA).format(cal.time)
            val weekday = SimpleDateFormat("EEEE", Locale.CHINA).format(cal.time)

            val pendingDates = state.todos.filter { !it.isDone }.map { it.dueDateIso }.toSet()
            val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            val weekStart = (cal.clone() as Calendar).apply {
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
            val days = (0 until 7).map { offset ->
                val d = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, offset) }
                val dayNum = d.get(Calendar.DAY_OF_MONTH)
                val iso = isoFmt.format(d.time)
                Triple(weekLabels[offset], dayNum, Pair(iso == state.clock.isoDate, pendingDates.contains(iso)))
            }
            QuadCalendarInfo(todayDay, yearMonth, weekday, days)
        }

        AdaptiveWidgetCard(
            modifier = modifier.testTag("widget_calendar"),
            darkBorderColor = accent.copy(alpha = 0.30f),
            onClick = actions.onOpenCalendar
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = "日历",
                            tint = if (isLight) colors.badgeEmber else accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = calData.yearMonth,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isLight) colors.badgeEmber else accent
                        )
                    }
                    Text(
                        text = calData.weekday,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Giant Apple-style date + compact week strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = calData.todayDay.toString(),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 34.sp,
                            lineHeight = 34.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        calData.weekDays.forEach { (wLabel, num, flags) ->
                            val (isToday, hasTodo) = flags
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = wLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = colors.textMuted
                                )
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (isToday) accent else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = num.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(if (hasTodo) colors.linkAccent else Color.Transparent)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = if (isLight) "查看完整月历 ›" else "轻触展开完整月历与日程",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLight) colors.linkAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class QuadCalendarInfo(
    val todayDay: Int,
    val yearMonth: String,
    val weekday: String,
    val weekDays: List<Triple<String, Int, Pair<Boolean, Boolean>>>
)

/**
 * New Plugin 8: Todo Reminders Widget (1-tap desktop check-off + quick manager)
 */
object TodoWidgetPlugin : PenWidgetPlugin {
    override val id = "todo"
    override val title = "待办事项"
    override val subtitle = "桌面一键打卡勾选 + 学习任务清单"
    override val preferredWidth: Dp = 216.dp
    override val lowPowerNote = "Room 离线持久化"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        val colors = PenTheme.colors
        val isLight = colors.isAppleLight
        val pendingTodos = remember(state.todos) { state.todos.filter { !it.isDone } }
        val displayTodos = remember(state.todos) { state.todos.take(2) }
        val accent = if (isLight) colors.primaryAction else colors.vocabEmerald

        AdaptiveWidgetCard(
            modifier = modifier.testTag("widget_todo"),
            darkBorderColor = accent.copy(alpha = 0.30f),
            onClick = actions.onOpenTodo
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.TaskAlt,
                            contentDescription = "待办事项",
                            tint = accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "待办 · ${pendingTodos.size} 项未完成",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isLight) MaterialTheme.colorScheme.onSurface else accent
                        )
                    }
                    Text(
                        text = if (isLight) "管理 ›" else "+ 管理",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.linkAccent
                    )
                }

                if (displayTodos.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "今日待办已全部完成，轻触新增任务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        displayTodos.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { actions.onToggleTodoDone(item) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (item.isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = if (item.isDone) "已完成" else "标记完成",
                                    tint = if (item.isDone) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = if (item.isDone) colors.textMuted else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "轻触任务可直接打卡",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
            }
        }
    }
}
