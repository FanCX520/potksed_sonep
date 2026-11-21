package com.example

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.system.SafeMediaImporter
import com.example.ui.DesktopOverlay
import com.example.ui.PenOsUiState
import com.example.ui.PenOsViewModel
import com.example.ui.PenScreenMode
import com.example.ui.desktop.DesktopScreen
import com.example.ui.desktop.PomodoroFocusScreen
import com.example.ui.lab.PenLabScreen
import com.example.ui.standby.StandByScreen
import com.example.ui.theme.PenMotion
import com.example.ui.theme.PenOsTheme
import com.example.ui.theme.PenTheme
import com.example.ui.vault.HiddenVaultScreen
import com.example.ui.vocab.VocabStudyScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: PenOsViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        viewModel.nativeWidgetHost.startListeningSafe()
    }

    override fun onStop() {
        viewModel.nativeWidgetHost.stopListeningSafe()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureImmersiveStripBars()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            var pendingWallpaperIsLight by remember { mutableStateOf(false) }
            var pendingIconPkg by remember { mutableStateOf("") }
            var pendingWidgetId by remember { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }

            PenOsTheme(
                isAppleLight = state.config.isAppleLightDesktop(),
                externalPoetryFontPath = state.config.customPoetryFontPath
            ) {
                LaunchedEffect(state.mode, state.config.standByBrightness, state.deviceControl.brightnessFraction) {
                    applyWindowBrightnessAndWake(
                        mode = state.mode,
                        standByBrightness = state.config.standByBrightness,
                        controlBrightness = state.deviceControl.brightnessFraction
                    )
                }

                LaunchedEffect(state.secretTrigger.hapticPulseNonce) {
                    if (state.secretTrigger.hapticPulseNonce > 0) {
                        triggerSubtleHaptic()
                    }
                }

                // 1. Zero-crash Wallpaper Image Picker (Gallery ACTION_PICK / MT Manager ACTION_GET_CONTENT)
                val safeWallpaperLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { res ->
                    val uri: Uri? = res.data?.data
                    if (res.resultCode == Activity.RESULT_OK && uri != null) {
                        viewModel.importCustomWallpaperFromUri(pendingWallpaperIsLight, uri)
                    }
                }

                // 2. Zero-crash Custom App Icon Image Picker
                val safeIconLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { res ->
                    val uri: Uri? = res.data?.data
                    if (res.resultCode == Activity.RESULT_OK && uri != null && pendingIconPkg.isNotBlank()) {
                        viewModel.importCustomAppIconFromUri(pendingIconPkg, uri)
                    }
                }

                // 3. Zero-crash External Poetry Font Picker (MT Manager)
                val safeFontLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { res ->
                    val uri: Uri? = res.data?.data
                    if (res.resultCode == Activity.RESULT_OK && uri != null) {
                        viewModel.importCustomPoetryFontFromUri(uri)
                    }
                }

                // 4. Zero-crash TXT/CSV Wordbook Picker (MT Manager)
                val safeVocabFileLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { res ->
                    val uri: Uri? = res.data?.data
                    if (res.resultCode == Activity.RESULT_OK && uri != null) {
                        viewModel.importCustomVocabFromUri(uri)
                    }
                }

                // 5. Safe JSON Export / Import Launchers
                val exportJsonLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri: Uri? ->
                    if (uri != null) {
                        scope.launch {
                            val json = viewModel.exportVocabJsonPayload()
                            runCatching {
                                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                                viewModel.showToast("JSON 词库进度已导出")
                            }.onFailure {
                                viewModel.showToast("导出失败：${it.message}")
                            }
                        }
                    }
                }

                val importJsonLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { res ->
                    val uri: Uri? = res.data?.data
                    if (res.resultCode == Activity.RESULT_OK && uri != null) {
                        scope.launch {
                            SafeMediaImporter.readTextFromUri(this@MainActivity, uri)
                                .onSuccess { viewModel.importVocabJsonPayload(it) }
                                .onFailure { viewModel.showToast("JSON 导入失败：${it.message}") }
                        }
                    }
                }

                // 6. Android Native AppWidgetHost Pick -> Bind -> Configure Chain
                val widgetConfigureLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { res ->
                    val wid = res.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
                    if (res.resultCode == Activity.RESULT_OK && wid != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        viewModel.onNativeWidgetAdded(wid)
                    } else if (wid != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        viewModel.nativeWidgetHost.deleteWidgetId(wid)
                    }
                    pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                }

                fun finalizeOrConfigureWidget(appWidgetId: Int) {
                    val info = viewModel.nativeWidgetHost.getProviderInfo(appWidgetId)
                    val cfgIntent = info?.let { viewModel.nativeWidgetHost.buildConfigureIntent(appWidgetId, it) }
                    if (cfgIntent != null) {
                        try {
                            widgetConfigureLauncher.launch(cfgIntent)
                            return
                        } catch (_: Exception) {
                        }
                    }
                    viewModel.onNativeWidgetAdded(appWidgetId)
                    pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                }

                val widgetBindLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { res ->
                    val wid = res.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
                    if (res.resultCode == Activity.RESULT_OK && wid != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        finalizeOrConfigureWidget(wid)
                    } else if (wid != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        viewModel.nativeWidgetHost.deleteWidgetId(wid)
                        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                    }
                }

                val widgetPickLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { res ->
                    val wid = res.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
                    if (res.resultCode == Activity.RESULT_OK && wid != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        val info = viewModel.nativeWidgetHost.getProviderInfo(wid)
                        if (info != null) {
                            finalizeOrConfigureWidget(wid)
                        } else {
                            viewModel.nativeWidgetHost.deleteWidgetId(wid)
                            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                        }
                    } else if (wid != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        viewModel.nativeWidgetHost.deleteWidgetId(wid)
                        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                    }
                }

                fun launchAddNativeWidgetFlow() {
                    val newId = viewModel.nativeWidgetHost.allocateWidgetId()
                    if (newId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                        viewModel.showToast("当前系统不支持原生小组件宿主")
                        return
                    }
                    pendingWidgetId = newId
                    val pickIntent = viewModel.nativeWidgetHost.buildSystemWidgetPickIntent(newId)
                    try {
                        widgetPickLauncher.launch(pickIntent)
                    } catch (_: Exception) {
                        // Fallback for stripped pens that lack Settings AppWidgetPickActivity: bind first available provider
                        val firstProvider = viewModel.nativeWidgetHost.getInstalledProviders().firstOrNull()
                        if (firstProvider != null) {
                            if (viewModel.nativeWidgetHost.bindProviderIfAllowed(newId, firstProvider.provider)) {
                                finalizeOrConfigureWidget(newId)
                            } else {
                                try {
                                    widgetBindLauncher.launch(
                                        viewModel.nativeWidgetHost.buildBindPermissionIntent(newId, firstProvider.provider)
                                    )
                                } catch (_: Exception) {
                                    viewModel.nativeWidgetHost.deleteWidgetId(newId)
                                    viewModel.showToast("系统未内置原生小组件选择器")
                                }
                            }
                        } else {
                            viewModel.nativeWidgetHost.deleteWidgetId(newId)
                            viewModel.showToast("设备未安装可用的安卓系统小组件")
                        }
                    }
                }

                fun launchSafeImagePicker(onLaunch: (Intent) -> Unit) {
                    val intent = SafeMediaImporter.buildSafeImagePickerIntent(this@MainActivity)
                    if (intent == null) {
                        viewModel.showToast("未检测到图库或文件管理器，请直接输入 /sdcard/... 图片路径")
                        return
                    }
                    try {
                        onLaunch(intent)
                    } catch (_: Exception) {
                        viewModel.showToast("系统选择器异常，请直接粘贴本地图片路径导入")
                    }
                }

                fun launchSafeFilePicker(mime: String, title: String, onLaunch: (Intent) -> Unit) {
                    val intent = SafeMediaImporter.buildSafeFilePickerIntent(this@MainActivity, mime, title)
                    if (intent == null) {
                        viewModel.showToast("未检测到文件管理器，请用 MT 管理器复制路径后粘贴导入")
                        return
                    }
                    try {
                        onLaunch(intent)
                    } catch (_: Exception) {
                        viewModel.showToast("打开文件选择器失败，请改用本地路径导入")
                    }
                }

                BackHandler(enabled = true) {
                    when (state.mode) {
                        PenScreenMode.DESKTOP -> {
                            if (state.activeOverlay != DesktopOverlay.NONE) viewModel.closeOverlay()
                        }
                        PenScreenMode.STANDBY -> viewModel.wakeFromStandBy()
                        PenScreenMode.POMODORO,
                        PenScreenMode.VOCAB,
                        PenScreenMode.LAB,
                        PenScreenMode.VAULT -> viewModel.switchMode(PenScreenMode.DESKTOP)
                    }
                }

                PenDisplayAdaptiveShell(
                    state = state,
                    onTogglePreviewFrame = { viewModel.setPenFramePreviewEnabled(it) }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = state.mode,
                            transitionSpec = {
                                when {
                                    targetState == PenScreenMode.POMODORO -> PenMotion.verticalScreenTransition(slideUpEnter = true)
                                    initialState == PenScreenMode.POMODORO -> PenMotion.verticalScreenTransition(slideUpEnter = false)
                                    else -> PenMotion.overlayEnter() togetherWith PenMotion.overlayExit()
                                }
                            },
                            label = "penos_mode_spring_transition"
                        ) { targetMode ->
                            when (targetMode) {
                                PenScreenMode.DESKTOP -> DesktopScreen(
                                    state = state,
                                    nativeWidgetHost = viewModel.nativeWidgetHost,
                                    onClockTap = viewModel::onClockTap,
                                    onClockLongPress = viewModel::onClockLongPress,
                                    onToggleQuickTheme = viewModel::toggleQuickDesignStyle,
                                    onLaunchScanApp = viewModel::launchScanApp,
                                    onLaunchApp = viewModel::launchApp,
                                    onTogglePinApp = viewModel::togglePinnedApp,
                                    onRefreshPoem = { viewModel.refreshPoem(force = true) },
                                    onOpenOverlay = viewModel::openOverlay,
                                    onCloseOverlay = viewModel::closeOverlay,
                                    onRefreshWeather = { viewModel.refreshWeather(force = true) },
                                    onSearchWeatherCities = viewModel::searchWeatherCities,
                                    onSelectWeatherCity = viewModel::selectWeatherCity,
                                    onRemoveSavedCity = viewModel::removeSavedWeatherCity,
                                    onSaveWeatherKeys = viewModel::saveWeatherApiKeys,
                                    onAddCalendarEvent = viewModel::addCalendarEvent,
                                    onDeleteCalendarEvent = viewModel::deleteCalendarEvent,
                                    onAddTodo = { viewModel.addTodo(it) },
                                    onAddTodoForDate = { title, iso -> viewModel.addTodo(title, iso) },
                                    onToggleTodoDone = viewModel::toggleTodoDone,
                                    onDeleteTodo = viewModel::deleteTodo,
                                    onClearCompletedTodos = viewModel::clearCompletedTodos,
                                    onOpenVocab = { viewModel.switchMode(PenScreenMode.VOCAB) },
                                    onEnterStandBy = { viewModel.switchMode(PenScreenMode.STANDBY) },
                                    onOpenPomodoro = { viewModel.openPomodoroFocus() },
                                    onOpenLab = { viewModel.switchMode(PenScreenMode.LAB) },
                                    onBrightnessChange = viewModel::setScreenBrightness,
                                    onVolumeChange = viewModel::setMediaVolume,
                                    onNightDimChange = viewModel::setNightDimAlpha,
                                    onToggleWhiteNoise = viewModel::toggleWhiteNoise,
                                    onSetDeckMode = viewModel::setDesktopDeckMode,
                                    onRequestAddNativeWidget = { launchAddNativeWidgetFlow() },
                                    onAdjustNativeWidgetWidth = viewModel::adjustNativeWidgetWidth,
                                    onRemoveNativeWidget = viewModel::removeNativeWidget
                                )

                                PenScreenMode.STANDBY -> StandByScreen(
                                    state = state,
                                    onWakeToDesktop = viewModel::wakeFromStandBy,
                                    onSwipeUpToPomodoro = { viewModel.openPomodoroFocus() },
                                    onCycleRightWidget = viewModel::cycleStandByRightWidget,
                                    onTogglePomodoro = viewModel::togglePomodoroRunning,
                                    onClockTap = viewModel::onClockTap,
                                    onClockLongPress = viewModel::onClockLongPress
                                )

                                PenScreenMode.POMODORO -> PomodoroFocusScreen(
                                    state = state,
                                    onToggleRunning = viewModel::togglePomodoroRunning,
                                    onReset = viewModel::resetPomodoro,
                                    onCycleDuration = viewModel::cyclePomodoroDuration,
                                    onToggleBreak = viewModel::togglePomodoroBreakMode,
                                    onToggleWhiteNoise = viewModel::toggleWhiteNoise,
                                    onExit = { viewModel.switchMode(PenScreenMode.DESKTOP) }
                                )

                                PenScreenMode.VOCAB -> VocabStudyScreen(
                                    state = state,
                                    onBack = { viewModel.switchMode(PenScreenMode.DESKTOP) },
                                    onSelectOption = viewModel::selectVocabOption,
                                    onKillWord = viewModel::killCurrentWord,
                                    onToggleHint = viewModel::toggleVocabHint,
                                    onNextWord = viewModel::nextVocabWord,
                                    onSwitchBook = viewModel::switchVocabBook,
                                    onPickVocabFile = {
                                        launchSafeFilePicker("text/*", "选择 TXT/CSV 词书（MT管理器）") {
                                            safeVocabFileLauncher.launch(it)
                                        }
                                    },
                                    onImportVocabPath = viewModel::importCustomVocabFromPath,
                                    onSyncWebDav = viewModel::syncVocabViaWebDav
                                )

                                PenScreenMode.LAB -> PenLabScreen(
                                    state = state,
                                    onBack = { viewModel.switchMode(PenScreenMode.DESKTOP) },
                                    onSendAiPrompt = viewModel::sendAiPrompt,
                                    onSaveAiConfig = viewModel::saveAiConfig,
                                    onSelectAiPreset = viewModel::selectAiPreset,
                                    onUpsertAiPreset = viewModel::upsertAiPreset,
                                    onDeleteAiPreset = viewModel::deleteAiPreset,
                                    onClearAiChat = viewModel::clearAiChat,
                                    onToggleWhiteNoise = viewModel::toggleWhiteNoise,
                                    onSetWhiteNoiseVolume = viewModel::setWhiteNoiseVolume,
                                    onBindScanFromRadar = viewModel::bindScanTarget,
                                    onBlockHomeFromRadar = viewModel::addBlockedHomeActivity,
                                    onSetNightDimAlpha = viewModel::setNightDimAlpha,
                                    onSetLowMemMode = viewModel::setLowMemMode,
                                    onPickPoetryFontFile = {
                                        launchSafeFilePicker("*/*", "选择 TTF/OTF 字体（MT管理器）") {
                                            safeFontLauncher.launch(it)
                                        }
                                    },
                                    onImportPoetryFontPath = viewModel::importCustomPoetryFontFromPath,
                                    onClearPoetryFont = viewModel::clearCustomPoetryFont
                                )

                                PenScreenMode.VAULT -> HiddenVaultScreen(
                                    state = state,
                                    onCloseVault = { viewModel.switchMode(PenScreenMode.DESKTOP) },
                                    onOpenLab = { viewModel.switchMode(PenScreenMode.LAB) },
                                    onVerifyPin = viewModel::verifyVaultPin,
                                    onLaunchApp = viewModel::launchApp,
                                    onToggleHiddenApp = viewModel::toggleHiddenApp,
                                    onTogglePinnedApp = viewModel::togglePinnedApp,
                                    onPickCustomAppIcon = { pkg ->
                                        pendingIconPkg = pkg
                                        launchSafeImagePicker { safeIconLauncher.launch(it) }
                                    },
                                    onImportAppIconFromPath = viewModel::importCustomAppIconFromPath,
                                    onSetAppIconColor = viewModel::setCustomAppIconColor,
                                    onClearAppIcon = viewModel::clearCustomAppIcon,
                                    onSelectDesignStyle = viewModel::setDesignStyle,
                                    onSelectStandByStyleMode = viewModel::setStandByStyleMode,
                                    onToggleWidget = viewModel::toggleWidget,
                                    onMoveWidget = viewModel::moveWidget,
                                    onSetDeckMode = viewModel::setDesktopDeckMode,
                                    onRequestAddNativeWidget = { launchAddNativeWidgetFlow() },
                                    onAdjustNativeWidgetWidth = viewModel::adjustNativeWidgetWidth,
                                    onRemoveNativeWidget = viewModel::removeNativeWidget,
                                    onSelectWallpaperPreset = viewModel::selectWallpaperPreset,
                                    onPickCustomWallpaper = { isLight ->
                                        pendingWallpaperIsLight = isLight
                                        launchSafeImagePicker { safeWallpaperLauncher.launch(it) }
                                    },
                                    onImportWallpaperFromPath = viewModel::importCustomWallpaperFromPath,
                                    onUpdateStandBy = viewModel::updateStandBySettings,
                                    onSetPenFramePreview = viewModel::setPenFramePreviewEnabled,
                                    onBindScanTarget = viewModel::bindScanTarget,
                                    onInspectPackageActivities = viewModel::inspectPackageActivities,
                                    onBindBlockedLauncher = viewModel::bindBlockedStockLauncher,
                                    onToggleBlockedHomeActivity = viewModel::toggleBlockedHomeActivity,
                                    onAddBlockedHomeActivity = viewModel::addBlockedHomeActivity,
                                    onToggleKeepAlive = viewModel::setKeepAliveEnabled,
                                    onRootSetHome = viewModel::executeRootSetDefaultHome,
                                    onRootDisableHomeComponent = viewModel::executeRootDisableHomeComponent,
                                    onRootFreezeStock = viewModel::executeRootFreezeStockLauncher,
                                    onRefreshDiagnostics = viewModel::refreshSystemDiagnostics,
                                    onSetVaultPin = viewModel::setVaultPin,
                                    onSaveWebDav = viewModel::saveWebDavConfig,
                                    onSyncWebDav = viewModel::syncVocabViaWebDav,
                                    onGenerateSyncCode = viewModel::generatePortableSyncCode,
                                    onImportSyncCode = viewModel::importPortableSyncCode,
                                    onExportJsonFile = {
                                        try {
                                            exportJsonLauncher.launch("penos_vocab_backup.json")
                                        } catch (_: Exception) {
                                            viewModel.showToast("无系统文档提供程序，请使用「生成同步码」备份")
                                        }
                                    },
                                    onImportJsonFile = {
                                        launchSafeFilePicker("*/*", "选择 JSON 备份文件（MT管理器）") {
                                            importJsonLauncher.launch(it)
                                        }
                                    }
                                )
                            }
                        }

                        // Floating Status Banner
                        AnimatedVisibility(
                            visible = !state.statusBannerMessage.isNullOrBlank(),
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 6.dp)
                        ) {
                            val colors = PenTheme.colors
                            Surface(
                                shape = RoundedCornerShape(980.dp),
                                color = if (colors.isAppleLight) Color(0xEE1D1D1F) else Color(0xEE141923),
                                border = if (colors.isAppleLight) null else BorderStroke(1.dp, colors.primaryAction.copy(alpha = 0.6f)),
                                modifier = Modifier.clickable { viewModel.clearStatusBanner() }
                            ) {
                                Text(
                                    text = state.statusBannerMessage.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Global Night Eye-Care Dimmer Overlay (non-touch-intercepting!)
                        if (state.config.nightDimAlpha > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0A0602).copy(alpha = state.config.nightDimAlpha.coerceIn(0f, 0.62f)))
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.onUserInteraction()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.wakeFromStandBy()
    }

    private fun configureImmersiveStripBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun applyWindowBrightnessAndWake(
        mode: PenScreenMode,
        standByBrightness: Float,
        controlBrightness: Float
    ) {
        val attrs = window.attributes
        attrs.screenBrightness = when (mode) {
            PenScreenMode.STANDBY -> standByBrightness.coerceIn(0.03f, 0.5f)
            else -> controlBrightness.coerceIn(0.06f, 1f)
        }
        window.attributes = attrs
        if (mode == PenScreenMode.STANDBY || mode == PenScreenMode.POMODORO) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @Suppress("DEPRECATION")
    private fun triggerSubtleHaptic() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(35L)
            }
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun PenDisplayAdaptiveShell(
    state: PenOsUiState,
    onTogglePreviewFrame: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val isLight = PenTheme.colors.isAppleLight
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isLight) Color(0xFFE8E8ED) else Color(0xFF040507))
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        val isRealPenScreen = maxHeight <= 250.dp
        val useHardwareFrame = !isRealPenScreen && state.config.penFramePreviewEnabled

        if (!useHardwareFrame) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()
            }
        } else {
            val targetWidth = minOf((maxWidth - 20.dp).coerceAtLeast(120.dp), 640.dp)
            val targetHeight = minOf((maxHeight - 48.dp).coerceAtLeast(120.dp), 178.dp)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier
                        .width(targetWidth)
                        .padding(bottom = 8.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PenOS 3.0 · 640×172 横屏视窗（上滑番茄 / 下滑控制中心 / 时间5连+长按密室）",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLight) Color(0xFF6E6E73) else PenTheme.colors.textMuted
                    )
                    Surface(
                        shape = RoundedCornerShape(980.dp),
                        color = if (isLight) Color.White else Color(0xFF161C27),
                        border = if (isLight) null else BorderStroke(1.dp, PenTheme.colors.glassBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(980.dp))
                            .clickable { onTogglePreviewFrame(false) }
                    ) {
                        Text(
                            text = "全屏自适应",
                            style = MaterialTheme.typography.labelSmall,
                            color = PenTheme.colors.linkAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .width(targetWidth)
                        .height(targetHeight),
                    shape = RoundedCornerShape(26.dp),
                    color = if (isLight) Color.White else Color.Black,
                    border = BorderStroke(2.dp, if (isLight) Color(0xFFDEDFE2) else Color(0xFF283142)),
                    shadowElevation = if (isLight) 4.dp else 12.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        content()
                    }
                }
            }
        }
    }
}
