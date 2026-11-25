package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.data.local.PenDatabase
import com.example.data.prefs.PenPreferencesRepository
import com.example.data.remote.PenAiMcpClient
import com.example.system.AppIconCache
import com.example.system.InstalledAppRepository
import com.example.system.StaticWallpaperCache

class PenOsApplication : Application() {

    val database: PenDatabase by lazy { PenDatabase.getInstance(this) }
    val preferences: PenPreferencesRepository by lazy { PenPreferencesRepository(this) }
    val installedAppRepository: InstalledAppRepository by lazy { InstalledAppRepository(this) }
    val aiMcpClient: PenAiMcpClient by lazy { PenAiMcpClient() }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            StaticWallpaperCache.clear()
            AppIconCache.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        StaticWallpaperCache.clear()
        AppIconCache.clear()
    }
}
