package com.example.system

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    /**
     * Copies an external font (.ttf/.otf) from URI or local path into internal filesDir.
     */
    suspend fun importFontFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "penos_fonts").apply { mkdirs() }
            val outFile = File(dir, "custom_poetry_font_${System.currentTimeMillis()}.ttf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("无法打开字体文件流")
            if (outFile.length() < 4096L) {
                outFile.delete()
                throw IllegalStateException("字体文件无效或过小")
            }
            outFile.absolutePath
        }
    }

    suspend fun importFontFromPath(context: Context, rawPath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = rawPath.trim().removePrefix("file://")
            val src = File(clean)
            if (!src.exists() || !src.isFile) throw IllegalArgumentException("字体文件不存在：$clean")
            val dir = File(context.filesDir, "penos_fonts").apply { mkdirs() }
            val outFile = File(dir, "custom_poetry_font_${System.currentTimeMillis()}.ttf")
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
