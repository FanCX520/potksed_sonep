package com.example.widgets

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme

@Composable
private fun GlassWidgetCard(
    modifier: Modifier = Modifier,
    accentBrush: Brush? = null,
    borderColor: Color = PenTheme.colors.glassBorder,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(18.dp),
        color = PenTheme.colors.glassCard,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (accentBrush != null) Modifier.background(accentBrush) else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            content()
        }
    }
}

/**
 * Plugin 1: Youdao Dictionary Pen style Quick Scan Hero -> Launches "学王扫学"
 */
object QuickScanWidgetPlugin : PenWidgetPlugin {
    override val id = "quick_scan"
    override val title = "学王扫学"
    override val subtitle = "有道词典笔风格一键扫描查词"
    override val preferredWidth: Dp = 186.dp
    override val lowPowerNote = "零后台常驻"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        val coral = PenTheme.colors.standByCoral
        val scanPkg = state.config.scanAppPackage
        val boundLabel = remember(scanPkg, state.installedApps) {
            state.installedApps.firstOrNull { it.packageName == scanPkg }?.label
                ?: if (scanPkg.isBlank()) "点击前往密室绑定" else "学王扫学"
        }

        GlassWidgetCard(
            modifier = modifier.testTag("widget_quick_scan"),
            borderColor = coral.copy(alpha = 0.45f),
            accentBrush = Brush.linearGradient(
                colors = listOf(
                    coral.copy(alpha = 0.28f),
                    Color(0x18161C27)
                )
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
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(coral),
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
                        color = coral.copy(alpha = 0.22f)
                    ) {
                        Text(
                            text = "SCAN",
                            style = MaterialTheme.typography.labelSmall,
                            color = coral,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = "学王扫学",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (scanPkg.isNotBlank()) "点击提笔扫描 · $boundLabel" else boundLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Plugin 2: Jinrishici (今日诗词 API) Poetry Card with low-power hourly cache
 */
object JinrishiciWidgetPlugin : PenWidgetPlugin {
    override val id = "jinrishici"
    override val title = "今日诗词"
    override val subtitle = "联网名句推荐 + 本地离线古诗缓存"
    override val preferredWidth: Dp = 224.dp
    override val lowPowerNote = "每小时低频刷新"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        val gold = PenTheme.colors.poetryGold
        val poem = state.poem

        GlassWidgetCard(
            modifier = modifier.testTag("widget_jinrishici"),
            borderColor = gold.copy(alpha = 0.30f),
            accentBrush = Brush.verticalGradient(
                colors = listOf(gold.copy(alpha = 0.12f), Color.Transparent)
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
                            tint = gold,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "今日诗词",
                            style = MaterialTheme.typography.labelMedium,
                            color = gold
                        )
                    }
                    IconButton(
                        onClick = actions.onRefreshPoem,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("refresh_poem_button")
                    ) {
                        if (state.isPoemLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = gold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "换一首诗词",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Text(
                    text = poem.content,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "〔${poem.dynasty}〕${poem.author} · 《${poem.title}》",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Plugin 3: Baicizhan-Style Vocabulary Mini Card
 */
object VocabProgressWidgetPlugin : PenWidgetPlugin {
    override val id = "vocab_card"
    override val title = "百词斩背词"
    override val subtitle = "离线词库 + WebDAV 云同步"
    override val preferredWidth: Dp = 198.dp
    override val lowPowerNote = "本地 Room 驱动"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        val emerald = PenTheme.colors.vocabEmerald
        val vocab = state.vocabSession
        val progressFraction = if (vocab.totalInBook > 0) {
            vocab.masteredCount.toFloat() / vocab.totalInBook.toFloat()
        } else {
            0f
        }
        val previewWord = vocab.currentWord?.word

        GlassWidgetCard(
            modifier = modifier.testTag("widget_vocab_card"),
            borderColor = emerald.copy(alpha = 0.35f),
            accentBrush = Brush.linearGradient(
                colors = listOf(emerald.copy(alpha = 0.16f), Color.Transparent)
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
                            tint = emerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "斩词记忆",
                            style = MaterialTheme.typography.labelMedium,
                            color = emerald
                        )
                    }
                    Text(
                        text = "${vocab.masteredCount}/${vocab.totalInBook}",
                        style = MaterialTheme.typography.labelMedium,
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
                        text = previewWord?.phonetic ?: "点击开启刷词",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = emerald,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
            }
        }
    }
}

/**
 * Plugin 4: CarPlay-Style Curated Pinned Apps Widget (Only selected apps, never full drawer!)
 */
object PinnedAppsWidgetPlugin : PenWidgetPlugin {
    override val id = "pinned_apps"
    override val title = "精选应用栏"
    override val subtitle = "类 CarPlay 仅展示指定常用软件"
    override val preferredWidth: Dp = 196.dp
    override val lowPowerNote = "图标轻量缓存"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        val glacier = PenTheme.colors.glacierBlue
        val visiblePinned = remember(state.config.pinnedDockPackages, state.config.hiddenPackages, state.installedApps) {
            state.config.pinnedDockPackages
                .filter { !state.config.hiddenPackages.contains(it) }
                .mapNotNull { pkg -> state.installedApps.firstOrNull { it.packageName == pkg } }
                .take(4)
        }

        GlassWidgetCard(
            modifier = modifier.testTag("widget_pinned_apps"),
            borderColor = glacier.copy(alpha = 0.28f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = "精选应用",
                            tint = glacier,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "常用应用",
                            style = MaterialTheme.typography.labelMedium,
                            color = glacier
                        )
                    }
                    Text(
                        text = "${visiblePinned.size} 个",
                        style = MaterialTheme.typography.labelSmall,
                        color = PenTheme.colors.textMuted
                    )
                }

                if (visiblePinned.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "在密室勾选常驻应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        visiblePinned.forEach { appInfo ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { actions.onLaunchApp(appInfo.packageName) }
                                    .padding(vertical = 4.dp)
                            ) {
                                val bmp = appInfo.iconBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = appInfo.label,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(glacier.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = appInfo.label.take(1),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color.White
                                        )
                                    }
                                }
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

/**
 * Plugin 5: Hardware Telemetry & Quick StandBy Trigger (1GB RAM / Battery / Wi-Fi)
 */
object PenStatusWidgetPlugin : PenWidgetPlugin {
    override val id = "pen_status"
    override val title = "笔端状态"
    override val subtitle = "电量 / 1GB 内存监测与一键息屏待机"
    override val preferredWidth: Dp = 154.dp
    override val lowPowerNote = "10秒低频采样"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        val clock = state.clock
        val glacier = PenTheme.colors.glacierBlue
        val amber = PenTheme.colors.standByAmber

        GlassWidgetCard(
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
                            tint = if (clock.batteryPercent > 20) PenTheme.colors.vocabEmerald else PenTheme.colors.dangerRose,
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
                        tint = if (clock.isWifiConnected) glacier else PenTheme.colors.textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = "可用内存",
                        tint = PenTheme.colors.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "余 ${clock.availRamMb}M",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = amber.copy(alpha = 0.20f),
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
                            tint = amber,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "息屏待机",
                            style = MaterialTheme.typography.labelSmall,
                            color = amber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
