package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class PenOsExtendedColors(
    val pureBlack: Color = PureBlack,
    val obsidian: Color = ObsidianBase,
    val glassCard: Color = SlateCard,
    val glassCardElevated: Color = SlateCardElevated,
    val glassBorder: Color = GlassBorder,
    val glassBorderBright: Color = GlassBorderBright,
    val standByCoral: Color = StandByCoral,
    val standByAmber: Color = StandByAmber,
    val glacierBlue: Color = CarPlayGlacier,
    val vocabEmerald: Color = VocabEmerald,
    val poetryGold: Color = PoetryGold,
    val dangerRose: Color = DangerRose,
    val textMuted: Color = TextMuted
)

val LocalPenOsColors = staticCompositionLocalOf { PenOsExtendedColors() }

private val PenOsDarkColorScheme = darkColorScheme(
    primary = StandByCoral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3E1C14),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = CarPlayGlacier,
    onSecondary = Color(0xFF041D2A),
    secondaryContainer = Color(0xFF102A3A),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = VocabEmerald,
    onTertiary = Color(0xFF04261B),
    background = ObsidianBase,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF1E2636),
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    error = DangerRose,
    onError = Color.White
)

@Composable
fun PenOsTheme(
    content: @Composable () -> Unit
) {
    val extendedColors = PenOsExtendedColors()
    CompositionLocalProvider(LocalPenOsColors provides extendedColors) {
        MaterialTheme(
            colorScheme = PenOsDarkColorScheme,
            typography = PenOsTypography,
            content = content
        )
    }
}

object PenTheme {
    val colors: PenOsExtendedColors
        @Composable get() = LocalPenOsColors.current
}
