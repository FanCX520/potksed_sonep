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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.prefs.PenConfig
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme
import com.example.widgets.WidgetActionBridge
import com.example.widgets.WidgetRegistry

@Composable
fun DesktopScreen(
    state: PenOsUiState,
    onClockTap: () -> Unit,
    onClockLongPress: () -> Unit,
    onLaunchScanApp: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onRefreshPoem: () -> Unit,
    onOpenPoemDetail: () -> Unit,
    onClosePoemDetail: () -> Unit,
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
        onOpenPoemDetail,
        onOpenVocab,
        onEnterStandBy
    ) {
        WidgetActionBridge(
            onLaunchScanApp = onLaunchScanApp,
            onLaunchApp = onLaunchApp,
            onRefreshPoem = onRefreshPoem,
            onOpenPoemDetail = onOpenPoemDetail,
            onOpenVocabStudy = onOpenVocab,
            onEnterStandBy = onEnterStandBy
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Custom Wallpaper Layer (low-overhead, no real-time blur for MT6580 GPU)
        WallpaperBackdrop(
            presetId = state.config.wallpaperPresetId,
            customUri = state.config.customWallpaperUri
        )

        // 2. Main CarPlay + Youdao Split Layout (Left Dock 68dp + Right Horizontal Widget Canvas)
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

        // 3. Full Poem Reader Overlay
        AnimatedVisibility(
            visible = state.showPoemDetailDialog,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PoemDetailOverlay(
                state = state,
                onRefresh = onRefreshPoem,
                onDismiss = onClosePoemDetail
            )
        }
    }
}

@Composable
private fun WallpaperBackdrop(
    presetId: String,
    customUri: String
) {
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
                // Pure low-power obsidian gradient
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

        // Crisp readability scrim over wallpaper
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0x9905070B),
                            Color(0x55080B12),
                            Color(0x7705070B)
                        )
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
    onOpenVocab: () -> Unit,
    onEnterStandBy: () -> Unit
) {
    val coral = PenTheme.colors.standByCoral
    val emerald = PenTheme.colors.vocabEmerald
    val amber = PenTheme.colors.standByAmber
    val isArmed = state.secretTrigger.isArmed

    Surface(
        modifier = Modifier
            .width(68.dp)
            .fillMaxHeight()
            .testTag("carplay_left_dock"),
        shape = RoundedCornerShape(18.dp),
        color = PenTheme.colors.glassCardElevated,
        border = BorderStroke(
            width = 1.dp,
            color = if (isArmed) coral.copy(alpha = 0.85f) else PenTheme.colors.glassBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Clock Trigger: Tap 5 times + Long-press to enter Hidden Vault!
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = onClockTap,
                        onLongClick = onClockLongPress
                    )
                    .padding(vertical = 4.dp)
                    .testTag("desktop_clock_trigger")
            ) {
                Text(
                    text = state.clock.timeHHmm,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isArmed) coral else MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (state.clock.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                        contentDescription = "电量",
                        tint = if (state.clock.batteryPercent > 20) emerald else PenTheme.colors.dangerRose,
                        modifier = Modifier.size(11.dp)
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
                            .background(coral)
                    )
                }
            }

            // Quick Action Pill Buttons (Vocab + StandBy)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(width = 50.dp, height = 38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenVocab)
                        .testTag("dock_vocab_button"),
                    shape = RoundedCornerShape(12.dp),
                    color = emerald.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, emerald.copy(alpha = 0.35f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = "背单词",
                            tint = emerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(width = 50.dp, height = 38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onEnterStandBy)
                        .testTag("dock_standby_button"),
                    shape = RoundedCornerShape(12.dp),
                    color = amber.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, amber.copy(alpha = 0.35f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = "进入 StandBy 息屏待机",
                            tint = amber,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PoemDetailOverlay(
    state: PenOsUiState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val poem = state.poem
    val gold = PenTheme.colors.poetryGold
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD905070B))
            .clickable(onClick = onDismiss)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .fillMaxHeight()
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(18.dp),
            color = PenTheme.colors.glassCardElevated,
            border = BorderStroke(1.dp, gold.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
                        color = gold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = poem.fullContent,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新诗词",
                            tint = gold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭诗词详情",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
