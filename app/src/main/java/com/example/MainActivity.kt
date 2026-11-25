package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.system.SafeMediaImporter
import com.example.ui.FocusSubMode
import com.example.ui.LocalClockFlow
import com.example.ui.PenOsViewModel
import com.example.ui.PenScreen
import com.example.ui.ai.AiChatScreen
import com.example.ui.desktop.AllAppsScreen
import com.example.ui.desktop.DesktopScreen
import com.example.ui.standby.StandByScreen
import com.example.ui.theme.PenOsTheme
import com.example.ui.theme.PenTheme
import com.example.ui.vault.HiddenVaultScreen
import com.example.ui.vocab.VocabStudyScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: PenOsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureSystemBars(isDark = true)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state.config.isDarkTheme) {
                configureSystemBars(isDark = state.config.isDarkTheme)
            }

            LaunchedEffect(state.screen, state.config.standbyBrightness) {
                applyStandByWindowBehavior(
                    isFocusScreen = state.screen == PenScreen.FOCUS,
                    standbyBrightness = state.config.standbyBrightness
                )
            }

            LaunchedEffect(state.hapticNonce) {
                if (state.hapticNonce > 0) triggerLightHaptic()
            }

            LaunchedEffect(state.toastMessage) {
                if (state.toastMessage != null) {
                    delay(2400L)
                    viewModel.consumeToast()
                }
            }

            val wallpaperPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { res ->
                val uri: Uri? = res.data?.data
                if (res.resultCode == Activity.RESULT_OK && uri != null) {
                    viewModel.importStaticWallpaperFromUri(uri)
                }
            }

            val vocabFilePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { res ->
                val uri: Uri? = res.data?.data
                if (res.resultCode == Activity.RESULT_OK && uri != null) {
                    viewModel.importCustomVocabFromUri(uri)
                }
            }

            fun safeLaunch(intent: Intent?, fallbackMsg: String, launcher: (Intent) -> Unit) {
                if (intent == null) {
                    viewModel.showToast(fallbackMsg)
                    return
                }
                runCatching { launcher(intent) }.onFailure { viewModel.showToast(fallbackMsg) }
            }

            CompositionLocalProvider(LocalClockFlow provides viewModel.clockFlow) {
                PenOsTheme(isDark = state.config.isDarkTheme) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Direct instant screen switch (zero heavy crossfade/slide GPU overhead on MT6582!)
                        when (state.screen) {
                            PenScreen.HOME -> DesktopScreen(
                                state = state,
                                onClockTap = viewModel::onHomeClockTap,
                                onClockLongPress = viewModel::onHomeClockLongPress,
                                onToggleDarkTheme = viewModel::toggleDarkTheme,
                                onLaunchScanApp = viewModel::launchScanApp,
                                onOpenAi = { viewModel.navigateTo(PenScreen.AI) },
                                onOpenVocab = { viewModel.navigateTo(PenScreen.VOCAB) },
                                onOpenFocus = { sub -> viewModel.openFocusMode(sub) },
                                onOpenAllApps = { viewModel.navigateTo(PenScreen.APPS) }
                            )

                            PenScreen.AI -> AiChatScreen(
                                state = state,
                                aiFlow = viewModel.aiFlow,
                                onBack = { viewModel.navigateTo(PenScreen.HOME) },
                                onSendPrompt = viewModel::sendAiPrompt,
                                onClearChat = viewModel::clearAiChat,
                                onSelectProfile = viewModel::selectAiProfile,
                                onUpsertProfile = viewModel::upsertAiProfile,
                                onDeleteProfile = viewModel::deleteAiProfile,
                                onSaveExternalMcp = viewModel::saveExternalMcp,
                                onTestMcp = { viewModel.testExternalMcpConnection() }
                            )

                            PenScreen.VOCAB -> VocabStudyScreen(
                                state = state,
                                onBack = { viewModel.navigateTo(PenScreen.HOME) },
                                onRevealMeaning = viewModel::revealVocabMeaning,
                                onGradeWord = viewModel::gradeCurrentWord,
                                onSwitchBook = viewModel::switchVocabBook,
                                onPickVocabFile = {
                                    safeLaunch(
                                        SafeMediaImporter.buildSafeFilePickerIntent(this@MainActivity, "text/*"),
                                        "未检测到文件管理器，请直接输入本地路径"
                                    ) { vocabFilePicker.launch(it) }
                                },
                                onImportVocabPath = viewModel::importCustomVocabFromPath
                            )

                            PenScreen.FOCUS -> StandByScreen(
                                state = state,
                                pomodoroFlow = viewModel.pomodoroFlow,
                                onBack = { viewModel.navigateTo(PenScreen.HOME) },
                                onSelectSubMode = viewModel::setFocusSubMode,
                                onTogglePomodoro = viewModel::togglePomodoroRunning,
                                onResetPomodoro = viewModel::resetPomodoro,
                                onCyclePomodoroDuration = viewModel::cyclePomodoroDuration,
                                onCycleStandbyBrightness = {
                                    val steps = listOf(0.12f, 0.28f, 0.55f, 0.85f)
                                    val cur = state.config.standbyBrightness
                                    val next = steps.firstOrNull { it > cur + 0.03f } ?: steps.first()
                                    viewModel.setStandbyBrightness(next)
                                }
                            )

                            PenScreen.APPS -> AllAppsScreen(
                                state = state,
                                onBack = { viewModel.navigateTo(PenScreen.HOME) },
                                onRefreshApps = viewModel::loadInstalledApps,
                                onLaunchApp = viewModel::launchApp,
                                onBindScanApp = viewModel::bindScanTarget,
                                onHideApp = viewModel::toggleHiddenApp
                            )

                            PenScreen.VAULT -> HiddenVaultScreen(
                                state = state,
                                onCloseVault = { viewModel.navigateTo(PenScreen.HOME) },
                                onVerifyPin = viewModel::verifyVaultPin,
                                onUpdatePin = viewModel::updateVaultPin,
                                onToggleDarkTheme = viewModel::toggleDarkTheme,
                                onSetShowSeconds = viewModel::setShowSecondsOnHome,
                                onSelectWallpaperMode = viewModel::setStaticWallpaperMode,
                                onPickCustomWallpaper = {
                                    safeLaunch(
                                        SafeMediaImporter.buildSafeImagePickerIntent(this@MainActivity),
                                        "未检测到图库，请改用本地路径导入"
                                    ) { wallpaperPicker.launch(it) }
                                },
                                onImportWallpaperPath = viewModel::importStaticWallpaperFromPath,
                                onToggleBypass60s = viewModel::toggleStockHomeBypass60s,
                                onToggleKeepAlive = viewModel::setKeepAliveEnabled,
                                onSetBlockedHomePackage = viewModel::setBlockedHomePackage,
                                onToggleBlockedActivity = viewModel::toggleBlockedHomeActivity,
                                onBindScanTarget = viewModel::bindScanTarget,
                                onInspectActivities = viewModel::inspectPackageActivities,
                                onToggleHiddenApp = viewModel::toggleHiddenApp,
                                onLaunchApp = viewModel::launchApp,
                                onRefreshDiagnostics = viewModel::refreshSystemDiagnostics,
                                onRootDisableHomeComponent = viewModel::executeRootDisableHomeComponent,
                                onOpenAiScreen = { viewModel.navigateTo(PenScreen.AI) }
                            )
                        }

                        // Minimalist monochrome toast pill
                        val toast = state.toastMessage
                        if (toast != null) {
                            val palette = PenTheme.palette
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .safeDrawingPadding()
                                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(palette.invertedBg)
                                    .border(1.dp, palette.hairline, RoundedCornerShape(99.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = toast,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.invertedInk
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun configureSystemBars(isDark: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
    }

    private fun applyStandByWindowBehavior(isFocusScreen: Boolean, standbyBrightness: Float) {
        if (isFocusScreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val attrs = window.attributes
        val target = if (isFocusScreen) standbyBrightness.coerceIn(0.05f, 1f) else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (attrs.screenBrightness != target) {
            attrs.screenBrightness = target
            window.attributes = attrs
        }
    }

    @Suppress("DEPRECATION")
    private fun triggerLightHaptic() {
        runCatching {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(18L)
            }
        }
    }
}
