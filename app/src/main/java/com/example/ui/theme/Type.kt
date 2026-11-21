package com.example.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import java.io.File

/**
 * Unified sculpted clock display font shared across BOTH Dark StandBy CarPlay and Apple White Gallery.
 * Guarantees consistent clock numbers across Left Dock, StandBy giant clock, and Full-Screen Pomodoro.
 */
val OutfitFontFamily = FontFamily(
    Font(R.font.outfit, FontWeight.Normal),
    Font(R.font.outfit, FontWeight.Medium),
    Font(R.font.outfit, FontWeight.SemiBold),
    Font(R.font.outfit, FontWeight.Bold)
)

val UnifiedClockFontFamily: FontFamily = OutfitFontFamily

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

// Apple SF Pro Display / SF Pro Text substitute for body/UI hierarchy in White Gallery mode
val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold)
)

// Bundled Noto Serif SC (思源宋体) dedicated to Classical Chinese Poetry (今日诗词)
val NotoSerifScFontFamily = FontFamily(
    Font(R.font.noto_serif_sc, FontWeight.Normal),
    Font(R.font.noto_serif_sc, FontWeight.Medium),
    Font(R.font.noto_serif_sc, FontWeight.SemiBold)
)

/**
 * Loads an optional user-specified external .ttf/.otf font (e.g. full Noto Serif SC from /sdcard/Fonts/),
 * gracefully falling back to the bundled NotoSerifScFontFamily if missing or invalid.
 */
fun resolvePoetryFontFamily(externalFontPath: String): FontFamily {
    if (externalFontPath.isBlank()) return NotoSerifScFontFamily
    return try {
        val file = File(externalFontPath)
        if (file.exists() && file.isFile && file.length() > 1024L) {
            FontFamily(Typeface.createFromFile(file))
        } else {
            NotoSerifScFontFamily
        }
    } catch (_: Exception) {
        NotoSerifScFontFamily
    }
}

val PenOsTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = UnifiedClockFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 68.sp,
        lineHeight = 72.sp,
        letterSpacing = (-1.6).sp,
        fontFeatureSettings = "tnum"
    ),
    displayMedium = TextStyle(
        fontFamily = UnifiedClockFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineMedium = TextStyle(
        fontFamily = UnifiedClockFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.2.sp
    )
)

// Apple White Gallery Vitrine Typography: shares UnifiedClockFontFamily for clocks, Inter for clean UI copy
val AppleGalleryTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = UnifiedClockFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 68.sp,
        lineHeight = 72.sp,
        letterSpacing = (-1.6).sp,
        fontFeatureSettings = "tnum"
    ),
    displayMedium = TextStyle(
        fontFamily = UnifiedClockFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineMedium = TextStyle(
        fontFamily = UnifiedClockFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.25).sp,
        fontFeatureSettings = "tnum"
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.22).sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.18).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = (-0.20).sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = (-0.12).sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = (-0.14).sp,
        fontFeatureSettings = "tnum"
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = (-0.10).sp,
        fontFeatureSettings = "tnum"
    )
)
