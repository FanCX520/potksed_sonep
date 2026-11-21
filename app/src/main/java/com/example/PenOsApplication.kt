package com.example

import android.app.Application
import com.example.data.local.PenDatabase
import com.example.data.prefs.PenPreferencesRepository
import com.example.data.remote.PenAiMcpClient
import com.example.data.remote.PoetryRepository
import com.example.data.remote.VocabSyncManager
import com.example.data.remote.WeatherRepository
import com.example.system.InstalledAppRepository
import com.example.system.NativeWidgetHostManager
import com.example.system.WhiteNoiseEngine

class PenOsApplication : Application() {

    val database: PenDatabase by lazy { PenDatabase.getInstance(this) }
    val preferences: PenPreferencesRepository by lazy { PenPreferencesRepository(this) }
    val poetryRepository: PoetryRepository by lazy { PoetryRepository(database.poemDao(), preferences) }
    val weatherRepository: WeatherRepository by lazy { WeatherRepository(database.weatherDao(), preferences) }
    val vocabSyncManager: VocabSyncManager by lazy { VocabSyncManager(database.vocabDao(), preferences) }
    val installedAppRepository: InstalledAppRepository by lazy { InstalledAppRepository(this) }
    val whiteNoiseEngine: WhiteNoiseEngine by lazy { WhiteNoiseEngine() }
    val aiMcpClient: PenAiMcpClient by lazy { PenAiMcpClient() }
    val nativeWidgetHostManager: NativeWidgetHostManager by lazy { NativeWidgetHostManager(this) }
}
