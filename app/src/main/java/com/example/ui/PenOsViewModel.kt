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
import com.example.data.local.CustomWidgetEntity
import com.example.data.local.DesktopSnapshotEntity
import com.example.data.local.DrawerSlotEntity
import com.example.data.local.MailAccountEntity
import com.example.data.local.MailMessageEntity
import com.example.data.local.OfflineSeedData
import com.example.data.local.TodoItemEntity
import com.example.data.local.VocabCatalogExpanded
import com.example.data.local.VocabProgressEntity
import com.example.data.local.VocabWordWithProgress
import com.example.data.prefs.AiApiProfile
import com.example.data.prefs.AiPreset
import com.example.data.prefs.MultiClockConfig
import com.example.data.prefs.PenConfig
import com.example.data.remote.AiChatMessage
import com.example.data.remote.WeatherCityOption
import com.example.data.remote.WmoWeatherMapper
import com.example.system.AntiLockCoordinator
import com.example.system.BitmapDecodeCache
import com.example.system.InstalledAppInfo
import com.example.system.LanServerStatus
import com.example.system.PenKeepAliveService
import com.example.system.SafeMediaImporter
import com.example.system.SystemDiagnostics
import com.example.system.SystemTakeoverHelper
import com.example.system.WhiteNoiseType
import com.example.data.remote.PenMcpToolResult
import com.example.ui.theme.FontSlotSpec
import com.example.ui.theme.PenFontCatalog
import com.example.ui.theme.PenMotionTier
import com.example.widgets.CustomWidgetDslParser
import com.example.widgets.CustomWidgetTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
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
    private val customWidgetDao = app.database.customWidgetDao()
    private val drawerSlotDao = app.database.drawerSlotDao()
    private val mailDao = app.database.mailDao()
    private val snapshotDao = app.database.desktopSnapshotDao()
    private val snapshotManager = app.snapshotManager
    private val poetryRepo = app.poetryRepository
    private val weatherRepo = app.weatherRepository
    private val syncManager = app.vocabSyncManager
    private val appRepo = app.installedAppRepository
    private val whiteNoise = app.whiteNoiseEngine
    private val aiMcpClient = app.aiMcpClient
    private val mailClient = app.mailClient
    val lanAdminServer = app.lanAdminServer
    val nativeWidgetHost = app.nativeWidgetHostManager

    private val _screenMode = MutableStateFlow(PenScreenMode.DESKTOP)
    private val _activeOverlay = MutableStateFlow(DesktopOverlay.NONE)

    // 60fps Architecture: 1-second ticks stay in clockTickFlow so only clock leaves recompose!
    private val initialTelemetry = readTelemetry(previous = ClockTelemetryState(), includeHeavyStats = true)
    private val _clockTick = MutableStateFlow(initialTelemetry)
    val clockTickFlow: StateFlow<ClockTelemetryState> = _clockTick.asStateFlow()

    // Slow telemetry in uiState updates only on minute/date/battery/RAM change (NOT every second)
    private val _slowTelemetry = MutableStateFlow(initialTelemetry)

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

    // Mail ephemeral UI state
    private val _selectedMailId = MutableStateFlow<String?>(null)
    private val _isMailLoading = MutableStateFlow(false)
    private val _isMailAiProcessing = MutableStateFlow(false)
    private val _mailAiSummary = MutableStateFlow("")
    private val _pendingMailDraft = MutableStateFlow<MailDraftState?>(null)
    private val _showMailAccountDialog = MutableStateFlow(false)

    // PenOS 5.0 Guardrails, RAM Governance, 60s Bypass & Gesture Haptics
    private val _pendingMcpChanges = MutableStateFlow<List<PendingMcpChange>>(emptyList())
    private val _memoryHealth = MutableStateFlow(MemoryHealthState(availRamMb = initialTelemetry.availRamMb))
    private val _stockHomeBypassSec = MutableStateFlow(0)
    private val _gestureHapticNonce = MutableStateFlow(0)

    private var allWordsCache: List<VocabWordWithProgress> = emptyList()
    private var lastUserInteractionElapsedMs: Long = SystemClock.elapsedRealtime()
    private var lastSecretTapElapsedMs: Long = 0L
    private var hasCheckedInitialSetup: Boolean = false
    private var isSilentMailSyncRunning: Boolean = false

    private val harnessExecutor = PenHarnessExecutor(
        prefs = prefs,
        database = app.database,
        weatherRepo = weatherRepo,
        whiteNoise = whiteNoise,
        getIsoDate = { _clockTick.value.isoDate },
        getInstalledApps = { _installedApps.value },
        onSetWeatherSource = { _weatherSourceStatus.value = it },
        onStartPomodoro = { openPomodoroFocus(autoStartMinutes = it) },
        onSetBrightness = { setScreenBrightness(it) },
        onSetVolume = { setMediaVolume(it) },
        onLaunchApp = { launchApp(it) },
        onPresentMailDraft = { to, subject, body ->
            _pendingMailDraft.value = MailDraftState(to = to, subject = subject, body = body, isAiGenerated = true)
            _activeOverlay.value = DesktopOverlay.NONE
            _screenMode.value = PenScreenMode.MAIL
        }
    )

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

    private data class Ext40Bundle(
        val customWidgets: List<CustomWidgetEntity>,
        val drawerSlots: List<DrawerSlotEntity>,
        val mail: MailUiState,
        val lanServer: LanServerStatus
    )

    private data class FocusControlBundle(
        val pomodoro: PomodoroState,
        val deviceControl: DeviceControlState,
        val isPoemLoading: Boolean,
        val lab: LabState
    )

    private data class Pen50Bundle(
        val snapshots: List<DesktopSnapshotEntity>,
        val pendingMcp: List<PendingMcpChange>,
        val memoryHealth: MemoryHealthState,
        val bypassSec: Int,
        val hapticNonce: Int
    )

    private data class AppMetaBundle(
        val installedApps: List<InstalledAppInfo>,
        val vocabSession: VocabSessionState,
        val secretTrigger: SecretTriggerState,
        val diagnostics: SystemDiagnostics,
        val isVaultUnlocked: Boolean,
        val isSyncing: Boolean,
        val statusBanner: String?,
        val syncCode: String?,
        val pen50: Pen50Bundle
    )

    private val coreFlow = combine(
        _screenMode,
        _activeOverlay,
        prefs.configFlow,
        _slowTelemetry,
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

    private val mailMetaFlow = combine(
        _selectedMailId,
        _isMailLoading,
        _isMailAiProcessing,
        _mailAiSummary,
        _pendingMailDraft
    ) { selId, loading, aiBusy, summary, draft ->
        listOf(selId, loading, aiBusy, summary, draft)
    }

    @Suppress("UNCHECKED_CAST")
    private val mailStateFlow = combine(
        mailDao.observeAccounts(),
        mailDao.observeRecentMessages(),
        mailMetaFlow,
        _showMailAccountDialog
    ) { accounts, msgs, meta, showAcc ->
        MailUiState(
            accounts = accounts,
            messages = msgs,
            selectedMessageId = meta[0] as String? ?: msgs.firstOrNull()?.id,
            isLoading = meta[1] as Boolean,
            isAiProcessing = meta[2] as Boolean,
            aiSummary = meta[3] as String,
            pendingDraft = meta[4] as MailDraftState?,
            showAccountDialog = showAcc
        )
    }

    private val ext40Flow = combine(
        customWidgetDao.observeAllCustomWidgets(),
        drawerSlotDao.observeAllSlots(),
        mailStateFlow,
        lanAdminServer.status
    ) { customWidgets, drawerSlots, mail, lan ->
        Ext40Bundle(customWidgets, drawerSlots, mail, lan)
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

    private val pen50Flow = combine(
        snapshotDao.observeRecentSnapshots(),
        _pendingMcpChanges,
        _memoryHealth,
        _stockHomeBypassSec,
        _gestureHapticNonce
    ) { snaps, pending, mem, bypassSec, haptic ->
        Pen50Bundle(snaps, pending, mem, bypassSec, haptic)
    }

    @Suppress("UNCHECKED_CAST")
    private val appMetaFlow = combine(appSubFlow, metaSubFlow, pen50Flow) { a, m, p50 ->
        AppMetaBundle(
            installedApps = a[0] as List<InstalledAppInfo>,
            vocabSession = a[1] as VocabSessionState,
            secretTrigger = a[2] as SecretTriggerState,
            diagnostics = a[3] as SystemDiagnostics,
            isVaultUnlocked = m[0] as Boolean,
            isSyncing = m[1] as Boolean,
            statusBanner = m[2] as String?,
            syncCode = m[3] as String?,
            pen50 = p50
        )
    }

    val uiState: StateFlow<PenOsUiState> = combine(
        coreFlow,
        productivityFlow,
        ext40Flow,
        focusControlFlow,
        appMetaFlow
    ) { core, prod, ext, focus, appMeta ->
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
            customWidgets = ext.customWidgets,
            drawerSlots = ext.drawerSlots,
            installedApps = appMeta.installedApps,
            vocabSession = appMeta.vocabSession,
            mail = ext.mail,
            lanServer = ext.lanServer,
            lab = focus.lab,
            secretTrigger = appMeta.secretTrigger,
            diagnostics = appMeta.diagnostics,
            isVaultUnlocked = appMeta.isVaultUnlocked,
            isSyncing = appMeta.isSyncing,
            statusBannerMessage = appMeta.statusBanner,
            generatedSyncCode = appMeta.syncCode,
            recentSnapshots = appMeta.pen50.snapshots,
            pendingMcpChanges = appMeta.pen50.pendingMcp,
            memoryHealth = appMeta.pen50.memoryHealth,
            stockHomeBypassRemainingSec = appMeta.pen50.bypassSec,
            gestureHapticNonce = appMeta.pen50.hapticNonce
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
        observeMemoryPressure()
        wireLanServerCallbacks()
        startLowPowerClockAndLoops()
        refreshInstalledApps()
        refreshSystemDiagnostics()
        refreshPoem(force = false)
        refreshWeather(force = false)
    }

    override fun onCleared() {
        whiteNoise.stop()
        lanAdminServer.stop()
        super.onCleared()
    }

    private fun wireLanServerCallbacks() {
        lanAdminServer.onRemoteAiPrompt = { prompt ->
            val cfg = prefs.currentConfig()
            val profile = cfg.activeAiProfile()
            val userMsg = AiChatMessage("lan_${System.currentTimeMillis()}", "user", "[局域网] $prompt")
            _aiMessages.update { (it + userMsg).takeLast(24) }
            var snapCaptured = false
            val result = aiMcpClient.sendChatWithMcp(
                profile = profile,
                history = _aiMessages.value,
                userPrompt = prompt,
                externalMcpUrl = cfg.externalMcpUrl,
                desktopToolExecutor = { name, args ->
                    if (!harnessExecutor.isReadOnlyTool(name) && !snapCaptured) {
                        snapshotManager.captureSnapshot("局域网 AI 修改前：${prompt.take(18)}", "LAN")
                        snapCaptured = true
                    }
                    harnessExecutor.execute(name, args)
                }
            )
            result.fold(
                onSuccess = { reply ->
                    _aiMessages.update { (it + reply).takeLast(24) }
                    reply.content to reply.toolActionsSummary
                },
                onFailure = { err ->
                    "执行失败：${err.message}" to ""
                }
            )
        }
        lanAdminServer.onMailAccountAdded = {
            syncAllMailAccounts()
        }
    }

    private fun observeMemoryPressure() {
        viewModelScope.launch {
            app.memoryPressureFlow.collect { nonce ->
                if (nonce > 0) {
                    enforceRamAutoDowngradeIfNeeded(availMb = _clockTick.value.availRamMb, forcedByTrim = true)
                }
            }
        }
    }

    private suspend fun enforceRamAutoDowngradeIfNeeded(availMb: Int, forcedByTrim: Boolean) {
        val cfg = prefs.currentConfig()
        _memoryHealth.update { it.copy(availRamMb = availMb) }
        val isHeavyWallpaper = cfg.dynamicWallpaperMode == PenConfig.DYN_WALLPAPER_MP4 ||
            cfg.dynamicWallpaperMode == PenConfig.DYN_WALLPAPER_GIF
        if ((forcedByTrim || availMb in 1..109) && isHeavyWallpaper) {
            BitmapDecodeCache.clear()
            PenFontCatalog.clearExternalCache()
            val prevMode = cfg.dynamicWallpaperMode
            val prevMedia = cfg.dynamicWallpaperMediaPath
            prefs.setDynamicWallpaperConfig(mode = PenConfig.DYN_WALLPAPER_STATIC)
            _memoryHealth.value = MemoryHealthState(
                isDegraded = true,
                previousWallpaperMode = prevMode,
                previousMediaPath = prevMedia,
                availRamMb = availMb
            )
            showToast("内存偏紧(${availMb}MB)，已自动降级动态壁纸以保障桌面流畅")
        }
    }

    fun restoreDegradedWallpaper() {
        onUserInteraction()
        val mem = _memoryHealth.value
        if (!mem.isDegraded || mem.previousWallpaperMode.isBlank()) return
        viewModelScope.launch {
            prefs.setDynamicWallpaperConfig(
                mode = mem.previousWallpaperMode,
                mediaPath = mem.previousMediaPath.takeIf { it.isNotBlank() }
            )
            _memoryHealth.value = mem.copy(isDegraded = false)
            showToast("已恢复先前的动态壁纸")
        }
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
            if (customWidgetDao.getCount() == 0) {
                val sampleBlocks = JSONArray().apply {
                    put(JSONObject().put("type", "header").put("title", "学习冲刺看板").put("badge", "MCP 可改"))
                    put(JSONObject().put("type", "countdown").put("label", "期末目标倒计时").put("target_iso", "2027-06-07"))
                    put(
                        JSONObject().put("type", "checklist").put(
                            "items",
                            JSONArray().apply {
                                put(JSONObject().put("text", "英语核心 20 词").put("done", false))
                                put(JSONObject().put("text", "25 分钟无干扰专注").put("done", false))
                            }
                        )
                    )
                    put(JSONObject().put("type", "action_button").put("label", "一键开启专注").put("action", "pomodoro").put("target", "25"))
                }
                customWidgetDao.upsert(
                    CustomWidgetEntity(
                        id = "sample_sprint_board",
                        title = "学习冲刺看板",
                        placement = CustomWidgetEntity.PLACEMENT_BOTH,
                        widthDp = 204,
                        accentHex = 0xFF38BDF8,
                        dslJson = sampleBlocks.toString()
                    )
                )
            }
            if (drawerSlotDao.getCount() == 0) {
                drawerSlotDao.insertAll(
                    listOf(
                        DrawerSlotEntity(id = "slot_clock", type = DrawerSlotEntity.TYPE_BUILTIN_CLOCK, refId = "multi_clock", title = "多样式时钟", sortOrder = 0),
                        DrawerSlotEntity(id = "slot_cw_sample_sprint_board", type = DrawerSlotEntity.TYPE_CUSTOM_WIDGET, refId = "sample_sprint_board", title = "学习冲刺看板", sortOrder = 1),
                        DrawerSlotEntity(id = "slot_ai_switch", type = DrawerSlotEntity.TYPE_AI_SWITCHER, refId = "ai_profiles", title = "AI 模型快切", sortOrder = 2),
                        DrawerSlotEntity(id = "slot_mail", type = DrawerSlotEntity.TYPE_BUILTIN_MAIL, refId = "mail_inbox", title = "笔端邮箱", sortOrder = 3),
                        DrawerSlotEntity(id = "slot_act_lan", type = DrawerSlotEntity.TYPE_ACTION_TILE, refId = "lan_admin", title = "局域网管理", sortOrder = 4)
                    )
                )
            }
        }
    }

    private fun observePreferencesForSystemGuards() {
        viewModelScope.launch {
            prefs.configFlow.collect { cfg ->
                if (!hasCheckedInitialSetup) {
                    hasCheckedInitialSetup = true
                    if (!cfg.setupAssistantCompleted) {
                        _screenMode.value = PenScreenMode.SETUP
                    }
                }
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
                val includeHeavy = (tickCount % 15 == 0)
                val nextClock = readTelemetry(previous = _clockTick.value, includeHeavyStats = includeHeavy)
                _clockTick.value = nextClock

                // Only propagate to monolithic uiState when minute/date or heavy telemetry changes!
                val prevSlow = _slowTelemetry.value
                if (includeHeavy ||
                    nextClock.timeHHmm != prevSlow.timeHHmm ||
                    nextClock.isoDate != prevSlow.isoDate
                ) {
                    _slowTelemetry.value = nextClock.copy(secondsStr = "00")
                }
                tickCount++

                // Pomodoro countdown tick (only mutates _pomodoro while running)
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

                // PenOS 5.0: Update 60s stock home bypass countdown
                val bypassSec = AntiLockCoordinator.remainingStockHomeBypassSec()
                if (_stockHomeBypassSec.value != bypassSec) {
                    _stockHomeBypassSec.value = bypassSec
                }

                // PenOS 5.0: 1GB RAM watchdog & 15-min silent Wi-Fi mail sync on heavy telemetry ticks
                if (includeHeavy) {
                    enforceRamAutoDowngradeIfNeeded(availMb = nextClock.availRamMb, forcedByTrim = false)
                    maybeRunSilentWifiMailSync(cfg = cfg, isWifi = nextClock.isWifiConnected)
                }

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

    private fun maybeRunSilentWifiMailSync(cfg: PenConfig, isWifi: Boolean) {
        if (!cfg.mailAutoSyncEnabled || !isWifi || _memoryHealth.value.isDegraded) return
        if (_screenMode.value == PenScreenMode.STANDBY || _isMailLoading.value || isSilentMailSyncRunning) return
        val now = System.currentTimeMillis()
        if (now - cfg.lastMailAutoSyncAt < 15L * 60L * 1000L) return

        viewModelScope.launch(Dispatchers.IO) {
            val accounts = mailDao.getAccountsSnapshot()
            if (accounts.isEmpty()) return@launch
            isSilentMailSyncRunning = true
            try {
                val unreadBefore = mailDao.getRecentMessagesSnapshot(60).count { it.isUnread }
                var anySuccess = false
                accounts.forEach { acc ->
                    mailClient.fetchRecentInbox(acc, maxCount = 15).onSuccess { list ->
                        mailDao.upsertMessages(list)
                        mailDao.updateAccountSyncedAt(acc.id, System.currentTimeMillis())
                        anySuccess = true
                    }
                }
                if (anySuccess) {
                    prefs.setLastMailAutoSyncAt(System.currentTimeMillis())
                    val unreadAfter = mailDao.getRecentMessagesSnapshot(60).count { it.isUnread }
                    if (unreadAfter > unreadBefore) {
                        showToast("收到 ${unreadAfter - unreadBefore} 封新邮件")
                    }
                }
            } finally {
                isSilentMailSyncRunning = false
            }
        }
    }

    // --- PenOS 5.0 Stock Home 60s Bypass, Gesture Tuning, Mail Auto-Sync & Config Backup ---
    fun toggleStockHomeBypass60s() {
        onUserInteraction()
        if (AntiLockCoordinator.isStockHomeBypassActive()) {
            AntiLockCoordinator.revokeStockHomeBypass()
            _stockHomeBypassSec.value = 0
            showToast("已恢复久学网原厂桌面拦截防锁回")
        } else {
            AntiLockCoordinator.grantStockHomeBypass(60_000L)
            _stockHomeBypassSec.value = 60
            showToast("已临时放行原厂桌面 60 秒（倒计时结束自动恢复拦截）")
        }
    }

    fun setSwipeSensitivityDp(dp: Float) {
        onUserInteraction()
        viewModelScope.launch { prefs.setSwipeSensitivityDp(dp) }
    }

    fun setGestureHapticsEnabled(enabled: Boolean) {
        onUserInteraction()
        viewModelScope.launch { prefs.setGestureHapticsEnabled(enabled) }
    }

    fun triggerGestureHaptic() {
        if (uiState.value.config.gestureHapticsEnabled) {
            _gestureHapticNonce.value += 1
        }
    }

    fun setMailAutoSyncEnabled(enabled: Boolean) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setMailAutoSyncEnabled(enabled)
            showToast(if (enabled) "已开启连 Wi-Fi 每 15 分钟静默收信" else "已关闭后台自动收信")
        }
    }

    suspend fun exportFullConfigJson(includeSecrets: Boolean = false): String =
        snapshotManager.exportFullConfigJson(includeSecrets = includeSecrets)

    fun importFullConfigJson(json: String) {
        onUserInteraction()
        viewModelScope.launch {
            snapshotManager.importFullConfigJson(json)
                .onSuccess { showToast(it) }
                .onFailure { showToast("导入配置失败：${it.message}") }
        }
    }

    fun syncFullConfigWithWebDav(uploadOnly: Boolean = false) {
        onUserInteraction()
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val res = snapshotManager.syncFullConfigWithWebDav(uploadOnly = uploadOnly)
            _isSyncing.value = false
            res.onSuccess { showToast(it) }
                .onFailure { showToast("WebDAV 配置同步失败：${it.message}") }
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

    // --- Setup Assistant ---
    fun completeSetupAssistant() {
        onUserInteraction()
        hasCheckedInitialSetup = true
        _screenMode.value = PenScreenMode.DESKTOP
        showToast("欢迎使用 PenOS 5.0，已准备就绪")
        viewModelScope.launch {
            prefs.setSetupAssistantCompleted(true)
        }
    }

    fun reopenSetupAssistant() {
        onUserInteraction()
        _activeOverlay.value = DesktopOverlay.NONE
        _screenMode.value = PenScreenMode.SETUP
    }

    // --- Motion Tier, 4-Mode Dynamic Wallpaper, 5-Slot Global Typography & 6-Style Clock ---
    fun setMotionTier(tierId: String) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setMotionTier(tierId)
            val label = when (tierId) {
                PenMotionTier.FAST.id -> "极速流畅（60fps 推荐）"
                PenMotionTier.SPRING.id -> "轻盈弹簧"
                else -> "零动效省电"
            }
            showToast("已切换动效档位：$label")
        }
    }

    fun setDesktopEngineMode(mode: String) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setDesktopEngineMode(mode)
            val label = if (mode == PenConfig.ENGINE_COMPOSE_FULL) {
                "完整 Compose 桌面（已深度优化）"
            } else {
                "原生 View 极速桌面（MT6582 推荐）"
            }
            showToast("主屏引擎已切换：$label")
        }
    }

    fun toggleDesktopEngineMode() {
        val current = uiState.value.config.desktopEngineMode
        val next = if (current == PenConfig.ENGINE_NATIVE_TURBO) {
            PenConfig.ENGINE_COMPOSE_FULL
        } else {
            PenConfig.ENGINE_NATIVE_TURBO
        }
        setDesktopEngineMode(next)
    }

    fun setZeroOverdrawMode(enabled: Boolean) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setZeroOverdrawMode(enabled)
            showToast(if (enabled) "已开启零过绘制纯色极速视觉" else "已切回半透明轻质感视觉")
        }
    }

    fun setDynamicWallpaperMode(mode: String, proceduralStyle: String? = null, mediaPath: String? = null) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setDynamicWallpaperConfig(mode = mode, proceduralStyle = proceduralStyle, mediaPath = mediaPath)
            val label = when (mode) {
                PenConfig.DYN_WALLPAPER_PROCEDURAL -> "代码极光动效"
                PenConfig.DYN_WALLPAPER_GIF -> "本地 GIF 动态壁纸"
                PenConfig.DYN_WALLPAPER_MP4 -> "本地 MP4 视频壁纸"
                PenConfig.DYN_WALLPAPER_LIVE -> "安卓系统动态壁纸透传"
                else -> "静态高清壁纸"
            }
            showToast("动态壁纸已切换：$label")
        }
    }

    fun importDynamicMediaFromUri(uri: Uri, extension: String) {
        onUserInteraction()
        viewModelScope.launch {
            SafeMediaImporter.importDynamicMediaFromUri(app, uri, extension)
                .onSuccess { path ->
                    val mode = if (extension.equals("mp4", true)) PenConfig.DYN_WALLPAPER_MP4 else PenConfig.DYN_WALLPAPER_GIF
                    prefs.setDynamicWallpaperConfig(mode = mode, mediaPath = path)
                    showToast("已应用本地 ${extension.uppercase()} 动态壁纸")
                }
                .onFailure { showToast("导入动态壁纸失败：${it.message}") }
        }
    }

    fun importDynamicMediaFromPath(rawPath: String) {
        onUserInteraction()
        viewModelScope.launch {
            SafeMediaImporter.importDynamicMediaFromPath(app, rawPath)
                .onSuccess { (ext, path) ->
                    val mode = if (ext == "mp4") PenConfig.DYN_WALLPAPER_MP4 else PenConfig.DYN_WALLPAPER_GIF
                    prefs.setDynamicWallpaperConfig(mode = mode, mediaPath = path)
                    showToast("已加载 ${ext.uppercase()} 动态壁纸")
                }
                .onFailure { showToast("读取失败：${it.message}") }
        }
    }

    fun setMultiClockConfig(style: String? = null, shape: String? = null, showSeconds: Boolean? = null) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setMultiClockConfig(style = style, shape = shape, showSeconds = showSeconds)
        }
    }

    fun cycleMultiClockStyle() {
        onUserInteraction()
        val styles = MultiClockConfig.allStyles.map { it.first }
        val cur = uiState.value.config.multiClock.style
        val next = styles[(styles.indexOf(cur).coerceAtLeast(0) + 1) % styles.size]
        setMultiClockConfig(style = next)
    }

    fun updateFontSlot(slotName: String, spec: FontSlotSpec) {
        onUserInteraction()
        viewModelScope.launch { prefs.updateFontSlot(slotName, spec) }
    }

    fun importFontForSlotFromUri(slotName: String, uri: Uri) {
        onUserInteraction()
        viewModelScope.launch {
            SafeMediaImporter.importFontFromUri(app, uri)
                .onSuccess { path ->
                    val cur = prefs.currentConfig().typographyProfile.slotByName(slotName)
                    prefs.updateFontSlot(slotName, cur.copy(familyKey = "external", externalPath = path))
                    showToast("已应用外部字体到 $slotName 通道")
                }
                .onFailure { showToast("字体导入失败：${it.message}") }
        }
    }

    fun importFontForSlotFromPath(slotName: String, rawPath: String) {
        onUserInteraction()
        viewModelScope.launch {
            SafeMediaImporter.importFontFromPath(app, rawPath)
                .onSuccess { path ->
                    val cur = prefs.currentConfig().typographyProfile.slotByName(slotName)
                    prefs.updateFontSlot(slotName, cur.copy(familyKey = "external", externalPath = path))
                    showToast("已从路径加载字体到 $slotName 通道")
                }
                .onFailure { showToast("字体路径无效：${it.message}") }
        }
    }

    fun resetTypographyProfile() {
        onUserInteraction()
        viewModelScope.launch {
            prefs.resetTypographyProfile()
            showToast("5 通道全局字体已重置默认")
        }
    }

    // --- Custom DSL Widgets & Apple TV Drawer ---
    fun toggleCustomWidgetChecklist(widget: CustomWidgetEntity, blockIndex: Int, itemIndex: Int) {
        onUserInteraction()
        val updatedJson = CustomWidgetDslParser.toggleChecklistItem(widget.dslJson, blockIndex, itemIndex)
        mutateCustomWidgetDsl(widget, updatedJson)
    }

    fun mutateCustomWidgetDsl(widget: CustomWidgetEntity, updatedDslJson: String) {
        onUserInteraction()
        viewModelScope.launch(Dispatchers.IO) {
            customWidgetDao.upsert(widget.copy(dslJson = updatedDslJson, updatedAt = System.currentTimeMillis()))
        }
    }

    fun refreshCustomHttpBlock(widget: CustomWidgetEntity, blockIndex: Int, url: String, jsonPath: String) {
        onUserInteraction()
        viewModelScope.launch {
            val res = CustomWidgetDslParser.fetchHttpJsonValue(url, jsonPath)
            val nowLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            res.onSuccess { value ->
                val updated = CustomWidgetDslParser.updateHttpJsonCache(widget.dslJson, blockIndex, value, nowLabel)
                customWidgetDao.upsert(widget.copy(dslJson = updated, updatedAt = System.currentTimeMillis()))
                showToast("JSON 指标已刷新：$value")
            }.onFailure { err ->
                showToast("JSON 拉取失败：${err.message}")
            }
        }
    }

    fun runWidgetMacro(steps: List<String>) {
        onUserInteraction()
        if (steps.isEmpty()) return
        viewModelScope.launch {
            steps.take(6).forEach { rawStep ->
                val parts = rawStep.trim().split(":", limit = 2)
                val cmd = parts[0].lowercase()
                val arg = parts.getOrElse(1) { "" }.trim()
                when (cmd) {
                    "night_eye_on" -> prefs.setNightDimAlpha(0.35f)
                    "night_eye_off" -> prefs.setNightDimAlpha(0f)
                    "white_noise" -> {
                        if (arg.equals("stop", true)) {
                            whiteNoise.stop()
                        } else {
                            val t = WhiteNoiseType.entries.firstOrNull { it.id.equals(arg, true) } ?: WhiteNoiseType.RAIN
                            whiteNoise.start(t)
                        }
                    }
                    "pomodoro" -> openPomodoroFocus(autoStartMinutes = arg.toIntOrNull() ?: 25)
                    "brightness" -> arg.toIntOrNull()?.let { setScreenBrightness(it / 100f) }
                    "volume" -> arg.toIntOrNull()?.let { setMediaVolume(it / 100f) }
                    "theme_light" -> prefs.setDesignStyle(PenConfig.STYLE_APPLE_LIGHT)
                    "theme_dark" -> prefs.setDesignStyle(PenConfig.STYLE_DARK_STANDBY)
                    else -> dispatchCustomWidgetAction(cmd, arg)
                }
            }
            showToast("宏工作流已执行（${steps.size} 步）")
        }
    }

    fun installWidgetTemplate(templateId: String) {
        onUserInteraction()
        val tpl = CustomWidgetTemplates.findById(templateId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            snapshotManager.captureSnapshot("安装模板「${tpl.title}」前", "MANUAL")
            customWidgetDao.upsert(tpl.toEntity())
            val existingSlots = drawerSlotDao.getAllSnapshot()
            if (existingSlots.none { it.type == DrawerSlotEntity.TYPE_CUSTOM_WIDGET && it.refId == tpl.id }) {
                drawerSlotDao.upsert(
                    DrawerSlotEntity(
                        id = "slot_cw_${tpl.id}",
                        type = DrawerSlotEntity.TYPE_CUSTOM_WIDGET,
                        refId = tpl.id,
                        title = tpl.title,
                        sortOrder = existingSlots.size
                    )
                )
            }
            showToast("已一键安装模板「${tpl.title}」到主屏和抽屉")
        }
    }

    fun dispatchCustomWidgetAction(action: String, target: String) {
        onUserInteraction()
        when (action.lowercase()) {
            "pomodoro" -> openPomodoroFocus(autoStartMinutes = target.toIntOrNull() ?: 25)
            "mail" -> switchMode(PenScreenMode.MAIL)
            "lab" -> switchMode(PenScreenMode.LAB)
            "vocab" -> switchMode(PenScreenMode.VOCAB)
            "drawer" -> switchMode(PenScreenMode.APPLE_TV_DRAWER)
            "theme_toggle" -> toggleQuickDesignStyle()
            "lan_admin" -> {
                if (!lanAdminServer.status.value.isRunning) toggleLanAdminServer()
                switchMode(PenScreenMode.LAB)
            }
            "white_noise" -> {
                if (target.equals("stop", true)) {
                    stopWhiteNoise()
                } else {
                    val t = WhiteNoiseType.entries.firstOrNull { it.id.equals(target, true) } ?: WhiteNoiseType.RAIN
                    toggleWhiteNoise(t)
                }
            }
            "launch_app" -> {
                if (target.isNotBlank()) launchApp(target) else openOverlay(DesktopOverlay.ALL_APPS)
            }
            else -> showToast("已执行组件动作：$action")
        }
    }

    fun deleteCustomWidget(widgetId: String) {
        onUserInteraction()
        viewModelScope.launch(Dispatchers.IO) {
            customWidgetDao.deleteById(widgetId)
            drawerSlotDao.deleteById("slot_cw_$widgetId")
        }
        showToast("已移除自定义组件")
    }

    fun addDrawerSlot(slotType: String, refId: String, title: String) {
        onUserInteraction()
        viewModelScope.launch(Dispatchers.IO) {
            val count = drawerSlotDao.getCount()
            drawerSlotDao.upsert(
                DrawerSlotEntity(
                    id = "slot_${slotType.lowercase()}_${refId.ifBlank { System.currentTimeMillis().toString() }}",
                    type = slotType,
                    refId = refId,
                    title = title,
                    sortOrder = count
                )
            )
        }
        showToast("已添加「$title」到 Apple TV 抽屉")
    }

    fun removeDrawerSlot(slotId: String) {
        onUserInteraction()
        viewModelScope.launch(Dispatchers.IO) { drawerSlotDao.deleteById(slotId) }
        showToast("已从抽屉移除")
    }

    // --- LAN Web Admin Console ---
    fun toggleLanAdminServer() {
        onUserInteraction()
        if (lanAdminServer.status.value.isRunning) {
            lanAdminServer.stop()
            showToast("局域网网页控制台已关闭")
        } else {
            val port = uiState.value.config.lanAdminPort
            lanAdminServer.start(port)
            showToast("局域网网页控制台已启动（端口 $port）")
        }
    }

    fun regenerateLanPin() {
        onUserInteraction()
        viewModelScope.launch {
            val newPin = prefs.regenerateLanAdminPin()
            if (lanAdminServer.status.value.isRunning) {
                val port = uiState.value.config.lanAdminPort
                lanAdminServer.stop()
                lanAdminServer.start(port)
            }
            showToast("已刷新 6 位局域网配对码：$newPin")
        }
    }

    // --- Multi-AI API Profiles ---
    fun selectAiApiProfile(profileId: String) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setActiveAiProfile(profileId)
            val name = uiState.value.config.aiProfiles.firstOrNull { it.id == profileId }?.name ?: profileId
            showToast("当前 AI 模型已切换：$name")
        }
    }

    fun upsertAiApiProfile(profile: AiApiProfile) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.upsertAiApiProfile(profile)
            showToast("已保存 AI 模型预设「${profile.name}」")
        }
    }

    fun deleteAiApiProfile(profileId: String) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.deleteAiApiProfile(profileId)
            showToast("已删除 AI 模型预设")
        }
    }

    // --- Built-In IMAP/SMTP Email Client + AI Summarize & Draft ---
    fun setShowMailAccountDialog(show: Boolean) {
        onUserInteraction()
        _showMailAccountDialog.value = show
    }

    fun saveMailAccount(account: MailAccountEntity) {
        onUserInteraction()
        viewModelScope.launch(Dispatchers.IO) {
            mailDao.upsertAccount(account)
            _showMailAccountDialog.value = false
            showToast("已保存邮箱账号 ${account.emailAddress}，正在同步收件箱…")
            syncMailAccountInternal(account)
        }
    }

    fun deleteMailAccount(accountId: String) {
        onUserInteraction()
        viewModelScope.launch(Dispatchers.IO) {
            mailDao.deleteAccount(accountId)
            mailDao.deleteMessagesForAccount(accountId)
        }
        showToast("已移除邮箱账号")
    }

    fun syncAllMailAccounts() {
        onUserInteraction()
        if (_isMailLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val accounts = mailDao.getAccountsSnapshot()
            if (accounts.isEmpty()) {
                showToast("请先配置 IMAP 邮箱账号与授权码")
                _showMailAccountDialog.value = true
                return@launch
            }
            _isMailLoading.value = true
            var totalFetched = 0
            var lastError: String? = null
            accounts.forEach { acc ->
                val res = mailClient.fetchRecentInbox(acc, maxCount = 15)
                res.onSuccess { list ->
                    mailDao.upsertMessages(list)
                    totalFetched += list.size
                }.onFailure { err ->
                    lastError = err.message
                }
            }
            _isMailLoading.value = false
            if (lastError != null && totalFetched == 0) {
                showToast("收信失败：$lastError")
            } else {
                showToast("邮箱同步完成，共缓存 $totalFetched 封邮件")
            }
        }
    }

    private suspend fun syncMailAccountInternal(account: MailAccountEntity) {
        _isMailLoading.value = true
        mailClient.fetchRecentInbox(account, maxCount = 15)
            .onSuccess { list ->
                mailDao.upsertMessages(list)
                showToast("已同步 ${list.size} 封最新邮件")
            }
            .onFailure { err ->
                showToast("收信失败：${err.message}")
            }
        _isMailLoading.value = false
    }

    fun selectMailMessage(messageId: String) {
        onUserInteraction()
        _selectedMailId.value = messageId
        viewModelScope.launch(Dispatchers.IO) {
            mailDao.setUnread(messageId, false)
        }
    }

    fun openMailCompose(to: String = "", subject: String = "", body: String = "", isAiGenerated: Boolean = false) {
        onUserInteraction()
        _pendingMailDraft.value = MailDraftState(to = to, subject = subject, body = body, isAiGenerated = isAiGenerated)
    }

    fun closeMailCompose() {
        onUserInteraction()
        _pendingMailDraft.value = null
    }

    fun sendMailDraft(to: String, subject: String, body: String) {
        onUserInteraction()
        val cleanTo = to.trim()
        if (!cleanTo.contains("@")) {
            showToast("请填写有效的收件人邮箱地址")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val account = mailDao.getAccountsSnapshot().firstOrNull { it.isDefault }
                ?: mailDao.getAccountsSnapshot().firstOrNull()
            if (account == null) {
                showToast("请先绑定发信邮箱账号")
                return@launch
            }
            _isMailLoading.value = true
            val res = mailClient.sendEmail(account, cleanTo, subject.trim().ifBlank { "无主题" }, body)
            _isMailLoading.value = false
            res.onSuccess {
                _pendingMailDraft.value = null
                showToast("邮件已成功发送至 $cleanTo")
            }.onFailure { err ->
                showToast("发信失败：${err.message}")
            }
        }
    }

    fun summarizeInboxWithAi() {
        onUserInteraction()
        if (_isMailAiProcessing.value) return
        val messages = uiState.value.mail.messages.take(8)
        if (messages.isEmpty()) {
            showToast("当前收件箱为空，请先点击同步收信")
            return
        }
        val profile = uiState.value.config.activeAiProfile()
        val digestInput = messages.mapIndexed { idx, m ->
            "${idx + 1}. [${if (m.isUnread) "未读" else "已读"}] 发件人:${m.fromAddress} | 主题:${m.subject} | 摘要:${m.snippet.take(100)}"
        }.joinToString("\n")

        viewModelScope.launch {
            _isMailAiProcessing.value = true
            val res = aiMcpClient.sendQuickTextPrompt(
                profile = profile,
                systemPrompt = "你是精简高效的词典笔邮件助理。请用3~4条极简中文要点总结以下邮件，标注需要紧急回复的事项，控制在120字以内。",
                userPrompt = digestInput
            )
            _isMailAiProcessing.value = false
            res.onSuccess { summary ->
                _mailAiSummary.value = summary
            }.onFailure { err ->
                showToast("AI 总结失败：${err.message}")
            }
        }
    }

    fun draftReplyWithAi(message: MailMessageEntity) {
        onUserInteraction()
        if (_isMailAiProcessing.value) return
        val profile = uiState.value.config.activeAiProfile()
        viewModelScope.launch {
            _isMailAiProcessing.value = true
            val res = aiMcpClient.sendQuickTextPrompt(
                profile = profile,
                systemPrompt = "你是得体的邮件回复助理。根据收到的邮件起草一封简洁礼貌的中文回复正文（直接输出正文内容，不含主题行）。",
                userPrompt = "发件人：${message.fromAddress}\n主题：${message.subject}\n正文：\n${message.bodyText.take(600)}"
            )
            _isMailAiProcessing.value = false
            res.onSuccess { draftBody ->
                val replySubject = if (message.subject.startsWith("Re:", true)) message.subject else "Re: ${message.subject}"
                val replyEmail = extractEmailAddress(message.fromAddress)
                _pendingMailDraft.value = MailDraftState(
                    to = replyEmail,
                    subject = replySubject,
                    body = draftBody,
                    isAiGenerated = true
                )
            }.onFailure { err ->
                showToast("AI 起草失败：${err.message}")
            }
        }
    }

    private fun extractEmailAddress(raw: String): String {
        val match = Regex("<([^>]+@[^>]+)>").find(raw)
        return match?.groupValues?.getOrNull(1)?.trim() ?: raw.trim()
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
            prefs.setDynamicWallpaperConfig(mode = PenConfig.DYN_WALLPAPER_STATIC)
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
                    prefs.setDynamicWallpaperConfig(mode = PenConfig.DYN_WALLPAPER_STATIC)
                    BitmapDecodeCache.clear()
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
                    prefs.setDynamicWallpaperConfig(mode = PenConfig.DYN_WALLPAPER_STATIC)
                    BitmapDecodeCache.clear()
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

    fun importCustomPoetryFontFromUri(uri: Uri) = importFontForSlotFromUri("poetry", uri)

    fun importCustomPoetryFontFromPath(rawPath: String) = importFontForSlotFromPath("poetry", rawPath)

    fun clearCustomPoetryFont() {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setCustomPoetryFontPath("")
            val cur = prefs.currentConfig().typographyProfile.poetry
            prefs.updateFontSlot("poetry", cur.copy(familyKey = "noto_serif_sc", externalPath = ""))
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
        viewModelScope.launch(Dispatchers.IO) { calendarEventDao.deleteById(eventId) }
    }

    // --- Todo Items ---
    fun addTodo(title: String, dueDateIso: String = _clockTick.value.isoDate, priority: Int = 1) {
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
        viewModelScope.launch { _inspectedActivities.value = appRepo.listExportedActivities(packageName) }
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
        if (!ok) showToast("未找到扫学入口，请在密室重新选择 Activity")
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

    // --- Baicizhan 2.0 Vocab Trainer ---
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
            wrongCount = 0,
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
        val currentOrNext = (if (!forceNextWord && previousId != null) {
            bookWords.firstOrNull { it.word.id == previousId }
        } else null) ?: pickNextWord(bookWords, previousId)

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
            }.toList()

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

    // --- Lab AI + Harness MCP ---
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

    fun setMcpPreviewBeforeApply(enabled: Boolean) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setMcpPreviewBeforeApply(enabled)
            showToast(if (enabled) "已开启 MCP 变更差异预览护栏" else "已切换为 MCP 直接执行（仍自动存档快照）")
        }
    }

    fun togglePendingMcpChange(changeId: String) {
        onUserInteraction()
        _pendingMcpChanges.update { list ->
            list.map { if (it.id == changeId) it.copy(selected = !it.selected) else it }
        }
    }

    fun discardPendingMcpChanges() {
        onUserInteraction()
        _pendingMcpChanges.value = emptyList()
        showToast("已放弃待应用的 AI 修改")
    }

    fun applyPendingMcpChanges() {
        onUserInteraction()
        val selected = _pendingMcpChanges.value.filter { it.selected }
        if (selected.isEmpty()) {
            _pendingMcpChanges.value = emptyList()
            showToast("未勾选任何变更项")
            return
        }
        viewModelScope.launch {
            val label = selected.first().humanSummary.take(26)
            snapshotManager.captureSnapshot("AI 应用前：$label", "MCP")
            val logs = mutableListOf<String>()
            selected.forEach { item ->
                val argsObj = runCatching { JSONObject(item.argsJson) }.getOrDefault(JSONObject())
                val res = harnessExecutor.execute(item.toolName, argsObj)
                logs.add("${if (res.success) "✓" else "✗"} ${res.summary}")
            }
            _pendingMcpChanges.value = emptyList()
            _aiMessages.update {
                (it + AiChatMessage(
                    id = "apply_${System.currentTimeMillis()}",
                    role = "assistant",
                    content = "已存档快照并应用 ${selected.size} 项变更（可随时一键撤销）：\n${logs.joinToString("\n")}",
                    toolActionsSummary = logs.joinToString(" · ")
                )).takeLast(24)
            }
            showToast("已应用 ${selected.size} 项 AI 变更（支持一键回滚）")
        }
    }

    fun rollbackLatestSnapshot() {
        onUserInteraction()
        viewModelScope.launch {
            snapshotManager.restoreLatestSnapshot()
                .onSuccess { showToast(it) }
                .onFailure { showToast(it.message ?: "暂无可撤销的桌面快照") }
        }
    }

    fun restoreSnapshot(snapshotId: Long) {
        onUserInteraction()
        viewModelScope.launch {
            snapshotManager.restoreSnapshot(snapshotId)
                .onSuccess { showToast(it) }
                .onFailure { showToast(it.message ?: "回滚失败") }
        }
    }

    fun sendAiPrompt(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || _isAiLoading.value) return
        onUserInteraction()

        val cfg = uiState.value.config
        val profile = cfg.activeAiProfile()
        val userMsg = AiChatMessage("u_${System.currentTimeMillis()}", "user", clean)
        val historyBefore = _aiMessages.value
        _aiMessages.value = (historyBefore + userMsg).takeLast(24)

        viewModelScope.launch {
            _isAiLoading.value = true
            var autoSnapTaken = false
            val stagedThisTurn = mutableListOf<PendingMcpChange>()

            val result = aiMcpClient.sendChatWithMcp(
                profile = profile,
                history = historyBefore,
                userPrompt = clean,
                externalMcpUrl = cfg.externalMcpUrl,
                desktopToolExecutor = { toolName, args ->
                    if (harnessExecutor.isReadOnlyTool(toolName)) {
                        harnessExecutor.execute(toolName, args)
                    } else if (cfg.mcpPreviewBeforeApply) {
                        val summary = harnessExecutor.describeChange(toolName, args)
                        val pending = PendingMcpChange(
                            id = "chg_${System.currentTimeMillis()}_${stagedThisTurn.size}",
                            toolName = toolName,
                            argsJson = args.toString(),
                            humanSummary = summary,
                            selected = true
                        )
                        stagedThisTurn.add(pending)
                        PenMcpToolResult(toolName, "已加入待确认预览队列：$summary（等待用户在确认卡片点击「应用并存档」）", true)
                    } else {
                        if (!autoSnapTaken) {
                            snapshotManager.captureSnapshot("AI 修改前：${clean.take(20)}", "MCP")
                            autoSnapTaken = true
                        }
                        harnessExecutor.execute(toolName, args)
                    }
                }
            )
            _isAiLoading.value = false
            if (stagedThisTurn.isNotEmpty()) {
                _pendingMcpChanges.update { it + stagedThisTurn }
            }
            result.onSuccess { reply ->
                _aiMessages.update { (it + reply).takeLast(24) }
            }.onFailure { err ->
                _aiMessages.update {
                    (it + AiChatMessage("err_${System.currentTimeMillis()}", "assistant", "请求失败：${err.message}")).takeLast(24)
                }
            }
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
