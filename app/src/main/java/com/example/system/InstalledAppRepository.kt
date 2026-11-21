package com.example.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val launchActivityName: String = "",
    val homeActivityName: String = "",
    val isHomeLauncher: Boolean = false,
    val isLikelyScanApp: Boolean = false,
    val iconBitmap: Bitmap? = null
)

class InstalledAppRepository(private val context: Context) {

    private val scanKeywords = listOf(
        "扫学", "学王", "久学", "词典", "扫描", "查词", "有道",
        "xuewang", "saoxue", "jiuxue", "scan", "dict", "pen", "youdao", "translate", "ocr"
    )

    suspend fun loadInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val selfPkg = context.packageName

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homeActivityMap = mutableMapOf<String, String>()
        for (ri in queryIntentActivitiesCompat(pm, homeIntent)) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName != selfPkg) {
                homeActivityMap[ai.packageName] = ai.name
            }
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = queryIntentActivitiesCompat(pm, launcherIntent)

        val seen = HashSet<String>()
        val result = mutableListOf<InstalledAppInfo>()

        for (resolveInfo in activities) {
            val ai = resolveInfo.activityInfo ?: continue
            val pkg = ai.packageName ?: continue
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
                    launchActivityName = ai.name.orEmpty(),
                    homeActivityName = homeActivityMap[pkg].orEmpty(),
                    isHomeLauncher = homeActivityMap.containsKey(pkg),
                    isLikelyScanApp = likelyScan,
                    iconBitmap = smallIcon
                )
            )
        }

        // Also include non-launcher HOME packages (e.g., stock Jiuxuewang home shells!)
        for ((homePkg, homeAct) in homeActivityMap) {
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
                    launchActivityName = "",
                    homeActivityName = homeAct,
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

    /**
     * Lists exported activities inside a package (helpful for picking the exact Scan Activity on Jiuxuewang pens).
     */
    suspend fun listExportedActivities(packageName: String): List<String> = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext emptyList()
        try {
            @Suppress("DEPRECATION")
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
                )
            } else {
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            }
            pkgInfo.activities
                ?.filter { it.exported && it.enabled }
                ?.map { it.name }
                ?.distinct()
                .orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Launches an explicit package + optional activity class (isolating task stack for Jiuxuewang Scan Activity).
     */
    fun launchComponent(
        packageName: String,
        activityClass: String = "",
        isolateScanStack: Boolean = false
    ): Boolean {
        if (packageName.isBlank()) return false
        AntiLockCoordinator.allowIntentionalLaunch(packageName, activityClass)

        val pm = context.packageManager

        // 1. Explicit ComponentName if provided (critical for Jiuxuewang Scan Activity!)
        if (activityClass.isNotBlank()) {
            val normalizedClass = if (activityClass.startsWith(".")) "$packageName$activityClass" else activityClass
            val explicit = Intent().apply {
                component = ComponentName(packageName, normalizedClass)
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (isolateScanStack) {
                    addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                }
            }
            if (tryStartActivity(explicit)) return true
        }

        // 2. Standard getLaunchIntentForPackage
        val launchIntent = pm.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (launchIntent != null && tryStartActivity(launchIntent)) return true

        // 3. Explicit CATEGORY_LAUNCHER resolution via queryIntentActivities (fixes API 21 stripped ROMs)
        val queryLauncher = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        val launcherMatches = queryIntentActivitiesCompat(pm, queryLauncher)
        for (ri in launcherMatches) {
            val ai = ri.activityInfo ?: continue
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(ai.packageName, ai.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (tryStartActivity(intent)) return true
        }

        // 4. Fallback to CATEGORY_HOME or leanback/main entry
        val fallback = Intent(Intent.ACTION_MAIN).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(fallback)
    }

    fun launchPackage(packageName: String): Boolean = launchComponent(packageName)

    private fun tryStartActivity(intent: Intent): Boolean {
        return try {
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
        if (drawable == null || sizePx <= 0) return null
        return try {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
