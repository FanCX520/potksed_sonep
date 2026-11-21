package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class PenOsExtendedColors(
    val isAppleLight: Boolean = false,
    val pureBlack: Color = PureBlack,
    val obsidian: Color = ObsidianBase,
    val canvasBackground: Color = ObsidianBase,
    val glassCard: Color = SlateCard,
    val glassCardElevated: Color = SlateCardElevated,
    val secondaryTintCard: Color = Color(0xFF1A2231),
    val chipSurface: Color = Color(0x22FFFFFF),
    val glassBorder: Color = GlassBorder,
    val glassBorderBright: Color = GlassBorderBright,
    val showCardBorder: Boolean = true,
    val cardCornerRadius: Dp = 18.dp,
    val primaryAction: Color = StandByCoral,
    val linkAccent: Color = CarPlayGlacier,
    val badgeEmber: Color = StandByAmber,
    val standByCoral: Color = StandByCoral,
    val standByAmber: Color = StandByAmber,
    val glacierBlue: Color = CarPlayGlacier,
    val vocabEmerald: Color = VocabEmerald,
    val poetryGold: Color = PoetryGold,
    val dangerRose: Color = DangerRose,
    val textMuted: Color = TextMuted,
    val overlayScrim: Color = Color(0xD905070B)
)

private val DarkExtendedColors = PenOsExtendedColors()

// Exact Apple White Gallery Vitrine mapping from DESIGN_4374f950.md:
// Pure White canvas (#FFFFFF), Fog Mist (#F3F6F6) & Paper Gray (#FAFAFC) tint cards,
// Silver Smoke (#E8E8ED) chips, ZERO borders / ZERO shadows, Apple Blue (#0071E3) primary CTA,
// Link Blue (#0066CC) secondary links, Ember (#B64400) naked badges.
private val AppleLightExtendedColors = PenOsExtendedColors(
    isAppleLight = true,
    pureBlack = AppleTrueBlack,
    obsidian = AppleFogMist,
    canvasBackground = AppleFogMist,
    glassCard = ApplePureWhite,
    glassCardElevated = ApplePureWhite,
    secondaryTintCard = ApplePaperGray,
    chipSurface = AppleSilverSmoke,
    glassBorder = AppleAshBorder,
    glassBorderBright = AppleAshBorder,
    showCardBorder = false,
    cardCornerRadius = 24.dp,
    primaryAction = AppleBlue,
    linkAccent = AppleLinkBlue,
    badgeEmber = AppleEmber,
    standByCoral = AppleBlue,
    standByAmber = AppleEmber,
    glacierBlue = AppleLinkBlue,
    vocabEmerald = AppleBlue,
    poetryGold = AppleEmber,
    dangerRose = AppleEmber,
    textMuted = AppleGraphite,
    overlayScrim = Color(0xC8F3F6F6)
)

val LocalPenOsColors = staticCompositionLocalOf { DarkExtendedColors }

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

private val AppleLightColorScheme = lightColorScheme(
    primary = AppleBlue,
    onPrimary = ApplePureWhite,
    primaryContainer = AppleSilverSmoke,
    onPrimaryContainer = AppleNearBlack,
    secondary = AppleLinkBlue,
    onSecondary = ApplePureWhite,
    secondaryContainer = AppleFogMist,
    onSecondaryContainer = AppleCharcoal,
    tertiary = AppleEmber,
    onTertiary = ApplePureWhite,
    background = AppleFogMist,
    onBackground = AppleNearBlack,
    surface = ApplePureWhite,
    onSurface = AppleNearBlack,
    surfaceVariant = AppleSilverSmoke,
    onSurfaceVariant = AppleGraphite,
    outline = AppleAshBorder,
    error = AppleEmber,
    onError = ApplePureWhite
)

@Composable
fun PenOsTheme(
    isAppleLight: Boolean = false,
    content: @Composable () -> Unit
) {
    val extendedColors = if (isAppleLight) AppleLightExtendedColors else DarkExtendedColors
    val colorScheme = if (isAppleLight) AppleLightColorScheme else PenOsDarkColorScheme
    val typography = if (isAppleLight) AppleGalleryTypography else PenOsTypography

    CompositionLocalProvider(LocalPenOsColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

object PenTheme {
    val colors: PenOsExtendedColors
        @Composable get() = LocalPenOsColors.current
}
