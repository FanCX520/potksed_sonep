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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.prefs.PenConfig
import com.example.system.SafeMediaImporter
import com.example.system.SystemTakeoverHelper
import com.example.system.buildSystemLiveWallpaperChooserIntent
import com.example.ui.DesktopOverlay
import com.example.ui.PenOsUiState
import com.example.ui.PenOsViewModel
import com.example.ui.PenScreenMode
import com.example.ui.desktop.DesktopScreen
import com.example.ui.desktop.PomodoroFocusScreen
import com.example.ui.drawer.AppleTvDrawerScreen
import com.example.ui.lab.PenLabScreen
import com.example.ui.mail.MailScreen
import com.example.ui.setup.SetupAssistantScreen
import com.example.ui.standby.StandByScreen
import com.example.ui.theme.PenMotion
import com.example.ui.theme.PenMotionTier
import com.example.ui.theme.PenOsTheme
import com.example.ui.theme.PenTheme
import com.example.ui.vault.HiddenVaultScreen
import com.example.ui.vocab.VocabStudyScreen
import com.example.widgets.LocalClockTickFlow
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
            var pendingFontSlot by remember { mutableStateOf("poetry") }
            var pendingDynamicExt by remember { mutableStateOf("gif") }
            var pendingWidgetId by remember { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }

            val motionTier = state.config.motionTier()

            CompositionLocalProvider(LocalClockTickFlow provides viewModel.clockTickFlow) {
                PenOsTheme(
                    isAppleLight = state.config.isAppleLightDesktop(),
                    typographyProfile = state.config.typographyProfile,
                    motionTier = motionTier,
                    zeroOverdraw = state.config.zeroOverdrawMode,
                    externalPoetryFontPath = state.config.customPoetryFontPath
                ) {
                    LaunchedEffect(state.mode, state.config.standByBrightness, state.deviceControl.brightnessFraction) {
                        applyWindowBrightnessAndWake(
                            mode = state.mode,
                            standByBrightness = state.config.standByBrightness,
                            controlBrightness = state.deviceControl.brightnessFraction
                        )
                    }

                    // Mode 4 Dynamic Wallpaper: System LiveWallpaper window passthrough
                    LaunchedEffect(state.config.dynamicWallpaperMode) {
                        if (state.config.dynamicWallpaperMode == PenConfig.DYN_WALLPAPER_LIVE) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                        }
                    }

                    LaunchedEffect(state.secretTrigger.hapticPulseNonce) {
                        if (state.secretTrigger.hapticPulseNonce > 0) {
                            triggerSubtleHaptic(35L)
                        }
                    }

                    LaunchedEffect(state.gestureHapticNonce) {
                        if (state.gestureHapticNonce > 0) {
                            triggerSubtleHaptic(20L)
                        }
                    }

                    // 1. Zero-crash Wallpaper Image Picker
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

                    // 3. Zero-crash Per-Slot External Font Picker (MT Manager)
                    val safeFontLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { res ->
                        val uri: Uri? = res.data?.data
                        if (res.resultCode == Activity.RESULT_OK && uri != null) {
                            viewModel.importFontForSlotFromUri(pendingFontSlot, uri)
                        }
                    }

                    // 4. Zero-crash GIF / MP4 Dynamic Wallpaper Picker
                    val safeDynamicMediaLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { res ->
                        val uri: Uri? = res.data?.data
                        if (res.resultCode == Activity.RESULT_OK && uri != null) {
                            viewModel.importDynamicMediaFromUri(uri, pendingDynamicExt)
                        }
                    }

                    // 5. Zero-crash TXT/CSV Wordbook Picker
                    val safeVocabFileLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { res ->
                        val uri: Uri? = res.data?.data
                        if (res.resultCode == Activity.RESULT_OK && uri != null) {
                            viewModel.importCustomVocabFromUri(uri)
                        }
                    }

                    // 6. Safe JSON Export / Import Launchers
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

                    // 6b. PenOS 5.0 Full Config JSON Export / Import Launchers (redacted by default)
                    val exportFullConfigLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/json")
                    ) { uri: Uri? ->
                        if (uri != null) {
                            scope.launch {
                                val json = viewModel.exportFullConfigJson(includeSecrets = false)
                                runCatching {
                                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                                    viewModel.showToast("全量配置已脱敏导出（不含 API 密钥）")
                                }.onFailure {
                                    viewModel.showToast("全量导出失败：${it.message}")
                                }
                            }
                        }
                    }

                    val importFullConfigLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { res ->
                        val uri: Uri? = res.data?.data
                        if (res.resultCode == Activity.RESULT_OK && uri != null) {
                            scope.launch {
                                SafeMediaImporter.readTextFromUri(this@MainActivity, uri)
                                    .onSuccess { viewModel.importFullConfigJson(it) }
                                    .onFailure { viewModel.showToast("全量导入失败：${it.message}") }
                            }
                        }
                    }

                    // 7. Android Native AppWidgetHost Pick -> Bind -> Configure Chain
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
                            viewModel.showToast("未检测到图库或文件管理器，请改用局域网无线上传或输入本地路径")
                            return
                        }
                        try {
                            onLaunch(intent)
                        } catch (_: Exception) {
                            viewModel.showToast("系统选择器异常，请改用局域网无线上传")
                        }
                    }

                    fun launchSafeFilePicker(mime: String, title: String, onLaunch: (Intent) -> Unit) {
                        val intent = SafeMediaImporter.buildSafeFilePickerIntent(this@MainActivity, mime, title)
                        if (intent == null) {
                            viewModel.showToast("未检测到文件管理器，可用局域网网页后台直接无线上传！")
                            return
                        }
                        try {
                            onLaunch(intent)
                        } catch (_: Exception) {
                            viewModel.showToast("打开选择器失败，可改用局域网后台上传")
                        }
                    }

                    fun openSystemLiveWallpaperChooser() {
                        val intent = buildSystemLiveWallpaperChooserIntent()
                        if (intent.resolveActivity(packageManager) != null) {
                            runCatching { startActivity(intent) }.onFailure {
                                viewModel.showToast("设备未内置系统动态壁纸选择器，已启用透传模式")
                            }
                        } else {
                            viewModel.showToast("当前精简 ROM 未内置 LiveWallpaper 选择器，推荐选 1·代码极光 / 2·GIF / 3·MP4")
                        }
                    }

                    BackHandler(enabled = true) {
                        when (state.mode) {
                            PenScreenMode.SETUP -> viewModel.completeSetupAssistant()
                            PenScreenMode.DESKTOP -> {
                                if (state.activeOverlay != DesktopOverlay.NONE) viewModel.closeOverlay()
                            }
                            PenScreenMode.STANDBY -> viewModel.wakeFromStandBy()
                            PenScreenMode.APPLE_TV_DRAWER,
                            PenScreenMode.POMODORO,
                            PenScreenMode.VOCAB,
                            PenScreenMode.MAIL,
                            PenScreenMode.LAB,
                            PenScreenMode.VAULT -> viewModel.switchMode(PenScreenMode.DESKTOP)
                        }
                    }

                    PenDisplayAdaptiveShell(
                        state = state,
                        onTogglePreviewFrame = { viewModel.setPenFramePreviewEnabled(it) }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val renderScreen: @Composable (PenScreenMode) -> Unit = { targetMode ->
                                when (targetMode) {
                                    PenScreenMode.SETUP -> SetupAssistantScreen(
                                        state = state,
                                        onSelectDesignStyle = viewModel::setDesignStyle,
                                        onSelectMotionTier = viewModel::setMotionTier,
                                        onSelectDynamicWallpaperMode = { mode ->
                                            viewModel.setDynamicWallpaperMode(mode)
                                            if (mode == PenConfig.DYN_WALLPAPER_LIVE) openSystemLiveWallpaperChooser()
                                        },
                                        onCycleClockStyle = viewModel::cycleMultiClockStyle,
                                        onSetPoetryScale = { scale ->
                                            val cur = state.config.typographyProfile.poetry
                                            viewModel.updateFontSlot("poetry", cur.copy(scale = scale))
                                        },
                                        onOpenAccessibilitySettings = {
                                            SystemTakeoverHelper.openAccessibilitySettings(this@MainActivity)
                                        },
                                        onToggleLanServer = viewModel::toggleLanAdminServer,
                                        onOpenMailSetup = {
                                            viewModel.completeSetupAssistant()
                                            viewModel.switchMode(PenScreenMode.MAIL)
                                            viewModel.setShowMailAccountDialog(true)
                                        },
                                        onComplete = viewModel::completeSetupAssistant,
                                        onSelectDesktopEngineMode = viewModel::setDesktopEngineMode
                                    )

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
                                        onOpenAppleTvDrawer = { viewModel.switchMode(PenScreenMode.APPLE_TV_DRAWER) },
                                        onOpenPomodoro = { viewModel.openPomodoroFocus() },
                                        onOpenMail = { viewModel.switchMode(PenScreenMode.MAIL) },
                                        onCycleClockStyle = viewModel::cycleMultiClockStyle,
                                        onOpenLab = { viewModel.switchMode(PenScreenMode.LAB) },
                                        onToggleLanServer = viewModel::toggleLanAdminServer,
                                        onSetMotionTier = viewModel::setMotionTier,
                                        onToggleCustomChecklist = viewModel::toggleCustomWidgetChecklist,
                                        onCustomWidgetAction = viewModel::dispatchCustomWidgetAction,
                                        onBrightnessChange = viewModel::setScreenBrightness,
                                        onVolumeChange = viewModel::setMediaVolume,
                                        onNightDimChange = viewModel::setNightDimAlpha,
                                        onToggleWhiteNoise = viewModel::toggleWhiteNoise,
                                        onSetDeckMode = viewModel::setDesktopDeckMode,
                                        onRequestAddNativeWidget = { launchAddNativeWidgetFlow() },
                                        onAdjustNativeWidgetWidth = viewModel::adjustNativeWidgetWidth,
                                        onRemoveNativeWidget = viewModel::removeNativeWidget,
                                        onGestureHaptic = viewModel::triggerGestureHaptic,
                                        onMutateCustomDsl = viewModel::mutateCustomWidgetDsl,
                                        onRunWidgetMacro = viewModel::runWidgetMacro,
                                        onRefreshHttpBlock = viewModel::refreshCustomHttpBlock,
                                        onRollbackLatestSnapshot = viewModel::rollbackLatestSnapshot,
                                        onRestoreDegradedWallpaper = viewModel::restoreDegradedWallpaper,
                                        onToggleDesktopEngine = viewModel::toggleDesktopEngineMode
                                    )

                                    PenScreenMode.APPLE_TV_DRAWER -> AppleTvDrawerScreen(
                                        state = state,
                                        onClose = { viewModel.switchMode(PenScreenMode.DESKTOP) },
                                        onEnterPomodoro = { viewModel.openPomodoroFocus() },
                                        onCycleClockStyle = viewModel::cycleMultiClockStyle,
                                        onOpenMail = { viewModel.switchMode(PenScreenMode.MAIL) },
                                        onOpenLab = { viewModel.switchMode(PenScreenMode.LAB) },
                                        onSelectAiProfile = viewModel::selectAiApiProfile,
                                        onToggleCustomChecklist = viewModel::toggleCustomWidgetChecklist,
                                        onCustomWidgetAction = viewModel::dispatchCustomWidgetAction,
                                        onAddDrawerSlot = viewModel::addDrawerSlot,
                                        onRemoveDrawerSlot = viewModel::removeDrawerSlot,
                                        onGestureHaptic = viewModel::triggerGestureHaptic,
                                        onMutateCustomDsl = viewModel::mutateCustomWidgetDsl,
                                        onRunWidgetMacro = viewModel::runWidgetMacro,
                                        onRefreshHttpBlock = viewModel::refreshCustomHttpBlock,
                                        onInstallTemplate = viewModel::installWidgetTemplate
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

                                    PenScreenMode.MAIL -> MailScreen(
                                        state = state,
                                        onBack = { viewModel.switchMode(PenScreenMode.DESKTOP) },
                                        onSync = viewModel::syncAllMailAccounts,
                                        onSelectMessage = viewModel::selectMailMessage,
                                        onSummarizeInbox = viewModel::summarizeInboxWithAi,
                                        onDraftReplyWithAi = viewModel::draftReplyWithAi,
                                        onOpenCompose = { to, subj, body -> viewModel.openMailCompose(to, subj, body, false) },
                                        onCloseCompose = viewModel::closeMailCompose,
                                        onSendDraft = viewModel::sendMailDraft,
                                        onSetShowAccountDialog = viewModel::setShowMailAccountDialog,
                                        onSaveAccount = viewModel::saveMailAccount,
                                        onDeleteAccount = viewModel::deleteMailAccount
                                    )

                                    PenScreenMode.LAB -> PenLabScreen(
                                        state = state,
                                        onBack = { viewModel.switchMode(PenScreenMode.DESKTOP) },
                                        onSendAiPrompt = viewModel::sendAiPrompt,
                                        onSelectAiProfile = viewModel::selectAiApiProfile,
                                        onUpsertAiProfile = viewModel::upsertAiApiProfile,
                                        onDeleteAiProfile = viewModel::deleteAiApiProfile,
                                        onSaveExternalMcpUrl = { ext ->
                                            val p = state.config.activeAiProfile()
                                            viewModel.saveAiConfig(p.baseUrl, p.apiKey, p.model, ext)
                                        },
                                        onClearAiChat = viewModel::clearAiChat,
                                        onToggleLanServer = viewModel::toggleLanAdminServer,
                                        onRegenerateLanPin = viewModel::regenerateLanPin,
                                        onReopenSetupAssistant = viewModel::reopenSetupAssistant,
                                        onUpdateFontSlot = viewModel::updateFontSlot,
                                        onPickFontForSlot = { slot ->
                                            pendingFontSlot = slot
                                            launchSafeFilePicker("*/*", "选择 TTF/OTF 字体（MT管理器）") {
                                                safeFontLauncher.launch(it)
                                            }
                                        },
                                        onResetFonts = viewModel::resetTypographyProfile,
                                        onSetDynamicWallpaperMode = { mode, proc ->
                                            viewModel.setDynamicWallpaperMode(mode, proc)
                                        },
                                        onPickGifWallpaper = {
                                            pendingDynamicExt = "gif"
                                            launchSafeFilePicker("image/gif", "选择 GIF 动态壁纸（图库/MT管理器）") {
                                                safeDynamicMediaLauncher.launch(it)
                                            }
                                        },
                                        onPickMp4Wallpaper = {
                                            pendingDynamicExt = "mp4"
                                            launchSafeFilePicker("video/mp4", "选择 MP4 视频壁纸（MT管理器）") {
                                                safeDynamicMediaLauncher.launch(it)
                                            }
                                        },
                                        onOpenSystemLiveWallpaperChooser = { openSystemLiveWallpaperChooser() },
                                        onSetClockStyle = viewModel::setMultiClockConfig,
                                        onCycleClockStyle = viewModel::cycleMultiClockStyle,
                                        onSetMotionTier = viewModel::setMotionTier,
                                        onToggleWhiteNoise = viewModel::toggleWhiteNoise,
                                        onSetWhiteNoiseVolume = viewModel::setWhiteNoiseVolume,
                                        onBindScanFromRadar = viewModel::bindScanTarget,
                                        onBlockHomeFromRadar = viewModel::addBlockedHomeActivity,
                                        onSetNightDimAlpha = viewModel::setNightDimAlpha,
                                        onSetLowMemMode = viewModel::setLowMemMode,
                                        onToggleMcpPreview = viewModel::setMcpPreviewBeforeApply,
                                        onTogglePendingMcpChange = viewModel::togglePendingMcpChange,
                                        onApplyPendingMcpChanges = viewModel::applyPendingMcpChanges,
                                        onDiscardPendingMcpChanges = viewModel::discardPendingMcpChanges,
                                        onRollbackLatestSnapshot = viewModel::rollbackLatestSnapshot,
                                        onInstallWidgetTemplate = viewModel::installWidgetTemplate,
                                        onExportFullConfigJson = {
                                            try {
                                                exportFullConfigLauncher.launch("penos_full_config.json")
                                            } catch (_: Exception) {
                                                viewModel.showToast("无系统文档提供程序，请使用 WebDAV 全量同步或局域网后台导出")
                                            }
                                        },
                                        onImportFullConfigJson = {
                                            launchSafeFilePicker("*/*", "选择 PenOS 全量配置 JSON（MT管理器）") {
                                                importFullConfigLauncher.launch(it)
                                            }
                                        },
                                        onSyncFullConfigWebDav = { viewModel.syncFullConfigWithWebDav(uploadOnly = false) },
                                        onSetSwipeSensitivityDp = viewModel::setSwipeSensitivityDp,
                                        onSetGestureHapticsEnabled = viewModel::setGestureHapticsEnabled,
                                        onSetMailAutoSyncEnabled = viewModel::setMailAutoSyncEnabled,
                                        onRestoreDegradedWallpaper = viewModel::restoreDegradedWallpaper,
                                        onSetDesktopEngineMode = viewModel::setDesktopEngineMode,
                                        onSetZeroOverdrawMode = viewModel::setZeroOverdrawMode
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
                                        },
                                        onToggleStockHomeBypass60s = viewModel::toggleStockHomeBypass60s
                                    )
                                }
                            }

                            if (motionTier == PenMotionTier.NONE) {
                                renderScreen(state.mode)
                            } else {
                                AnimatedContent(
                                    targetState = state.mode,
                                    transitionSpec = {
                                        when {
                                            targetState == PenScreenMode.POMODORO || targetState == PenScreenMode.APPLE_TV_DRAWER ->
                                                PenMotion.verticalScreenTransition(slideUpEnter = true, tier = motionTier)
                                            initialState == PenScreenMode.POMODORO || initialState == PenScreenMode.APPLE_TV_DRAWER ->
                                                PenMotion.verticalScreenTransition(slideUpEnter = false, tier = motionTier)
                                            else -> PenMotion.overlayEnter(tier = motionTier) togetherWith PenMotion.overlayExit(tier = motionTier)
                                        }
                                    },
                                    label = "penos_mode_transition"
                                ) { targetMode ->
                                    renderScreen(targetMode)
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

                            // Global Night Eye-Care Dimmer Overlay
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
    private fun triggerSubtleHaptic(durationMs: Long = 35L) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(durationMs)
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
    val isLiveWallpaper = state.config.dynamicWallpaperMode == PenConfig.DYN_WALLPAPER_LIVE
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val maxWidthDp = if (containerSize.width > 0) with(density) { containerSize.width.toDp() } else 640.dp
    val maxHeightDp = if (containerSize.height > 0) with(density) { containerSize.height.toDp() } else 360.dp
    val isRealPenScreen = containerSize.height > 0 && maxHeightDp <= 250.dp
    val useHardwareFrame = !isRealPenScreen && state.config.penFramePreviewEnabled

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .background(
                when {
                    isLiveWallpaper -> Color.Transparent
                    isLight -> Color(0xFFE8E8ED)
                    else -> Color(0xFF040507)
                }
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        if (!useHardwareFrame) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()
            }
        } else {
            val targetWidth = minOf((maxWidthDp - 20.dp).coerceAtLeast(120.dp), 640.dp)
            val targetHeight = minOf((maxHeightDp - 48.dp).coerceAtLeast(120.dp), 178.dp)

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
                        text = "PenOS 5.5 · 640×172 词典笔模拟视窗（上滑抽屉 / 下滑控制中心）",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLight) Color(0xFF6E6E73) else PenTheme.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(980.dp),
                        color = if (isLight) Color.White else Color(0xFF161C27),
                        border = BorderStroke(1.dp, if (isLight) Color(0xFFD5DCE4) else PenTheme.colors.glassBorder),
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

                // Zero shadowElevation: eliminates RenderThread offscreen shadow/clip penalty around AndroidView
                Surface(
                    modifier = Modifier
                        .width(targetWidth)
                        .height(targetHeight),
                    shape = RoundedCornerShape(22.dp),
                    color = if (isLiveWallpaper) Color.Transparent else if (isLight) Color.White else Color.Black,
                    border = BorderStroke(1.5.dp, if (isLight) Color(0xFFD0D7E2) else Color(0xFF283142)),
                    shadowElevation = 0.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        content()
                    }
                }
            }
        }
    }
}
