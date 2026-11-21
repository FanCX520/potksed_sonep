package com.example.ui.desktop

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.TodoItemEntity
import com.example.data.prefs.PenConfig
import com.example.data.remote.WeatherCityOption
import com.example.system.NativeWidgetHostManager
import com.example.system.WhiteNoiseType
import com.example.ui.DesktopOverlay
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenMotion
import com.example.ui.theme.PenTheme
import com.example.ui.theme.springPressScale
import com.example.widgets.AdaptiveWidgetCard
import com.example.widgets.NativeSystemWidgetCard
import com.example.widgets.WidgetActionBridge
import com.example.widgets.WidgetRegistry
import java.io.File

@Composable
fun DesktopScreen(
    state: PenOsUiState,
    nativeWidgetHost: NativeWidgetHostManager,
    onClockTap: () -> Unit,
    onClockLongPress: () -> Unit,
    onToggleQuickTheme: () -> Unit,
    onLaunchScanApp: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onTogglePinApp: (String) -> Unit,
    onRefreshPoem: () -> Unit,
    onOpenOverlay: (DesktopOverlay) -> Unit,
    onCloseOverlay: () -> Unit,
    onRefreshWeather: () -> Unit,
    onSearchWeatherCities: (String) -> Unit,
    onSelectWeatherCity: (WeatherCityOption) -> Unit,
    onRemoveSavedCity: (String) -> Unit,
    onSaveWeatherKeys: (String, String) -> Unit,
    onAddCalendarEvent: (String, String, String) -> Unit,
    onDeleteCalendarEvent: (Long) -> Unit,
    onAddTodo: (String) -> Unit,
    onAddTodoForDate: (String, String) -> Unit,
    onToggleTodoDone: (TodoItemEntity) -> Unit,
    onDeleteTodo: (Long) -> Unit,
    onClearCompletedTodos: () -> Unit,
    onOpenVocab: () -> Unit,
    onEnterStandBy: () -> Unit,
    onOpenPomodoro: () -> Unit,
    onOpenLab: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onNightDimChange: (Float) -> Unit,
    onToggleWhiteNoise: (WhiteNoiseType) -> Unit,
    onSetDeckMode: (String) -> Unit,
    onRequestAddNativeWidget: () -> Unit,
    onAdjustNativeWidgetWidth: (Int, Int) -> Unit,
    onRemoveNativeWidget: (Int) -> Unit,
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
        onOpenPomodoro,
        onOpenLab,
        onRefreshWeather,
        onToggleTodoDone
    ) {
        WidgetActionBridge(
            onLaunchScanApp = onLaunchScanApp,
            onLaunchApp = onLaunchApp,
            onOpenAllApps = { onOpenOverlay(DesktopOverlay.ALL_APPS) },
            onRefreshPoem = onRefreshPoem,
            onOpenPoemDetail = { onOpenOverlay(DesktopOverlay.POEM) },
            onOpenVocabStudy = onOpenVocab,
            onEnterStandBy = onEnterStandBy,
            onOpenPomodoro = onOpenPomodoro,
            onOpenLab = onOpenLab,
            onOpenControlCenter = { onOpenOverlay(DesktopOverlay.CONTROL_CENTER) },
            onOpenWeather = { onOpenOverlay(DesktopOverlay.WEATHER) },
            onRefreshWeather = onRefreshWeather,
            onOpenCalendar = { onOpenOverlay(DesktopOverlay.CALENDAR) },
            onOpenTodo = { onOpenOverlay(DesktopOverlay.TODO) },
            onToggleTodoDone = onToggleTodoDone
        )
    }

    val isLight = PenTheme.colors.isAppleLight
    val isNativeOnly = state.config.desktopDeckMode == PenConfig.DECK_MODE_NATIVE_ONLY
    var verticalDragAccum by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(state.activeOverlay) {
                if (state.activeOverlay == DesktopOverlay.NONE) {
                    detectVerticalDragGestures(
                        onDragStart = { verticalDragAccum = 0f },
                        onVerticalDrag = { _, dragAmount -> verticalDragAccum += dragAmount },
                        onDragEnd = {
                            when {
                                verticalDragAccum < -34f -> onOpenPomodoro()
                                verticalDragAccum > 34f -> onOpenOverlay(DesktopOverlay.CONTROL_CENTER)
                            }
                            verticalDragAccum = 0f
                        }
                    )
                }
            }
    ) {
        // 1. Independent Theme Wallpaper Backdrop (Dark vs Apple White Gallery)
        SharedThemeWallpaperBackdrop(
            isAppleLight = isLight,
            presetId = state.config.activeWallpaperPresetFor(isLight),
            customPath = state.config.activeCustomWallpaperPathFor(isLight),
            forFullScreenClock = false
        )

        // 2. Main Split Layout: Left CarPlay Dock + Right Hybrid Widget Deck (1+2 Native Support)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CarPlayLeftDock(
                state = state,
                isNativeOnly = isNativeOnly,
                onClockTap = onClockTap,
                onClockLongPress = onClockLongPress,
                onToggleQuickTheme = onToggleQuickTheme,
                onOpenVocab = onOpenVocab,
                onOpenLab = onOpenLab,
                onOpenControlCenter = { onOpenOverlay(DesktopOverlay.CONTROL_CENTER) },
                onEnterStandBy = onEnterStandBy,
                onToggleDeckMode = {
                    onSetDeckMode(if (isNativeOnly) PenConfig.DECK_MODE_MIXED else PenConfig.DECK_MODE_NATIVE_ONLY)
                }
            )

            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("desktop_widget_deck"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 6.dp)
            ) {
                if (!isNativeOnly) {
                    items(enabledPlugins, key = { "plugin_${it.id}" }) { plugin ->
                        plugin.Render(
                            state = state,
                            actions = widgetActions,
                            modifier = Modifier.width(plugin.preferredWidth)
                        )
                    }
                }

                // 1+2 Native Android AppWidgets (appear inline in mixed mode AND exclusively in native_only tab!)
                items(state.config.nativeWidgetIds, key = { "native_$it" }) { widgetId ->
                    val widthDp = state.config.nativeWidgetWidths[widgetId] ?: 200
                    NativeSystemWidgetCard(
                        appWidgetId = widgetId,
                        widthDp = widthDp,
                        hostManager = nativeWidgetHost,
                        showManageBar = isNativeOnly,
                        onAdjustWidth = { delta -> onAdjustNativeWidgetWidth(widgetId, delta) },
                        onRemove = { onRemoveNativeWidget(widgetId) }
                    )
                }

                if (isNativeOnly) {
                    item(key = "add_native_widget_card") {
                        AdaptiveWidgetCard(
                            modifier = Modifier
                                .width(172.dp)
                                .testTag("add_native_widget_card"),
                            onClick = onRequestAddNativeWidget
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "添加系统原生小组件",
                                    tint = PenTheme.colors.primaryAction,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "添加系统原生小组件",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "调用安卓 AppWidgetHost",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Spring-Animated Strip Overlays (Poem, Weather, Calendar, Todo, Control Center, All Apps)
        AnimatedVisibility(
            visible = state.activeOverlay != DesktopOverlay.NONE,
            enter = PenMotion.overlayEnter(),
            exit = PenMotion.overlayExit()
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
                    onRemoveSavedCity = onRemoveSavedCity,
                    onSaveWeatherKeys = onSaveWeatherKeys,
                    onRefreshWeather = onRefreshWeather,
                    onDismiss = onCloseOverlay
                )
                DesktopOverlay.CALENDAR -> CalendarDetailOverlay(
                    state = state,
                    onAddCalendarEvent = onAddCalendarEvent,
                    onDeleteCalendarEvent = onDeleteCalendarEvent,
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
                DesktopOverlay.CONTROL_CENTER -> ControlCenterOverlay(
                    state = state,
                    onBrightnessChange = onBrightnessChange,
                    onVolumeChange = onVolumeChange,
                    onNightDimChange = onNightDimChange,
                    onToggleWhiteNoise = onToggleWhiteNoise,
                    onToggleTheme = onToggleQuickTheme,
                    onOpenPomodoro = onOpenPomodoro,
                    onOpenLab = onOpenLab,
                    onRequestAddNativeWidget = onRequestAddNativeWidget,
                    onDismiss = onCloseOverlay
                )
                DesktopOverlay.ALL_APPS -> AllAppsOverlay(
                    state = state,
                    onLaunchApp = {
                        onCloseOverlay()
                        onLaunchApp(it)
                    },
                    onTogglePinApp = onTogglePinApp,
                    onDismiss = onCloseOverlay
                )
                DesktopOverlay.NONE -> Unit
            }
        }
    }
}

/**
 * Shared independent Wallpaper Backdrop for BOTH themes:
 * - Apple White Gallery has its own dedicated light clock wallpapers (`img_wallpaper_gallery_light`, `pure_mist`, `warm_paper`, custom light)
 * - Dark StandBy CarPlay has its own dedicated dark wallpapers (`img_wallpaper_aurora`, `img_wallpaper_ink`, `obsidian`, custom dark)
 */
@Composable
fun SharedThemeWallpaperBackdrop(
    isAppleLight: Boolean,
    presetId: String,
    customPath: String,
    forFullScreenClock: Boolean = false
) {
    val customBitmap = remember(customPath) {
        if (customPath.isNotBlank()) {
            val file = File(customPath)
            if (file.exists()) {
                runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
            } else null
        } else null
    }

    if (isAppleLight) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PenTheme.colors.canvasBackground)
        ) {
            when {
                presetId == PenConfig.WALLPAPER_CUSTOM && customBitmap != null -> {
                    Image(
                        bitmap = customBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (!forFullScreenClock) {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xAAF3F6F6)))
                    }
                }
                presetId == PenConfig.WALLPAPER_GALLERY_LIGHT -> {
                    Image(
                        painter = painterResource(id = R.drawable.img_wallpaper_gallery_light),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (!forFullScreenClock) {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0x99F3F6F6)))
                    }
                }
                presetId == PenConfig.WALLPAPER_WARM_PAPER -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFFBF8F3), Color(0xFFF3ECE1), Color(0xFFEBE4D8))
                                )
                            )
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFF3F6F6), Color(0xFFEAF0F5), Color(0xFFF5F6F8))
                                )
                            )
                    )
                }
            }
        }
        return
    }

    // Dark StandBy CarPlay independent wallpapers
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PenTheme.colors.obsidian)
    ) {
        when {
            presetId == PenConfig.WALLPAPER_CUSTOM && customBitmap != null -> {
                Image(
                    bitmap = customBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            presetId == PenConfig.WALLPAPER_AURORA -> {
                Image(
                    painter = painterResource(id = R.drawable.img_wallpaper_aurora),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            presetId == PenConfig.WALLPAPER_INK -> {
                Image(
                    painter = painterResource(id = R.drawable.img_wallpaper_ink),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
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

        if (!forFullScreenClock) {
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarPlayLeftDock(
    state: PenOsUiState,
    isNativeOnly: Boolean,
    onClockTap: () -> Unit,
    onClockLongPress: () -> Unit,
    onToggleQuickTheme: () -> Unit,
    onOpenVocab: () -> Unit,
    onOpenLab: () -> Unit,
    onOpenControlCenter: () -> Unit,
    onEnterStandBy: () -> Unit,
    onToggleDeckMode: () -> Unit
) {
    val colors = PenTheme.colors
    val isLight = colors.isAppleLight
    val isArmed = state.secretTrigger.isArmed
    val dockShape = RoundedCornerShape(colors.cardCornerRadius)

    Surface(
        modifier = Modifier
            .width(70.dp)
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
            // Top Secret Clock Trigger (Unified Clock Font across BOTH themes!)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = onClockTap,
                        onLongClick = onClockLongPress
                    )
                    .padding(vertical = 2.dp)
                    .testTag("desktop_clock_trigger")
            ) {
                Text(
                    text = state.clock.timeHHmm,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PenTheme.clockFont,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
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
                    Spacer(modifier = Modifier.height(1.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(colors.primaryAction)
                    )
                }
            }

            // Middle: 2x2 Action Mini Grid (Vocab, Lab, Control Center, Native Widgets Tab)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DockCircleButton(
                        icon = Icons.Rounded.School,
                        desc = "百词斩背单词",
                        tint = if (isLight) MaterialTheme.colorScheme.onSurface else colors.vocabEmerald,
                        bg = if (isLight) colors.chipSurface else colors.vocabEmerald.copy(alpha = 0.18f),
                        tag = "dock_vocab_button",
                        onClick = onOpenVocab
                    )
                    DockCircleButton(
                        icon = Icons.Rounded.Science,
                        desc = "AI 与白噪音实验室",
                        tint = if (isLight) MaterialTheme.colorScheme.onSurface else colors.glacierBlue,
                        bg = if (isLight) colors.chipSurface else colors.glacierBlue.copy(alpha = 0.18f),
                        tag = "dock_lab_button",
                        onClick = onOpenLab
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DockCircleButton(
                        icon = Icons.Rounded.Tune,
                        desc = "控制中心（亮度/音量）",
                        tint = if (isLight) MaterialTheme.colorScheme.onSurface else colors.standByCoral,
                        bg = if (isLight) colors.chipSurface else colors.standByCoral.copy(alpha = 0.18f),
                        tag = "dock_control_button",
                        onClick = onOpenControlCenter
                    )
                    DockCircleButton(
                        icon = Icons.Rounded.Widgets,
                        desc = "切换原生组件视图",
                        tint = if (isNativeOnly) Color.White else if (isLight) MaterialTheme.colorScheme.onSurface else colors.poetryGold,
                        bg = if (isNativeOnly) colors.primaryAction else if (isLight) colors.chipSurface else colors.poetryGold.copy(alpha = 0.18f),
                        tag = "dock_native_tab_button",
                        onClick = onToggleDeckMode
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DockCircleButton(
                        icon = Icons.Rounded.Bedtime,
                        desc = "StandBy 息屏待机",
                        tint = if (isLight) MaterialTheme.colorScheme.onSurface else colors.standByAmber,
                        bg = if (isLight) colors.chipSurface else colors.standByAmber.copy(alpha = 0.18f),
                        tag = "dock_standby_button",
                        onClick = onEnterStandBy
                    )
                    DockCircleButton(
                        icon = if (isLight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        desc = "切换主题方案",
                        tint = if (isLight) MaterialTheme.colorScheme.onSurface else colors.glacierBlue,
                        bg = if (isLight) colors.chipSurface else colors.glacierBlue.copy(alpha = 0.18f),
                        tag = "dock_theme_toggle_button",
                        onClick = onToggleQuickTheme
                    )
                }
            }
        }
    }
}

@Composable
private fun DockCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tint: Color,
    bg: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(25.dp)
            .springPressScale(pressedScale = 0.88f, onClick = onClick)
            .clip(CircleShape)
            .testTag(tag),
        shape = CircleShape,
        color = bg
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = desc,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
