package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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

private val ZeroOverdrawDarkColors = PenOsExtendedColors(
    isAppleLight = false,
    pureBlack = Color(0xFF06080D),
    obsidian = Color(0xFF06080D),
    canvasBackground = Color(0xFF06080D),
    glassCard = Color(0xFF12161F),
    glassCardElevated = Color(0xFF161B26),
    secondaryTintCard = Color(0xFF1A2231),
    chipSurface = Color(0xFF1E2636),
    glassBorder = Color(0xFF232B3A),
    glassBorderBright = Color(0xFF334155),
    showCardBorder = true,
    cardCornerRadius = 16.dp,
    overlayScrim = Color(0xE605070B)
)

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

private val ZeroOverdrawLightColors = AppleLightExtendedColors.copy(
    obsidian = Color(0xFFF2F4F7),
    canvasBackground = Color(0xFFF2F4F7),
    glassCard = Color(0xFFFFFFFF),
    glassCardElevated = Color(0xFFFFFFFF),
    chipSurface = Color(0xFFE9EEF4),
    glassBorder = Color(0xFFE2E8F0),
    glassBorderBright = Color(0xFFCBD5E1),
    showCardBorder = true,
    cardCornerRadius = 16.dp,
    overlayScrim = Color(0xE6F2F4F7)
)

val LocalPenOsColors = staticCompositionLocalOf { ZeroOverdrawDarkColors }
val LocalPoetryFontFamily = staticCompositionLocalOf<FontFamily> { NotoSerifScFontFamily }
val LocalClockFontFamily = staticCompositionLocalOf<FontFamily> { UnifiedClockFontFamily }
val LocalTypographyProfile = staticCompositionLocalOf { GranularTypographyProfile() }
val LocalPenMotionTier = staticCompositionLocalOf { PenMotionTier.FAST }
val LocalZeroOverdraw = staticCompositionLocalOf { true }

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
    externalPoetryFontPath: String = "",
    typographyProfile: GranularTypographyProfile = GranularTypographyProfile(),
    motionTier: PenMotionTier = PenMotionTier.FAST,
    zeroOverdraw: Boolean = true,
    content: @Composable () -> Unit
) {
    val extendedColors = when {
        zeroOverdraw && isAppleLight -> ZeroOverdrawLightColors
        zeroOverdraw && !isAppleLight -> ZeroOverdrawDarkColors
        isAppleLight -> AppleLightExtendedColors
        else -> DarkExtendedColors
    }
    val colorScheme = if (isAppleLight) AppleLightColorScheme else PenOsDarkColorScheme
    val effectiveProfile = remember(typographyProfile, externalPoetryFontPath) {
        if (externalPoetryFontPath.isNotBlank() && typographyProfile.poetry.externalPath.isBlank()) {
            typographyProfile.copy(
                poetry = typographyProfile.poetry.copy(
                    familyKey = PenFontCatalog.FAMILY_EXTERNAL,
                    externalPath = externalPoetryFontPath
                )
            )
        } else {
            typographyProfile
        }
    }
    val typography = remember(effectiveProfile, isAppleLight) {
        buildDynamicTypography(effectiveProfile, isAppleLight)
    }
    val poetryFont = remember(effectiveProfile.poetry) {
        PenFontCatalog.resolve(effectiveProfile.poetry.familyKey, effectiveProfile.poetry.externalPath)
    }
    val clockFont = remember(effectiveProfile.clock) {
        PenFontCatalog.resolve(effectiveProfile.clock.familyKey, effectiveProfile.clock.externalPath)
    }

    CompositionLocalProvider(
        LocalPenOsColors provides extendedColors,
        LocalPoetryFontFamily provides poetryFont,
        LocalClockFontFamily provides clockFont,
        LocalTypographyProfile provides effectiveProfile,
        LocalPenMotionTier provides motionTier,
        LocalZeroOverdraw provides zeroOverdraw
    ) {
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

    val poetryFont: FontFamily
        @Composable get() = LocalPoetryFontFamily.current

    val clockFont: FontFamily
        @Composable get() = LocalClockFontFamily.current

    val typographyProfile: GranularTypographyProfile
        @Composable get() = LocalTypographyProfile.current

    val motionTier: PenMotionTier
        @Composable get() = LocalPenMotionTier.current

    val zeroOverdraw: Boolean
        @Composable get() = LocalZeroOverdraw.current
}
