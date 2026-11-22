package com.example.system

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Zero-crash media/file importer designed for stripped Android 5.0.2 dictionary pens
 * that lack DocumentsUI/Photo Picker and only have Gallery (图库) or MT Manager (MT管理器).
 */
object SafeMediaImporter {

    /**
     * Builds an image picker intent that NEVER uses PickVisualMedia (which crashes on API 21 ROMs).
     * Tries Gallery ACTION_PICK first, falls back to ACTION_GET_CONTENT chooser (which surfaces MT Manager).
     */
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
                Intent.createChooser(pickIntent, "选择图片（图库 / MT管理器）").apply {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(getContentIntent))
                }
            }
            pickIntent.resolveActivity(pm) != null -> pickIntent
            getContentIntent.resolveActivity(pm) != null -> Intent.createChooser(getContentIntent, "选择图片（图库 / MT管理器）")
            else -> null
        }
    }

    /**
     * Builds a safe file picker intent for TXT/CSV wordlists or TTF/OTF fonts via MT Manager or third-party file apps.
     */
    fun buildSafeFilePickerIntent(context: Context, mimeType: String = "*/*", title: String = "选择本地文件（MT管理器）"): Intent? {
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

    /**
     * Imports an image from a Content URI into app-internal filesDir with 1GB-RAM-safe downsampling.
     * Returns the persistent absolute file path so it never loses URI permissions after reboot.
     */
    suspend fun importImageFromUri(
        context: Context,
        uri: Uri,
        targetName: String,
        maxDimensionPx: Int = 960
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: throw IllegalStateException("无法读取所选图片（可能缺少访问权限）")

            saveBitmapToInternal(context, bitmap, targetName)
        }
    }

    /**
     * Imports an image from a direct local filesystem path (e.g. /sdcard/Pictures/bg.jpg copied from MT Manager).
     */
    suspend fun importImageFromFilePath(
        context: Context,
        rawPath: String,
        targetName: String,
        maxDimensionPx: Int = 960
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = rawPath.trim().removePrefix("file://")
            val file = File(clean)
            if (!file.exists() || !file.isFile) {
                throw IllegalArgumentException("文件不存在：$clean")
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                ?: throw IllegalStateException("图片格式不支持或无读取权限")

            saveBitmapToInternal(context, bitmap, targetName)
        }
    }

    const val FONT_WARN_BYTES = 12L * 1024L * 1024L
    const val FONT_MAX_BYTES = 24L * 1024L * 1024L

    data class FontProbeReport(
        val filePath: String,
        val sizeBytes: Long,
        val warningMessage: String? = null
    )

    /**
     * Validates font magic bytes (TTF / OTF / TTC), file size (<= 24MB for 1GB RAM Android 5.0.2),
     * and runs a real offscreen Typeface + Canvas render probe to catch corrupt or OOM-prone fonts
     * BEFORE applying them to the launcher.
     */
    fun validateAndProbeFontFile(file: File): Result<FontProbeReport> = runCatching {
        require(file.exists() && file.isFile) { "字体文件不存在" }
        val size = file.length()
        require(size >= 4096L) { "字体文件过小或已损坏（< 4KB）" }
        require(size <= FONT_MAX_BYTES) {
            "字体体积 ${"%.1f".format(size / 1048576.0)}MB 超过 1GB 内存安全上限（24MB），已拦截以防闪退"
        }

        val header = ByteArray(4)
        FileInputStream(file).use { fis ->
            val read = fis.read(header)
            require(read == 4) { "无法读取字体文件头" }
        }
        val tagStr = String(header, Charsets.ISO_8859_1)
        val isTrueType = header[0] == 0x00.toByte() && header[1] == 0x01.toByte() &&
            header[2] == 0x00.toByte() && header[3] == 0x00.toByte()
        val validMagic = isTrueType || tagStr == "OTTO" || tagStr == "ttcf" || tagStr == "true"
        require(validMagic) { "非有效 TTF/OTF/TTC 字体文件，已安全取消导入" }

        // Sandbox offscreen render test catching RuntimeException AND OutOfMemoryError
        try {
            val tf = Typeface.createFromFile(file)
                ?: throw IllegalStateException("系统无法解析该字体文件")
            val probeBmp = Bitmap.createBitmap(96, 36, Bitmap.Config.RGB_565)
            try {
                val canvas = Canvas(probeBmp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = 20f
                    color = Color.WHITE
                }
                canvas.drawText("永Aa123", 4f, 26f, paint)
            } finally {
                probeBmp.recycle()
            }
        } catch (t: Throwable) {
            throw IllegalStateException("字体试渲染失败（${t.javaClass.simpleName}），已自动回退默认字体")
        }

        val warn = if (size > FONT_WARN_BYTES) {
            "提示：字体体积 ${"%.1f".format(size / 1048576.0)}MB 偏大，如遇卡顿建议换回内置字体"
        } else null
        FontProbeReport(file.absolutePath, size, warn)
    }

    /**
     * Copies an external font (.ttf/.otf) from URI into internal filesDir with OOM & render self-test.
     */
    suspend fun importFontFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "penos_fonts").apply { mkdirs() }
            val outFile = File(dir, "custom_font_${System.currentTimeMillis()}.ttf")
            var copied = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        copied += n
                        if (copied > FONT_MAX_BYTES) {
                            throw IllegalStateException("字体文件超过 24MB 安全上限，已中止导入防 OOM")
                        }
                        output.write(buf, 0, n)
                    }
                }
            } ?: throw IllegalStateException("无法打开字体文件流")

            val probe = validateAndProbeFontFile(outFile)
            if (probe.isFailure) {
                outFile.delete()
                throw probe.exceptionOrNull()!!
            }
            outFile.absolutePath
        }
    }

    suspend fun importFontFromPath(context: Context, rawPath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = rawPath.trim().removePrefix("file://")
            val src = File(clean)
            if (!src.exists() || !src.isFile) throw IllegalArgumentException("字体文件不存在：$clean")
            val preCheck = validateAndProbeFontFile(src)
            if (preCheck.isFailure) throw preCheck.exceptionOrNull()!!

            val dir = File(context.filesDir, "penos_fonts").apply { mkdirs() }
            val outFile = File(dir, "custom_font_${System.currentTimeMillis()}.ttf")
            FileInputStream(src).use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            outFile.absolutePath
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

    /**
     * Copies a GIF or MP4 dynamic wallpaper from a Content URI into app-private filesDir/penos_media.
     */
    suspend fun importDynamicMediaFromUri(
        context: Context,
        uri: Uri,
        extension: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanExt = extension.lowercase().trimStart('.')
            require(cleanExt == "gif" || cleanExt == "mp4") { "仅支持 .gif 或 .mp4 动态壁纸" }
            val dir = File(context.filesDir, "penos_media").apply { mkdirs() }
            val outFile = File(dir, "dyn_${System.currentTimeMillis()}.$cleanExt")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("无法读取所选媒体文件流")
            if (outFile.length() < 1024L) {
                outFile.delete()
                throw IllegalStateException("媒体文件过小或已损坏")
            }
            outFile.absolutePath
        }
    }

    /**
     * Copies a GIF or MP4 dynamic wallpaper from a direct local filesystem path (e.g. from MT Manager).
     */
    suspend fun importDynamicMediaFromPath(
        context: Context,
        rawPath: String
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = rawPath.trim().removePrefix("file://")
            val src = File(clean)
            if (!src.exists() || !src.isFile) throw IllegalArgumentException("文件不存在：$clean")
            val ext = src.extension.lowercase()
            require(ext == "gif" || ext == "mp4") { "仅支持 .gif 或 .mp4 文件" }
            val dir = File(context.filesDir, "penos_media").apply { mkdirs() }
            val outFile = File(dir, "dyn_${System.currentTimeMillis()}.$ext")
            FileInputStream(src).use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            ext to outFile.absolutePath
        }
    }

    private fun saveBitmapToInternal(context: Context, bitmap: Bitmap, targetName: String): String {
        val dir = File(context.filesDir, "penos_media").apply { mkdirs() }
        val outFile = File(dir, "$targetName.jpg")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, fos)
        }
        bitmap.recycle()
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
