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
            // Start low-power keep-alive service
            try {
                val svcIntent = Intent(context, PenKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent)
                } else {
                    context.startService(svcIntent)
                }
            } catch (_: Exception) {
                // Ignore background restriction on newer Android if not default home yet
            }

            // Bring PenOS Home to foreground on boot
            try {
                val launch = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(launch)
            } catch (_: Exception) {
                // Safe fallback
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
        if (pkg.isBlank() || pkg == packageName) return

        if (AntiLockCoordinator.shouldPullBackToPenOs(pkg, packageName)) {
            val now = System.currentTimeMillis()
            if (now - lastPullBackMs > 800L) {
                lastPullBackMs = now
                val backHome = Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
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
    val isRootAvailable: Boolean
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
        val isAccessibility = isAccessibilityEnabled(context) || AntiLockCoordinator.guardServiceConnected.get()
        val hasRoot = checkRootAvailable()
        SystemDiagnostics(
            currentDefaultHomePackage = currentHome,
            isPenOsDefaultHome = currentHome == context.packageName,
            isAccessibilityGuardActive = isAccessibility,
            isRootAvailable = hasRoot
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
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
        }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun buildAdbTakeoverScript(selfPackage: String, stockLauncherPkg: String): String {
        val targetStock = stockLauncherPkg.ifBlank { "<原厂桌面包名>" }
        return buildString {
            appendLine("# 1. 将 PenOS 设为系统默认主屏幕 (Android 5.0+)")
            appendLine("adb shell cmd package set-home-activity $selfPackage/.MainActivity")
            appendLine("# 若安卓 5.0 无 cmd 命令，请冻结原厂桌面后按 Home 键选择 PenOS：")
            appendLine("adb shell pm disable-user --user 0 $targetStock")
            appendLine("# 2. 如需恢复原厂桌面：")
            appendLine("adb shell pm enable $targetStock")
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
