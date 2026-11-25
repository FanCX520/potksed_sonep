package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalPenPalette = staticCompositionLocalOf { ObsidianDarkPalette }

object PenTheme {
    val palette: PenMonoPalette
        @Composable get() = LocalPenPalette.current
}

@Composable
fun PenOsTheme(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val palette = if (isDark) ObsidianDarkPalette else SwissPaperLightPalette
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.invertedBg,
            onPrimary = palette.invertedInk,
            secondary = palette.accent,
            onSecondary = palette.onAccent,
            background = palette.canvas,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceAlt,
            onSurfaceVariant = palette.inkMuted,
            outline = palette.hairline,
            error = palette.danger,
            onError = palette.onAccent
        )
    } else {
        lightColorScheme(
            primary = palette.invertedBg,
            onPrimary = palette.invertedInk,
            secondary = palette.accent,
            onSecondary = palette.onAccent,
            background = palette.canvas,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceAlt,
            onSurfaceVariant = palette.inkMuted,
            outline = palette.hairline,
            error = palette.danger,
            onError = palette.onAccent
        )
    }

    CompositionLocalProvider(LocalPenPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PenTypography,
            content = content
        )
    }
}
