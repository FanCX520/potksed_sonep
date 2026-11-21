package com.example

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
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DesktopOverlay
import com.example.ui.PenOsUiState
import com.example.ui.PenOsViewModel
import com.example.ui.PenScreenMode
import com.example.ui.desktop.DesktopScreen
import com.example.ui.standby.StandByScreen
import com.example.ui.theme.PenOsTheme
import com.example.ui.theme.PenTheme
import com.example.ui.vault.HiddenVaultScreen
import com.example.ui.vocab.VocabStudyScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: PenOsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureImmersiveStripBars()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            PenOsTheme(isAppleLight = state.config.isAppleLightDesktop()) {
                LaunchedEffect(state.mode, state.config.standByBrightness) {
                    applyStandByWindowSettings(
                        isStandBy = state.mode == PenScreenMode.STANDBY,
                        standByBrightness = state.config.standByBrightness
                    )
                }

                LaunchedEffect(state.secretTrigger.hapticPulseNonce) {
                    if (state.secretTrigger.hapticPulseNonce > 0) {
                        triggerSubtleHaptic()
                    }
                }

                val wallpaperPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: Exception) {
                        }
                        viewModel.setCustomWallpaperUri(uri.toString())
                    }
                }

                val exportJsonLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri: Uri? ->
                    if (uri != null) {
                        scope.launch {
                            val json = viewModel.exportVocabJsonPayload()
                            runCatching {
                                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                            }
                        }
                    }
                }

                val importJsonLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        runCatching {
                            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            if (!json.isNullOrBlank()) {
                                viewModel.importVocabJsonPayload(json)
                            }
                        }
                    }
                }

                BackHandler(enabled = true) {
                    when (state.mode) {
                        PenScreenMode.DESKTOP -> {
                            if (state.activeOverlay != DesktopOverlay.NONE) {
                                viewModel.closeOverlay()
                            }
                        }
                        PenScreenMode.STANDBY -> viewModel.wakeFromStandBy()
                        PenScreenMode.VOCAB, PenScreenMode.VAULT -> viewModel.switchMode(PenScreenMode.DESKTOP)
                    }
                }

                PenDisplayAdaptiveShell(
                    state = state,
                    onTogglePreviewFrame = { viewModel.setPenFramePreviewEnabled(it) }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = state.mode,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "penos_mode_transition"
                        ) { targetMode ->
                            when (targetMode) {
                                PenScreenMode.DESKTOP -> DesktopScreen(
                                    state = state,
                                    onClockTap = viewModel::onClockTap,
                                    onClockLongPress = viewModel::onClockLongPress,
                                    onToggleQuickTheme = viewModel::toggleQuickDesignStyle,
                                    onLaunchScanApp = viewModel::launchScanApp,
                                    onLaunchApp = viewModel::launchApp,
                                    onRefreshPoem = { viewModel.refreshPoem(force = true) },
                                    onOpenOverlay = viewModel::openOverlay,
                                    onCloseOverlay = viewModel::closeOverlay,
                                    onRefreshWeather = { viewModel.refreshWeather(force = true) },
                                    onSearchWeatherCities = viewModel::searchWeatherCities,
                                    onSelectWeatherCity = viewModel::selectWeatherCity,
                                    onAddTodo = { viewModel.addTodo(it) },
                                    onAddTodoForDate = { title, iso -> viewModel.addTodo(title, iso) },
                                    onToggleTodoDone = viewModel::toggleTodoDone,
                                    onDeleteTodo = viewModel::deleteTodo,
                                    onClearCompletedTodos = viewModel::clearCompletedTodos,
                                    onOpenVocab = { viewModel.switchMode(PenScreenMode.VOCAB) },
                                    onEnterStandBy = { viewModel.switchMode(PenScreenMode.STANDBY) }
                                )

                                PenScreenMode.STANDBY -> StandByScreen(
                                    state = state,
                                    onWakeToDesktop = viewModel::wakeFromStandBy,
                                    onClockTap = viewModel::onClockTap,
                                    onClockLongPress = viewModel::onClockLongPress
                                )

                                PenScreenMode.VOCAB -> VocabStudyScreen(
                                    state = state,
                                    onBack = { viewModel.switchMode(PenScreenMode.DESKTOP) },
                                    onSelectOption = viewModel::selectVocabOption,
                                    onKillWord = viewModel::killCurrentWord,
                                    onToggleHint = viewModel::toggleVocabHint,
                                    onNextWord = viewModel::nextVocabWord,
                                    onSwitchBook = viewModel::switchVocabBook,
                                    onSyncWebDav = viewModel::syncVocabViaWebDav
                                )

                                PenScreenMode.VAULT -> HiddenVaultScreen(
                                    state = state,
                                    onCloseVault = { viewModel.switchMode(PenScreenMode.DESKTOP) },
                                    onVerifyPin = viewModel::verifyVaultPin,
                                    onLaunchApp = viewModel::launchApp,
                                    onToggleHiddenApp = viewModel::toggleHiddenApp,
                                    onTogglePinnedApp = viewModel::togglePinnedApp,
                                    onSelectDesignStyle = viewModel::setDesignStyle,
                                    onSelectStandByStyleMode = viewModel::setStandByStyleMode,
                                    onToggleWidget = viewModel::toggleWidget,
                                    onMoveWidget = viewModel::moveWidget,
                                    onSelectWallpaperPreset = viewModel::selectWallpaperPreset,
                                    onPickCustomWallpaper = {
                                        wallpaperPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onUpdateStandBy = viewModel::updateStandBySettings,
                                    onSetPenFramePreview = viewModel::setPenFramePreviewEnabled,
                                    onBindScanApp = viewModel::bindScanApp,
                                    onBindBlockedLauncher = viewModel::bindBlockedStockLauncher,
                                    onToggleKeepAlive = viewModel::setKeepAliveEnabled,
                                    onRootSetHome = viewModel::executeRootSetDefaultHome,
                                    onRootFreezeStock = viewModel::executeRootFreezeStockLauncher,
                                    onRefreshDiagnostics = viewModel::refreshSystemDiagnostics,
                                    onSetVaultPin = viewModel::setVaultPin,
                                    onSaveWebDav = viewModel::saveWebDavConfig,
                                    onSyncWebDav = viewModel::syncVocabViaWebDav,
                                    onGenerateSyncCode = viewModel::generatePortableSyncCode,
                                    onImportSyncCode = viewModel::importPortableSyncCode,
                                    onExportJsonFile = { exportJsonLauncher.launch("penos_vocab_backup.json") },
                                    onImportJsonFile = { importJsonLauncher.launch(arrayOf("application/json", "*/*")) }
                                )
                            }
                        }

                        // Lightweight Floating Status Banner
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

    private fun applyStandByWindowSettings(isStandBy: Boolean, standByBrightness: Float) {
        val attrs = window.attributes
        attrs.screenBrightness = if (isStandBy) {
            standByBrightness.coerceIn(0.03f, 0.5f)
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = attrs
        if (isStandBy) {
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
                        text = "PenOS 2.0 · 640×172 横屏视窗（点时间5下后长按进密室）",
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
