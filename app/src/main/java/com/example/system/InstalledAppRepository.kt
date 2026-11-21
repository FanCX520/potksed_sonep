package com.example.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isHomeLauncher: Boolean,
    val isLikelyScanApp: Boolean,
    val iconBitmap: Bitmap? = null
)

class InstalledAppRepository(private val context: Context) {

    private val scanKeywords = listOf(
        "扫学", "学王", "词典", "扫描", "查词", "有道",
        "xuewang", "saoxue", "scan", "dict", "pen", "youdao", "translate", "ocr"
    )

    suspend fun loadInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val selfPkg = context.packageName

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homePackages = queryIntentActivitiesCompat(pm, homeIntent)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = queryIntentActivitiesCompat(pm, launcherIntent)

        val seen = HashSet<String>()
        val result = mutableListOf<InstalledAppInfo>()

        for (resolveInfo in activities) {
            val pkg = resolveInfo.activityInfo?.packageName ?: continue
            if (pkg == selfPkg || !seen.add(pkg)) continue

            val label = resolveInfo.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg }
            val lowerLabel = label.lowercase()
            val lowerPkg = pkg.lowercase()
            val likelyScan = scanKeywords.any { kw -> lowerLabel.contains(kw) || lowerPkg.contains(kw) }

            val smallIcon = try {
                drawableToCompactBitmap(resolveInfo.loadIcon(pm), 72)
            } catch (_: Exception) {
                null
            }

            result.add(
                InstalledAppInfo(
                    packageName = pkg,
                    label = label,
                    isHomeLauncher = homePackages.contains(pkg),
                    isLikelyScanApp = likelyScan,
                    iconBitmap = smallIcon
                )
            )
        }

        // Also include non-launcher HOME packages (common on stock dictionary pens!)
        for (homePkg in homePackages) {
            if (homePkg == selfPkg || !seen.add(homePkg)) continue
            val appInfo = try {
                pm.getApplicationInfo(homePkg, 0)
            } catch (_: Exception) {
                null
            }
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: homePkg
            result.add(
                InstalledAppInfo(
                    packageName = homePkg,
                    label = label,
                    isHomeLauncher = true,
                    isLikelyScanApp = scanKeywords.any {
                        label.lowercase().contains(it) || homePkg.lowercase().contains(it)
                    },
                    iconBitmap = appInfo?.let {
                        try { drawableToCompactBitmap(pm.getApplicationIcon(it), 72) } catch (_: Exception) { null }
                    }
                )
            )
        }

        result.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isLikelyScanApp }
                .thenBy { it.label.lowercase() }
        )
    }

    fun launchPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        AntiLockCoordinator.allowIntentionalLaunch(packageName)
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    setPackage(packageName)
                    addCategory(Intent.CATEGORY_HOME)
                }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun queryIntentActivitiesCompat(pm: PackageManager, intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(intent, 0)
        }

    private fun drawableToCompactBitmap(drawable: Drawable?, sizePx: Int): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
