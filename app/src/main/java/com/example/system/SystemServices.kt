package com.example.system

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            try {
                val svcIntent = Intent(context, PenKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent)
                } else {
                    context.startService(svcIntent)
                }
            } catch (_: Exception) {
            }

            try {
                val launch = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(launch)
            } catch (_: Exception) {
            }
        }
    }
}

class PenLauncherGuardService : AccessibilityService() {

    private var lastPullBackMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        AntiLockCoordinator.guardServiceConnected.set(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString().orEmpty()
        val cls = event.className?.toString().orEmpty()
        if (pkg.isBlank() || pkg == packageName) return

        val shouldBlock = AntiLockCoordinator.shouldPullBackToPenOs(pkg, cls, packageName)
        AntiLockCoordinator.recordWindowEvent(pkg, cls, shouldBlock)

        if (shouldBlock) {
            val now = System.currentTimeMillis()
            if (now - lastPullBackMs > 380L) {
                lastPullBackMs = now
                val backHome = Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                }
                try {
                    startActivity(backHome)
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        AntiLockCoordinator.guardServiceConnected.set(false)
        super.onDestroy()
    }
}

class PenKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.keep_alive_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, openIntent, piFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_penos)
            .setContentTitle(getString(R.string.keep_alive_notification_title))
            .setContentText(getString(R.string.keep_alive_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "penos_keep_alive"
        private const val NOTIFICATION_ID = 1021

        fun start(context: Context) {
            try {
                val intent = Intent(context, PenKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, PenKeepAliveService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}

data class SystemDiagnostics(
    val currentDefaultHomePackage: String,
    val isPenOsDefaultHome: Boolean,
    val isAccessibilityGuardActive: Boolean,
    val isRootAvailable: Boolean,
    val detectedStockHomeComponent: String = ""
)

object SystemTakeoverHelper {

    suspend fun inspectSystem(context: Context): SystemDiagnostics = withContext(Dispatchers.IO) {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(homeIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        val currentHome = resolved?.activityInfo?.packageName ?: "未设置"

        @Suppress("DEPRECATION")
        val allHomes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(homeIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            context.packageManager.queryIntentActivities(homeIntent, 0)
        }
        val stockHome = allHomes.firstOrNull { it.activityInfo?.packageName != context.packageName }
        val stockComp = stockHome?.activityInfo?.let { "${it.packageName}/${it.name}" }.orEmpty()

        val isAccessibility = isAccessibilityEnabled(context) || AntiLockCoordinator.guardServiceConnected.get()
        val hasRoot = checkRootAvailable()
        SystemDiagnostics(
            currentDefaultHomePackage = currentHome,
            isPenOsDefaultHome = currentHome == context.packageName,
            isAccessibilityGuardActive = isAccessibility,
            isRootAvailable = hasRoot,
            detectedStockHomeComponent = stockComp
        )
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, PenLauncherGuardService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledServices.contains(expected) || enabledServices.contains(PenLauncherGuardService::class.java.simpleName)
    }

    fun openHomeSettings(context: Context) {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    fun openAccessibilitySettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openWifiSettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openSystemSettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun canWriteSystemSettings(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(context) else true

    fun openWriteSettingsPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    fun getSystemBrightnessFraction(context: Context): Float {
        return try {
            val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 140)
            (raw / 255f).coerceIn(0.05f, 1f)
        } catch (_: Exception) {
            0.55f
        }
    }

    fun writeSystemBrightness(context: Context, fraction: Float): Boolean {
        if (!canWriteSystemSettings(context)) return false
        return try {
            val value = (fraction.coerceIn(0.04f, 1f) * 255f).toInt().coerceIn(10, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getMediaVolumeFraction(context: Context): Float {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0.5f
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            (cur.toFloat() / max.toFloat()).coerceIn(0f, 1f)
        } catch (_: Exception) {
            0.5f
        }
    }

    fun setMediaVolumeFraction(context: Context, fraction: Float) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val target = (fraction.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (_: Exception) {
        }
    }

    fun buildAdbTakeoverScript(
        selfPackage: String,
        stockLauncherPkg: String,
        stockHomeComponent: String = ""
    ): String {
        val targetStock = stockLauncherPkg.ifBlank { "<原厂桌面包名>" }
        val homeComp = stockHomeComponent.ifBlank { "$targetStock/.HomeActivity" }
        return buildString {
            appendLine("# 1. 将 PenOS 设为默认主屏幕")
            appendLine("adb shell cmd package set-home-activity $selfPackage/.MainActivity")
            appendLine("# 2. 【久学网专用】仅禁用原厂 HomeActivity（保留同包名扫学功能！）")
            appendLine("adb shell pm disable $homeComp")
            appendLine("# 3. 如需整包冻结/恢复原厂桌面：")
            appendLine("adb shell pm disable-user --user 0 $targetStock")
            appendLine("adb shell pm enable $homeComp")
        }.trim()
    }

    suspend fun runRootCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                output.ifBlank { "命令执行成功" }
            } else {
                throw IllegalStateException(error.ifBlank { "Root 命令返回错误码 $exitCode" })
            }
        }
    }

    private fun checkRootAvailable(): Boolean {
        val paths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su")
        return paths.any { java.io.File(it).exists() }
    }
}
