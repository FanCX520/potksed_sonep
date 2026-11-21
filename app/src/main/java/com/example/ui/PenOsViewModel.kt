package com.example.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PenOsApplication
import com.example.data.local.CachedPoemEntity
import com.example.data.local.CachedWeatherEntity
import com.example.data.local.CalendarEventEntity
import com.example.data.local.OfflineSeedData
import com.example.data.local.TodoItemEntity
import com.example.data.local.VocabCatalogExpanded
import com.example.data.local.VocabProgressEntity
import com.example.data.local.VocabWordEntity
import com.example.data.local.VocabWordWithProgress
import com.example.data.prefs.AiPreset
import com.example.data.prefs.PenConfig
import com.example.data.remote.AiChatMessage
import com.example.data.remote.PenMcpToolResult
import com.example.data.remote.WeatherCityOption
import com.example.data.remote.WmoWeatherMapper
import com.example.system.AntiLockCoordinator
import com.example.system.InstalledAppInfo
import com.example.system.PenKeepAliveService
import com.example.system.SafeMediaImporter
import com.example.system.SystemDiagnostics
import com.example.system.SystemTakeoverHelper
import com.example.system.WhiteNoiseType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PenOsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PenOsApplication
    private val prefs = app.preferences
    private val vocabDao = app.database.vocabDao()
    private val todoDao = app.database.todoDao()
    private val calendarEventDao = app.database.calendarEventDao()
    private val poetryRepo = app.poetryRepository
    private val weatherRepo = app.weatherRepository
    private val syncManager = app.vocabSyncManager
    private val appRepo = app.installedAppRepository
    private val whiteNoise = app.whiteNoiseEngine
    private val aiMcpClient = app.aiMcpClient
    val nativeWidgetHost = app.nativeWidgetHostManager

    private val _screenMode = MutableStateFlow(PenScreenMode.DESKTOP)
    private val _activeOverlay = MutableStateFlow(DesktopOverlay.NONE)
    private val _clock = MutableStateFlow(readTelemetry(previous = ClockTelemetryState(), includeHeavyStats = true))
    private val _pomodoro = MutableStateFlow(PomodoroState())
    private val _deviceControl = MutableStateFlow(readDeviceControl())
    private val _isPoemLoading = MutableStateFlow(false)
    private val _isWeatherLoading = MutableStateFlow(false)
    private val _weatherSourceStatus = MutableStateFlow("本地缓存")
    private val _citySearchResults = MutableStateFlow(WmoWeatherMapper.presetCities.take(16))
    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _vocabSession = MutableStateFlow(VocabSessionState())
    private val _aiMessages = MutableStateFlow(LabState().messages)
    private val _isAiLoading = MutableStateFlow(false)
    private val _inspectedActivities = MutableStateFlow<List<String>>(emptyList())
    private val _secretTrigger = MutableStateFlow(SecretTriggerState())
    private val _diagnostics = MutableStateFlow(SystemDiagnostics("检测中", false, false, false))
    private val _isVaultUnlocked = MutableStateFlow(false)
    private val _isSyncing = MutableStateFlow(false)
    private val _statusBanner = MutableStateFlow<String?>(null)
    private val _generatedSyncCode = MutableStateFlow<String?>(null)

    private var allWordsCache: List<VocabWordWithProgress> = emptyList()
    private var lastUserInteractionElapsedMs: Long = SystemClock.elapsedRealtime()
    private var lastSecretTapElapsedMs: Long = 0L

    private data class CoreBundle(
        val mode: PenScreenMode,
        val overlay: DesktopOverlay,
        val config: PenConfig,
        val clock: ClockTelemetryState,
        val poem: CachedPoemEntity
    )

    private data class ProductivityBundle(
        val weather: CachedWeatherEntity,
        val isWeatherLoading: Boolean,
        val weatherSource: String,
        val cities: List<WeatherCityOption>,
        val todos: List<TodoItemEntity>,
        val events: List<CalendarEventEntity>
    )

    private data class FocusControlBundle(
        val pomodoro: PomodoroState,
        val deviceControl: DeviceControlState,
        val isPoemLoading: Boolean,
        val lab: LabState
    )

    private data class AppMetaBundle(
        val installedApps: List<InstalledAppInfo>,
        val vocabSession: VocabSessionState,
        val secretTrigger: SecretTriggerState,
        val diagnostics: SystemDiagnostics,
        val isVaultUnlocked: Boolean,
        val isSyncing: Boolean,
        val statusBanner: String?,
        val syncCode: String?
    )

    private val coreFlow = combine(
        _screenMode,
        _activeOverlay,
        prefs.configFlow,
        _clock,
        poetryRepo.latestPoemFlow
    ) { mode, overlay, config, clock, poem ->
        CoreBundle(mode, overlay, config, clock, poem ?: OfflineSeedData.defaultPoems.first())
    }

    private val weatherSubFlow = combine(
        weatherRepo.currentWeatherFlow,
        _isWeatherLoading,
        _weatherSourceStatus,
        _citySearchResults
    ) { weather, loading, source, cities ->
        Triple(weather ?: WmoWeatherMapper.fallbackWeather, loading to source, cities)
    }

    private val productivityFlow = combine(
        weatherSubFlow,
        todoDao.observeAllTodos(),
        calendarEventDao.observeAllEvents()
    ) { wTriple, todos, events ->
        ProductivityBundle(
            weather = wTriple.first,
            isWeatherLoading = wTriple.second.first,
            weatherSource = wTriple.second.second,
            cities = wTriple.third,
            todos = todos,
            events = events
        )
    }

    private val labFlow = combine(
        _aiMessages,
        _isAiLoading,
        whiteNoise.state,
        AntiLockCoordinator.radarLog,
        _inspectedActivities
    ) { msgs, aiLoading, wn, radar, acts ->
        LabState(msgs, aiLoading, wn, radar, acts)
    }

    private val focusControlFlow = combine(
        _pomodoro,
        _deviceControl,
        _isPoemLoading,
        labFlow
    ) { pomo, dev, poemLoad, lab ->
        FocusControlBundle(pomo, dev, poemLoad, lab)
    }

    private val appSubFlow = combine(
        _installedApps,
        _vocabSession,
        _secretTrigger,
        _diagnostics
    ) { apps, vocab, secret, diag ->
        listOf(apps, vocab, secret, diag)
    }

    private val metaSubFlow = combine(
        _isVaultUnlocked,
        _isSyncing,
        _statusBanner,
        _generatedSyncCode
    ) { unlocked, syncing, banner, code ->
        listOf(unlocked, syncing, banner, code)
    }

    @Suppress("UNCHECKED_CAST")
    private val appMetaFlow = combine(appSubFlow, metaSubFlow) { a, m ->
        AppMetaBundle(
            installedApps = a[0] as List<InstalledAppInfo>,
            vocabSession = a[1] as VocabSessionState,
            secretTrigger = a[2] as SecretTriggerState,
            diagnostics = a[3] as SystemDiagnostics,
            isVaultUnlocked = m[0] as Boolean,
            isSyncing = m[1] as Boolean,
            statusBanner = m[2] as String?,
            syncCode = m[3] as String?
        )
    }

    val uiState: StateFlow<PenOsUiState> = combine(
        coreFlow,
        productivityFlow,
        focusControlFlow,
        appMetaFlow
    ) { core, prod, focus, appMeta ->
        PenOsUiState(
            mode = core.mode,
            activeOverlay = core.overlay,
            config = core.config,
            clock = core.clock,
            pomodoro = focus.pomodoro,
            deviceControl = focus.deviceControl,
            poem = core.poem,
            isPoemLoading = focus.isPoemLoading,
            weather = prod.weather,
            isWeatherLoading = prod.isWeatherLoading,
            weatherSourceStatus = prod.weatherSource,
            citySearchResults = prod.cities,
            todos = prod.todos,
            calendarEvents = prod.events,
            installedApps = appMeta.installedApps,
            vocabSession = appMeta.vocabSession,
            lab = focus.lab,
            secretTrigger = appMeta.secretTrigger,
            diagnostics = appMeta.diagnostics,
            isVaultUnlocked = appMeta.isVaultUnlocked,
            isSyncing = appMeta.isSyncing,
            statusBannerMessage = appMeta.statusBanner,
            generatedSyncCode = appMeta.syncCode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PenOsUiState()
    )

    init {
        initializeOfflineData()
        observePreferencesForSystemGuards()
        observeVocabWords()
        startLowPowerClockAndLoops()
        refreshInstalledApps()
        refreshSystemDiagnostics()
        refreshPoem(force = false)
        refreshWeather(force = false)
    }

    override fun onCleared() {
        whiteNoise.stop()
        super.onCleared()
    }

    private fun initializeOfflineData() {
        viewModelScope.launch(Dispatchers.IO) {
            if (vocabDao.getWordCount() < 150) {
                vocabDao.insertWords(OfflineSeedData.defaultWords)
                vocabDao.insertWords(VocabCatalogExpanded.allExpandedWords)
            }
            poetryRepo.ensureSeeded()
            weatherRepo.ensureSeeded()
            val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            if (todoDao.getTodoCount() == 0) {
                todoDao.insertTodos(
                    listOf(
                        TodoItemEntity(title = "今日背诵 20 个核心英语单词", dueDateIso = todayIso, priority = 2),
                        TodoItemEntity(title = "用学王扫学复习错题本生词", dueDateIso = todayIso, priority = 1),
                        TodoItemEntity(title = "晚间听读一篇短文并打卡", dueDateIso = todayIso, priority = 0)
                    )
                )
            }
            if (calendarEventDao.getEventCount() == 0) {
                calendarEventDao.insertEvent(
                    CalendarEventEntity(dateIso = todayIso, timeHHmm = "07:30", title = "晨间背诵英语核心词")
                )
                calendarEventDao.insertEvent(
                    CalendarEventEntity(dateIso = todayIso, timeHHmm = "19:30", title = "晚自习番茄专注 25 分钟")
                )
            }
        }
    }

    private fun observePreferencesForSystemGuards() {
        viewModelScope.launch {
            prefs.configFlow.collect { cfg ->
                AntiLockCoordinator.updateRules(
                    blockedPkg = cfg.blockedStockLauncherPackage,
                    blockedActivities = cfg.blockedHomeActivities,
                    scanPkg = cfg.scanAppPackage,
                    scanActivityClass = cfg.scanActivityClass
                )
                if (cfg.keepAliveEnabled) {
                    PenKeepAliveService.start(app)
                } else {
                    PenKeepAliveService.stop(app)
                }
                if (_vocabSession.value.activeBook != cfg.activeVocabBook) {
                    _vocabSession.update { it.copy(activeBook = cfg.activeVocabBook) }
                    rebuildVocabQuestion(forceNextWord = true)
                }
                if (!_pomodoro.value.isRunning && !_pomodoro.value.isBreakMode) {
                    val targetSec = cfg.pomodoroMinutes * 60
                    if (_pomodoro.value.totalSeconds != targetSec && _pomodoro.value.remainingSeconds == _pomodoro.value.totalSeconds) {
                        _pomodoro.update { it.copy(totalSeconds = targetSec, remainingSeconds = targetSec) }
                    }
                }
            }
        }
    }

    private fun observeVocabWords() {
        viewModelScope.launch {
            vocabDao.observeAllWordsWithProgress().collect { list ->
                allWordsCache = list
                rebuildVocabQuestion(forceNextWord = _vocabSession.value.currentWord == null)
            }
        }
    }

    private fun startLowPowerClockAndLoops() {
        viewModelScope.launch {
            var tickCount = 0
            while (isActive) {
                val nowElapsed = SystemClock.elapsedRealtime()
                _clock.value = readTelemetry(previous = _clock.value, includeHeavyStats = (tickCount % 10 == 0))
                tickCount++

                // Pomodoro countdown tick
                val pomo = _pomodoro.value
                if (pomo.isRunning) {
                    if (pomo.remainingSeconds > 1) {
                        _pomodoro.value = pomo.copy(remainingSeconds = pomo.remainingSeconds - 1)
                    } else {
                        val wasBreak = pomo.isBreakMode
                        val nextTotal = if (wasBreak) uiState.value.config.pomodoroMinutes * 60 else 5 * 60
                        _pomodoro.value = pomo.copy(
                            isRunning = false,
                            isBreakMode = !wasBreak,
                            totalSeconds = nextTotal,
                            remainingSeconds = nextTotal,
                            completedSessions = if (!wasBreak) pomo.completedSessions + 1 else pomo.completedSessions
                        )
                        if (!wasBreak) {
                            whiteNoise.stop()
                            showToast("专注番茄已完成！建议休息 5 分钟")
                        } else {
                            showToast("休息结束，准备好开启下一个番茄了吗？")
                        }
                    }
                }

                val secret = _secretTrigger.value
                if ((secret.tapCount > 0 || secret.isArmed) && nowElapsed - lastSecretTapElapsedMs > 4500L) {
                    _secretTrigger.value = SecretTriggerState()
                }

                val cfg = uiState.value.config
                if (_screenMode.value == PenScreenMode.DESKTOP &&
                    _activeOverlay.value == DesktopOverlay.NONE &&
                    cfg.standByTimeoutSec > 0
                ) {
                    val idleSec = (nowElapsed - lastUserInteractionElapsedMs) / 1000L
                    if (idleSec >= cfg.standByTimeoutSec) {
                        _screenMode.value = PenScreenMode.STANDBY
                    }
                }

                delay(1000L)
            }
        }
    }

    fun onUserInteraction() {
        lastUserInteractionElapsedMs = SystemClock.elapsedRealtime()
    }

    fun switchMode(mode: PenScreenMode) {
        onUserInteraction()
        _activeOverlay.value = DesktopOverlay.NONE
        if (mode != PenScreenMode.VAULT) {
            _isVaultUnlocked.value = false
        }
        _screenMode.value = mode
        if (mode == PenScreenMode.VAULT) {
            refreshInstalledApps()
            refreshSystemDiagnostics()
        }
    }

    fun openOverlay(overlay: DesktopOverlay) {
        onUserInteraction()
        if (overlay == DesktopOverlay.CONTROL_CENTER) {
            _deviceControl.value = readDeviceControl()
        }
        if (overlay == DesktopOverlay.ALL_APPS && _installedApps.value.isEmpty()) {
            refreshInstalledApps()
        }
        _activeOverlay.value = overlay
    }

    fun closeOverlay() {
        onUserInteraction()
        _activeOverlay.value = DesktopOverlay.NONE
    }

    fun wakeFromStandBy() {
        onUserInteraction()
        _screenMode.value = PenScreenMode.DESKTOP
    }

    // --- Pomodoro Focus Mode ---
    fun openPomodoroFocus(autoStartMinutes: Int? = null) {
        onUserInteraction()
        if (autoStartMinutes != null) {
            val mins = autoStartMinutes.coerceIn(1, 120)
            viewModelScope.launch { prefs.setPomodoroMinutes(mins) }
            _pomodoro.update {
                it.copy(
                    totalSeconds = mins * 60,
                    remainingSeconds = mins * 60,
                    isRunning = true,
                    isBreakMode = false
                )
            }
        }
        _activeOverlay.value = DesktopOverlay.NONE
        _screenMode.value = PenScreenMode.POMODORO
    }

    fun togglePomodoroRunning() {
        onUserInteraction()
        _pomodoro.update { it.copy(isRunning = !it.isRunning) }
    }

    fun resetPomodoro() {
        onUserInteraction()
        val mins = if (_pomodoro.value.isBreakMode) 5 else uiState.value.config.pomodoroMinutes
        _pomodoro.update {
            it.copy(totalSeconds = mins * 60, remainingSeconds = mins * 60, isRunning = false)
        }
    }

    fun cyclePomodoroDuration() {
        onUserInteraction()
        val presets = listOf(15, 25, 45, 60)
        val curMins = (_pomodoro.value.totalSeconds / 60).coerceAtLeast(1)
        val nextMins = presets.firstOrNull { it > curMins } ?: presets.first()
        viewModelScope.launch { prefs.setPomodoroMinutes(nextMins) }
        _pomodoro.update {
            it.copy(
                totalSeconds = nextMins * 60,
                remainingSeconds = nextMins * 60,
                isRunning = false,
                isBreakMode = false
            )
        }
    }

    fun togglePomodoroBreakMode() {
        onUserInteraction()
        val nextBreak = !_pomodoro.value.isBreakMode
        val mins = if (nextBreak) 5 else uiState.value.config.pomodoroMinutes
        _pomodoro.update {
            it.copy(
                isBreakMode = nextBreak,
                totalSeconds = mins * 60,
                remainingSeconds = mins * 60,
                isRunning = false
            )
        }
    }

    // --- Control Center (Brightness, Volume, Night Eye-Care, Low-Mem) ---
    fun setScreenBrightness(fraction: Float) {
        onUserInteraction()
        val clamped = fraction.coerceIn(0.06f, 1f)
        val wrote = SystemTakeoverHelper.writeSystemBrightness(app, clamped)
        _deviceControl.update {
            it.copy(brightnessFraction = clamped, canWriteSystemBrightness = wrote || SystemTakeoverHelper.canWriteSystemSettings(app))
        }
    }

    fun setMediaVolume(fraction: Float) {
        onUserInteraction()
        val clamped = fraction.coerceIn(0f, 1f)
        SystemTakeoverHelper.setMediaVolumeFraction(app, clamped)
        _deviceControl.update { it.copy(mediaVolumeFraction = clamped) }
    }

    fun setNightDimAlpha(alpha: Float) {
        onUserInteraction()
        viewModelScope.launch { prefs.setNightDimAlpha(alpha) }
    }

    fun setLowMemMode(enabled: Boolean) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setLowMemMode(enabled)
            showToast(if (enabled) "已开启 1GB 极简内存模式" else "已恢复标准动效模式")
        }
    }

    // --- White Noise DSP ---
    fun toggleWhiteNoise(type: WhiteNoiseType = whiteNoise.state.value.activeType) {
        onUserInteraction()
        whiteNoise.toggle(type)
    }

    fun stopWhiteNoise() {
        onUserInteraction()
        whiteNoise.stop()
    }

    fun setWhiteNoiseVolume(vol: Float) {
        onUserInteraction()
        whiteNoise.setVolume(vol)
    }

    // --- Dual Design Style, StandBy Swipe & Independent Wallpapers ---
    fun setDesignStyle(style: String) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setDesignStyle(style)
            showToast(if (style == PenConfig.STYLE_APPLE_LIGHT) "已切换：Apple 极简白画廊" else "已切换：StandBy 暗黑 CarPlay")
        }
    }

    fun toggleQuickDesignStyle() {
        val next = if (uiState.value.config.isAppleLightDesktop()) {
            PenConfig.STYLE_DARK_STANDBY
        } else {
            PenConfig.STYLE_APPLE_LIGHT
        }
        setDesignStyle(next)
    }

    fun setStandByStyleMode(mode: String) {
        onUserInteraction()
        viewModelScope.launch { prefs.setStandByStyleMode(mode) }
    }

    fun cycleStandByRightWidget(delta: Int) {
        onUserInteraction()
        val order = PenConfig.STANDBY_WIDGET_ORDER
        val curIdx = order.indexOf(uiState.value.config.standByRightWidget).coerceAtLeast(0)
        val nextIdx = (curIdx + delta + order.size) % order.size
        viewModelScope.launch { prefs.setStandByConfig(rightWidget = order[nextIdx]) }
    }

    fun selectWallpaperPreset(isLightTheme: Boolean, presetId: String) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setWallpaperForTheme(isLightTheme, presetId)
            showToast(if (isLightTheme) "已更新白画廊时钟壁纸" else "已更新暗黑时钟壁纸")
        }
    }

    fun importCustomWallpaperFromUri(isLightTheme: Boolean, uri: Uri) {
        onUserInteraction()
        viewModelScope.launch {
            val tag = if (isLightTheme) "wall_light_${System.currentTimeMillis()}" else "wall_dark_${System.currentTimeMillis()}"
            SafeMediaImporter.importImageFromUri(app, uri, tag, maxDimensionPx = 960)
                .onSuccess { path ->
                    prefs.setWallpaperForTheme(isLightTheme, PenConfig.WALLPAPER_CUSTOM, path)
                    showToast(if (isLightTheme) "已应用白画廊自定义壁纸" else "已应用暗黑自定义壁纸")
                }
                .onFailure { showToast("导入壁纸失败：${it.message}") }
        }
    }

    fun importCustomWallpaperFromPath(isLightTheme: Boolean, rawPath: String) {
        onUserInteraction()
        viewModelScope.launch {
            val tag = if (isLightTheme) "wall_light_${System.currentTimeMillis()}" else "wall_dark_${System.currentTimeMillis()}"
            SafeMediaImporter.importImageFromFilePath(app, rawPath, tag, maxDimensionPx = 960)
                .onSuccess { path ->
                    prefs.setWallpaperForTheme(isLightTheme, PenConfig.WALLPAPER_CUSTOM, path)
                    showToast("已从本地路径应用自定义壁纸")
                }
                .onFailure { showToast("读取失败：${it.message}") }
        }
    }

    // --- Custom App Icons & External Poetry Font ---
    fun importCustomAppIconFromUri(packageName: String, uri: Uri) {
        onUserInteraction()
        viewModelScope.launch {
            val safeKey = packageName.replace(".", "_")
            SafeMediaImporter.importImageFromUri(app, uri, "icon_${safeKey}_${System.currentTimeMillis()}", maxDimensionPx = 192)
                .onSuccess { path ->
                    prefs.setCustomAppIcon(packageName, path)
                    showToast("已替换应用自定义图标")
                }
                .onFailure { showToast("图标导入失败：${it.message}") }
        }
    }

    fun importCustomAppIconFromPath(packageName: String, rawPath: String) {
        onUserInteraction()
        viewModelScope.launch {
            val safeKey = packageName.replace(".", "_")
            SafeMediaImporter.importImageFromFilePath(app, rawPath, "icon_${safeKey}_${System.currentTimeMillis()}", maxDimensionPx = 192)
                .onSuccess { path ->
                    prefs.setCustomAppIcon(packageName, path)
                    showToast("已替换应用自定义图标")
                }
                .onFailure { showToast("图标路径无效：${it.message}") }
        }
    }

    fun setCustomAppIconColor(packageName: String, colorHex: String) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setCustomAppIcon(packageName, "color:$colorHex")
            showToast("已设置纯色主题图标")
        }
    }

    fun clearCustomAppIcon(packageName: String) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setCustomAppIcon(packageName, "")
            showToast("已恢复系统默认图标")
        }
    }

    fun importCustomPoetryFontFromUri(uri: Uri) {
        onUserInteraction()
        viewModelScope.launch {
            SafeMediaImporter.importFontFromUri(app, uri)
                .onSuccess { path ->
                    prefs.setCustomPoetryFontPath(path)
                    showToast("已应用外部古诗词字体")
                }
                .onFailure { showToast("字体导入失败：${it.message}") }
        }
    }

    fun importCustomPoetryFontFromPath(rawPath: String) {
        onUserInteraction()
        viewModelScope.launch {
            SafeMediaImporter.importFontFromPath(app, rawPath)
                .onSuccess { path ->
                    prefs.setCustomPoetryFontPath(path)
                    showToast("已加载本地古诗词字体")
                }
                .onFailure { showToast("字体读取失败：${it.message}") }
        }
    }

    fun clearCustomPoetryFont() {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setCustomPoetryFontPath("")
            showToast("已恢复内置思源宋体")
        }
    }

    // --- Android Native System Widgets (1+2 Hybrid Mode) ---
    fun setDesktopDeckMode(mode: String) {
        onUserInteraction()
        viewModelScope.launch { prefs.setDesktopDeckMode(mode) }
    }

    fun onNativeWidgetAdded(appWidgetId: Int) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.addNativeWidgetId(appWidgetId, widthDp = 200)
            showToast("已添加安卓原生小组件")
        }
    }

    fun removeNativeWidget(appWidgetId: Int) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.removeNativeWidgetId(appWidgetId)
            nativeWidgetHost.deleteWidgetId(appWidgetId)
            showToast("已移除原生小组件")
        }
    }

    fun adjustNativeWidgetWidth(appWidgetId: Int, deltaDp: Int) {
        onUserInteraction()
        viewModelScope.launch {
            val cur = uiState.value.config.nativeWidgetWidths[appWidgetId] ?: 200
            prefs.setNativeWidgetWidth(appWidgetId, cur + deltaDp)
        }
    }

    // --- Secret Vault Gesture (5 taps + Long-Press) ---
    fun onClockTap() {
        onUserInteraction()
        val now = SystemClock.elapsedRealtime()
        val current = _secretTrigger.value
        val nextCount = if (now - lastSecretTapElapsedMs <= 1400L) current.tapCount + 1 else 1
        lastSecretTapElapsedMs = now

        if (nextCount >= 5) {
            _secretTrigger.value = SecretTriggerState(
                tapCount = 5,
                isArmed = true,
                hapticPulseNonce = current.hapticPulseNonce + 1
            )
            showToast("隐藏入口已就绪，请长按时间进入密室")
        } else {
            _secretTrigger.value = current.copy(tapCount = nextCount, isArmed = false)
        }
    }

    fun onClockLongPress() {
        onUserInteraction()
        val current = _secretTrigger.value
        val now = SystemClock.elapsedRealtime()
        if (current.isArmed && now - lastSecretTapElapsedMs <= 4500L) {
            _secretTrigger.value = SecretTriggerState(hapticPulseNonce = current.hapticPulseNonce + 1)
            val hasPin = uiState.value.config.vaultPin.isNotBlank()
            _isVaultUnlocked.value = !hasPin
            _activeOverlay.value = DesktopOverlay.NONE
            _screenMode.value = PenScreenMode.VAULT
            refreshInstalledApps()
            refreshSystemDiagnostics()
        }
    }

    fun verifyVaultPin(inputPin: String): Boolean {
        val expected = uiState.value.config.vaultPin
        val ok = expected.isBlank() || inputPin == expected
        if (ok) _isVaultUnlocked.value = true else showToast("密码不正确")
        return ok
    }

    // --- Poetry ---
    fun refreshPoem(force: Boolean) {
        if (_isPoemLoading.value) return
        viewModelScope.launch {
            _isPoemLoading.value = true
            poetryRepo.refreshPoem(force = force)
            _isPoemLoading.value = false
        }
    }

    // --- China ICP-Filed Multi-City Weather ---
    fun refreshWeather(force: Boolean) {
        if (_isWeatherLoading.value) return
        viewModelScope.launch {
            _isWeatherLoading.value = true
            val res = weatherRepo.refreshWeather(force = force)
            _weatherSourceStatus.value = res.sourceLabel
            _isWeatherLoading.value = false
            if (force && res.errorHint != null) {
                showToast(res.errorHint)
            } else if (force && res.isLive) {
                showToast("天气已同步（${res.sourceLabel}）")
            }
        }
    }

    fun searchWeatherCities(query: String) {
        onUserInteraction()
        viewModelScope.launch {
            weatherRepo.searchCities(query).onSuccess { list ->
                _citySearchResults.value = list.ifEmpty { WmoWeatherMapper.presetCities.take(16) }
            }
        }
    }

    fun selectWeatherCity(city: WeatherCityOption) {
        onUserInteraction()
        viewModelScope.launch {
            _isWeatherLoading.value = true
            val res = weatherRepo.selectCityAndRefresh(city)
            _weatherSourceStatus.value = res.sourceLabel
            _isWeatherLoading.value = false
            if (res.isLive) {
                showToast("已切换至 ${city.displayName}（${res.sourceLabel}）")
            } else {
                showToast("已保存城市 ${city.displayName}（离线缓存）")
            }
        }
    }

    fun removeSavedWeatherCity(cityName: String) {
        onUserInteraction()
        viewModelScope.launch { prefs.removeWeatherCityFromFavorites(cityName) }
    }

    fun saveWeatherApiKeys(qWeatherKey: String, amapKey: String) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.saveWeatherApiKeys(qWeatherKey, amapKey)
            showToast("已保存国内备案天气 Key，正在同步…")
            refreshWeather(force = true)
        }
    }

    // --- Custom Timed Calendar Events ---
    fun addCalendarEvent(dateIso: String, timeHHmm: String, title: String) {
        onUserInteraction()
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        val cleanTime = normalizeHHmm(timeHHmm)
        viewModelScope.launch(Dispatchers.IO) {
            calendarEventDao.insertEvent(
                CalendarEventEntity(dateIso = dateIso, timeHHmm = cleanTime, title = cleanTitle)
            )
        }
        showToast("已添加日程 $cleanTime $cleanTitle")
    }

    fun deleteCalendarEvent(eventId: Long) {
        onUserInteraction()
        viewModelScope.launch(Dispatchers.IO) {
            calendarEventDao.deleteById(eventId)
        }
    }

    // --- Todo Items ---
    fun addTodo(title: String, dueDateIso: String = _clock.value.isoDate, priority: Int = 1) {
        onUserInteraction()
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            todoDao.insertTodo(TodoItemEntity(title = trimmed, dueDateIso = dueDateIso, priority = priority))
        }
    }

    fun toggleTodoDone(todo: TodoItemEntity) {
        onUserInteraction()
        viewModelScope.launch(Dispatchers.IO) { todoDao.setDone(todo.id, !todo.isDone) }
    }

    fun deleteTodo(todoId: Long) {
        onUserInteraction()
        viewModelScope.launch(Dispatchers.IO) { todoDao.deleteById(todoId) }
    }

    fun clearCompletedTodos() {
        onUserInteraction()
        viewModelScope.launch(Dispatchers.IO) { todoDao.clearCompleted() }
        showToast("已清理已完成待办")
    }

    // --- Installed Apps & Jiuxuewang Component-Level Launch/Guard ---
    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = appRepo.loadInstalledApps()
            _installedApps.value = apps

            val cfg = prefs.currentConfig()
            if (cfg.scanAppPackage.isBlank()) {
                apps.firstOrNull { it.isLikelyScanApp }?.let { prefs.setScanTarget(it.packageName, it.launchActivityName) }
            }
            if (cfg.blockedStockLauncherPackage.isBlank()) {
                val detectedHome = apps.firstOrNull { it.isHomeLauncher }
                if (detectedHome != null) {
                    prefs.setBlockedStockLauncherPackage(detectedHome.packageName)
                    if (detectedHome.homeActivityName.isNotBlank()) {
                        prefs.addBlockedHomeActivity("${detectedHome.packageName}/${detectedHome.homeActivityName}")
                    }
                }
            }
            if (cfg.pinnedDockPackages.isEmpty() && apps.isNotEmpty()) {
                apps.filter { !cfg.hiddenPackages.contains(it.packageName) }
                    .take(4)
                    .forEach { prefs.togglePinnedPackage(it.packageName) }
            }
        }
    }

    fun inspectPackageActivities(packageName: String) {
        viewModelScope.launch {
            _inspectedActivities.value = appRepo.listExportedActivities(packageName)
        }
    }

    fun launchScanApp() {
        onUserInteraction()
        val cfg = uiState.value.config
        if (cfg.scanAppPackage.isBlank()) {
            showToast("请先在密室或雷达中绑定「学王扫学」应用及 Activity")
            return
        }
        val ok = appRepo.launchComponent(
            packageName = cfg.scanAppPackage,
            activityClass = cfg.scanActivityClass,
            isolateScanStack = true
        )
        if (!ok) {
            showToast("未找到扫学入口，请在密室重新选择 Activity")
        }
    }

    fun launchApp(packageName: String) {
        onUserInteraction()
        val appInfo = _installedApps.value.firstOrNull { it.packageName == packageName }
        val preferredAct = appInfo?.launchActivityName.orEmpty()
        if (!appRepo.launchComponent(packageName, preferredAct)) {
            showToast("无法启动该应用（可能已被系统禁用）")
        }
    }

    fun toggleHiddenApp(packageName: String) {
        viewModelScope.launch { prefs.toggleHiddenPackage(packageName) }
    }

    fun togglePinnedApp(packageName: String) {
        viewModelScope.launch { prefs.togglePinnedPackage(packageName) }
    }

    fun bindScanTarget(packageName: String, activityClass: String = "") {
        viewModelScope.launch {
            prefs.setScanTarget(packageName, activityClass)
            val short = activityClass.substringAfterLast('.').ifBlank { packageName }
            showToast("已绑定扫学入口：$short")
        }
    }

    fun bindBlockedStockLauncher(packageName: String) {
        viewModelScope.launch {
            prefs.setBlockedStockLauncherPackage(packageName)
            showToast("已设置原厂桌面包名拦截")
        }
    }

    fun toggleBlockedHomeActivity(flatComponent: String) {
        viewModelScope.launch {
            prefs.toggleBlockedHomeActivity(flatComponent)
            showToast("已更新组件级拉黑名单")
        }
    }

    fun addBlockedHomeActivity(flatComponent: String) {
        viewModelScope.launch {
            prefs.addBlockedHomeActivity(flatComponent)
            showToast("已拉黑原厂桌面组件：${flatComponent.substringAfterLast('.')}")
        }
    }

    fun toggleWidget(widgetId: String) {
        viewModelScope.launch {
            val current = uiState.value.config.enabledWidgetIds.toMutableList()
            if (current.contains(widgetId)) {
                if (current.size <= 1) {
                    showToast("桌面至少保留一个小组件")
                    return@launch
                }
                current.remove(widgetId)
            } else {
                current.add(widgetId)
            }
            prefs.setEnabledWidgets(current)
        }
    }

    fun moveWidget(widgetId: String, delta: Int) {
        viewModelScope.launch {
            val current = uiState.value.config.enabledWidgetIds.toMutableList()
            val idx = current.indexOf(widgetId)
            if (idx == -1) return@launch
            val targetIdx = (idx + delta).coerceIn(0, current.lastIndex)
            if (targetIdx == idx) return@launch
            val item = current.removeAt(idx)
            current.add(targetIdx, item)
            prefs.setEnabledWidgets(current)
        }
    }

    fun updateStandBySettings(
        timeoutSec: Int? = null,
        brightness: Float? = null,
        rightWidget: String? = null,
        clockColor: String? = null
    ) {
        viewModelScope.launch { prefs.setStandByConfig(timeoutSec, brightness, rightWidget, clockColor) }
    }

    fun setVaultPin(pin: String) {
        viewModelScope.launch {
            prefs.setVaultPin(pin)
            showToast(if (pin.isBlank()) "已关闭密室密码锁" else "密室密码已更新")
        }
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setKeepAliveEnabled(enabled) }
    }

    fun setPenFramePreviewEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setPenFramePreviewEnabled(enabled) }
    }

    // --- System Takeover Diagnostics & Root ---
    fun refreshSystemDiagnostics() {
        viewModelScope.launch { _diagnostics.value = SystemTakeoverHelper.inspectSystem(app) }
    }

    fun executeRootSetDefaultHome() {
        viewModelScope.launch {
            val cmd = "cmd package set-home-activity ${app.packageName}/.MainActivity"
            SystemTakeoverHelper.runRootCommand(cmd)
                .onSuccess {
                    showToast("Root 设为默认桌面成功")
                    refreshSystemDiagnostics()
                }
                .onFailure { showToast("Root 执行失败: ${it.message}") }
        }
    }

    fun executeRootDisableHomeComponent(componentFlat: String) {
        val target = componentFlat.ifBlank { uiState.value.diagnostics.detectedStockHomeComponent }
        if (target.isBlank() || !target.contains("/")) {
            showToast("未识别到原厂 HomeActivity 组件，请先在雷达中查看")
            return
        }
        viewModelScope.launch {
            SystemTakeoverHelper.runRootCommand("pm disable $target")
                .onSuccess {
                    showToast("已单组件禁用原厂桌面，同包名扫学不受影响！")
                    refreshSystemDiagnostics()
                }
                .onFailure { showToast("Root 执行失败: ${it.message}") }
        }
    }

    fun executeRootFreezeStockLauncher(freeze: Boolean) {
        val target = uiState.value.config.blockedStockLauncherPackage
        if (target.isBlank()) {
            showToast("请先选择原厂桌面包名")
            return
        }
        viewModelScope.launch {
            val cmd = if (freeze) "pm disable-user --user 0 $target" else "pm enable $target"
            SystemTakeoverHelper.runRootCommand(cmd)
                .onSuccess {
                    showToast(if (freeze) "已冻结原厂桌面防锁回" else "已恢复原厂桌面")
                    refreshSystemDiagnostics()
                }
                .onFailure { showToast("Root 执行失败: ${it.message}") }
        }
    }

    // --- Baicizhan 2.0 Vocab Trainer (200+ Graded + Mistakes + Custom TXT/CSV Import + Slash) ---
    fun switchVocabBook(bookId: String) {
        onUserInteraction()
        viewModelScope.launch { prefs.setActiveVocabBook(bookId) }
    }

    fun importCustomVocabFromUri(uri: Uri) {
        onUserInteraction()
        viewModelScope.launch {
            SafeMediaImporter.readTextFromUri(app, uri)
                .onSuccess { text -> importParsedWordbook(text) }
                .onFailure { showToast("读取词书失败：${it.message}") }
        }
    }

    fun importCustomVocabFromPath(rawPath: String) {
        onUserInteraction()
        viewModelScope.launch {
            SafeMediaImporter.readTextFromPath(rawPath)
                .onSuccess { text -> importParsedWordbook(text) }
                .onFailure { showToast("路径读取失败：${it.message}") }
        }
    }

    private suspend fun importParsedWordbook(rawText: String) {
        val parsed = VocabCatalogExpanded.parseCustomWordbook(rawText, bookId = "custom")
        if (parsed.isEmpty()) {
            showToast("未解析到有效单词（支持 word,音标,释义 或 word\\t释义）")
            return
        }
        vocabDao.upsertWords(parsed)
        prefs.setActiveVocabBook("custom")
        showToast("成功导入 ${parsed.size} 个自定义单词！")
    }

    fun selectVocabOption(optionWordId: String) {
        onUserInteraction()
        val session = _vocabSession.value
        val current = session.currentWord ?: return
        if (session.selectedOptionId != null) return

        val isCorrect = (optionWordId == current.word.id)
        val now = System.currentTimeMillis()
        val prev = current.progress

        val nextFamiliarity = if (isCorrect) ((prev?.familiarity ?: 0) + 1).coerceAtMost(5) else 0
        val nextStatus = if (isCorrect && nextFamiliarity >= 3) {
            VocabProgressEntity.STATUS_MASTERED
        } else {
            VocabProgressEntity.STATUS_LEARNING
        }
        val intervalMs = when (nextFamiliarity) {
            0 -> 5L * 60L * 1000L
            1 -> 30L * 60L * 1000L
            2 -> 12L * 60L * 60L * 1000L
            else -> 3L * 24L * 60L * 60L * 1000L
        }

        val updated = VocabProgressEntity(
            wordId = current.word.id,
            status = nextStatus,
            familiarity = nextFamiliarity,
            nextReviewAt = now + intervalMs,
            wrongCount = (prev?.wrongCount ?: 0) + if (isCorrect) 0 else 1,
            updatedAt = now
        )

        _vocabSession.update {
            it.copy(
                selectedOptionId = optionWordId,
                answeredCorrectly = isCorrect,
                showExampleHint = true,
                sessionStreak = if (isCorrect) it.sessionStreak + 1 else 0,
                dailyMasteredCount = if (isCorrect && nextStatus == VocabProgressEntity.STATUS_MASTERED) {
                    it.dailyMasteredCount + 1
                } else it.dailyMasteredCount
            )
        }

        viewModelScope.launch {
            vocabDao.upsertProgress(updated)
            delay(if (isCorrect) 650L else 1200L)
            rebuildVocabQuestion(forceNextWord = true)
        }
    }

    fun killCurrentWord() {
        onUserInteraction()
        val current = _vocabSession.value.currentWord ?: return
        val now = System.currentTimeMillis()
        val mastered = VocabProgressEntity(
            wordId = current.word.id,
            status = VocabProgressEntity.STATUS_MASTERED,
            familiarity = 5,
            nextReviewAt = now + 30L * 24L * 60L * 60L * 1000L,
            wrongCount = 0, // Clears from mistake book once slashed!
            updatedAt = now
        )
        _vocabSession.update {
            it.copy(
                slashEffectNonce = it.slashEffectNonce + 1,
                dailyMasteredCount = it.dailyMasteredCount + 1
            )
        }
        viewModelScope.launch {
            vocabDao.upsertProgress(mastered)
            delay(340L)
            rebuildVocabQuestion(forceNextWord = true)
        }
    }

    fun toggleVocabHint() {
        onUserInteraction()
        _vocabSession.update { it.copy(showExampleHint = !it.showExampleHint) }
    }

    fun nextVocabWord() {
        onUserInteraction()
        rebuildVocabQuestion(forceNextWord = true)
    }

    private fun rebuildVocabQuestion(forceNextWord: Boolean) {
        val activeBook = _vocabSession.value.activeBook
        val mistakesAll = allWordsCache.filter { (it.progress?.wrongCount ?: 0) > 0 }
        val customAll = allWordsCache.filter { it.word.bookId == "custom" }

        val bookWords = when (activeBook) {
            "mistakes" -> mistakesAll.ifEmpty { allWordsCache.take(10) }
            else -> allWordsCache.filter { it.word.bookId == activeBook }.ifEmpty { allWordsCache }
        }
        if (bookWords.isEmpty()) return

        val mastered = bookWords.count { it.progress?.status == VocabProgressEntity.STATUS_MASTERED }
        val learning = bookWords.count { it.progress?.status == VocabProgressEntity.STATUS_LEARNING }

        val previousId = _vocabSession.value.currentWord?.word?.id
        val currentOrNext = if (!forceNextWord && previousId != null) {
            bookWords.firstOrNull { it.word.id == previousId }
        } else {
            null
        } ?: pickNextWord(bookWords, previousId)

        val distractors = allWordsCache
            .asSequence()
            .filter { it.word.id != currentOrNext.word.id }
            .shuffled()
            .take(3)
            .map {
                VocabQuizOption(
                    wordId = it.word.id,
                    meaningZh = "${it.word.partOfSpeech} ${it.word.meaningZh}",
                    mnemonicTag = it.word.mnemonicTag,
                    accentHex = it.word.accentHex,
                    isCorrect = false
                )
            }
            .toList()

        val correctOption = VocabQuizOption(
            wordId = currentOrNext.word.id,
            meaningZh = "${currentOrNext.word.partOfSpeech} ${currentOrNext.word.meaningZh}",
            mnemonicTag = currentOrNext.word.mnemonicTag,
            accentHex = currentOrNext.word.accentHex,
            isCorrect = true
        )

        _vocabSession.update {
            it.copy(
                activeBook = activeBook,
                currentWord = currentOrNext,
                options = (distractors + correctOption).shuffled(),
                selectedOptionId = null,
                answeredCorrectly = null,
                showExampleHint = false,
                masteredCount = mastered,
                learningCount = learning,
                mistakeCount = mistakesAll.size,
                customCount = customAll.size,
                totalInBook = bookWords.size
            )
        }
    }

    private fun pickNextWord(pool: List<VocabWordWithProgress>, excludeId: String?): VocabWordWithProgress {
        val now = System.currentTimeMillis()
        val unmastered = pool.filter { it.progress?.status != VocabProgressEntity.STATUS_MASTERED }
        val dueReviews = unmastered.filter {
            val p = it.progress
            p != null && p.nextReviewAt in 1L..now && it.word.id != excludeId
        }
        if (dueReviews.isNotEmpty()) return dueReviews.first()
        val fresh = unmastered.filter { it.word.id != excludeId }
        if (fresh.isNotEmpty()) return fresh.random()
        return pool.firstOrNull { it.word.id != excludeId } ?: pool.first()
    }

    // --- Lab AI + Custom Presets + Built-in PenOS Desktop MCP Tool Execution ---
    fun saveAiConfig(baseUrl: String, apiKey: String, model: String, externalMcpUrl: String) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.saveAiConfig(baseUrl, apiKey, model, externalMcpUrl)
            showToast("AI 与 MCP 接口配置已保存")
        }
    }

    fun selectAiPreset(presetId: String) {
        onUserInteraction()
        viewModelScope.launch { prefs.setActiveAiPreset(presetId) }
    }

    fun upsertAiPreset(preset: AiPreset) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.upsertAiPreset(preset)
            showToast("已保存 AI 预设「${preset.name}」")
        }
    }

    fun deleteAiPreset(presetId: String) {
        onUserInteraction()
        viewModelScope.launch { prefs.deleteAiPreset(presetId) }
    }

    fun clearAiChat() {
        onUserInteraction()
        _aiMessages.value = LabState().messages
    }

    fun sendAiPrompt(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || _isAiLoading.value) return
        onUserInteraction()

        val cfg = uiState.value.config
        val preset = cfg.aiPresets.firstOrNull { it.id == cfg.activeAiPresetId } ?: cfg.aiPresets.first()
        val userMsg = AiChatMessage("u_${System.currentTimeMillis()}", "user", clean)
        val historyBefore = _aiMessages.value
        _aiMessages.value = (historyBefore + userMsg).takeLast(24)

        viewModelScope.launch {
            _isAiLoading.value = true
            val result = aiMcpClient.sendChatWithMcp(
                baseUrl = cfg.aiBaseUrl,
                apiKey = cfg.aiApiKey,
                model = cfg.aiModel,
                preset = preset,
                history = historyBefore,
                userPrompt = clean,
                externalMcpUrl = cfg.externalMcpUrl,
                desktopToolExecutor = { toolName, args -> executeBuiltInMcpTool(toolName, args) }
            )
            _isAiLoading.value = false
            result.onSuccess { reply ->
                _aiMessages.update { (it + reply).takeLast(24) }
            }.onFailure { err ->
                _aiMessages.update {
                    (it + AiChatMessage("err_${System.currentTimeMillis()}", "assistant", "请求失败：${err.message}")).takeLast(24)
                }
            }
        }
    }

    private suspend fun executeBuiltInMcpTool(toolName: String, args: JSONObject): PenMcpToolResult {
        return when (toolName) {
            "switch_theme" -> {
                val style = args.optString("desktop_style", PenConfig.STYLE_DARK_STANDBY)
                val normalized = if (style.contains("light", ignoreCase = true)) PenConfig.STYLE_APPLE_LIGHT else PenConfig.STYLE_DARK_STANDBY
                prefs.setDesignStyle(normalized)
                val standBy = args.optString("standby_style", "")
                if (standBy.isNotBlank()) prefs.setStandByStyleMode(standBy)
                PenMcpToolResult(toolName, "已切换桌面风格为 ${if (normalized == PenConfig.STYLE_APPLE_LIGHT) "Apple白画廊" else "暗黑StandBy"}", true)
            }
            "manage_widgets" -> {
                val wid = args.optString("widget_id")
                val enable = args.optBoolean("enable", true)
                val cur = uiState.value.config.enabledWidgetIds.toMutableList()
                if (enable && !cur.contains(wid) && PenConfig.DEFAULT_WIDGET_ORDER.contains(wid)) {
                    cur.add(wid)
                    prefs.setEnabledWidgets(cur)
                } else if (!enable && cur.contains(wid) && cur.size > 1) {
                    cur.remove(wid)
                    prefs.setEnabledWidgets(cur)
                }
                PenMcpToolResult(toolName, "已${if (enable) "开启" else "隐藏"}小组件 $wid", true)
            }
            "add_todo" -> {
                val title = args.optString("title").trim()
                val due = args.optString("due_date").ifBlank { _clock.value.isoDate }
                if (title.isBlank()) return PenMcpToolResult(toolName, "待办标题为空", false)
                todoDao.insertTodo(TodoItemEntity(title = title, dueDateIso = due, priority = 1))
                PenMcpToolResult(toolName, "已添加待办「$title」", true)
            }
            "add_calendar_event" -> {
                val title = args.optString("title").trim()
                val dateIso = args.optString("date_iso").ifBlank { _clock.value.isoDate }
                val time = normalizeHHmm(args.optString("time_hhmm", "08:30"))
                if (title.isBlank()) return PenMcpToolResult(toolName, "日程标题为空", false)
                calendarEventDao.insertEvent(CalendarEventEntity(dateIso = dateIso, timeHHmm = time, title = title))
                PenMcpToolResult(toolName, "已添加日程 $dateIso $time「$title」", true)
            }
            "switch_weather_city" -> {
                val cityName = args.optString("city_name").trim()
                val found = WmoWeatherMapper.findBestCity(cityName) ?: WeatherCityOption(cityName, "自定义", "", 35.0, 114.0)
                val res = weatherRepo.selectCityAndRefresh(found)
                _weatherSourceStatus.value = res.sourceLabel
                PenMcpToolResult(toolName, "天气已切换为 ${found.displayName}（${res.sourceLabel}）", true)
            }
            "start_pomodoro" -> {
                val mins = args.optInt("minutes", 25).coerceIn(1, 120)
                openPomodoroFocus(autoStartMinutes = mins)
                PenMcpToolResult(toolName, "已开启 $mins 分钟全屏番茄专注", true)
            }
            "control_white_noise" -> {
                val action = args.optString("action", "play")
                if (action.equals("stop", ignoreCase = true)) {
                    whiteNoise.stop()
                    PenMcpToolResult(toolName, "已停止白噪音", true)
                } else {
                    val soundId = args.optString("sound", "rain")
                    val type = WhiteNoiseType.entries.firstOrNull { it.id.equals(soundId, ignoreCase = true) } ?: WhiteNoiseType.RAIN
                    whiteNoise.start(type)
                    PenMcpToolResult(toolName, "已开始播放白噪音：${type.label}", true)
                }
            }
            "adjust_device" -> {
                val changes = mutableListOf<String>()
                if (args.has("brightness_percent")) {
                    val b = (args.optInt("brightness_percent", 60) / 100f).coerceIn(0.08f, 1f)
                    setScreenBrightness(b)
                    changes.add("亮度${(b * 100).toInt()}%")
                }
                if (args.has("volume_percent")) {
                    val v = (args.optInt("volume_percent", 50) / 100f).coerceIn(0f, 1f)
                    setMediaVolume(v)
                    changes.add("音量${(v * 100).toInt()}%")
                }
                if (args.has("night_eye_care")) {
                    val night = args.optBoolean("night_eye_care", false)
                    prefs.setNightDimAlpha(if (night) 0.35f else 0f)
                    changes.add(if (night) "护眼暗屏开" else "护眼暗屏关")
                }
                PenMcpToolResult(toolName, "设备已调节：${changes.joinToString(" / ").ifBlank { "无变更" }}", true)
            }
            "add_vocab_word" -> {
                val word = args.optString("word").trim()
                val meaning = args.optString("meaning_zh").trim()
                if (word.isBlank() || meaning.isBlank()) return PenMcpToolResult(toolName, "单词或释义为空", false)
                val entity = VocabWordEntity(
                    id = "ai_${word.lowercase().replace(Regex("[^a-z0-9]"), "_")}",
                    word = word,
                    phonetic = args.optString("phonetic", "/$word/"),
                    partOfSpeech = "ai.",
                    meaningZh = meaning,
                    mnemonicTag = "AI 收录",
                    rootBreakdown = args.optString("root_breakdown", "AI 词根助记"),
                    visualCue = "来自 AI 实验室生词卡片",
                    exampleEn = args.optString("example_en", "Remember \"$word\" in context."),
                    exampleZh = meaning,
                    bookId = "custom",
                    accentHex = 0xFF38BDF8
                )
                vocabDao.upsertWords(listOf(entity))
                PenMcpToolResult(toolName, "已收录生词「$word」到自定义词书", true)
            }
            else -> PenMcpToolResult(toolName, "未知工具：$toolName", false)
        }
    }

    // --- Cloud Sync ---
    fun saveWebDavConfig(url: String, user: String, pass: String) {
        viewModelScope.launch {
            prefs.saveWebDavConfig(url, user, pass)
            showToast("WebDAV 配置已保存")
        }
    }

    fun syncVocabViaWebDav() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val result = syncManager.syncWithWebDav()
            _isSyncing.value = false
            result.onSuccess { showToast(it.message) }
                .onFailure { showToast(it.message ?: "WebDAV 同步失败") }
        }
    }

    fun generatePortableSyncCode() {
        viewModelScope.launch {
            _generatedSyncCode.value = syncManager.generateSyncCode()
            showToast("离线同步码已生成")
        }
    }

    fun importPortableSyncCode(code: String) {
        viewModelScope.launch {
            syncManager.importSyncCode(code)
                .onSuccess { showToast(it.message) }
                .onFailure { showToast(it.message ?: "同步码导入失败") }
        }
    }

    suspend fun exportVocabJsonPayload(): String = syncManager.exportJson()

    fun importVocabJsonPayload(json: String) {
        viewModelScope.launch {
            syncManager.importJson(json)
                .onSuccess { showToast(it.message) }
                .onFailure { showToast(it.message ?: "JSON 备份导入失败") }
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun showToast(msg: String) {
        _statusBanner.value = msg
        viewModelScope.launch {
            delay(3200L)
            if (_statusBanner.value == msg) _statusBanner.value = null
        }
    }

    private fun normalizeHHmm(raw: String): String {
        val parts = raw.trim().replace("：", ":").split(":")
        val hh = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val mm = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return "%02d:%02d".format(hh, mm)
    }

    private fun readDeviceControl(): DeviceControlState = DeviceControlState(
        brightnessFraction = SystemTakeoverHelper.getSystemBrightnessFraction(app),
        mediaVolumeFraction = SystemTakeoverHelper.getMediaVolumeFraction(app),
        canWriteSystemBrightness = SystemTakeoverHelper.canWriteSystemSettings(app)
    )

    private fun readTelemetry(previous: ClockTelemetryState, includeHeavyStats: Boolean): ClockTelemetryState {
        val now = Date()
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val secFmt = SimpleDateFormat("ss", Locale.getDefault())
        val dateFmt = SimpleDateFormat("M月d日 E", Locale.CHINA)
        val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        if (!includeHeavyStats) {
            return previous.copy(
                timeHHmm = timeFmt.format(now),
                secondsStr = secFmt.format(now),
                dateLabel = dateFmt.format(now),
                isoDate = isoFmt.format(now)
            )
        }

        val batteryIntent = try {
            app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else previous.batteryPercent
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val wifiConnected = try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) false
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } else {
                @Suppress("DEPRECATION")
                val info = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                info != null && info.isConnected && info.type == ConnectivityManager.TYPE_WIFI
            }
        } catch (_: Exception) {
            false
        }

        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val availMb = (memInfo.availMem / (1024L * 1024L)).toInt().coerceAtLeast(1)
        val totalMb = (memInfo.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(availMb)

        return ClockTelemetryState(
            timeHHmm = timeFmt.format(now),
            secondsStr = secFmt.format(now),
            dateLabel = dateFmt.format(now),
            isoDate = isoFmt.format(now),
            batteryPercent = batteryPct,
            isCharging = charging,
            isWifiConnected = wifiConnected,
            availRamMb = availMb,
            totalRamMb = totalMb
        )
    }
}
