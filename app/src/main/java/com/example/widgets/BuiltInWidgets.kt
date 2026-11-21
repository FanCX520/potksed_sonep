package com.example.widgets

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.system.InstalledAppInfo
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme
import com.example.ui.theme.springPressScale
import java.io.File

/**
 * Shared modular widget container honoring both themes + Apple spring press scale.
 */
@Composable
fun AdaptiveWidgetCard(
    modifier: Modifier = Modifier,
    darkAccentBrush: Brush? = null,
    darkBorderColor: Color = PenTheme.colors.glassBorder,
    useSecondaryLightTint: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val penColors = PenTheme.colors
    val isLight = penColors.isAppleLight
    val shape = RoundedCornerShape(penColors.cardCornerRadius)
    val surfaceColor = when {
        isLight && useSecondaryLightTint -> penColors.secondaryTintCard
        else -> penColors.glassCard
    }
    val border = if (penColors.showCardBorder) BorderStroke(1.dp, darkBorderColor) else null

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .then(if (onClick != null) Modifier.springPressScale(pressedScale = 0.965f, onClick = onClick) else Modifier)
            .clip(shape),
        shape = shape,
        color = surfaceColor,
        border = border,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isLight && darkAccentBrush != null) Modifier.background(darkAccentBrush) else Modifier)
                .padding(horizontal = 13.dp, vertical = 10.dp)
        ) {
            content()
        }
    }
}

/**
 * Renders an app icon honoring user custom icon specs (file path or "color:#RRGGBB" badge).
 */
@Composable
fun PenAppIconImage(
    appInfo: InstalledAppInfo,
    customIconSpec: String?,
    sizeDp: Dp = 34.dp,
    cornerDp: Dp = 9.dp
) {
    val colors = PenTheme.colors
    val customBitmap = remember(customIconSpec) {
        if (!customIconSpec.isNullOrBlank() && !customIconSpec.startsWith("color:")) {
            val f = File(customIconSpec)
            if (f.exists()) {
                runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull()
            } else null
        } else null
    }
    val customColor = remember(customIconSpec) {
        if (customIconSpec?.startsWith("color:") == true) {
            runCatching {
                Color(android.graphics.Color.parseColor(customIconSpec.removePrefix("color:")))
            }.getOrNull()
        } else null
    }

    when {
        customBitmap != null -> {
            Image(
                bitmap = customBitmap,
                contentDescription = appInfo.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(sizeDp)
                    .clip(RoundedCornerShape(cornerDp))
            )
        }
        customColor != null -> {
            Box(
                modifier = Modifier
                    .size(sizeDp)
                    .clip(RoundedCornerShape(cornerDp))
                    .background(customColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = appInfo.label.take(1),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        appInfo.iconBitmap != null -> {
            Image(
                bitmap = appInfo.iconBitmap.asImageBitmap(),
                contentDescription = appInfo.label,
                modifier = Modifier
                    .size(sizeDp)
                    .clip(RoundedCornerShape(cornerDp))
            )
        }
        else -> {
            Box(
                modifier = Modifier
                    .size(sizeDp)
                    .clip(RoundedCornerShape(cornerDp))
                    .background(colors.chipSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = appInfo.label.take(1),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

object QuickScanWidgetPlugin : PenWidgetPlugin {
    override val id = "quick_scan"
    override val title = "学王扫学"
    override val subtitle = "久学网组件级隔离一键扫描查词"
    override val preferredWidth: Dp = 186.dp
    override val lowPowerNote = "零后台常驻"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        val colors = PenTheme.colors
        val isLight = colors.isAppleLight
        val primary = colors.primaryAction
        val scanPkg = state.config.scanAppPackage
        val scanAct = state.config.scanActivityClass
        val boundLabel = remember(scanPkg, scanAct, state.installedApps) {
            val base = state.installedApps.firstOrNull { it.packageName == scanPkg }?.label
                ?: if (scanPkg.isBlank()) "密室绑定扫学" else "学王扫学"
            if (scanAct.isNotBlank()) "$base·隔离" else base
        }

        AdaptiveWidgetCard(
            modifier = modifier.testTag("widget_quick_scan"),
            darkBorderColor = primary.copy(alpha = 0.45f),
            darkAccentBrush = Brush.linearGradient(
                colors = listOf(primary.copy(alpha = 0.28f), Color(0x18161C27))
            ),
            onClick = actions.onLaunchScanApp
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
                    if (isLight) {
                        Text(
                            text = "快速查词",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.badgeEmber
                        )
                        Surface(shape = RoundedCornerShape(980.dp), color = primary) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DocumentScanner,
                                    contentDescription = "启动学王扫学",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "扫描",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DocumentScanner,
                                contentDescription = "启动学王扫学",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = primary.copy(alpha = 0.22f)
                        ) {
                            Text(
                                text = "SCAN",
                                style = MaterialTheme.typography.labelSmall,
                                color = primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = "学王扫学",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = if (isLight) 17.sp else 15.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isLight) "提笔即扫 · $boundLabel ›" else "点击提笔扫描 · $boundLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLight) colors.linkAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

object JinrishiciWidgetPlugin : PenWidgetPlugin {
    override val id = "jinrishici"
    override val title = "今日诗词"
    override val subtitle = "思源宋体排印 · 联网名句 + 离线缓存"
    override val preferredWidth: Dp = 230.dp
    override val lowPowerNote = "思源宋体"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        val colors = PenTheme.colors
        val isLight = colors.isAppleLight
        val accent = if (isLight) colors.badgeEmber else colors.poetryGold
        val poem = state.poem
        val poetryFont = PenTheme.poetryFont

        AdaptiveWidgetCard(
            modifier = modifier.testTag("widget_jinrishici"),
            darkBorderColor = accent.copy(alpha = 0.30f),
            darkAccentBrush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.12f), Color.Transparent)
            ),
            onClick = actions.onOpenPoemDetail
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
                            imageVector = Icons.Rounded.AutoStories,
                            contentDescription = "今日诗词",
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "今日诗词 · 思源宋体",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                    }
                    IconButton(
                        onClick = actions.onRefreshPoem,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("refresh_poem_button")
                    ) {
                        if (state.isPoemLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = accent
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "换一首诗词",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Text(
                    text = poem.content,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = poetryFont,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "〔${poem.dynasty}〕${poem.author} · 《${poem.title}》",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = poetryFont),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

object VocabProgressWidgetPlugin : PenWidgetPlugin {
    override val id = "vocab_card"
    override val title = "百词斩 2.0"
    override val subtitle = "200+ 分级词库 · 词根图卡 · 错词本"
    override val preferredWidth: Dp = 204.dp
    override val lowPowerNote = "本地 Room"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        val colors = PenTheme.colors
        val isLight = colors.isAppleLight
        val accent = if (isLight) colors.primaryAction else colors.vocabEmerald
        val vocab = state.vocabSession
        val progressFraction = if (vocab.totalInBook > 0) {
            vocab.masteredCount.toFloat() / vocab.totalInBook.toFloat()
        } else 0f
        val previewWord = vocab.currentWord?.word

        AdaptiveWidgetCard(
            modifier = modifier.testTag("widget_vocab_card"),
            darkBorderColor = accent.copy(alpha = 0.35f),
            darkAccentBrush = Brush.linearGradient(
                colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent)
            ),
            onClick = actions.onOpenVocabStudy
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
                            imageVector = Icons.Rounded.School,
                            contentDescription = "背单词",
                            tint = accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "斩词记忆",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                    }
                    Text(
                        text = if (vocab.mistakeCount > 0) "${vocab.masteredCount}/${vocab.totalInBook} · 错${vocab.mistakeCount}" else "${vocab.masteredCount}/${vocab.totalInBook}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column {
                    Text(
                        text = previewWord?.word ?: "Start",
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = previewWord?.rootBreakdown?.ifBlank { previewWord.phonetic } ?: "点击进入百词斩 ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(980.dp)),
                    color = accent,
                    trackColor = if (isLight) colors.chipSurface else Color.White.copy(alpha = 0.12f)
                )
            }
        }
    }
}

object PinnedAppsWidgetPlugin : PenWidgetPlugin {
    override val id = "pinned_apps"
    override val title = "常用应用"
    override val subtitle = "支持自定义图标 + 一键打开全部应用抽屉"
    override val preferredWidth: Dp = 206.dp
    override val lowPowerNote = "轻量图标缓存"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        val colors = PenTheme.colors
        val accent = colors.linkAccent
        val visiblePinned = remember(state.config.pinnedDockPackages, state.config.hiddenPackages, state.installedApps) {
            state.config.pinnedDockPackages
                .filter { !state.config.hiddenPackages.contains(it) }
                .mapNotNull { pkg -> state.installedApps.firstOrNull { it.packageName == pkg } }
                .take(4)
        }

        AdaptiveWidgetCard(
            modifier = modifier.testTag("widget_pinned_apps"),
            darkBorderColor = accent.copy(alpha = 0.28f),
            onClick = if (visiblePinned.isEmpty()) actions.onOpenAllApps else null
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { actions.onOpenAllApps() }
                        .padding(vertical = 1.dp)
                        .testTag("open_all_apps_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = "常用应用",
                            tint = if (colors.isAppleLight) MaterialTheme.colorScheme.onSurface else accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "常用应用",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (colors.isAppleLight) MaterialTheme.colorScheme.onSurface else accent
                        )
                    }
                    Text(
                        text = "全部应用 ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.linkAccent
                    )
                }

                if (visiblePinned.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { actions.onOpenAllApps() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "轻触打开全部应用并固定常用软件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        visiblePinned.forEach { appInfo ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .springPressScale(pressedScale = 0.90f) {
                                        actions.onLaunchApp(appInfo.packageName)
                                    }
                                    .clip(RoundedCornerShape(10.dp))
                                    .padding(vertical = 3.dp)
                                    .testTag("pinned_app_${appInfo.packageName}")
                            ) {
                                PenAppIconImage(
                                    appInfo = appInfo,
                                    customIconSpec = state.config.customAppIcons[appInfo.packageName],
                                    sizeDp = 36.dp,
                                    cornerDp = 10.dp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = appInfo.label,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
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

object PenStatusWidgetPlugin : PenWidgetPlugin {
    override val id = "pen_status"
    override val title = "笔端状态"
    override val subtitle = "电量 / 内存 / 快捷控制中心与息屏"
    override val preferredWidth: Dp = 160.dp
    override val lowPowerNote = "10秒低频采样"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        val colors = PenTheme.colors
        val isLight = colors.isAppleLight
        val clock = state.clock

        AdaptiveWidgetCard(
            modifier = modifier.testTag("widget_pen_status"),
            onClick = actions.onEnterStandBy
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
                            imageVector = if (clock.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                            contentDescription = "电量",
                            tint = if (isLight) MaterialTheme.colorScheme.onSurface else {
                                if (clock.batteryPercent > 20) colors.vocabEmerald else colors.dangerRose
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${clock.batteryPercent}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (clock.isWifiConnected) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                        contentDescription = "无线网络",
                        tint = if (clock.isWifiConnected) colors.linkAccent else colors.textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = "可用内存",
                        tint = colors.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "余 ${clock.availRamMb}M",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isLight) {
                    Text(
                        text = "进入息屏待机 ›",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.linkAccent
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.standByAmber.copy(alpha = 0.20f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = "进入 StandBy 息屏待机",
                                tint = colors.standByAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "息屏待机",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.standByAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
