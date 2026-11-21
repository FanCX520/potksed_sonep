package com.example.system

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Manages Android Native System Widgets (AppWidgetHost) safely on API 21+ dictionary pens.
 * Supports allocating widget IDs, picking/binding system providers, launching configure activities,
 * creating scaled AppWidgetHostViews for 172dp strip height, and cleaning up orphaned IDs.
 */
class NativeWidgetHostManager(private val context: Context) {

    companion object {
        const val APPWIDGET_HOST_ID = 0x50454E33 // "PEN3"
        private const val TAG = "NativeWidgetHost"
    }

    val appWidgetManager: AppWidgetManager? = try {
        AppWidgetManager.getInstance(context)
    } catch (_: Throwable) {
        null
    }

    val appWidgetHost: AppWidgetHost? = try {
        AppWidgetHost(context.applicationContext, APPWIDGET_HOST_ID)
    } catch (_: Throwable) {
        null
    }

    @Volatile
    private var isListening = false

    fun startListeningSafe() {
        if (isListening) return
        try {
            appWidgetHost?.startListening()
            isListening = true
        } catch (t: Throwable) {
            Log.w(TAG, "AppWidgetHost.startListening failed", t)
        }
    }

    fun stopListeningSafe() {
        if (!isListening) return
        try {
            appWidgetHost?.stopListening()
        } catch (t: Throwable) {
            Log.w(TAG, "AppWidgetHost.stopListening failed", t)
        } finally {
            isListening = false
        }
    }

    fun allocateWidgetId(): Int {
        return try {
            appWidgetHost?.allocateAppWidgetId() ?: AppWidgetManager.INVALID_APPWIDGET_ID
        } catch (_: Throwable) {
            AppWidgetManager.INVALID_APPWIDGET_ID
        }
    }

    fun deleteWidgetId(appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        try {
            appWidgetHost?.deleteAppWidgetId(appWidgetId)
        } catch (_: Throwable) {
        }
    }

    fun getProviderInfo(appWidgetId: Int): AppWidgetProviderInfo? {
        return try {
            appWidgetManager?.getAppWidgetInfo(appWidgetId)
        } catch (_: Throwable) {
            null
        }
    }

    fun getInstalledProviders(): List<AppWidgetProviderInfo> {
        return try {
            appWidgetManager?.installedProviders.orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun buildSystemWidgetPickIntent(appWidgetId: Int): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList<AppWidgetProviderInfo>())
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList<Bundle>())
        }
    }

    fun bindProviderIfAllowed(appWidgetId: Int, provider: ComponentName): Boolean {
        return try {
            appWidgetManager?.bindAppWidgetIdIfAllowed(appWidgetId, provider) == true
        } catch (_: Throwable) {
            false
        }
    }

    fun buildBindPermissionIntent(appWidgetId: Int, provider: ComponentName): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }
    }

    fun buildConfigureIntent(appWidgetId: Int, info: AppWidgetProviderInfo): Intent? {
        val cfg = info.configure ?: return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = cfg
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    fun createHostView(activityContext: Context, appWidgetId: Int, widthDp: Int, heightDp: Int = 138): AppWidgetHostView? {
        val info = getProviderInfo(appWidgetId) ?: return null
        val host = appWidgetHost ?: return null
        return try {
            val view = host.createView(activityContext, appWidgetId, info)
            view.setAppWidget(appWidgetId, info)
            val opts = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            }
            view.updateAppWidgetOptions(opts)
            view
        } catch (t: Throwable) {
            Log.w(TAG, "createHostView failed for $appWidgetId", t)
            null
        }
    }

    fun resolveProviderLabel(info: AppWidgetProviderInfo): String {
        return try {
            info.loadLabel(context.packageManager).orEmpty().ifBlank { info.provider.shortClassName }
        } catch (_: Throwable) {
            info.provider.shortClassName
        }
    }
}
