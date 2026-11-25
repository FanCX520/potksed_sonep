package com.example.system

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Single-entry bounded static wallpaper cache decoded strictly on Dispatchers.IO in RGB_565
 * to keep MT6582 / 1GB RAM footprint minimal and avoid main-thread frame drops.
 */
object StaticWallpaperCache {
    @Volatile
    private var cachedKey: String = ""

    @Volatile
    private var cachedBitmap: ImageBitmap? = null

    fun peek(key: String): ImageBitmap? = if (key == cachedKey) cachedBitmap else null

    suspend fun loadFile(path: String, maxDimPx: Int = 800): ImageBitmap? {
        if (path.isBlank()) return null
        if (path == cachedKey && cachedBitmap != null) return cachedBitmap
        return withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists() || !file.isFile) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bmp = BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
            if (bmp != null) {
                cachedKey = path
                cachedBitmap = bmp
            }
            bmp
        }
    }

    suspend fun loadDrawable(context: Context, resId: Int, maxDimPx: Int = 800): ImageBitmap? {
        val key = "res:$resId"
        if (key == cachedKey && cachedBitmap != null) return cachedBitmap
        return withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resId, bounds)
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bmp = BitmapFactory.decodeResource(context.resources, resId, opts)?.asImageBitmap()
            if (bmp != null) {
                cachedKey = key
                cachedBitmap = bmp
            }
            bmp
        }
    }

    fun clear() {
        cachedKey = ""
        cachedBitmap = null
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / sample > maxDim || height / sample > maxDim) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }
}

/**
 * Zero-crash static image & TXT/CSV importer compatible with stripped Android 5.0.2 ROMs.
 */
object SafeMediaImporter {

    fun buildSafeImagePickerIntent(context: Context): Intent? {
        val pm = context.packageManager
        val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        return when {
            pickIntent.resolveActivity(pm) != null && getContentIntent.resolveActivity(pm) != null -> {
                Intent.createChooser(pickIntent, "选择静态壁纸（图库 / MT管理器）").apply {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(getContentIntent))
                }
            }
            pickIntent.resolveActivity(pm) != null -> pickIntent
            getContentIntent.resolveActivity(pm) != null -> Intent.createChooser(getContentIntent, "选择静态壁纸")
            else -> null
        }
    }

    fun buildSafeFilePickerIntent(context: Context, mimeType: String = "*/*", title: String = "选择词库文件（TXT/CSV）"): Intent? {
        val pm = context.packageManager
        val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        if (getContent.resolveActivity(pm) != null) {
            return Intent.createChooser(getContent, title)
        }
        val fallback = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
        return if (fallback.resolveActivity(pm) != null) Intent.createChooser(fallback, title) else null
    }

    suspend fun importImageFromUri(
        context: Context,
        uri: Uri,
        targetName: String = "static_wallpaper",
        maxDimensionPx: Int = 960
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: throw IllegalStateException("无法读取所选图片")
            saveBitmapToInternal(context, bitmap, targetName)
        }
    }

    suspend fun importImageFromFilePath(
        context: Context,
        rawPath: String,
        targetName: String = "static_wallpaper",
        maxDimensionPx: Int = 960
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = rawPath.trim().removePrefix("file://")
            val file = File(clean)
            if (!file.exists() || !file.isFile) throw IllegalArgumentException("文件不存在：$clean")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
                ?: throw IllegalStateException("图片格式不支持或无读取权限")
            saveBitmapToInternal(context, bitmap, targetName)
        }
    }

    suspend fun readTextFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw IllegalStateException("无法读取文件内容")
        }
    }

    suspend fun readTextFromPath(rawPath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = rawPath.trim().removePrefix("file://")
            val file = File(clean)
            if (!file.exists() || !file.isFile) throw IllegalArgumentException("文件不存在：$clean")
            file.readText(Charsets.UTF_8)
        }
    }

    private fun saveBitmapToInternal(context: Context, bitmap: Bitmap, targetName: String): String {
        val dir = File(context.filesDir, "penos_wallpaper").apply { mkdirs() }
        val outFile = File(dir, "$targetName.jpg")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 86, fos)
        }
        bitmap.recycle()
        StaticWallpaperCache.clear()
        return outFile.absolutePath
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / sample > maxDim || height / sample > maxDim) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }
}
