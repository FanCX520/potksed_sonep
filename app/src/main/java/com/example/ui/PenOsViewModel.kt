package com.example.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PenOsApplication
import com.example.data.local.CachedPoemEntity
import com.example.data.local.OfflineSeedData
import com.example.data.local.VocabProgressEntity
import com.example.data.local.VocabWordEntity
import com.example.data.local.VocabWordWithProgress
import com.example.data.prefs.PenConfig
import com.example.system.AntiLockCoordinator
import com.example.system.InstalledAppInfo
import com.example.system.PenKeepAliveService
import com.example.system.SystemDiagnostics
import com.example.system.SystemTakeoverHelper
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PenScreenMode {
    DESKTOP,
    STANDBY,
    VOCAB,
    VAULT
}

data class ClockTelemetryState(
    val timeHHmm: String = "08:30",
    val secondsStr: String = "00",
    val dateLabel: String = "11月20日 周五",
    val batteryPercent: Int = 85,
    val isCharging: Boolean = false,
    val isWifiConnected: Boolean = false,
    val availRamMb: Int = 420,
    val totalRamMb: Int = 1024
)

data class VocabQuizOption(
    val wordId: String,
    val meaningZh: String,
    val mnemonicTag: String,
    val accentHex: Long,
    val isCorrect: Boolean
)

data class VocabSessionState(
    val activeBook: String = "highschool",
    val currentWord: VocabWordWithProgress? = null,
    val options: List<VocabQuizOption> = emptyList(),
    val selectedOptionId: String? = null,
    val answeredCorrectly: Boolean? = null,
    val showExampleHint: Boolean = false,
    val masteredCount: Int = 0,
    val learningCount: Int = 0,
    val totalInBook: Int = 0,
    val sessionStreak: Int = 0
)

data class SecretTriggerState(
    val tapCount: Int = 0,
    val isArmed: Boolean = false,
    val hapticPulseNonce: Int = 0
)

data class PenOsUiState(
    val mode: PenScreenMode = PenScreenMode.DESKTOP,
    val config: PenConfig = PenConfig(),
    val clock: ClockTelemetryState = ClockTelemetryState(),
    val poem: CachedPoemEntity = OfflineSeedData.defaultPoems.first(),
    val isPoemLoading: Boolean = false,
    val showPoemDetailDialog: Boolean = false,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val vocabSession: VocabSessionState = VocabSessionState(),
    val secretTrigger: SecretTriggerState = SecretTriggerState(),
    val diagnostics: SystemDiagnostics = SystemDiagnostics("检测中", false, false, false),
    val isVaultUnlocked: Boolean = false,
    val isSyncing: Boolean = false,
    val statusBannerMessage: String? = null,
    val generatedSyncCode: String? = null
)

class PenOsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PenOsApplication
    private val prefs = app.preferences
    private val vocabDao = app.database.vocabDao()
    private val poetryRepo = app.poetryRepository
    private val syncManager = app.vocabSyncManager
    private val appRepo = app.installedAppRepository

    private val _screenMode = MutableStateFlow(PenScreenMode.DESKTOP)
    private val _clock = MutableStateFlow(readTelemetry())
    private val _isPoemLoading = MutableStateFlow(false)
    private val _showPoemDialog = MutableStateFlow(false)
    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _vocabSession = MutableStateFlow(VocabSessionState())
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
        val config: PenConfig,
        val clock: ClockTelemetryState,
        val poem: CachedPoemEntity,
        val isPoemLoading: Boolean
    )

    private data class AuxiliaryBundle(
        val showPoemDialog: Boolean,
        val installedApps: List<InstalledAppInfo>,
        val vocabSession: VocabSessionState,
        val secretTrigger: SecretTriggerState,
        val diagnostics: SystemDiagnostics
    )

    private data class MetaBundle(
        val isVaultUnlocked: Boolean,
        val isSyncing: Boolean,
        val statusBanner: String?,
        val syncCode: String?
    )

    private val coreFlow = combine(
        _screenMode,
        prefs.configFlow,
        _clock,
        poetryRepo.latestPoemFlow,
        _isPoemLoading
    ) { mode, config, clock, poem, loading ->
        CoreBundle(mode, config, clock, poem ?: OfflineSeedData.defaultPoems.first(), loading)
    }

    private val auxFlow = combine(
        _showPoemDialog,
        _installedApps,
        _vocabSession,
        _secretTrigger,
        _diagnostics
    ) { showPoem, apps, vocab, secret, diag ->
        AuxiliaryBundle(showPoem, apps, vocab, secret, diag)
    }

    private val metaFlow = combine(
        _isVaultUnlocked,
        _isSyncing,
        _statusBanner,
        _generatedSyncCode
    ) { unlocked, syncing, banner, code ->
        MetaBundle(unlocked, syncing, banner, code)
    }

    val uiState: StateFlow<PenOsUiState> = combine(coreFlow, auxFlow, metaFlow) { core, aux, meta ->
        PenOsUiState(
            mode = core.mode,
            config = core.config,
            clock = core.clock,
            poem = core.poem,
            isPoemLoading = core.isPoemLoading,
            showPoemDetailDialog = aux.showPoemDialog,
            installedApps = aux.installedApps,
            vocabSession = aux.vocabSession,
            secretTrigger = aux.secretTrigger,
            diagnostics = aux.diagnostics,
            isVaultUnlocked = meta.isVaultUnlocked,
            isSyncing = meta.isSyncing,
            statusBannerMessage = meta.statusBanner,
            generatedSyncCode = meta.syncCode
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
        startLowPowerClockAndIdleLoop()
        refreshInstalledApps()
        refreshSystemDiagnostics()
        refreshPoem(force = false)
    }

    private fun initializeOfflineData() {
        viewModelScope.launch(Dispatchers.IO) {
            if (vocabDao.getWordCount() == 0) {
                vocabDao.insertWords(OfflineSeedData.defaultWords)
            }
            poetryRepo.ensureSeeded()
        }
    }

    private fun observePreferencesForSystemGuards() {
        viewModelScope.launch {
            prefs.configFlow.collect { cfg ->
                AntiLockCoordinator.updateBlockedLauncher(cfg.blockedStockLauncherPackage)
                if (cfg.keepAliveEnabled) {
                    PenKeepAliveService.start(app)
                } else {
                    PenKeepAliveService.stop(app)
                }
                if (_vocabSession.value.activeBook != cfg.activeVocabBook) {
                    _vocabSession.update { it.copy(activeBook = cfg.activeVocabBook) }
                    rebuildVocabQuestion(forceNextWord = true)
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

    private fun startLowPowerClockAndIdleLoop() {
        viewModelScope.launch {
            var tickCount = 0
            while (isActive) {
                val nowElapsed = SystemClock.elapsedRealtime()
                // Update clock every 1s in Desktop/StandBy, refresh battery/RAM every 10s for low power
                _clock.value = readTelemetry(includeHeavyStats = (tickCount % 10 == 0))
                tickCount++

                // Reset secret tap armed state if idle > 4.5 seconds
                val secret = _secretTrigger.value
                if ((secret.tapCount > 0 || secret.isArmed) && nowElapsed - lastSecretTapElapsedMs > 4500L) {
                    _secretTrigger.value = SecretTriggerState()
                }

                // Auto-enter Apple StandBy after configured idle timeout when on Desktop
                val cfg = uiState.value.config
                if (_screenMode.value == PenScreenMode.DESKTOP && cfg.standByTimeoutSec > 0) {
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
        if (mode != PenScreenMode.VAULT) {
            _isVaultUnlocked.value = false
        }
        _screenMode.value = mode
        if (mode == PenScreenMode.VAULT) {
            refreshInstalledApps()
            refreshSystemDiagnostics()
        }
    }

    fun wakeFromStandBy() {
        onUserInteraction()
        _screenMode.value = PenScreenMode.DESKTOP
    }

    /**
     * Secret Vault Gesture: Tap the clock 5 times consecutively, then long-press to open!
     */
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
            _screenMode.value = PenScreenMode.VAULT
            refreshInstalledApps()
            refreshSystemDiagnostics()
        }
    }

    fun verifyVaultPin(inputPin: String): Boolean {
        val expected = uiState.value.config.vaultPin
        val ok = expected.isBlank() || inputPin == expected
        if (ok) {
            _isVaultUnlocked.value = true
        } else {
            showToast("密码不正确")
        }
        return ok
    }

    // --- Poetry (Jinrishici API) ---
    fun refreshPoem(force: Boolean) {
        if (_isPoemLoading.value) return
        viewModelScope.launch {
            _isPoemLoading.value = true
            poetryRepo.refreshPoem(force = force)
            _isPoemLoading.value = false
        }
    }

    fun setPoemDetailVisible(visible: Boolean) {
        onUserInteraction()
        _showPoemDialog.value = visible
    }

    // --- Installed Apps & Quick Scan ("学王扫学") ---
    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = appRepo.loadInstalledApps()
            _installedApps.value = apps

            // Auto-detect likely 学王扫学 package on first run if unset
            val cfg = prefs.currentConfig()
            if (cfg.scanAppPackage.isBlank()) {
                val detectedScan = apps.firstOrNull { it.isLikelyScanApp }
                if (detectedScan != null) {
                    prefs.setScanAppPackage(detectedScan.packageName)
                }
            }
            // Auto-detect stock home launcher for anti-lock suggestion if unset
            if (cfg.blockedStockLauncherPackage.isBlank()) {
                val detectedHome = apps.firstOrNull { it.isHomeLauncher && !it.isLikelyScanApp }
                    ?: apps.firstOrNull { it.isHomeLauncher }
                if (detectedHome != null) {
                    prefs.setBlockedStockLauncherPackage(detectedHome.packageName)
                }
            }
            // Pre-pin up to 3 useful visible apps on CarPlay dock if empty
            if (cfg.pinnedDockPackages.isEmpty() && apps.isNotEmpty()) {
                val defaults = apps
                    .filter { !cfg.hiddenPackages.contains(it.packageName) }
                    .take(3)
                defaults.forEach { prefs.togglePinnedPackage(it.packageName) }
            }
        }
    }

    fun launchScanApp() {
        onUserInteraction()
        val scanPkg = uiState.value.config.scanAppPackage
        if (scanPkg.isBlank()) {
            showToast("请点击时间5下后长按进入密室，绑定「学王扫学」应用")
            return
        }
        val launched = appRepo.launchPackage(scanPkg)
        if (!launched) {
            showToast("未找到扫学应用($scanPkg)，请在密室重新绑定")
        }
    }

    fun launchApp(packageName: String) {
        onUserInteraction()
        if (!appRepo.launchPackage(packageName)) {
            showToast("无法启动该应用")
        }
    }

    // --- Preferences & Modular Widgets ---
    fun toggleHiddenApp(packageName: String) {
        viewModelScope.launch { prefs.toggleHiddenPackage(packageName) }
    }

    fun togglePinnedApp(packageName: String) {
        viewModelScope.launch { prefs.togglePinnedPackage(packageName) }
    }

    fun bindScanApp(packageName: String) {
        viewModelScope.launch {
            prefs.setScanAppPackage(packageName)
            showToast("已绑定扫学应用: $packageName")
        }
    }

    fun bindBlockedStockLauncher(packageName: String) {
        viewModelScope.launch {
            prefs.setBlockedStockLauncherPackage(packageName)
            showToast("已设置防锁定拦截目标")
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

    fun selectWallpaperPreset(presetId: String) {
        viewModelScope.launch { prefs.setWallpaper(presetId) }
    }

    fun setCustomWallpaperUri(uriString: String) {
        viewModelScope.launch {
            prefs.setWallpaper(PenConfig.WALLPAPER_CUSTOM, uriString)
            showToast("已应用自定义壁纸")
        }
    }

    fun updateStandBySettings(
        timeoutSec: Int? = null,
        brightness: Float? = null,
        rightWidget: String? = null,
        clockColor: String? = null
    ) {
        viewModelScope.launch {
            prefs.setStandByConfig(timeoutSec, brightness, rightWidget, clockColor)
        }
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
        viewModelScope.launch {
            _diagnostics.value = SystemTakeoverHelper.inspectSystem(app)
        }
    }

    fun executeRootSetDefaultHome() {
        viewModelScope.launch {
            val cmd = "cmd package set-home-activity ${app.packageName}/.MainActivity"
            val result = SystemTakeoverHelper.runRootCommand(cmd)
            result.onSuccess {
                showToast("Root 设为默认桌面成功")
                refreshSystemDiagnostics()
            }.onFailure {
                showToast("Root 执行失败: ${it.message}")
            }
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
            val result = SystemTakeoverHelper.runRootCommand(cmd)
            result.onSuccess {
                showToast(if (freeze) "已冻结原厂桌面防锁回" else "已恢复原厂桌面")
                refreshSystemDiagnostics()
            }.onFailure {
                showToast("Root 执行失败: ${it.message}")
            }
        }
    }

    // --- Baicizhan-Style Vocabulary Trainer ---
    fun switchVocabBook(bookId: String) {
        onUserInteraction()
        viewModelScope.launch {
            prefs.setActiveVocabBook(bookId)
        }
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
        val nextStatus = when {
            isCorrect && nextFamiliarity >= 3 -> VocabProgressEntity.STATUS_MASTERED
            else -> VocabProgressEntity.STATUS_LEARNING
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
                sessionStreak = if (isCorrect) it.sessionStreak + 1 else 0
            )
        }

        viewModelScope.launch {
            vocabDao.upsertProgress(updated)
            delay(if (isCorrect) 680L else 1250L)
            rebuildVocabQuestion(forceNextWord = true)
        }
    }

    fun killCurrentWord() {
        // 百词斩经典「斩」：直接标记已完全掌握
        onUserInteraction()
        val current = _vocabSession.value.currentWord ?: return
        val now = System.currentTimeMillis()
        val mastered = VocabProgressEntity(
            wordId = current.word.id,
            status = VocabProgressEntity.STATUS_MASTERED,
            familiarity = 5,
            nextReviewAt = now + 30L * 24L * 60L * 60L * 1000L,
            wrongCount = current.progress?.wrongCount ?: 0,
            updatedAt = now
        )
        viewModelScope.launch {
            vocabDao.upsertProgress(mastered)
            showToast("已斩「${current.word.word}」")
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
        val bookWords = allWordsCache.filter { it.word.bookId == activeBook }
            .ifEmpty { allWordsCache }
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

        val options = (distractors + correctOption).shuffled()

        _vocabSession.update {
            it.copy(
                activeBook = activeBook,
                currentWord = currentOrNext,
                options = options,
                selectedOptionId = null,
                answeredCorrectly = null,
                showExampleHint = false,
                masteredCount = mastered,
                learningCount = learning,
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

    // --- Cloud Sync (WebDAV + Portable Sync Code + JSON) ---
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
            result.onSuccess { summary ->
                showToast(summary.message)
            }.onFailure { err ->
                showToast(err.message ?: "WebDAV 同步失败")
            }
        }
    }

    fun generatePortableSyncCode() {
        viewModelScope.launch {
            val code = syncManager.generateSyncCode()
            _generatedSyncCode.value = code
            showToast("离线同步码已生成")
        }
    }

    fun importPortableSyncCode(code: String) {
        viewModelScope.launch {
            val result = syncManager.importSyncCode(code)
            result.onSuccess { showToast(it.message) }
                .onFailure { showToast(it.message ?: "同步码导入失败") }
        }
    }

    suspend fun exportVocabJsonPayload(): String = syncManager.exportJson()

    fun importVocabJsonPayload(json: String) {
        viewModelScope.launch {
            val result = syncManager.importJson(json)
            result.onSuccess { showToast(it.message) }
                .onFailure { showToast(it.message ?: "JSON 备份导入失败") }
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    private fun showToast(msg: String) {
        _statusBanner.value = msg
        viewModelScope.launch {
            delay(3200L)
            if (_statusBanner.value == msg) {
                _statusBanner.value = null
            }
        }
    }

    private fun readTelemetry(includeHeavyStats: Boolean = true): ClockTelemetryState {
        val now = Date()
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val secFmt = SimpleDateFormat("ss", Locale.getDefault())
        val dateFmt = SimpleDateFormat("M月d日 E", Locale.CHINA)

        val prev = _clock.value
        if (!includeHeavyStats) {
            return prev.copy(
                timeHHmm = timeFmt.format(now),
                secondsStr = secFmt.format(now),
                dateLabel = dateFmt.format(now)
            )
        }

        // Battery status
        val batteryIntent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else prev.batteryPercent
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // Wi-Fi status (API 21+ compatible)
        val wifiConnected = try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                false
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } else {
                @Suppress("DEPRECATION")
                val info = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                info != null && info.isConnected && info.type == ConnectivityManager.TYPE_WIFI
            }
        } catch (_: Exception) {
            false
        }

        // RAM usage for 1GB pen monitoring
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val availMb = (memInfo.availMem / (1024L * 1024L)).toInt().coerceAtLeast(1)
        val totalMb = (memInfo.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(availMb)

        return ClockTelemetryState(
            timeHHmm = timeFmt.format(now),
            secondsStr = secFmt.format(now),
            dateLabel = dateFmt.format(now),
            batteryPercent = batteryPct,
            isCharging = charging,
            isWifiConnected = wifiConnected,
            availRamMb = availMb,
            totalRamMb = totalMb
        )
    }
}
