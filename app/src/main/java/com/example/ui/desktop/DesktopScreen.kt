package com.example.ui.desktop

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.TodoItemEntity
import com.example.data.prefs.PenConfig
import com.example.data.remote.WeatherCityOption
import com.example.ui.DesktopOverlay
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme
import com.example.widgets.WidgetActionBridge
import com.example.widgets.WidgetRegistry

@Composable
fun DesktopScreen(
    state: PenOsUiState,
    onClockTap: () -> Unit,
    onClockLongPress: () -> Unit,
    onToggleQuickTheme: () -> Unit,
    onLaunchScanApp: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onRefreshPoem: () -> Unit,
    onOpenOverlay: (DesktopOverlay) -> Unit,
    onCloseOverlay: () -> Unit,
    onRefreshWeather: () -> Unit,
    onSearchWeatherCities: (String) -> Unit,
    onSelectWeatherCity: (WeatherCityOption) -> Unit,
    onAddTodo: (String) -> Unit,
    onAddTodoForDate: (String, String) -> Unit,
    onToggleTodoDone: (TodoItemEntity) -> Unit,
    onDeleteTodo: (Long) -> Unit,
    onClearCompletedTodos: () -> Unit,
    onOpenVocab: () -> Unit,
    onEnterStandBy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabledPlugins = remember(state.config.enabledWidgetIds) {
        WidgetRegistry.resolveEnabled(state.config.enabledWidgetIds)
    }

    val widgetActions = remember(
        onLaunchScanApp,
        onLaunchApp,
        onRefreshPoem,
        onOpenOverlay,
        onOpenVocab,
        onEnterStandBy,
        onRefreshWeather,
        onToggleTodoDone
    ) {
        WidgetActionBridge(
            onLaunchScanApp = onLaunchScanApp,
            onLaunchApp = onLaunchApp,
            onRefreshPoem = onRefreshPoem,
            onOpenPoemDetail = { onOpenOverlay(DesktopOverlay.POEM) },
            onOpenVocabStudy = onOpenVocab,
            onEnterStandBy = onEnterStandBy,
            onOpenWeather = { onOpenOverlay(DesktopOverlay.WEATHER) },
            onRefreshWeather = onRefreshWeather,
            onOpenCalendar = { onOpenOverlay(DesktopOverlay.CALENDAR) },
            onOpenTodo = { onOpenOverlay(DesktopOverlay.TODO) },
            onToggleTodoDone = onToggleTodoDone
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Wallpaper / Apple White Gallery Vitrine Canvas
        WallpaperBackdrop(
            isAppleLight = PenTheme.colors.isAppleLight,
            presetId = state.config.wallpaperPresetId,
            customUri = state.config.customWallpaperUri
        )

        // 2. Main Split Layout: Left CarPlay Dock + Right Modular Widget Deck
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CarPlayLeftDock(
                state = state,
                onClockTap = onClockTap,
                onClockLongPress = onClockLongPress,
                onToggleQuickTheme = onToggleQuickTheme,
                onOpenVocab = onOpenVocab,
                onEnterStandBy = onEnterStandBy
            )

            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("desktop_widget_deck"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 6.dp)
            ) {
                items(enabledPlugins, key = { it.id }) { plugin ->
                    plugin.Render(
                        state = state,
                        actions = widgetActions,
                        modifier = Modifier.width(plugin.preferredWidth)
                    )
                }
            }
        }

        // 3. Interactive Strip Overlays (Poem, Weather, Calendar, Todo)
        AnimatedVisibility(
            visible = state.activeOverlay != DesktopOverlay.NONE,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            when (state.activeOverlay) {
                DesktopOverlay.POEM -> PoemDetailOverlay(
                    state = state,
                    onRefresh = onRefreshPoem,
                    onDismiss = onCloseOverlay
                )
                DesktopOverlay.WEATHER -> WeatherDetailOverlay(
                    state = state,
                    onSearchCity = onSearchWeatherCities,
                    onSelectCity = onSelectWeatherCity,
                    onRefreshWeather = onRefreshWeather,
                    onDismiss = onCloseOverlay
                )
                DesktopOverlay.CALENDAR -> CalendarDetailOverlay(
                    state = state,
                    onToggleTodoDone = onToggleTodoDone,
                    onAddTodoForDate = onAddTodoForDate,
                    onDismiss = onCloseOverlay
                )
                DesktopOverlay.TODO -> TodoManagerOverlay(
                    state = state,
                    onAddTodo = onAddTodo,
                    onToggleTodoDone = onToggleTodoDone,
                    onDeleteTodo = onDeleteTodo,
                    onClearCompleted = onClearCompletedTodos,
                    onDismiss = onCloseOverlay
                )
                DesktopOverlay.NONE -> Unit
            }
        }
    }
}

@Composable
private fun WallpaperBackdrop(
    isAppleLight: Boolean,
    presetId: String,
    customUri: String
) {
    if (isAppleLight) {
        // Apple White Gallery Vitrine: vast Fog Mist #F3F6F6 gallery canvas so Pure White #FFFFFF cards float effortlessly
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PenTheme.colors.canvasBackground)
        ) {
            if (presetId == PenConfig.WALLPAPER_CUSTOM && customUri.isNotBlank()) {
                AsyncImage(
                    model = Uri.parse(customUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCCF3F6F6))
                )
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PenTheme.colors.obsidian)
    ) {
        when (presetId) {
            PenConfig.WALLPAPER_AURORA -> {
                Image(
                    painter = painterResource(id = R.drawable.img_wallpaper_aurora),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            PenConfig.WALLPAPER_INK -> {
                Image(
                    painter = painterResource(id = R.drawable.img_wallpaper_ink),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            PenConfig.WALLPAPER_CUSTOM -> {
                if (customUri.isNotBlank()) {
                    AsyncImage(
                        model = Uri.parse(customUri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF07090D), Color(0xFF101521), Color(0xFF07090D))
                            )
                        )
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0x9905070B), Color(0x55080B12), Color(0x7705070B))
                    )
                )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarPlayLeftDock(
    state: PenOsUiState,
    onClockTap: () -> Unit,
    onClockLongPress: () -> Unit,
    onToggleQuickTheme: () -> Unit,
    onOpenVocab: () -> Unit,
    onEnterStandBy: () -> Unit
) {
    val colors = PenTheme.colors
    val isLight = colors.isAppleLight
    val isArmed = state.secretTrigger.isArmed
    val dockShape = RoundedCornerShape(colors.cardCornerRadius)

    Surface(
        modifier = Modifier
            .width(68.dp)
            .fillMaxHeight()
            .testTag("carplay_left_dock"),
        shape = dockShape,
        color = colors.glassCardElevated,
        border = when {
            isArmed -> BorderStroke(1.5.dp, colors.primaryAction)
            colors.showCardBorder -> BorderStroke(1.dp, colors.glassBorder)
            else -> null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Secret Clock Trigger: 5 taps + Long-press -> Hidden Vault
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = onClockTap,
                        onLongClick = onClockLongPress
                    )
                    .padding(vertical = 3.dp)
                    .testTag("desktop_clock_trigger")
            ) {
                Text(
                    text = state.clock.timeHHmm,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = if (isLight) FontWeight.SemiBold else FontWeight.Bold
                    ),
                    color = if (isArmed) colors.primaryAction else MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (state.clock.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                        contentDescription = "电量",
                        tint = if (isLight) MaterialTheme.colorScheme.onSurfaceVariant else {
                            if (state.clock.batteryPercent > 20) colors.vocabEmerald else colors.dangerRose
                        },
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "${state.clock.batteryPercent}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isArmed) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(colors.primaryAction)
                    )
                }
            }

            // Compact Pill Actions: Vocab + StandBy + Quick Theme Switch
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(width = 50.dp, height = 28.dp)
                        .clip(RoundedCornerShape(980.dp))
                        .clickable(onClick = onOpenVocab)
                        .testTag("dock_vocab_button"),
                    shape = RoundedCornerShape(980.dp),
                    color = if (isLight) colors.chipSurface else colors.vocabEmerald.copy(alpha = 0.18f),
                    border = if (isLight) null else BorderStroke(1.dp, colors.vocabEmerald.copy(alpha = 0.35f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = "背单词",
                            tint = if (isLight) MaterialTheme.colorScheme.onSurface else colors.vocabEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onEnterStandBy)
                            .testTag("dock_standby_button"),
                        shape = CircleShape,
                        color = if (isLight) colors.chipSurface else colors.standByAmber.copy(alpha = 0.18f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = "进入 StandBy 息屏待机",
                                tint = if (isLight) MaterialTheme.colorScheme.onSurface else colors.standByAmber,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onToggleQuickTheme)
                            .testTag("dock_theme_toggle_button"),
                        shape = CircleShape,
                        color = if (isLight) colors.chipSurface else colors.glacierBlue.copy(alpha = 0.18f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isLight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                contentDescription = "一键切换设计方案",
                                tint = if (isLight) MaterialTheme.colorScheme.onSurface else colors.glacierBlue,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
