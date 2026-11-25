package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class PenMonoPalette(
    val isDark: Boolean,
    val canvas: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val hairline: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val invertedBg: Color,
    val invertedInk: Color,
    val scrimOnWallpaper: Color,
    val danger: Color,
    val success: Color
)

val ObsidianDarkPalette = PenMonoPalette(
    isDark = true,
    canvas = Color(0xFF000000),
    surface = Color(0xFF0E0E10),
    surfaceAlt = Color(0xFF1A1A1E),
    hairline = Color(0xFF28282E),
    ink = Color(0xFFFFFFFF),
    inkMuted = Color(0xFF9A9AA2),
    accent = Color(0xFFFF453A),
    onAccent = Color(0xFFFFFFFF),
    invertedBg = Color(0xFFFFFFFF),
    invertedInk = Color(0xFF000000),
    scrimOnWallpaper = Color(0xAA000000),
    danger = Color(0xFFFF453A),
    success = Color(0xFF32D74B)
)

val SwissPaperLightPalette = PenMonoPalette(
    isDark = false,
    canvas = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF0F0F3),
    hairline = Color(0xFFDFDFE4),
    ink = Color(0xFF0A0A0C),
    inkMuted = Color(0xFF66666E),
    accent = Color(0xFF0071E3),
    onAccent = Color(0xFFFFFFFF),
    invertedBg = Color(0xFF0A0A0C),
    invertedInk = Color(0xFFFFFFFF),
    scrimOnWallpaper = Color(0xB3FAFAFA),
    danger = Color(0xFFD93025),
    success = Color(0xFF1E8E3E)
)
