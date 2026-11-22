package com.example.ui.setup

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.system.QrCodeCanvas
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenFontCatalog
import com.example.ui.theme.PenMotionTier
import com.example.ui.theme.PenTheme
import com.example.widgets.MultiStyleClockCard

/**
 * Apple-style 6-Step Landscape Setup Assistant tailored for 640x172 dictionary pens.
 * Always skippable via `setup_skip_button` and re-runnable anytime from Hidden Vault.
 */
@Composable
fun SetupAssistantScreen(
    state: PenOsUiState,
    onSelectDesignStyle: (String) -> Unit,
    onSelectMotionTier: (String) -> Unit,
    onSelectDynamicWallpaperMode: (String) -> Unit,
    onCycleClockStyle: () -> Unit,
    onSetPoetryScale: (Float) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleLanServer: () -> Unit,
    onOpenMailSetup: () -> Unit,
    onComplete: () -> Unit,
    onSelectDesktopEngineMode: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 6

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("setup_assistant_screen"),
        color = colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar: Step indicator + Skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PenOS 5.5 设置向导",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "步骤 ${step + 1} / $totalSteps",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.surfaceVariant,
                    modifier = Modifier
                        .clickable { onComplete() }
                        .testTag("setup_skip_button")
                ) {
                    Text(
                        text = "跳过向导",
                        fontSize = 10.sp,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            // Center Step Content (weight 1f so bottom nav NEVER clips on 172dp screen!)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) {
                when (step) {
                    0 -> StepWelcome()
                    1 -> StepThemeAndMotion(
                        config = state.config,
                        onSelectDesignStyle = onSelectDesignStyle,
                        onSelectMotionTier = onSelectMotionTier,
                        onSelectDesktopEngineMode = onSelectDesktopEngineMode
                    )
                    2 -> StepClockAndWallpaper(
                        config = state.config,
                        onCycleClockStyle = onCycleClockStyle,
                        onSelectDynamicWallpaperMode = onSelectDynamicWallpaperMode
                    )
                    3 -> StepFontsPreview(
                        config = state.config,
                        onSetPoetryScale = onSetPoetryScale
                    )
                    4 -> StepAntiLock(
                        diagnostics = state.diagnostics,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings
                    )
                    else -> StepLanAndMail(
                        state = state,
                        onToggleLanServer = onToggleLanServer,
                        onOpenMailSetup = onOpenMailSetup
                    )
                }
            }

            // Bottom Navigation Strip (26dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step Dots
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(totalSteps) { idx ->
                        Box(
                            modifier = Modifier
                                .width(if (idx == step) 16.dp else 6.dp)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(if (idx == step) colors.primary else colors.outlineVariant)
                                .clickable { step = idx }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.surfaceVariant,
                            modifier = Modifier
                                .clickable { step-- }
                                .testTag("setup_prev_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(12.dp))
                                Text("上一步", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    val isLast = step == totalSteps - 1
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.primary,
                        modifier = Modifier
                            .clickable { if (isLast) onComplete() else step++ }
                            .testTag(if (isLast) "setup_finish_button" else "setup_next_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isLast) "开始使用 PenOS 5.5" else "继续",
                                fontSize = 10.sp,
                                color = colors.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (isLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = colors.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepWelcome() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.primary, modifier = Modifier.size(34.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "你好，欢迎来到 PenOS 5.5（MT6582 极速版）",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "专为 1GHz 词典笔横屏打造：默认原生 View 极速主屏 + 零过绘制纯色轻质感 + 首屏 3 张高频卡片，随时一键切换完整 Compose 桌面，完整保留 AI MCP、局域网控制台与邮箱。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                lineHeight = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StepThemeAndMotion(
    config: PenConfig,
    onSelectDesignStyle: (String) -> Unit,
    onSelectMotionTier: (String) -> Unit,
    onSelectDesktopEngineMode: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                Text("1. 桌面引擎与风格", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SetupChoiceChip("原生极速(推荐)", config.desktopEngineMode == PenConfig.ENGINE_NATIVE_TURBO, Modifier.weight(1f)) {
                    onSelectDesktopEngineMode(PenConfig.ENGINE_NATIVE_TURBO)
                }
                SetupChoiceChip("完整Compose", config.desktopEngineMode == PenConfig.ENGINE_COMPOSE_FULL, Modifier.weight(1f)) {
                    onSelectDesktopEngineMode(PenConfig.ENGINE_COMPOSE_FULL)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SetupChoiceChip("暗黑曜石", !config.isAppleLightDesktop(), Modifier.weight(1f)) {
                    onSelectDesignStyle(PenConfig.STYLE_DARK_STANDBY)
                }
                SetupChoiceChip("Apple 雾白", config.isAppleLightDesktop(), Modifier.weight(1f)) {
                    onSelectDesignStyle(PenConfig.STYLE_APPLE_LIGHT)
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("2. 动效流畅度（MT6582 推荐零动效）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SetupChoiceChip("零动效", config.motionTierId == PenMotionTier.NONE.id, Modifier.weight(1f)) {
                    onSelectMotionTier(PenMotionTier.NONE.id)
                }
                SetupChoiceChip("极速 60fps", config.motionTierId == PenMotionTier.FAST.id, Modifier.weight(1f)) {
                    onSelectMotionTier(PenMotionTier.FAST.id)
                }
                SetupChoiceChip("轻盈弹簧", config.motionTierId == PenMotionTier.SPRING.id, Modifier.weight(1f)) {
                    onSelectMotionTier(PenMotionTier.SPRING.id)
                }
            }
        }
    }
}

@Composable
private fun StepClockAndWallpaper(
    config: PenConfig,
    onCycleClockStyle: () -> Unit,
    onSelectDynamicWallpaperMode: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MultiStyleClockCard(
            clockConfig = config.multiClock,
            onCycleStyle = onCycleClockStyle,
            modifier = Modifier
                .width(156.dp)
                .fillMaxHeight()
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("轻触左侧表盘可即时切换 6 种开源时钟样式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("选择动态壁纸引擎（4 模式随时可切）：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SetupChoiceChip("1·代码极光", config.dynamicWallpaperMode == PenConfig.DYN_WALLPAPER_PROCEDURAL, Modifier.weight(1f)) {
                    onSelectDynamicWallpaperMode(PenConfig.DYN_WALLPAPER_PROCEDURAL)
                }
                SetupChoiceChip("2·本地GIF", config.dynamicWallpaperMode == PenConfig.DYN_WALLPAPER_GIF, Modifier.weight(1f)) {
                    onSelectDynamicWallpaperMode(PenConfig.DYN_WALLPAPER_GIF)
                }
                SetupChoiceChip("3·本地MP4", config.dynamicWallpaperMode == PenConfig.DYN_WALLPAPER_MP4, Modifier.weight(1f)) {
                    onSelectDynamicWallpaperMode(PenConfig.DYN_WALLPAPER_MP4)
                }
                SetupChoiceChip("4·系统动态", config.dynamicWallpaperMode == PenConfig.DYN_WALLPAPER_LIVE, Modifier.weight(1f)) {
                    onSelectDynamicWallpaperMode(PenConfig.DYN_WALLPAPER_LIVE)
                }
            }
        }
    }
}

@Composable
private fun StepFontsPreview(
    config: PenConfig,
    onSetPoetryScale: (Float) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val poetryScale = config.typographyProfile.poetry.scale
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.TextFields, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                Text("5 通道细分全局字体系统", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "“海内存知己，天涯若比邻。”",
                fontFamily = PenFontCatalog.resolve(
                    config.typographyProfile.poetry.familyKey,
                    config.typographyProfile.poetry.externalPath
                ),
                fontSize = (15f * poetryScale).sp,
                color = colors.onBackground
            )
            Text(
                text = "可在「实验室 → 5通道字体」或局域网后台分别调节时钟/标题/正文/诗词/徽章字体与导入 TTF。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                fontSize = 9.sp
            )
        }
        Column(modifier = Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("诗词字号快速体验", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SetupChoiceChip("紧凑", poetryScale < 0.95f, Modifier.weight(1f)) { onSetPoetryScale(0.9f) }
                SetupChoiceChip("标准", poetryScale in 0.95f..1.1f, Modifier.weight(1f)) { onSetPoetryScale(1.0f) }
                SetupChoiceChip("大字", poetryScale > 1.1f, Modifier.weight(1f)) { onSetPoetryScale(1.22f) }
            }
        }
    }
}

@Composable
private fun StepAntiLock(
    diagnostics: com.example.system.SystemDiagnostics,
    onOpenAccessibilitySettings: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.primaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Security, contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("久学网同包名防锁回保护", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "仅拦截原厂 HomeActivity，精准放行同包名的学王扫学与查词页。无障碍服务：${if (diagnostics.isAccessibilityGuardActive) "已开启" else "待开启"}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.primary,
                modifier = Modifier.clickable { onOpenAccessibilitySettings() }
            ) {
                Text(
                    "去开启无障碍防锁回守护",
                    fontSize = 10.sp,
                    color = colors.onPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun StepLanAndMail(
    state: PenOsUiState,
    onToggleLanServer: () -> Unit,
    onOpenMailSetup: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val lan = state.lanServer
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (lan.isRunning && lan.url.isNotBlank()) {
            QrCodeCanvas(
                content = lan.url,
                modifier = Modifier.size(84.dp)
            )
        } else {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surfaceVariant,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Language, contentDescription = null, tint = colors.primary, modifier = Modifier.size(30.dp))
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (lan.isRunning) "手机/电脑扫码配置笔端：${lan.url}" else "开启局域网网页后台 · 告别窄屏打字",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (lan.isRunning) {
                    "6位配对码：${lan.pin} · 支持无线传 GIF/MP4/字体、配多套 AI API 与邮箱！"
                } else {
                    "开启后扫码即可在手机或电脑浏览器中统一配置 AI 模型、邮箱、字体与壁纸。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                fontSize = 10.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.primary,
                    modifier = Modifier.clickable { onToggleLanServer() }
                ) {
                    Text(
                        if (lan.isRunning) "关闭局域网服务" else "立即开启局域网后台",
                        fontSize = 10.sp,
                        color = colors.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceVariant,
                    modifier = Modifier.clickable { onOpenMailSetup() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Rounded.Mail, contentDescription = null, modifier = Modifier.size(11.dp))
                        Text("去绑定邮箱", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
