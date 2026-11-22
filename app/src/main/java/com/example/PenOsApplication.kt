package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.data.local.PenDatabase
import com.example.data.prefs.PenPreferencesRepository
import com.example.data.remote.ImapSmtpMailClient
import com.example.data.remote.PenAiMcpClient
import com.example.data.remote.PoetryRepository
import com.example.data.remote.VocabSyncManager
import com.example.data.remote.WeatherRepository
import com.example.system.BitmapDecodeCache
import com.example.system.InstalledAppRepository
import com.example.system.LanWebAdminServer
import com.example.system.NativeWidgetHostManager
import com.example.system.PenDesktopSnapshotManager
import com.example.system.WhiteNoiseEngine
import com.example.ui.theme.PenFontCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PenOsApplication : Application() {

    val database: PenDatabase by lazy { PenDatabase.getInstance(this) }
    val preferences: PenPreferencesRepository by lazy { PenPreferencesRepository(this) }
    val poetryRepository: PoetryRepository by lazy { PoetryRepository(database.poemDao(), preferences) }
    val weatherRepository: WeatherRepository by lazy { WeatherRepository(database.weatherDao(), preferences) }
    val vocabSyncManager: VocabSyncManager by lazy { VocabSyncManager(database.vocabDao(), preferences) }
    val snapshotManager: PenDesktopSnapshotManager by lazy { PenDesktopSnapshotManager(preferences, database) }
    val installedAppRepository: InstalledAppRepository by lazy { InstalledAppRepository(this) }
    val whiteNoiseEngine: WhiteNoiseEngine by lazy { WhiteNoiseEngine() }
    val aiMcpClient: PenAiMcpClient by lazy { PenAiMcpClient() }
    val mailClient: ImapSmtpMailClient by lazy { ImapSmtpMailClient() }
    val lanAdminServer: LanWebAdminServer by lazy {
        LanWebAdminServer(this, preferences, database, snapshotManager)
    }
    val nativeWidgetHostManager: NativeWidgetHostManager by lazy { NativeWidgetHostManager(this) }

    private val _memoryPressureNonce = MutableStateFlow(0)
    val memoryPressureFlow: StateFlow<Int> = _memoryPressureNonce.asStateFlow()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            BitmapDecodeCache.clear()
            PenFontCatalog.clearExternalCache()
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            _memoryPressureNonce.value += 1
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        BitmapDecodeCache.clear()
        PenFontCatalog.clearExternalCache()
        _memoryPressureNonce.value += 1
    }
}
