package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.prefs.PenConfig
import com.example.system.StaticWallpaperCache
import com.example.ui.theme.PenTheme

/**
 * Zero-overdraw static background:
 * - WALLPAPER_SOLID: single solid canvas fill (0 image decode, 0 overdraw).
 * - Presets / Custom file: single IO-decoded RGB_565 bitmap + single readability scrim.
 */
@Composable
fun StaticWallpaperBackground(
    config: PenConfig,
    modifier: Modifier = Modifier
) {
    val palette = PenTheme.palette
    val context = LocalContext.current
    val mode = config.staticWallpaperMode
    val customPath = config.customWallpaperPath

    if (mode == PenConfig.WALLPAPER_SOLID) {
        Box(modifier = modifier.fillMaxSize().background(palette.canvas))
        return
    }

    val cacheKey = remember(mode, customPath) {
        when (mode) {
            PenConfig.WALLPAPER_INK -> "res:${R.drawable.img_wallpaper_ink}"
            PenConfig.WALLPAPER_AURORA -> "res:${R.drawable.img_wallpaper_aurora}"
            PenConfig.WALLPAPER_GALLERY -> "res:${R.drawable.img_wallpaper_gallery_light}"
            PenConfig.WALLPAPER_CUSTOM -> customPath
            else -> ""
        }
    }

    var bitmap by remember(cacheKey) { mutableStateOf<ImageBitmap?>(StaticWallpaperCache.peek(cacheKey)) }

    LaunchedEffect(cacheKey) {
        bitmap = when (mode) {
            PenConfig.WALLPAPER_INK -> StaticWallpaperCache.loadDrawable(context, R.drawable.img_wallpaper_ink)
            PenConfig.WALLPAPER_AURORA -> StaticWallpaperCache.loadDrawable(context, R.drawable.img_wallpaper_aurora)
            PenConfig.WALLPAPER_GALLERY -> StaticWallpaperCache.loadDrawable(context, R.drawable.img_wallpaper_gallery_light)
            PenConfig.WALLPAPER_CUSTOM -> StaticWallpaperCache.loadFile(customPath)
            else -> null
        }
    }

    Box(modifier = modifier.fillMaxSize().background(palette.canvas)) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.scrimOnWallpaper)
            )
        }
    }
}

@Composable
fun MonoTopBar(
    title: String,
    subtitle: String = "",
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backTestTag: String = "nav_back_button",
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val palette = PenTheme.palette
    Column(modifier = modifier.fillMaxWidth().background(palette.canvas)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(backTestTag)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = palette.ink
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing
            )
        }
        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
    }
}

@Composable
fun MonoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    useAccent: Boolean = false,
    testTag: String = ""
) {
    val palette = PenTheme.palette
    val bg = when {
        !enabled -> palette.surfaceAlt
        useAccent -> palette.accent
        else -> palette.invertedBg
    }
    val fg = when {
        !enabled -> palette.inkMuted
        useAccent -> palette.onAccent
        else -> palette.invertedInk
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MonoOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    testTag: String = ""
) {
    val palette = PenTheme.palette
    val shape = RoundedCornerShape(10.dp)
    val bg = if (selected) palette.invertedBg else palette.surface
    val fg = if (selected) palette.invertedInk else palette.ink
    val border = if (selected) palette.invertedBg else palette.hairline
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = if (enabled) fg else palette.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MonoCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    borderColor: Color? = null,
    content: @Composable () -> Unit
) {
    val palette = PenTheme.palette
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, borderColor ?: palette.hairline),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
fun MonoSectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = PenTheme.palette.inkMuted,
        modifier = modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

@Composable
fun MonoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    val palette = PenTheme.palette
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(shape)
            .background(if (selected) palette.invertedBg else palette.surfaceAlt)
            .border(1.dp, if (selected) palette.invertedBg else palette.hairline, shape)
            .clickable(onClick = onClick)
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) palette.invertedInk else palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
