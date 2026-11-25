package com.example.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PenOsApplication
import com.example.data.local.OfflineSeedData
import com.example.data.local.VocabCatalogExpanded
import com.example.data.local.VocabProgressEntity
import com.example.data.local.VocabWordEntity
import com.example.data.prefs.AiApiProfile
import com.example.data.prefs.PenConfig
import com.example.data.remote.AiChatMessage
import com.example.system.AntiLockCoordinator
import com.example.system.PenKeepAliveService
import com.example.system.SafeMediaImporter
import com.example.system.SystemTakeoverHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class PenOsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PenOsApplication
    private val prefs = app.preferences
    private val vocabDao = app.database.vocabDao()
    private val installedAppsRepo = app.installedAppRepository
    private val aiClient = app.aiMcpClient

    // 1. Isolated 1-second clock flow (NEVER triggers main uiState recomposition!)
    private val _clockFlow = MutableStateFlow(buildClockSnapshot())
    val clockFlow: StateFlow<ClockSnapshot> = _clockFlow.asStateFlow()

    // 2. Isolated Focus / Pomodoro flow
    private val _pomodoroFlow = MutableStateFlow(PomodoroState())
    val pomodoroFlow: StateFlow<PomodoroState> = _pomodoroFlow.asStateFlow()

    // 3. Isolated AI Chat + External MCP flow
    private val _aiFlow = MutableStateFlow(AiSessionState())
    val aiFlow: StateFlow<AiSessionState> = _aiFlow.asStateFlow()

    // 4. Lean main launcher state
    private val _uiState = MutableStateFlow(PenOsUiState())
    val uiState: StateFlow<PenOsUiState> = _uiState.asStateFlow()

    private var cachedBookWords: List<VocabWordEntity> = emptyList()
    private var cachedProgressMap: Map<String, VocabProgressEntity> = emptyMap()
    private var lastSecretTapMs: Long = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else _uiState.value.batteryPercent
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            _uiState.update {
                if (it.batteryPercent == pct && it.isCharging == charging) it
                else it.copy(batteryPercent = pct, isCharging = charging)
            }
        }
    }

    init {
        registerBatteryMonitor()
        refreshConnectivityStatus()
        startClockAndTimerLoops()
        observePreferences()
        observeVocabDatabase()
        observeRadarLog()
        seedVocabIfNeeded()
        loadInstalledApps()
        refreshSystemDiagnostics()
    }

    override fun onCleared() {
        runCatching { app.unregisterReceiver(batteryReceiver) }
        super.onCleared()
    }

    private fun registerBatteryMonitor() {
        runCatching {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val sticky = app.registerReceiver(batteryReceiver, filter)
            if (sticky != null) batteryReceiver.onReceive(app, sticky)
        }
    }

    private fun refreshConnectivityStatus() {
        val connected = runCatching {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val net = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(net)
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } else {
                @Suppress("DEPRECATION")
                cm?.activeNetworkInfo?.isConnected == true
            }
        }.getOrDefault(false)
        _uiState.update { if (it.wifiConnected == connected) it else it.copy(wifiConnected = connected) }
    }

    private fun startClockAndTimerLoops() {
        viewModelScope.launch {
            var tickCounter = 0
            while (isActive) {
                _clockFlow.value = buildClockSnapshot()

                // Tick Pomodoro only when running
                val pomo = _pomodoroFlow.value
                if (pomo.isRunning) {
                    if (pomo.remainingSeconds > 1) {
                        _pomodoroFlow.value = pomo.copy(remainingSeconds = pomo.remainingSeconds - 1)
                    } else {
                        val nextBreak = !pomo.isBreak
                        val nextTotal = if (nextBreak) 5 * 60 else _uiState.value.config.pomodoroMinutes * 60
                        _pomodoroFlow.value = pomo.copy(
                            isRunning = false,
                            isBreak = nextBreak,
                            totalSeconds = nextTotal,
                            remainingSeconds = nextTotal,
                            completedSessions = if (!pomo.isBreak) pomo.completedSessions + 1 else pomo.completedSessions
                        )
                        showToast(if (nextBreak) "专注完成，休息 5 分钟" else "休息结束，准备下一轮专注")
                        triggerHaptic()
                    }
                }

                // Update 60s Jiuxuewang stock-home bypass countdown only if active
                val bypassRem = AntiLockCoordinator.remainingStockHomeBypassSec()
                if (_uiState.value.stockHomeBypassSec != bypassRem) {
                    _uiState.update { it.copy(stockHomeBypassSec = bypassRem) }
                }

                // Refresh Wi-Fi status quietly every 15 seconds
                tickCounter++
                if (tickCounter % 15 == 0) {
                    refreshConnectivityStatus()
                }

                val nowMs = System.currentTimeMillis()
                val delayMs = (1000L - (nowMs % 1000L)).coerceIn(200L, 1000L)
                delay(delayMs)
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            prefs.configFlow.collect { cfg ->
                AntiLockCoordinator.updateRules(
                    blockedPkg = cfg.blockedHomePackage,
                    blockedActivities = cfg.blockedHomeActivities,
                    scanPkg = cfg.scanAppPackage,
                    scanActivityClass = cfg.scanAppActivity
                )
                if (cfg.keepAliveEnabled) {
                    PenKeepAliveService.start(app)
                } else {
                    PenKeepAliveService.stop(app)
                }
                _uiState.update { it.copy(config = cfg) }
                val curPomo = _pomodoroFlow.value
                if (!curPomo.isRunning && !curPomo.isBreak && curPomo.totalSeconds != cfg.pomodoroMinutes * 60) {
                    val secs = cfg.pomodoroMinutes * 60
                    _pomodoroFlow.value = curPomo.copy(totalSeconds = secs, remainingSeconds = secs)
                }
            }
        }
    }

    private fun observeRadarLog() {
        viewModelScope.launch {
            AntiLockCoordinator.radarLog.collect { log ->
                _uiState.update { it.copy(radarLog = log) }
            }
        }
    }

    private fun seedVocabIfNeeded() {
        viewModelScope.launch(Dispatchers.IO) {
            if (vocabDao.countWords() < 50) {
                vocabDao.insertWords(OfflineSeedData.defaultWords + VocabCatalogExpanded.allExpandedWords)
            }
        }
    }

    private fun observeVocabDatabase() {
        val dayStartMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        viewModelScope.launch {
            val activeBookFlow = prefs.configFlow.map { it.activeVocabBook }.distinctUntilChanged()
            combine(
                activeBookFlow,
                activeBookFlow.flatMapLatest { bookId -> vocabDao.observeWordsByBook(bookId) },
                vocabDao.observeAllProgress(),
                vocabDao.observeReviewedSince(dayStartMs),
                vocabDao.observeMasteredCount()
            ) { bookId, words, progressList, todayReviewed, mastered ->
                cachedBookWords = words
                cachedProgressMap = progressList.associateBy { it.wordId }
                val currentId = _uiState.value.vocab.currentWord?.id
                val keepCurrent = words.firstOrNull { it.id == currentId }
                val nextWord = keepCurrent ?: pickNextWord(words, cachedProgressMap, excludeId = null)
                VocabStudyState(
                    activeBookId = bookId,
                    wordsInBook = words.size,
                    todayReviewed = todayReviewed,
                    masteredCount = mastered,
                    currentWord = nextWord,
                    showMeaning = if (keepCurrent != null) _uiState.value.vocab.showMeaning else false
                )
            }.collect { vocabState ->
                _uiState.update { it.copy(vocab = vocabState) }
            }
        }
    }

    private fun pickNextWord(
        words: List<VocabWordEntity>,
        progressMap: Map<String, VocabProgressEntity>,
        excludeId: String?
    ): VocabWordEntity? {
        if (words.isEmpty()) return null
        val candidates = words.filter { it.id != excludeId }.ifEmpty { words }
        val now = System.currentTimeMillis()
        // Priority: due/wrong words -> unmastered -> least recently reviewed
        return candidates.minByOrNull { word ->
            val p = progressMap[word.id]
            when {
                p == null -> 1_000_000L // unseen words first
                p.masteryLevel >= 3 -> 9_000_000_000L + p.lastReviewedAt
                p.nextReviewAt <= now -> p.nextReviewAt - p.wrongCount * 60_000L
                else -> 5_000_000_000L + p.lastReviewedAt
            }
        }
    }

    // ==================== Navigation & Theme ====================

    fun navigateTo(screen: PenScreen) {
        _uiState.update {
            it.copy(
                screen = screen,
                vaultUnlocked = if (screen == PenScreen.VAULT) it.vaultUnlocked else false
            )
        }
        if (screen == PenScreen.VAULT) {
            refreshSystemDiagnostics()
        }
    }

    fun toggleDarkTheme() {
        val next = !_uiState.value.config.isDarkTheme
        viewModelScope.launch { prefs.setDarkTheme(next) }
        triggerHaptic()
    }

    fun setShowSecondsOnHome(show: Boolean) {
        viewModelScope.launch { prefs.setShowSecondsOnHome(show) }
    }

    // ==================== Secret Vault Gesture (5 taps + Long Press) ====================

    fun onHomeClockTap() {
        val now = System.currentTimeMillis()
        val nextCount = if (now - lastSecretTapMs > 3000L) 1 else (_uiState.value.secretTapCount + 1).coerceAtMost(5)
        lastSecretTapMs = now
        _uiState.update { it.copy(secretTapCount = nextCount) }
        if (nextCount == 5) {
            triggerHaptic()
            showToast("已就绪：长按时钟进入隐藏密室")
        }
    }

    fun onHomeClockLongPress() {
        val now = System.currentTimeMillis()
        if (_uiState.value.secretTapCount >= 5 && now - lastSecretTapMs <= 6000L) {
            _uiState.update { it.copy(secretTapCount = 0, screen = PenScreen.VAULT, vaultUnlocked = false) }
            triggerHaptic()
            refreshSystemDiagnostics()
        }
    }

    fun verifyVaultPin(input: String): Boolean {
        val ok = input == _uiState.value.config.vaultPin
        if (ok) {
            _uiState.update { it.copy(vaultUnlocked = true) }
            triggerHaptic()
        } else {
            showToast("密室密码错误")
        }
        return ok
    }

    fun updateVaultPin(newPin: String) {
        val clean = newPin.trim()
        if (clean.length !in 4..8 || !clean.all { it.isDigit() }) {
            showToast("密码需为 4~8 位数字")
            return
        }
        viewModelScope.launch {
            prefs.setVaultPin(clean)
            showToast("密室密码已更新")
        }
    }

    // ==================== Static Wallpaper ====================

    fun setStaticWallpaperMode(mode: String) {
        viewModelScope.launch {
            prefs.setStaticWallpaper(mode)
            showToast(if (mode == PenConfig.WALLPAPER_SOLID) "已切换纯色零过绘制模式" else "静态壁纸已生效")
        }
    }

    fun importStaticWallpaperFromUri(uri: Uri) {
        viewModelScope.launch {
            SafeMediaImporter.importImageFromUri(app, uri, "penos_static_bg")
                .onSuccess { path ->
                    prefs.setStaticWallpaper(PenConfig.WALLPAPER_CUSTOM, path)
                    showToast("自定义静态壁纸已应用")
                }
                .onFailure { showToast("壁纸导入失败：${it.message}") }
        }
    }

    fun importStaticWallpaperFromPath(path: String) {
        viewModelScope.launch {
            SafeMediaImporter.importImageFromFilePath(app, path, "penos_static_bg")
                .onSuccess { saved ->
                    prefs.setStaticWallpaper(PenConfig.WALLPAPER_CUSTOM, saved)
                    showToast("本地静态壁纸已应用")
                }
                .onFailure { showToast("导入失败：${it.message}") }
        }
    }

    // ==================== Scan App & App Launcher ====================

    fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = installedAppsRepo.loadInstalledApps()
            _uiState.update { it.copy(installedApps = apps) }
            // Auto-bind first detected scan app if unset
            val cfg = _uiState.value.config
            if (cfg.scanAppPackage.isBlank()) {
                val likely = apps.firstOrNull { it.isLikelyScanApp }
                if (likely != null) {
                    prefs.setScanApp(likely.packageName, likely.launchActivityName)
                }
            }
        }
    }

    fun launchScanApp() {
        val cfg = _uiState.value.config
        if (cfg.scanAppPackage.isBlank()) {
            showToast("未绑定扫学入口，请在密室或全部应用中绑定")
            navigateTo(PenScreen.APPS)
            return
        }
        val ok = installedAppsRepo.launchComponent(
            packageName = cfg.scanAppPackage,
            activityClass = cfg.scanAppActivity,
            isolateScanStack = cfg.scanAppPackage == cfg.blockedHomePackage
        )
        if (!ok) showToast("无法启动绑定的扫学应用")
    }

    fun launchApp(packageName: String, activityClass: String = "") {
        val ok = installedAppsRepo.launchComponent(packageName, activityClass)
        if (!ok) showToast("启动应用失败：$packageName")
    }

    fun bindScanTarget(packageName: String, activityClass: String = "") {
        viewModelScope.launch {
            prefs.setScanApp(packageName, activityClass)
            showToast("已绑定扫学入口")
        }
    }

    fun toggleHiddenApp(packageName: String) {
        val current = _uiState.value.config.hiddenPackages
        val updated = if (current.contains(packageName)) current - packageName else current + packageName
        viewModelScope.launch { prefs.setHiddenPackages(updated) }
    }

    fun inspectPackageActivities(packageName: String) {
        viewModelScope.launch {
            val list = installedAppsRepo.listExportedActivities(packageName)
            _uiState.update { it.copy(inspectedActivities = list) }
        }
    }

    // ==================== Jiuxuewang Anti-Lock & 60s Bypass ====================

    fun refreshSystemDiagnostics() {
        viewModelScope.launch {
            val diag = SystemTakeoverHelper.inspectSystem(app)
            _uiState.update { it.copy(diagnostics = diag) }
            if (_uiState.value.config.blockedHomePackage.isBlank() && diag.detectedStockHomeComponent.contains("/")) {
                val stockPkg = diag.detectedStockHomeComponent.substringBefore("/")
                prefs.setBlockedHomePackage(stockPkg)
                prefs.setBlockedHomeActivities(setOf(diag.detectedStockHomeComponent))
            }
        }
    }

    fun setBlockedHomePackage(pkg: String) {
        viewModelScope.launch {
            prefs.setBlockedHomePackage(pkg)
            showToast("已更新拦截原厂桌面包名")
        }
    }

    fun toggleBlockedHomeActivity(component: String) {
        val clean = component.trim()
        if (clean.isBlank()) return
        val cur = _uiState.value.config.blockedHomeActivities
        val next = if (cur.contains(clean)) cur - clean else cur + clean
        viewModelScope.launch { prefs.setBlockedHomeActivities(next) }
    }

    fun toggleStockHomeBypass60s() {
        if (AntiLockCoordinator.isStockHomeBypassActive()) {
            AntiLockCoordinator.revokeStockHomeBypass()
            _uiState.update { it.copy(stockHomeBypassSec = 0) }
            showToast("已立即恢复久学网防锁回拦截")
        } else {
            AntiLockCoordinator.grantStockHomeBypass(60_000L)
            _uiState.update { it.copy(stockHomeBypassSec = 60) }
            showToast("已临时放行原厂桌面 60 秒")
        }
        triggerHaptic()
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setKeepAliveEnabled(enabled)
            showToast(if (enabled) "已开启防强杀低功耗守护" else "已关闭后台常驻守护")
        }
    }

    fun executeRootDisableHomeComponent() {
        val comp = _uiState.value.config.blockedHomeActivities.firstOrNull()
            ?: _uiState.value.diagnostics.detectedStockHomeComponent
        if (comp.isBlank()) {
            showToast("未检测到原厂 HomeActivity 组件")
            return
        }
        viewModelScope.launch {
            SystemTakeoverHelper.runRootCommand("pm disable $comp")
                .onSuccess { showToast("已通过 Root 禁用原厂桌面组件") }
                .onFailure { showToast("Root 执行失败：${it.message}") }
        }
    }

    // ==================== AI Chat & External MCP ====================

    fun selectAiProfile(profileId: String) {
        viewModelScope.launch {
            prefs.selectAiProfile(profileId)
            val name = _uiState.value.config.aiProfiles.firstOrNull { it.id == profileId }?.name.orEmpty()
            if (name.isNotBlank()) showToast("已切换模型：$name")
        }
    }

    fun upsertAiProfile(profile: AiApiProfile) {
        val current = _uiState.value.config.aiProfiles
        val updated = if (current.any { it.id == profile.id }) {
            current.map { if (it.id == profile.id) profile else it }
        } else {
            current + profile
        }
        viewModelScope.launch {
            prefs.saveAiProfiles(updated, profile.id)
            showToast("模型预设「${profile.name}」已保存")
        }
    }

    fun deleteAiProfile(profileId: String) {
        val current = _uiState.value.config.aiProfiles
        if (current.size <= 1) {
            showToast("至少需保留 1 套模型预设")
            return
        }
        val updated = current.filterNot { it.id == profileId }
        val nextActive = if (_uiState.value.config.activeAiProfileId == profileId) {
            updated.first().id
        } else {
            _uiState.value.config.activeAiProfileId
        }
        viewModelScope.launch {
            prefs.saveAiProfiles(updated, nextActive)
            showToast("已删除模型预设")
        }
    }

    fun saveExternalMcp(url: String, token: String) {
        viewModelScope.launch {
            prefs.saveExternalMcp(url, token)
            showToast("外部 MCP 配置已保存")
            if (url.isNotBlank()) testExternalMcpConnection(url, token)
        }
    }

    fun testExternalMcpConnection(
        url: String = _uiState.value.config.externalMcpUrl,
        token: String = _uiState.value.config.externalMcpToken
    ) {
        if (url.isBlank()) {
            _aiFlow.update { it.copy(discoveredMcpTools = emptyList(), mcpStatusNote = "未配置外部 MCP 地址") }
            return
        }
        viewModelScope.launch {
            _aiFlow.update { it.copy(isTestingMcp = true, mcpStatusNote = "正在拉取工具列表…") }
            aiClient.discoverExternalMcpTools(url, token)
                .onSuccess { tools ->
                    _aiFlow.update {
                        it.copy(
                            isTestingMcp = false,
                            discoveredMcpTools = tools,
                            mcpStatusNote = "已连接 · 发现 ${tools.size} 个 MCP 工具"
                        )
                    }
                }
                .onFailure { err ->
                    _aiFlow.update {
                        it.copy(
                            isTestingMcp = false,
                            discoveredMcpTools = emptyList(),
                            mcpStatusNote = "连接失败：${err.message}"
                        )
                    }
                }
        }
    }

    fun sendAiPrompt(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || _aiFlow.value.isLoading) return
        val cfg = _uiState.value.config
        val profile = cfg.activeAiProfile()

        val userMsg = AiChatMessage(
            id = "u_${System.currentTimeMillis()}",
            role = "user",
            content = clean
        )
        val historyBefore = _aiFlow.value.messages
        _aiFlow.update {
            it.copy(messages = it.messages + userMsg, isLoading = true)
        }

        viewModelScope.launch {
            aiClient.sendChat(
                profile = profile,
                history = historyBefore,
                userPrompt = clean,
                externalMcpUrl = cfg.externalMcpUrl,
                externalMcpToken = cfg.externalMcpToken
            ).onSuccess { reply ->
                _aiFlow.update { it.copy(messages = it.messages + reply, isLoading = false) }
            }.onFailure { err ->
                val errReply = AiChatMessage(
                    id = "e_${System.currentTimeMillis()}",
                    role = "assistant",
                    content = "请求失败：${err.message}"
                )
                _aiFlow.update { it.copy(messages = it.messages + errReply, isLoading = false) }
            }
        }
    }

    fun clearAiChat() {
        _aiFlow.update { it.copy(messages = emptyList()) }
    }

    // ==================== Baicizhan Offline Vocabulary ====================

    fun revealVocabMeaning() {
        _uiState.update { it.copy(vocab = it.vocab.copy(showMeaning = true)) }
    }

    fun gradeCurrentWord(grade: Int) {
        // grade: 0=忘记, 1=模糊, 2=认识, 3=斩词(已掌握)
        val word = _uiState.value.vocab.currentWord ?: return
        val existing = cachedProgressMap[word.id]
        val now = System.currentTimeMillis()
        val (newLevel, nextIntervalMs, wrongInc) = when (grade) {
            3 -> Triple(3, 30L * 24 * 3600_000L, 0)
            2 -> Triple(((existing?.masteryLevel ?: 0) + 1).coerceAtMost(3), 24L * 3600_000L, 0)
            1 -> Triple(1, 30L * 60_000L, 0)
            else -> Triple(0, 5L * 60_000L, 1)
        }
        val entity = VocabProgressEntity(
            wordId = word.id,
            masteryLevel = newLevel,
            wrongCount = (existing?.wrongCount ?: 0) + wrongInc,
            lastReviewedAt = now,
            nextReviewAt = now + nextIntervalMs
        )
        viewModelScope.launch(Dispatchers.IO) {
            vocabDao.upsertProgress(entity)
            withContext(Dispatchers.Main) {
                val updatedMap = cachedProgressMap.toMutableMap().apply { put(word.id, entity) }
                cachedProgressMap = updatedMap
                val next = pickNextWord(cachedBookWords, updatedMap, excludeId = word.id)
                _uiState.update {
                    it.copy(vocab = it.vocab.copy(currentWord = next, showMeaning = false))
                }
            }
        }
        triggerHaptic()
    }

    fun switchVocabBook(bookId: String) {
        viewModelScope.launch {
            prefs.setActiveVocabBook(bookId)
            _uiState.update { it.copy(vocab = it.vocab.copy(currentWord = null, showMeaning = false)) }
        }
    }

    fun importCustomVocabFromUri(uri: Uri) {
        viewModelScope.launch {
            SafeMediaImporter.readTextFromUri(app, uri)
                .onSuccess { importParsedVocab(it) }
                .onFailure { showToast("词书读取失败：${it.message}") }
        }
    }

    fun importCustomVocabFromPath(rawPath: String) {
        viewModelScope.launch {
            SafeMediaImporter.readTextFromPath(rawPath)
                .onSuccess { importParsedVocab(it) }
                .onFailure { showToast("词书读取失败：${it.message}") }
        }
    }

    private suspend fun importParsedVocab(rawText: String) {
        val parsed = withContext(Dispatchers.Default) {
            VocabCatalogExpanded.parseCustomWordbook(rawText, bookId = "custom")
        }
        if (parsed.isEmpty()) {
            showToast("未识别到有效单词行")
            return
        }
        withContext(Dispatchers.IO) { vocabDao.insertWords(parsed) }
        prefs.setActiveVocabBook("custom")
        showToast("已导入 ${parsed.size} 个自定义单词")
    }

    // ==================== StandBy Clock & Pomodoro Focus ====================

    fun openFocusMode(subMode: FocusSubMode) {
        _pomodoroFlow.update { it.copy(subMode = subMode) }
        navigateTo(PenScreen.FOCUS)
    }

    fun setFocusSubMode(subMode: FocusSubMode) {
        _pomodoroFlow.update { it.copy(subMode = subMode) }
    }

    fun togglePomodoroRunning() {
        _pomodoroFlow.update { it.copy(isRunning = !it.isRunning) }
        triggerHaptic()
    }

    fun resetPomodoro() {
        val mins = _uiState.value.config.pomodoroMinutes
        val secs = mins * 60
        _pomodoroFlow.update {
            it.copy(isRunning = false, isBreak = false, totalSeconds = secs, remainingSeconds = secs)
        }
    }

    fun cyclePomodoroDuration() {
        val presets = listOf(15, 25, 45, 60)
        val cur = _uiState.value.config.pomodoroMinutes
        val next = presets.firstOrNull { it > cur } ?: presets.first()
        viewModelScope.launch {
            prefs.setPomodoroMinutes(next)
            val secs = next * 60
            _pomodoroFlow.update {
                it.copy(isRunning = false, isBreak = false, totalSeconds = secs, remainingSeconds = secs)
            }
        }
    }

    fun setStandbyBrightness(fraction: Float) {
        viewModelScope.launch { prefs.setStandbyBrightness(fraction) }
    }

    // ==================== Utilities ====================

    fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    fun consumeToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun triggerHaptic() {
        _uiState.update { it.copy(hapticNonce = it.hapticNonce + 1) }
    }

    companion object {
        private val WEEKDAYS_ZH = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

        fun buildClockSnapshot(nowMs: Long = System.currentTimeMillis()): ClockSnapshot {
            val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
            val h = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
            val m = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
            val s = cal.get(Calendar.SECOND).toString().padStart(2, '0')
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val dow = WEEKDAYS_ZH[(cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)]
            return ClockSnapshot(
                hour = h,
                minute = m,
                second = s,
                dateLine = "${month}月${day}日 $dow",
                epochMillis = nowMs
            )
        }
    }
}
