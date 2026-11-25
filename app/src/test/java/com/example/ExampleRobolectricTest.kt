package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.local.OfflineSeedData
import com.example.data.prefs.PenConfig
import com.example.ui.AiSessionState
import com.example.ui.ClockSnapshot
import com.example.ui.FocusSubMode
import com.example.ui.LocalClockFlow
import com.example.ui.PenOsUiState
import com.example.ui.PomodoroState
import com.example.ui.VocabStudyState
import com.example.ui.ai.AiChatScreen
import com.example.ui.desktop.DesktopScreen
import com.example.ui.standby.StandByScreen
import com.example.ui.theme.PenOsTheme
import com.example.ui.vault.HiddenVaultScreen
import com.example.ui.vocab.VocabStudyScreen
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun desktopScreen_displaysGiantClockAndBottomDockAndTogglesTheme() {
        val clockFlow = MutableStateFlow(ClockSnapshot("09", "41", "12", "11月24日 周二"))
        var isDark by mutableStateOf(true)
        var aiOpened = false

        composeRule.setContent {
            CompositionLocalProvider(LocalClockFlow provides clockFlow) {
                PenOsTheme(isDark = isDark) {
                    DesktopScreen(
                        state = PenOsUiState(
                            config = PenConfig(isDarkTheme = isDark),
                            batteryPercent = 88,
                            wifiConnected = true
                        ),
                        onClockTap = {},
                        onClockLongPress = {},
                        onToggleDarkTheme = { isDark = !isDark },
                        onLaunchScanApp = {},
                        onOpenAi = { aiOpened = true },
                        onOpenVocab = {},
                        onOpenFocus = {},
                        onOpenAllApps = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home_clock_stage").assertIsDisplayed()
        composeRule.onNodeWithTag("dock_scan").assertIsDisplayed()
        composeRule.onNodeWithTag("dock_ai").assertIsDisplayed()
        composeRule.onNodeWithTag("dock_vocab").assertIsDisplayed()
        composeRule.onNodeWithTag("dock_focus").assertIsDisplayed()
        composeRule.onNodeWithTag("dock_apps").assertIsDisplayed()

        composeRule.onNodeWithTag("home_theme_toggle").performClick()
        assertEquals(false, isDark)

        composeRule.onNodeWithTag("dock_ai").performClick()
        assertTrue(aiOpened)
    }

    @Test
    fun aiChatScreen_showsModelSwitcherAndMcpControls() {
        val aiFlow = MutableStateFlow(AiSessionState())

        composeRule.setContent {
            PenOsTheme(isDark = true) {
                AiChatScreen(
                    state = PenOsUiState(config = PenConfig()),
                    aiFlow = aiFlow,
                    onBack = {},
                    onSendPrompt = {},
                    onClearChat = {},
                    onSelectProfile = {},
                    onUpsertProfile = {},
                    onDeleteProfile = {},
                    onSaveExternalMcp = { _, _ -> },
                    onTestMcp = {}
                )
            }
        }

        composeRule.onNodeWithTag("ai_mcp_button").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_prompt_input").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_send_button").assertIsDisplayed()
    }

    @Test
    fun vocabStudyScreen_revealsMeaningAndGradesWord() {
        var showMeaning by mutableStateOf(false)
        var lastGrade = -1
        val word = OfflineSeedData.defaultWords.first()

        composeRule.setContent {
            PenOsTheme(isDark = false) {
                VocabStudyScreen(
                    state = PenOsUiState(
                        config = PenConfig(isDarkTheme = false),
                        vocab = VocabStudyState(
                            activeBookId = "highschool",
                            wordsInBook = 40,
                            todayReviewed = 3,
                            masteredCount = 10,
                            currentWord = word,
                            showMeaning = showMeaning
                        )
                    ),
                    onBack = {},
                    onRevealMeaning = { showMeaning = true },
                    onGradeWord = { lastGrade = it },
                    onSwitchBook = {},
                    onPickVocabFile = {},
                    onImportVocabPath = {}
                )
            }
        }

        composeRule.onNodeWithText(word.word).assertIsDisplayed()
        composeRule.onNodeWithTag("vocab_reveal_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vocab_meaning_text").assertIsDisplayed()
        composeRule.onNodeWithTag("vocab_grade_kill").performClick()
        assertEquals(3, lastGrade)
    }

    @Test
    fun standByScreen_switchesBetweenStandByAndPomodoro() {
        val clockFlow = MutableStateFlow(ClockSnapshot("22", "15", "05", "11月24日 周二"))
        val pomoFlow = MutableStateFlow(PomodoroState(subMode = FocusSubMode.STANDBY))

        composeRule.setContent {
            CompositionLocalProvider(LocalClockFlow provides clockFlow) {
                PenOsTheme(isDark = true) {
                    StandByScreen(
                        state = PenOsUiState(),
                        pomodoroFlow = pomoFlow,
                        onBack = {},
                        onSelectSubMode = { pomoFlow.value = pomoFlow.value.copy(subMode = it) },
                        onTogglePomodoro = { pomoFlow.value = pomoFlow.value.copy(isRunning = !pomoFlow.value.isRunning) },
                        onResetPomodoro = {},
                        onCyclePomodoroDuration = {},
                        onCycleStandbyBrightness = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("standby_status_line").assertIsDisplayed()
        composeRule.onNodeWithTag("focus_tab_pomodoro").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pomo_timer_text").assertIsDisplayed()
        composeRule.onNodeWithTag("pomo_toggle").performClick()
        assertTrue(pomoFlow.value.isRunning)
    }

    @Test
    fun aiProfileCodec_roundTripsProfilesCleanly() {
        val profiles = listOf(
            com.example.data.prefs.AiApiProfile(
                id = "ds",
                name = "DeepSeek V3",
                baseUrl = "https://api.deepseek.com/v1",
                apiKey = "sk-test-123",
                model = "deepseek-chat",
                temperature = 0.5f,
                enableExternalMcp = true
            ),
            com.example.data.prefs.AiApiProfile(
                id = "ollama",
                name = "本地 Ollama",
                baseUrl = "http://192.168.1.10:11434/v1",
                apiKey = "ollama",
                model = "qwen2.5:7b",
                temperature = 0.7f,
                enableExternalMcp = false
            )
        )
        val encoded = com.example.data.prefs.PenPreferencesRepository.encodeAiProfiles(profiles)
        val decoded = com.example.data.prefs.PenPreferencesRepository.decodeAiProfiles(encoded)
        assertEquals(2, decoded.size)
        assertEquals("DeepSeek V3", decoded[0].name)
        assertEquals("sk-test-123", decoded[0].apiKey)
        assertEquals(false, decoded[1].enableExternalMcp)
    }

    @Test
    fun hiddenVault_unlocksWithPinAndToggles60sBypass() {
        var unlocked by mutableStateOf(false)
        var bypassToggled = false

        composeRule.setContent {
            PenOsTheme(isDark = true) {
                HiddenVaultScreen(
                    state = PenOsUiState(vaultUnlocked = unlocked),
                    onCloseVault = {},
                    onVerifyPin = { pin ->
                        val ok = pin == "0000"
                        if (ok) unlocked = true
                        ok
                    },
                    onUpdatePin = {},
                    onToggleDarkTheme = {},
                    onSetShowSeconds = {},
                    onSelectWallpaperMode = {},
                    onPickCustomWallpaper = {},
                    onImportWallpaperPath = {},
                    onToggleBypass60s = { bypassToggled = true },
                    onToggleKeepAlive = {},
                    onSetBlockedHomePackage = {},
                    onToggleBlockedActivity = {},
                    onBindScanTarget = { _, _ -> },
                    onInspectActivities = {},
                    onToggleHiddenApp = {},
                    onLaunchApp = { _, _ -> },
                    onRefreshDiagnostics = {},
                    onRootDisableHomeComponent = {},
                    onOpenAiScreen = {}
                )
            }
        }

        composeRule.onNodeWithTag("vault_pin_gate").assertIsDisplayed()
        repeat(4) { composeRule.onNodeWithTag("pin_key_0").performClick() }
        assertTrue(unlocked)

        composeRule.onNodeWithTag("vault_tab_1").performClick()
        composeRule.onNodeWithTag("vault_bypass_60s").performClick()
        assertTrue(bypassToggled)
    }
}
