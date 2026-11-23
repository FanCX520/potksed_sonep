package com.example.system

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Movie
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.prefs.PenConfig
import com.example.ui.theme.PenTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Downsampling LRU cache for wallpaper & custom icon bitmaps tuned for 1GB RAM / 640x172 pen displays.
 * Prevents main-thread disk decoding spikes and eliminates Mali-400 GPU texture stalls.
 */
object BitmapDecodeCache {
    private val cache = LinkedHashMap<String, ImageBitmap>(12, 0.75f, true)

    fun decodeSampled(path: String, maxWidthPx: Int = 720, maxHeightPx: Int = 360): ImageBitmap? {
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        val key = "$path@${file.lastModified()}_${maxWidthPx}x$maxHeightPx"
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val decoded = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxWidthPx || bounds.outHeight / sample > maxHeightPx) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
        }.getOrNull()

        if (decoded != null) {
            synchronized(cache) {
                if (cache.size >= 12) {
                    cache.entries.firstOrNull()?.let { cache.remove(it.key) }
                }
                cache[key] = decoded
            }
        }
        return decoded
    }

    fun peekMemoryCache(path: String, maxWidthPx: Int = 720, maxHeightPx: Int = 360): ImageBitmap? {
        if (path.isBlank()) return null
        val prefix = "$path@"
        val suffix = "_${maxWidthPx}x$maxHeightPx"
        return synchronized(cache) {
            cache.entries.firstOrNull { it.key.startsWith(prefix) && it.key.endsWith(suffix) }?.value
        }
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
    }
}

/**
 * Asynchronous downsampled bitmap loader that NEVER touches disk or BitmapFactory on Main Thread.
 */
@Composable
fun rememberSampledBitmap(
    path: String,
    maxWidthPx: Int = 720,
    maxHeightPx: Int = 360
): ImageBitmap? {
    val cached = remember(path, maxWidthPx, maxHeightPx) {
        BitmapDecodeCache.peekMemoryCache(path, maxWidthPx, maxHeightPx)
    }
    val state = produceState<ImageBitmap?>(initialValue = cached, path, maxWidthPx, maxHeightPx) {
        if (path.isBlank()) {
            value = null
            return@produceState
        }
        val mem = BitmapDecodeCache.peekMemoryCache(path, maxWidthPx, maxHeightPx)
        if (mem != null) {
            value = mem
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            BitmapDecodeCache.decodeSampled(path, maxWidthPx, maxHeightPx)
        }
    }
    return state.value
}

/**
 * Unified 4-Mode Dynamic Wallpaper + Dual-Theme Static Wallpaper Backdrop:
 * 1) procedural: GPU-friendly throttled 24fps Canvas art (aurora_flow / star_trails / soft_rain / gallery_breeze)
 * 2) gif: API-21 native android.graphics.Movie local GIF animation
 * 3) mp4: Hardware-decoded TextureView + muted looping MediaPlayer
 * 4) live: Transparent canvas passing through Android System LiveWallpaper (FLAG_SHOW_WALLPAPER)
 * + static: Independent Dark StandBy / Apple White Gallery static wallpapers
 */
@Composable
fun PenDynamicWallpaperBackdrop(
    config: PenConfig,
    isAppleLight: Boolean,
    forFullScreenClock: Boolean = false,
    animateEnabled: Boolean = !config.lowMemMode
) {
    when (config.dynamicWallpaperMode) {
        PenConfig.DYN_WALLPAPER_LIVE -> {
            // Transparent window backdrop so system Android LiveWallpaper shows underneath!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isAppleLight) Color(0x38FFFFFF) else Color(0x4506080E))
            )
            return
        }

        PenConfig.DYN_WALLPAPER_PROCEDURAL -> {
            ProceduralLiveBackdrop(
                style = config.proceduralWallpaperStyle,
                isAppleLight = isAppleLight,
                animate = animateEnabled
            )
            return
        }

        PenConfig.DYN_WALLPAPER_GIF -> {
            val file = remember(config.dynamicWallpaperMediaPath) {
                config.dynamicWallpaperMediaPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
            }
            if (file != null) {
                GifWallpaperPlayer(file = file, animate = animateEnabled)
                if (!forFullScreenClock) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isAppleLight) Color(0x66F3F6F6) else Color(0x5506080E))
                    )
                }
                return
            }
        }

        PenConfig.DYN_WALLPAPER_MP4 -> {
            val file = remember(config.dynamicWallpaperMediaPath) {
                config.dynamicWallpaperMediaPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
            }
            if (file != null) {
                Mp4WallpaperPlayer(file = file, playing = animateEnabled)
                if (!forFullScreenClock) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isAppleLight) Color(0x55F3F6F6) else Color(0x4806080E))
                    )
                }
                return
            }
        }
    }

    // Default / Fallback: Independent Static Theme Wallpaper (downsampled & cached)
    StaticThemeWallpaperBackdrop(
        isAppleLight = isAppleLight,
        presetId = config.activeWallpaperPresetFor(isAppleLight),
        customPath = config.activeCustomWallpaperPathFor(isAppleLight),
        forFullScreenClock = forFullScreenClock
    )
}

@Composable
fun StaticThemeWallpaperBackdrop(
    isAppleLight: Boolean,
    presetId: String,
    customPath: String,
    forFullScreenClock: Boolean = false
) {
    val zeroOverdraw = PenTheme.zeroOverdraw
    if (isAppleLight && (presetId == PenConfig.WALLPAPER_PURE_MIST || (zeroOverdraw && presetId != PenConfig.WALLPAPER_CUSTOM && presetId != PenConfig.WALLPAPER_GALLERY_LIGHT && presetId != PenConfig.WALLPAPER_WARM_PAPER))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PenTheme.colors.canvasBackground)
        )
        return
    }
    if (!isAppleLight && (presetId == PenConfig.WALLPAPER_OBSIDIAN || (zeroOverdraw && presetId != PenConfig.WALLPAPER_CUSTOM && presetId != PenConfig.WALLPAPER_AURORA && presetId != PenConfig.WALLPAPER_INK))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PenTheme.colors.obsidian)
        )
        return
    }

    val customBitmap = if (presetId == PenConfig.WALLPAPER_CUSTOM && customPath.isNotBlank()) {
        rememberSampledBitmap(customPath, maxWidthPx = 720, maxHeightPx = 320)
    } else null

    if (isAppleLight) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PenTheme.colors.canvasBackground)
        ) {
            when {
                presetId == PenConfig.WALLPAPER_CUSTOM && customBitmap != null -> {
                    Image(
                        bitmap = customBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (!forFullScreenClock) {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0x99F3F6F6)))
                    }
                }
                presetId == PenConfig.WALLPAPER_GALLERY_LIGHT -> {
                    Image(
                        painter = painterResource(id = R.drawable.img_wallpaper_gallery_light),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (!forFullScreenClock) {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0x88F3F6F6)))
                    }
                }
                presetId == PenConfig.WALLPAPER_WARM_PAPER -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(Color(0xFFFBF8F3), Color(0xFFF3ECE1), Color(0xFFEBE4D8))))
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.horizontalGradient(listOf(Color(0xFFF3F6F6), Color(0xFFEAF0F5), Color(0xFFF5F6F8))))
                    )
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PenTheme.colors.obsidian)
    ) {
        when {
            presetId == PenConfig.WALLPAPER_CUSTOM && customBitmap != null -> {
                Image(
                    bitmap = customBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            presetId == PenConfig.WALLPAPER_AURORA -> {
                Image(
                    painter = painterResource(id = R.drawable.img_wallpaper_aurora),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            presetId == PenConfig.WALLPAPER_INK -> {
                Image(
                    painter = painterResource(id = R.drawable.img_wallpaper_ink),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> Unit
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (forFullScreenClock) Color(0x5206080E) else Color(0x7807090F))
        )
    }
}

@Composable
private fun ProceduralLiveBackdrop(
    style: String,
    isAppleLight: Boolean,
    animate: Boolean
) {
    var tickMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(animate, style) {
        if (!animate) return@LaunchedEffect
        val start = SystemClock.uptimeMillis()
        while (true) {
            tickMs = SystemClock.uptimeMillis() - start
            delay(42L) // ~24fps ultra-lightweight cadence for MT6580 GPU
        }
    }
    val phase = (tickMs / 1000f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val baseColors = if (isAppleLight) {
            listOf(Color(0xFFF3F6FA), Color(0xFFEAF2F8), Color(0xFFF8F5F0))
        } else {
            listOf(Color(0xFF050812), Color(0xFF0B1426), Color(0xFF070A12))
        }
        drawRect(Brush.linearGradient(baseColors))

        when (style) {
            PenConfig.PROC_STAR_TRAILS -> {
                val starColor = if (isAppleLight) Color(0xFF0071E3) else Color(0xFFBAE6FD)
                for (i in 0 until 26) {
                    val angle = phase * (0.08f + (i % 5) * 0.025f) + i * 0.7f
                    val radius = (0.14f + (i % 7) * 0.11f) * maxOf(w, h) * 0.6f
                    val cx = w * 0.5f + cos(angle) * radius
                    val cy = h * 0.5f + sin(angle) * radius * 0.42f
                    val alpha = (0.28f + 0.45f * ((sin(phase * 1.4f + i) + 1f) / 2f)).coerceIn(0.15f, 0.85f)
                    drawCircle(color = starColor.copy(alpha = alpha), radius = 1.6f + (i % 3), center = Offset(cx, cy))
                }
            }
            PenConfig.PROC_SOFT_RAIN -> {
                val dropColor = if (isAppleLight) Color(0x550071E3) else Color(0x6638BDF8)
                for (i in 0 until 22) {
                    val x = ((i * 73f + phase * 18f) % w)
                    val y = ((i * 37f + phase * (90f + (i % 4) * 28f)) % (h + 40f)) - 20f
                    drawLine(
                        color = dropColor,
                        start = Offset(x, y),
                        end = Offset(x - 6f, y + 18f),
                        strokeWidth = 1.6f
                    )
                }
            }
            PenConfig.PROC_GALLERY_BREEZE -> {
                val c1 = if (isAppleLight) Color(0x440071E3) else Color(0x4438BDF8)
                val c2 = if (isAppleLight) Color(0x38FF7A59) else Color(0x44FF6B4A)
                val x1 = w * (0.25f + 0.16f * sin(phase * 0.45f))
                val y1 = h * (0.40f + 0.18f * cos(phase * 0.35f))
                val x2 = w * (0.75f + 0.14f * cos(phase * 0.40f))
                val y2 = h * (0.58f + 0.18f * sin(phase * 0.50f))
                drawCircle(
                    brush = Brush.radialGradient(listOf(c1, Color.Transparent), Offset(x1, y1), w * 0.42f),
                    radius = w * 0.42f,
                    center = Offset(x1, y1)
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(c2, Color.Transparent), Offset(x2, y2), w * 0.40f),
                    radius = w * 0.40f,
                    center = Offset(x2, y2)
                )
            }
            else -> { // aurora_flow
                val c1 = if (isAppleLight) Color(0x4438BDF8) else Color(0x5538BDF8)
                val c2 = if (isAppleLight) Color(0x3834D399) else Color(0x4834D399)
                val c3 = if (isAppleLight) Color(0x35FF6B4A) else Color(0x45FF6B4A)
                val o1 = Offset(w * (0.20f + 0.22f * sin(phase * 0.42f)), h * (0.35f + 0.25f * cos(phase * 0.31f)))
                val o2 = Offset(w * (0.55f + 0.20f * cos(phase * 0.36f)), h * (0.62f + 0.20f * sin(phase * 0.48f)))
                val o3 = Offset(w * (0.82f + 0.15f * sin(phase * 0.52f + 1.2f)), h * (0.38f + 0.22f * cos(phase * 0.38f)))
                drawCircle(Brush.radialGradient(listOf(c1, Color.Transparent), o1, w * 0.45f), w * 0.45f, o1)
                drawCircle(Brush.radialGradient(listOf(c2, Color.Transparent), o2, w * 0.42f), w * 0.42f, o2)
                drawCircle(Brush.radialGradient(listOf(c3, Color.Transparent), o3, w * 0.38f), w * 0.38f, o3)
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun GifWallpaperPlayer(file: File, animate: Boolean) {
    val movie = remember(file.absolutePath, file.lastModified()) {
        runCatching {
            FileInputStream(file).use { Movie.decodeStream(it) }
        }.getOrNull()
    }
    if (movie == null || movie.width() <= 0 || movie.height() <= 0) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0E17)))
        return
    }
    val duration = movie.duration().takeIf { it > 0 } ?: 1200
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(movie, animate) {
        if (!animate) return@LaunchedEffect
        val start = SystemClock.uptimeMillis()
        while (true) {
            elapsed = (SystemClock.uptimeMillis() - start) % duration
            delay(45L)
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        movie.setTime(elapsed.toInt())
        val scale = maxOf(size.width / movie.width().toFloat(), size.height / movie.height().toFloat())
        val dx = (size.width - movie.width() * scale) / 2f
        val dy = (size.height - movie.height() * scale) / 2f
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(dx, dy)
            native.scale(scale, scale)
            movie.draw(native, 0f, 0f)
            native.restore()
        }
    }
}

@Composable
private fun Mp4WallpaperPlayer(file: File, playing: Boolean) {
    val playerHolder = remember(file.absolutePath) { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(file.absolutePath) {
        onDispose {
            runCatching {
                playerHolder[0]?.stop()
                playerHolder[0]?.release()
                playerHolder[0] = null
            }
        }
    }
    LaunchedEffect(playing) {
        runCatching {
            val mp = playerHolder[0] ?: return@runCatching
            if (playing && !mp.isPlaying) mp.start() else if (!playing && mp.isPlaying) mp.pause()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        runCatching {
                            val mp = MediaPlayer().apply {
                                setDataSource(file.absolutePath)
                                setSurface(Surface(st))
                                isLooping = true
                                setVolume(0f, 0f)
                                prepareAsync()
                                setOnPreparedListener {
                                    if (playing) it.start()
                                }
                            }
                            playerHolder[0] = mp
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        runCatching {
                            playerHolder[0]?.stop()
                            playerHolder[0]?.release()
                            playerHolder[0] = null
                        }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        }
    )
}

fun buildSystemLiveWallpaperChooserIntent(): Intent =
    Intent("android.service.wallpaper.LIVE_WALLPAPER_CHOOSER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
