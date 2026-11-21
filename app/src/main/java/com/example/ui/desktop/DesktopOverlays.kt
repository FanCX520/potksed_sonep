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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TodoItemEntity
import com.example.data.remote.WeatherCityOption
import com.example.data.remote.WmoWeatherMapper
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme
import com.example.widgets.wmoWeatherIcon
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun StripOverlayShell(
    onDismiss: () -> Unit,
    testTagName: String,
    content: @Composable () -> Unit
) {
    val colors = PenTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.overlayScrim)
            .clickable(onClick = onDismiss)
            .padding(8.dp)
            .testTag(testTagName),
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
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun PoemDetailOverlay(
    state: PenOsUiState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val poem = state.poem
    val accent = PenTheme.colors.poetryGold
    StripOverlayShell(onDismiss = onDismiss, testTagName = "overlay_poem") {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "《${poem.title}》 · ${poem.dynasty} ${poem.author}",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = poem.fullContent,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新诗词", tint = accent)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WeatherDetailOverlay(
    state: PenOsUiState,
    onSearchCity: (String) -> Unit,
    onSelectCity: (WeatherCityOption) -> Unit,
    onRefreshWeather: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PenTheme.colors
    val weather = state.weather
    var query by remember { mutableStateOf("") }

    StripOverlayShell(onDismiss = onDismiss, testTagName = "overlay_weather") {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left: Live Forecast Summary
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
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
                            contentDescription = null,
                            tint = colors.primaryAction,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = weather.cityName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onRefreshWeather, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新天气", tint = colors.linkAccent, modifier = Modifier.size(16.dp))
                    }
                }

                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${weather.tempC.roundToInt()}°",
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp, lineHeight = 40.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column {
                        Text(
                            text = WmoWeatherMapper.describe(weather.weatherCode),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "高 ${weather.tempMaxC.roundToInt()}°  低 ${weather.tempMinC.roundToInt()}°  湿 ${weather.humidity}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    weather.dailySummary.split("|").filter { it.isNotBlank() }.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Right: Manual City Search & Presets (Zero GPS)
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("输入城市名（如 南京 / 深圳）", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onSearchCity(query) },
                        shape = RoundedCornerShape(980.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAction),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "搜索城市", modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(state.citySearchResults) { city ->
                        val active = city.displayName == weather.cityName
                        Surface(
                            shape = RoundedCornerShape(980.dp),
                            color = if (active) colors.primaryAction else colors.chipSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(980.dp))
                                .clickable { onSelectCity(city) }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = city.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                if (city.regionLabel.isNotBlank()) {
                                    Text(
                                        text = city.regionLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = if (active) Color.White.copy(alpha = 0.85f) else colors.textMuted,
                                        maxLines = 1
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
fun CalendarDetailOverlay(
    state: PenOsUiState,
    onToggleTodoDone: (TodoItemEntity) -> Unit,
    onAddTodoForDate: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PenTheme.colors
    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedDateIso by remember { mutableStateOf(state.clock.isoDate) }

    val monthModel = remember(monthOffset) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
        }
        val header = SimpleDateFormat("yyyy年 M月", Locale.CHINA).format(cal.time)
        val isoPrefix = SimpleDateFormat("yyyy-MM-", Locale.US).format(cal.time)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
        Triple(header, isoPrefix, Pair(firstDow, daysInMonth))
    }

    val dayTodos = remember(state.todos, selectedDateIso) {
        state.todos.filter { it.dueDateIso == selectedDateIso || it.dueDateIso.isBlank() }
    }
    val todoDateSet = remember(state.todos) {
        state.todos.filter { !it.isDone }.map { it.dueDateIso }.toSet()
    }

    StripOverlayShell(onDismiss = onDismiss, testTagName = "overlay_calendar") {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left: Compact Month Grid
            Column(
                modifier = Modifier
                    .weight(0.56f)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { monthOffset-- }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上个月", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = monthModel.first,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { monthOffset++ }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下个月", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Weekday Header
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(d, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.textMuted)
                        }
                    }
                }

                // 6 rows max compact grid
                val (firstDow, daysInMonth) = monthModel.third
                val totalCells = firstDow + daysInMonth
                val rows = (totalCells + 6) / 7
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (r in 0 until rows) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (c in 0 until 7) {
                                val cellIndex = r * 7 + c
                                val dayNum = cellIndex - firstDow + 1
                                if (dayNum in 1..daysInMonth) {
                                    val iso = monthModel.second + String.format(Locale.US, "%02d", dayNum)
                                    val isToday = iso == state.clock.isoDate
                                    val isSelected = iso == selectedDateIso
                                    val hasPending = todoDateSet.contains(iso)

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(22.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isSelected -> colors.primaryAction
                                                    isToday -> colors.chipSurface
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .clickable { selectedDateIso = iso },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = dayNum.toString(),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (hasPending && !isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(3.dp)
                                                        .clip(CircleShape)
                                                        .background(colors.linkAccent)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Right: Selected Date Schedule / Todos
            Column(
                modifier = Modifier
                    .weight(0.44f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$selectedDateIso 日程",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.badgeEmber
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭日历", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (dayTodos.isEmpty()) {
                        item {
                            Text(
                                text = "当天暂无待办安排",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    } else {
                        items(dayTodos, key = { it.id }) { todo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleTodoDone(todo) }
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (todo.isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (todo.isDone) colors.primaryAction else colors.textMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = todo.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(980.dp),
                        color = colors.chipSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(980.dp))
                            .clickable { onAddTodoForDate("复习当日单词", selectedDateIso) }
                    ) {
                        Text(
                            text = "+ 复习词卡",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(980.dp),
                        color = colors.chipSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(980.dp))
                            .clickable { onAddTodoForDate("完成课后测验", selectedDateIso) }
                    ) {
                        Text(
                            text = "+ 测验打卡",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TodoManagerOverlay(
    state: PenOsUiState,
    onAddTodo: (String) -> Unit,
    onToggleTodoDone: (TodoItemEntity) -> Unit,
    onDeleteTodo: (Long) -> Unit,
    onClearCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PenTheme.colors
    var inputText by remember { mutableStateOf("") }
    val quickTemplates = listOf("背 20 个单词", "英语听力 15 分钟", "错题复盘", "语文背诵打卡")

    StripOverlayShell(onDismiss = onDismiss, testTagName = "overlay_todo") {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left: Quick Add + Study Preset Chips
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "待办清单",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "清空已完成",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.linkAccent,
                        modifier = Modifier.clickable(onClick = onClearCompleted)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("输入新待办...", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("todo_input_field")
                    )
                    Button(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onAddTodo(inputText)
                                inputText = ""
                            }
                        },
                        shape = RoundedCornerShape(980.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAction),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("todo_add_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "添加待办", modifier = Modifier.size(16.dp))
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(quickTemplates) { preset ->
                        Surface(
                            shape = RoundedCornerShape(980.dp),
                            color = colors.chipSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(980.dp))
                                .clickable { onAddTodo(preset) }
                        ) {
                            Text(
                                text = "+ $preset",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Right: Todo List with 1-tap toggle & delete
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "共 ${state.todos.size} 项（待办 ${state.todos.count { !it.isDone }}）",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭待办", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.todos, key = { it.id }) { todo ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.chipSurface.copy(alpha = if (colors.isAppleLight) 0.55f else 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleTodoDone(todo) }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (todo.isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = "勾选",
                                    tint = if (todo.isDone) colors.primaryAction else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = todo.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = if (todo.isDone) colors.textMuted else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDeleteTodo(todo.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "删除待办",
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(14.dp)
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
