package com.example.ui.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.FocusSubMode
import com.example.ui.LocalClockFlow
import com.example.ui.PenOsUiState
import com.example.ui.components.StaticWallpaperBackground
import com.example.ui.theme.ClockDisplayFont
import com.example.ui.theme.PenIcons
import com.example.ui.theme.PenTheme

/**
 * PenOS 6.0 Minimalist Giant Clock Home Screen + Bottom Quick-Action Dock.
 *
 * Performance engineering:
 * - Zero horizontal widget decks, zero AndroidView, zero shadows/blurs, zero BoxWithConstraints.
 * - Clock ticks live in an isolated leaf composable (`GiantClockLeaf`) subscribed to `LocalClockFlow`,
 *   so wallpaper, top status bar, and bottom dock experience 0 recompositions per second.
 * - Adapts seamlessly between 640x172 landscape dictionary pens and portrait/landscape phones.
 */
@Composable
fun DesktopScreen(
    state: PenOsUiState,
    onClockTap: () -> Unit,
    onClockLongPress: () -> Unit,
    onToggleDarkTheme: () -> Unit,
    onLaunchScanApp: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenVocab: () -> Unit,
    onOpenFocus: (FocusSubMode) -> Unit,
    onOpenAllApps: () -> Unit
) {
    val palette = PenTheme.palette
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val isUltraCompactStrip = screenHeightDp in 1..280

    Box(modifier = Modifier.fillMaxSize()) {
        StaticWallpaperBackground(config = state.config)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(
                    horizontal = if (isUltraCompactStrip) 14.dp else 20.dp,
                    vertical = if (isUltraCompactStrip) 6.dp else 14.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Minimalist Top Telemetry Strip
            HomeTopTelemetryStrip(
                wifiConnected = state.wifiConnected,
                batteryPercent = state.batteryPercent,
                isCharging = state.isCharging,
                isDark = state.config.isDarkTheme,
                onToggleTheme = onToggleDarkTheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
            )

            // 2. Centered Architectural Giant Clock Stage (5-tap + Long Press -> Hidden Vault)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                contentAlignment = Alignment.Center
            ) {
                GiantClockStage(
                    showSeconds = state.config.showSecondsOnHome,
                    isUltraCompactStrip = isUltraCompactStrip,
                    secretTapCount = state.secretTapCount,
                    stockHomeBypassSec = state.stockHomeBypassSec,
                    vocabToday = state.vocab.todayReviewed,
                    vocabGoal = state.config.vocabDailyGoal,
                    onClockTap = onClockTap,
                    onClockLongPress = onClockLongPress,
                    onOpenVocab = onOpenVocab
                )
            }

            // 3. Bottom Quick-Action Dock (5 Tactile Monochrome Entries)
            HomeBottomDock(
                isUltraCompactStrip = isUltraCompactStrip,
                onLaunchScanApp = onLaunchScanApp,
                onOpenAi = onOpenAi,
                onOpenVocab = onOpenVocab,
                onOpenFocus = { onOpenFocus(FocusSubMode.STANDBY) },
                onOpenAllApps = onOpenAllApps,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            )
        }
    }
}

@Composable
private fun HomeTopTelemetryStrip(
    wifiConnected: Boolean,
    batteryPercent: Int,
    isCharging: Boolean,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = PenTheme.palette
    val clock by LocalClockFlow.current.collectAsStateWithLifecycle()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = clock.dateLine,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp
            ),
            color = palette.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("home_date_label")
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Wi-Fi precision status dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (wifiConnected) palette.success else palette.hairline)
                )
                Text(
                    text = if (wifiConnected) "WI-FI" else "离线",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkMuted
                )
            }

            // Battery readout
            Text(
                text = if (isCharging) "⚡$batteryPercent%" else "$batteryPercent%",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (batteryPercent <= 15) palette.danger else palette.ink,
                modifier = Modifier.testTag("home_battery_label")
            )

            // Instant 1-tap Black / White Theme Switch
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .border(1.dp, palette.hairline, CircleShape)
                    .clickable(onClick = onToggleTheme)
                    .testTag("home_theme_toggle")
                    .semantics {
                        contentDescription = if (isDark) "切换为极简白主题" else "切换为极简黑主题"
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = PenIcons.ThemeContrast,
                    contentDescription = null,
                    tint = palette.ink,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GiantClockStage(
    showSeconds: Boolean,
    isUltraCompactStrip: Boolean,
    secretTapCount: Int,
    stockHomeBypassSec: Int,
    vocabToday: Int,
    vocabGoal: Int,
    onClockTap: () -> Unit,
    onClockLongPress: () -> Unit,
    onOpenVocab: () -> Unit
) {
    val palette = PenTheme.palette
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClockTap,
                onLongClick = onClockLongPress
            )
            .testTag("home_clock_stage")
            .padding(horizontal = 16.dp, vertical = if (isUltraCompactStrip) 2.dp else 12.dp)
    ) {
        GiantClockNumeralsLeaf(
            showSeconds = showSeconds,
            isUltraCompactStrip = isUltraCompactStrip
        )

        if (!isUltraCompactStrip) {
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Minimalist status pill or active 60s bypass alert
        if (stockHomeBypassSec > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(palette.accent)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "原厂桌面临时放行 ${stockHomeBypassSec}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onAccent
                )
            }
        } else if (!isUltraCompactStrip) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.hairline, RoundedCornerShape(99.dp))
                    .clickable(onClick = onOpenVocab)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(palette.accent)
                )
                Text(
                    text = "今日背词 $vocabToday / $vocabGoal",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = palette.inkMuted
                )
            }
        }

        // Subtle secret 5-tap indicator (only visible while tapping towards Vault)
        if (secretTapCount > 0) {
            Spacer(modifier = Modifier.height(if (isUltraCompactStrip) 2.dp else 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(5) { idx ->
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (idx < secretTapCount) palette.accent else palette.hairline)
                    )
                }
            }
        }
    }
}

/**
 * Isolated 1-second leaf composable. Only this tiny Text row recomposes when clock ticks!
 */
@Composable
private fun GiantClockNumeralsLeaf(
    showSeconds: Boolean,
    isUltraCompactStrip: Boolean
) {
    val palette = PenTheme.palette
    val clock by LocalClockFlow.current.collectAsStateWithLifecycle()
    val fontSize = if (isUltraCompactStrip) 54.sp else 84.sp
    val lineHeight = if (isUltraCompactStrip) 56.sp else 88.sp

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = clock.hour,
            fontFamily = ClockDisplayFont,
            fontWeight = FontWeight.Light,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = (-2.5).sp,
            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            color = palette.ink
        )
        Text(
            text = ":",
            fontFamily = ClockDisplayFont,
            fontWeight = FontWeight.Normal,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = palette.accent,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Text(
            text = clock.minute,
            fontFamily = ClockDisplayFont,
            fontWeight = FontWeight.Light,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = (-2.5).sp,
            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            color = palette.ink
        )
        if (showSeconds) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = clock.second,
                fontFamily = ClockDisplayFont,
                fontWeight = FontWeight.Normal,
                fontSize = if (isUltraCompactStrip) 20.sp else 26.sp,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = palette.inkMuted,
                modifier = Modifier.padding(bottom = if (isUltraCompactStrip) 8.dp else 12.dp)
            )
        }
    }
}

@Composable
private fun HomeBottomDock(
    isUltraCompactStrip: Boolean,
    onLaunchScanApp: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenVocab: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenAllApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = PenTheme.palette
    val dockShape = RoundedCornerShape(18.dp)

    Row(
        modifier = modifier
            .clip(dockShape)
            .background(palette.surface)
            .border(1.dp, palette.hairline, dockShape)
            .padding(
                horizontal = if (isUltraCompactStrip) 6.dp else 8.dp,
                vertical = if (isUltraCompactStrip) 4.dp else 6.dp
            ),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockActionButton(
            icon = PenIcons.Scan,
            label = "扫学",
            emphasized = false,
            compact = isUltraCompactStrip,
            testTag = "dock_scan",
            onClick = onLaunchScanApp,
            modifier = Modifier.weight(1f)
        )
        DockActionButton(
            icon = PenIcons.VocabBook,
            label = "背词",
            emphasized = false,
            compact = isUltraCompactStrip,
            testTag = "dock_vocab",
            onClick = onOpenVocab,
            modifier = Modifier.weight(1f)
        )
        DockActionButton(
            icon = PenIcons.SparkleAi,
            label = "AI",
            emphasized = true,
            compact = isUltraCompactStrip,
            testTag = "dock_ai",
            onClick = onOpenAi,
            modifier = Modifier.weight(1.15f)
        )
        DockActionButton(
            icon = PenIcons.FocusTimer,
            label = "专注",
            emphasized = false,
            compact = isUltraCompactStrip,
            testTag = "dock_focus",
            onClick = onOpenFocus,
            modifier = Modifier.weight(1f)
        )
        DockActionButton(
            icon = PenIcons.AppsGrid,
            label = "应用",
            emphasized = false,
            compact = isUltraCompactStrip,
            testTag = "dock_apps",
            onClick = onOpenAllApps,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DockActionButton(
    icon: ImageVector,
    label: String,
    emphasized: Boolean,
    compact: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = PenTheme.palette
    val shape = RoundedCornerShape(12.dp)
    val bg = if (emphasized) palette.invertedBg else palette.surface
    val fg = if (emphasized) palette.invertedInk else palette.ink
    val labelColor = if (emphasized) palette.invertedInk else palette.inkMuted

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(vertical = if (compact) 4.dp else 7.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(if (compact) 18.dp else 20.dp)
        )
        if (!compact) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = labelColor,
                maxLines = 1
            )
        }
    }
}
