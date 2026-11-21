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
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
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
import com.example.widgets.PenAppIconImage
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
    val poetryFont = PenTheme.poetryFont
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
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = poetryFont),
                    color = accent
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = poem.fullContent,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = poetryFont,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    ),
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
    onRemoveSavedCity: (String) -> Unit,
    onSaveWeatherKeys: (String, String) -> Unit,
    onRefreshWeather: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PenTheme.colors
    val weather = state.weather
    var query by remember { mutableStateOf("") }
    var showKeyPanel by remember { mutableStateOf(false) }
    var amapKey by remember(state.config.weatherAmapKey) { mutableStateOf(state.config.weatherAmapKey) }
    var qWeatherKey by remember(state.config.weatherQWeatherKey) { mutableStateOf(state.config.weatherQWeatherKey) }

    StripOverlayShell(onDismiss = onDismiss, testTagName = "overlay_weather") {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left: Live Forecast + Source Badge
            Column(
                modifier = Modifier
                    .weight(0.40f)
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
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = weather.cityName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    Row {
                        IconButton(onClick = { showKeyPanel = !showKeyPanel }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Rounded.Key, contentDescription = "备案天气Key", tint = if (showKeyPanel) colors.badgeEmber else colors.textMuted, modifier = Modifier.size(15.dp))
                        }
                        IconButton(onClick = onRefreshWeather, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新天气", tint = colors.linkAccent, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${weather.tempC.roundToInt()}°",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = PenTheme.clockFont,
                            fontSize = 38.sp,
                            lineHeight = 38.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column {
                        Text(
                            text = WmoWeatherMapper.describe(weather.weatherCode),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${weather.tempMaxC.roundToInt()}°/${weather.tempMinC.roundToInt()}° · 湿${weather.humidity}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "数据源：${state.weatherSourceStatus}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = colors.linkAccent
                    )
                    weather.dailySummary.split("|").filter { it.isNotBlank() }.take(2).forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Right: Multi-city Favorites + Instant Search / Custom City Add OR ICP Key Config
            Column(
                modifier = Modifier
                    .weight(0.60f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (showKeyPanel) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("国内备案天气接口（选填）", style = MaterialTheme.typography.labelMedium, color = colors.badgeEmber)
                        IconButton(onClick = { showKeyPanel = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "返回城市", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = amapKey,
                            onValueChange = { amapKey = it },
                            placeholder = { Text("高德天气 Web Key", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = qWeatherKey,
                            onValueChange = { qWeatherKey = it },
                            placeholder = { Text("和风天气 Key", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            onSaveWeatherKeys(qWeatherKey, amapKey)
                            showKeyPanel = false
                        },
                        shape = RoundedCornerShape(980.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAction),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("保存并刷新（默认已免 Key 走国内备案源）", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                onSearchCity(it)
                            },
                            placeholder = { Text("搜城市/拼音或输任意市名", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("weather_city_search_input")
                        )
                        Button(
                            onClick = {
                                val clean = query.trim()
                                if (clean.isNotBlank()) {
                                    val found = WmoWeatherMapper.findBestCity(clean)
                                        ?: WeatherCityOption(clean.removeSuffix("市"), "自定义", "", 35.0, 114.0)
                                    onSelectCity(found)
                                    query = ""
                                } else {
                                    onSearchCity("")
                                }
                            },
                            shape = RoundedCornerShape(980.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAction),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("weather_city_add_button")
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = "添加或切换城市", modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Saved Favorite Cities Bar (1-tap switch + remove)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.config.savedCities, key = { "fav_${it.name}" }) { saved ->
                            val active = saved.name == weather.cityName
                            Surface(
                                shape = RoundedCornerShape(980.dp),
                                color = if (active) colors.primaryAction else colors.chipSurface,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(980.dp))
                                    .clickable {
                                        onSelectCity(WeatherCityOption(saved.name, saved.region, saved.cmaCode, saved.lat, saved.lon))
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = saved.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!active && state.config.savedCities.size > 1) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "删除收藏城市",
                                            tint = colors.textMuted,
                                            modifier = Modifier
                                                .size(11.dp)
                                                .clickable { onRemoveSavedCity(saved.name) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Search Results / National Cities
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.citySearchResults, key = { "res_${it.displayName}_${it.cmaCode}" }) { city ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.chipSurface.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectCity(city) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "+ ${city.displayName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = city.regionLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = colors.textMuted
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
    onAddCalendarEvent: (dateIso: String, timeHHmm: String, title: String) -> Unit,
    onDeleteCalendarEvent: (Long) -> Unit,
    onToggleTodoDone: (TodoItemEntity) -> Unit,
    onAddTodoForDate: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PenTheme.colors
    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedDateIso by remember { mutableStateOf(state.clock.isoDate) }
    var showAddForm by remember { mutableStateOf(false) }
    var eventTitle by remember { mutableStateOf("") }
    var eventHour by remember { mutableIntStateOf(8) }
    var eventMinute by remember { mutableIntStateOf(30) }

    val monthModel = remember(monthOffset) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
        }
        val header = SimpleDateFormat("yyyy年 M月", Locale.CHINA).format(cal.time)
        val isoPrefix = SimpleDateFormat("yyyy-MM-", Locale.US).format(cal.time)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        Triple(header, isoPrefix, Pair(firstDow, daysInMonth))
    }

    val dayEvents = remember(state.calendarEvents, selectedDateIso) {
        state.calendarEvents.filter { it.dateIso == selectedDateIso }.sortedBy { it.timeHHmm }
    }
    val dayTodos = remember(state.todos, selectedDateIso) {
        state.todos.filter { it.dueDateIso == selectedDateIso }
    }
    val markedDates = remember(state.todos, state.calendarEvents) {
        buildSet {
            state.todos.filter { !it.isDone }.forEach { add(it.dueDateIso) }
            state.calendarEvents.forEach { add(it.dateIso) }
        }
    }

    StripOverlayShell(onDismiss = onDismiss, testTagName = "overlay_calendar") {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left: Compact Month Grid
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { monthOffset-- }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上个月", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = monthModel.first,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { monthOffset++ }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下个月", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(d, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.textMuted)
                        }
                    }
                }

                val (firstDow, daysInMonth) = monthModel.third
                val rows = (firstDow + daysInMonth + 6) / 7
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    for (r in 0 until rows) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (c in 0 until 7) {
                                val dayNum = r * 7 + c - firstDow + 1
                                if (dayNum in 1..daysInMonth) {
                                    val iso = monthModel.second + String.format(Locale.US, "%02d", dayNum)
                                    val isToday = iso == state.clock.isoDate
                                    val isSelected = iso == selectedDateIso
                                    val hasMark = markedDates.contains(iso)

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(21.dp)
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
                                            if (hasMark && !isSelected) {
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

            // Right: Custom Timed Events + Inline Time Creator
            Column(
                modifier = Modifier
                    .weight(0.52f)
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(980.dp),
                            color = if (showAddForm) colors.badgeEmber else colors.primaryAction,
                            modifier = Modifier
                                .clip(RoundedCornerShape(980.dp))
                                .clickable { showAddForm = !showAddForm }
                                .testTag("calendar_toggle_add_event")
                        ) {
                            Text(
                                text = if (showAddForm) "返回列表" else "+ 定时日程",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "关闭日历", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                if (showAddForm) {
                    // Custom HH:mm stepper + title input
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = colors.primaryAction, modifier = Modifier.size(15.dp))
                            TimeStepperChip(
                                label = "%02d时".format(eventHour),
                                onMinus = { eventHour = (eventHour + 23) % 24 },
                                onPlus = { eventHour = (eventHour + 1) % 24 }
                            )
                            Text(":", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            TimeStepperChip(
                                label = "%02d分".format(eventMinute),
                                onMinus = { eventMinute = (eventMinute + 55) % 60 },
                                onPlus = { eventMinute = (eventMinute + 5) % 60 }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = eventTitle,
                                onValueChange = { eventTitle = it },
                                placeholder = { Text("输入日程（如 英语早读）", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("calendar_event_title_input")
                            )
                            Button(
                                onClick = {
                                    val t = eventTitle.trim().ifBlank { "学习提醒" }
                                    val hhmm = "%02d:%02d".format(eventHour, eventMinute)
                                    onAddCalendarEvent(selectedDateIso, hhmm, t)
                                    eventTitle = ""
                                    showAddForm = false
                                },
                                shape = RoundedCornerShape(980.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAction),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("calendar_event_save_button")
                            ) {
                                Text("保存", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (dayEvents.isEmpty() && dayTodos.isEmpty()) {
                            item {
                                Text(
                                    text = "当天暂无定时日程，点击右上角「+ 定时日程」添加具体时刻",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                        items(dayEvents, key = { "ev_${it.id}" }) { ev ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.chipSurface.copy(alpha = 0.75f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ev.timeHHmm,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = PenTheme.clockFont,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.primaryAction
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = ev.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onDeleteCalendarEvent(ev.id) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, "删除日程", tint = colors.textMuted, modifier = Modifier.size(13.dp))
                                    }
                                }
                            }
                        }
                        items(dayTodos, key = { "td_${it.id}" }) { todo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleTodoDone(todo) }
                                    .padding(vertical = 2.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (todo.isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (todo.isDone) colors.vocabEmerald else colors.textMuted,
                                    modifier = Modifier.size(14.dp)
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

                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Surface(
                            shape = RoundedCornerShape(980.dp),
                            color = colors.chipSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(980.dp))
                                .clickable { onAddCalendarEvent(selectedDateIso, "07:30", "早读背词") }
                        ) {
                            Text("+ 07:30早读", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                        }
                        Surface(
                            shape = RoundedCornerShape(980.dp),
                            color = colors.chipSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(980.dp))
                                .clickable { onAddCalendarEvent(selectedDateIso, "19:30", "晚自习专注") }
                        ) {
                            Text("+ 19:30自习", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                        }
                        Surface(
                            shape = RoundedCornerShape(980.dp),
                            color = colors.chipSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(980.dp))
                                .clickable { onAddTodoForDate("复习错题本", selectedDateIso) }
                        ) {
                            Text("+ 错题待办", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeStepperChip(
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    val colors = PenTheme.colors
    Surface(shape = RoundedCornerShape(10.dp), color = colors.chipSurface) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = "−",
                style = MaterialTheme.typography.titleSmall,
                color = colors.linkAccent,
                modifier = Modifier
                    .clickable(onClick = onMinus)
                    .padding(horizontal = 6.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = PenTheme.clockFont),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "+",
                style = MaterialTheme.typography.titleSmall,
                color = colors.linkAccent,
                modifier = Modifier
                    .clickable(onClick = onPlus)
                    .padding(horizontal = 6.dp)
            )
        }
    }
}

@Composable
fun AllAppsOverlay(
    state: PenOsUiState,
    onLaunchApp: (String) -> Unit,
    onTogglePinApp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PenTheme.colors
    val visibleApps = remember(state.installedApps, state.config.hiddenPackages) {
        state.installedApps.filter { !state.config.hiddenPackages.contains(it.packageName) }
    }

    StripOverlayShell(onDismiss = onDismiss, testTagName = "overlay_all_apps") {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "全部可见应用（${visibleApps.size} 个 · 点图标启动，点图钉固定到桌面）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭应用抽屉", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }

            if (visibleApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无可显示应用（可在密室取消隐藏）", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(visibleApps, key = { it.packageName }) { appInfo ->
                        val isPinned = state.config.pinnedDockPackages.contains(appInfo.packageName)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.chipSurface.copy(alpha = 0.72f),
                            modifier = Modifier
                                .width(84.dp)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onLaunchApp(appInfo.packageName) }
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PushPin,
                                        contentDescription = if (isPinned) "取消固定" else "固定到桌面",
                                        tint = if (isPinned) colors.primaryAction else colors.textMuted,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { onTogglePinApp(appInfo.packageName) }
                                    )
                                }
                                PenAppIconImage(
                                    appInfo = appInfo,
                                    customIconSpec = state.config.customAppIcons[appInfo.packageName],
                                    sizeDp = 38.dp,
                                    cornerDp = 10.dp
                                )
                                Text(
                                    text = appInfo.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
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
                    Text("待办清单", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
