package com.example.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.prefs.MultiClockConfig
import com.example.ui.ClockTelemetryState
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Decoupled 1-second Clock Flow CompositionLocal:
 * ONLY clock leaf composables collect this flow, eliminating full-screen recomposition on Android 5.0.2!
 */
val LocalClockTickFlow = compositionLocalOf<StateFlow<ClockTelemetryState>> {
    MutableStateFlow(ClockTelemetryState())
}

/**
 * Multi-Style Clock Widget inspired by open-source Compose widgets:
 * - Material 3 Expressive Bloom Analog (inspired by breadworkz/Clock)
 * - Swiss Railway Analog
 * - Retro Split-Flap Flip Clock (inspired by ScreenClock)
 * - KDE Modern Minimalist Stacked Digital (inspired by Dark-Witcher/modern-clock-android)
 * - Roman Classic Serif Dial
 * - 5x7 Retro Dot-Matrix LED
 */
object MultiStyleClockWidgetPlugin : PenWidgetPlugin {
    override val id: String = "multi_clock"
    override val title: String = "多样式时钟"
    override val subtitle: String = "6套表盘点击切换"
    override val preferredWidth: Dp = 156.dp
    override val lowPowerNote: String = "独立高频流零重组"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        MultiStyleClockCard(
            clockConfig = state.config.multiClock,
            onCycleStyle = actions.onCycleClockStyle,
            modifier = modifier
        )
    }
}

@Composable
fun MultiStyleClockCard(
    clockConfig: MultiClockConfig,
    onCycleStyle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clock by LocalClockTickFlow.current.collectAsStateWithLifecycle()
    val colors = PenTheme.colors
    val accent = Color(clockConfig.accentHex)
    val styleLabel = MultiClockConfig.allStyles.firstOrNull { it.first == clockConfig.style }?.second ?: "时钟"

    AdaptiveWidgetCard(
        modifier = modifier.testTag("widget_multi_clock"),
        darkBorderColor = accent.copy(alpha = 0.34f),
        onClick = onCycleStyle
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = styleLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = "点切",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (clockConfig.style) {
                    MultiClockConfig.STYLE_RAILWAY_SWISS -> AnalogClockFace(
                        clock = clock,
                        shape = clockConfig.shape,
                        accent = Color(0xFFE11D48),
                        showSeconds = clockConfig.showSeconds,
                        railwayMode = true,
                        romanMode = false
                    )
                    MultiClockConfig.STYLE_ROMAN_CLASSIC -> AnalogClockFace(
                        clock = clock,
                        shape = clockConfig.shape,
                        accent = accent,
                        showSeconds = clockConfig.showSeconds,
                        railwayMode = false,
                        romanMode = true
                    )
                    MultiClockConfig.STYLE_RETRO_FLIP -> RetroFlipClockFace(
                        clock = clock,
                        accent = accent,
                        showSeconds = clockConfig.showSeconds
                    )
                    MultiClockConfig.STYLE_KDE_MINIMAL -> KdeModernMinimalFace(
                        clock = clock,
                        accent = accent,
                        showSeconds = clockConfig.showSeconds
                    )
                    MultiClockConfig.STYLE_DOT_MATRIX -> DotMatrixClockFace(
                        clock = clock,
                        accent = accent
                    )
                    else -> AnalogClockFace(
                        clock = clock,
                        shape = clockConfig.shape,
                        accent = accent,
                        showSeconds = clockConfig.showSeconds,
                        railwayMode = false,
                        romanMode = false
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalogClockFace(
    clock: ClockTelemetryState,
    shape: String,
    accent: Color,
    showSeconds: Boolean,
    railwayMode: Boolean,
    romanMode: Boolean
) {
    val isLight = PenTheme.colors.isAppleLight
    val dialFill = if (isLight) Color(0xFFF1F4F8) else Color(0xFF141B29)
    val tickColor = if (isLight) Color(0xFF2B2F36) else Color(0xFFE2E8F0)
    val handMain = if (isLight) Color(0xFF14171C) else Color.White

    val parts = clock.timeHHmm.split(":")
    val hour = (parts.getOrNull(0)?.toIntOrNull() ?: 8) % 12
    val minute = (parts.getOrNull(1)?.toIntOrNull() ?: 30) % 60
    val second = (clock.secondsStr.toIntOrNull() ?: 0) % 60

    val cachedScallopPath = remember { Path() }
    val cachedScallopDim = remember { FloatArray(2) }

    Box(modifier = Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = minOf(size.width, size.height) / 2f - 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Reshapable outer silhouette (M3 Expressive Scallop / Squircle / Circle / Pill)
            when (shape) {
                MultiClockConfig.SHAPE_SCALLOP -> {
                    if (cachedScallopDim[0] != size.width || cachedScallopDim[1] != size.height) {
                        cachedScallopPath.reset()
                        val petals = 12
                        val steps = 96
                        for (i in 0..steps) {
                            val theta = (i.toFloat() / steps) * 2f * PI.toFloat()
                            val modR = r * (0.93f + 0.07f * cos(petals * theta))
                            val x = center.x + modR * cos(theta)
                            val y = center.y + modR * sin(theta)
                            if (i == 0) cachedScallopPath.moveTo(x, y) else cachedScallopPath.lineTo(x, y)
                        }
                        cachedScallopPath.close()
                        cachedScallopDim[0] = size.width
                        cachedScallopDim[1] = size.height
                    }
                    drawPath(cachedScallopPath, color = dialFill)
                    drawPath(cachedScallopPath, color = accent.copy(alpha = 0.35f), style = Stroke(width = 2.2f))
                }
                MultiClockConfig.SHAPE_SQUIRCLE, MultiClockConfig.SHAPE_PILL -> {
                    val cr = if (shape == MultiClockConfig.SHAPE_PILL) r * 0.65f else r * 0.42f
                    drawRoundRect(
                        color = dialFill,
                        topLeft = Offset(center.x - r, center.y - r),
                        size = Size(r * 2f, r * 2f),
                        cornerRadius = CornerRadius(cr, cr)
                    )
                    drawRoundRect(
                        color = accent.copy(alpha = 0.32f),
                        topLeft = Offset(center.x - r, center.y - r),
                        size = Size(r * 2f, r * 2f),
                        cornerRadius = CornerRadius(cr, cr),
                        style = Stroke(width = 2f)
                    )
                }
                else -> {
                    drawCircle(color = dialFill, radius = r, center = center)
                    drawCircle(color = accent.copy(alpha = 0.35f), radius = r, center = center, style = Stroke(width = 2f))
                }
            }

            // Hour/minute ticks
            val tickCount = if (railwayMode) 60 else 12
            for (i in 0 until tickCount) {
                val angle = (i * (360f / tickCount) - 90f) * (PI / 180.0)
                val isMajor = if (railwayMode) i % 5 == 0 else i % 3 == 0
                val inner = if (isMajor) r * 0.72f else r * 0.83f
                val outer = r * 0.90f
                val sw = when {
                    railwayMode && isMajor -> 3.0f
                    isMajor -> 2.4f
                    else -> 1.1f
                }
                drawLine(
                    color = if (isMajor) tickColor else tickColor.copy(alpha = 0.45f),
                    start = Offset(center.x + (inner * cos(angle)).toFloat(), center.y + (inner * sin(angle)).toFloat()),
                    end = Offset(center.x + (outer * cos(angle)).toFloat(), center.y + (outer * sin(angle)).toFloat()),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }

            // Hour hand
            val hourAngle = ((hour + minute / 60f) * 30f - 90f) * (PI / 180.0)
            drawLine(
                color = handMain,
                start = center,
                end = Offset(center.x + (r * 0.48f * cos(hourAngle)).toFloat(), center.y + (r * 0.48f * sin(hourAngle)).toFloat()),
                strokeWidth = 4.2f,
                cap = StrokeCap.Round
            )

            // Minute hand
            val minAngle = ((minute + second / 60f) * 6f - 90f) * (PI / 180.0)
            drawLine(
                color = handMain.copy(alpha = 0.92f),
                start = center,
                end = Offset(center.x + (r * 0.70f * cos(minAngle)).toFloat(), center.y + (r * 0.70f * sin(minAngle)).toFloat()),
                strokeWidth = 2.8f,
                cap = StrokeCap.Round
            )

            // Second hand
            if (showSeconds) {
                val secAngle = (second * 6f - 90f) * (PI / 180.0)
                val secEnd = Offset(center.x + (r * 0.76f * cos(secAngle)).toFloat(), center.y + (r * 0.76f * sin(secAngle)).toFloat())
                drawLine(color = accent, start = center, end = secEnd, strokeWidth = 1.6f, cap = StrokeCap.Round)
                if (railwayMode) {
                    drawCircle(color = accent, radius = 3.5f, center = Offset(center.x + (r * 0.62f * cos(secAngle)).toFloat(), center.y + (r * 0.62f * sin(secAngle)).toFloat()))
                }
            }
            drawCircle(color = accent, radius = 3.6f, center = center)
        }

        if (romanMode) {
            Text(
                text = "XII",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                color = tickColor,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 7.dp)
            )
            Text(
                text = "VI",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                color = tickColor,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp)
            )
        }
    }
}

@Composable
private fun RetroFlipClockFace(
    clock: ClockTelemetryState,
    accent: Color,
    showSeconds: Boolean
) {
    val parts = clock.timeHHmm.split(":")
    val hh = parts.getOrNull(0) ?: "08"
    val mm = parts.getOrNull(1) ?: "30"
    val isLight = PenTheme.colors.isAppleLight
    val flapBg = if (isLight) Color(0xFF1D1D1F) else Color(0xFF0E131D)
    val clockFont = PenTheme.clockFont

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        FlipTile(hh, flapBg, Color.White, clockFont)
        Text(":", color = accent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        FlipTile(mm, flapBg, Color.White, clockFont)
        if (showSeconds) {
            Text(
                text = clock.secondsStr,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = clockFont, fontSize = 10.sp),
                color = accent,
                modifier = Modifier.align(Alignment.Bottom).padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun FlipTile(
    digits: String,
    bg: Color,
    fg: Color,
    fontFamily: androidx.compose.ui.text.font.FontFamily
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = bg,
        modifier = Modifier.size(width = 48.dp, height = 56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digits,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = fontFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = fg
            )
            // Classic split-flap center hinge line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(Color(0x99000000))
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun KdeModernMinimalFace(
    clock: ClockTelemetryState,
    accent: Color,
    showSeconds: Boolean
) {
    val parts = clock.timeHHmm.split(":")
    val hh = parts.getOrNull(0) ?: "08"
    val mm = parts.getOrNull(1) ?: "30"
    val clockFont = PenTheme.clockFont

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent)
        )
        Column(verticalArrangement = Arrangement.spacedBy((-6).dp)) {
            Text(
                text = hh,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = clockFont,
                    fontSize = 32.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = mm,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = clockFont,
                        fontSize = 32.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Light
                    ),
                    color = accent
                )
                if (showSeconds) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = clock.secondsStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = clockFont, fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

private val DOT_GLYPHS: Map<Char, IntArray> = mapOf(
    '0' to intArrayOf(0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110),
    '1' to intArrayOf(0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110),
    '2' to intArrayOf(0b01110, 0b10001, 0b00001, 0b00110, 0b01000, 0b10000, 0b11111),
    '3' to intArrayOf(0b11110, 0b00001, 0b00001, 0b01110, 0b00001, 0b00001, 0b11110),
    '4' to intArrayOf(0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010),
    '5' to intArrayOf(0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110),
    '6' to intArrayOf(0b01110, 0b10000, 0b11110, 0b10001, 0b10001, 0b10001, 0b01110),
    '7' to intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000),
    '8' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110),
    '9' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100),
    ':' to intArrayOf(0b00000, 0b00100, 0b00100, 0b00000, 0b00100, 0b00100, 0b00000)
)

@Composable
private fun DotMatrixClockFace(
    clock: ClockTelemetryState,
    accent: Color
) {
    val text = clock.timeHHmm
    val dimDot = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Canvas(modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 4.dp)) {
        val totalCols = text.length * 6 - 1
        val step = minOf(size.width / totalCols, size.height / 7f)
        val dotR = step * 0.36f
        val startX = (size.width - totalCols * step) / 2f
        val startY = (size.height - 7 * step) / 2f

        text.forEachIndexed { chIdx, ch ->
            val glyph = DOT_GLYPHS[ch] ?: DOT_GLYPHS['0']!!
            val baseCol = chIdx * 6
            for (row in 0 until 7) {
                val bits = glyph[row]
                for (col in 0 until 5) {
                    val on = ((bits ushr (4 - col)) and 1) == 1
                    drawCircle(
                        color = if (on) accent else dimDot,
                        radius = dotR,
                        center = Offset(startX + (baseCol + col + 0.5f) * step, startY + (row + 0.5f) * step)
                    )
                }
            }
        }
    }
}
