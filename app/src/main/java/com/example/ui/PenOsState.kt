package com.example.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.example.data.local.VocabWordEntity
import com.example.data.prefs.PenConfig
import com.example.data.remote.AiChatMessage
import com.example.data.remote.McpToolInfo
import com.example.system.InstalledAppInfo
import com.example.system.SystemDiagnostics
import com.example.system.WindowRadarEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class PenScreen {
    HOME,
    AI,
    VOCAB,
    FOCUS,
    APPS,
    VAULT
}

enum class FocusSubMode {
    STANDBY,
    POMODORO
}

@Immutable
data class ClockSnapshot(
    val hour: String = "09",
    val minute: String = "41",
    val second: String = "00",
    val dateLine: String = "1月1日 周三",
    val epochMillis: Long = 0L
)

val LocalClockFlow = compositionLocalOf<StateFlow<ClockSnapshot>> {
    MutableStateFlow(ClockSnapshot())
}

@Immutable
data class PomodoroState(
    val subMode: FocusSubMode = FocusSubMode.STANDBY,
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val isBreak: Boolean = false,
    val completedSessions: Int = 0
)

@Immutable
data class AiSessionState(
    val messages: List<AiChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val discoveredMcpTools: List<McpToolInfo> = emptyList(),
    val isTestingMcp: Boolean = false,
    val mcpStatusNote: String = ""
)

@Immutable
data class VocabStudyState(
    val activeBookId: String = "highschool",
    val wordsInBook: Int = 0,
    val todayReviewed: Int = 0,
    val masteredCount: Int = 0,
    val currentWord: VocabWordEntity? = null,
    val showMeaning: Boolean = false
)

@Immutable
data class PenOsUiState(
    val screen: PenScreen = PenScreen.HOME,
    val config: PenConfig = PenConfig(),
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val wifiConnected: Boolean = false,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val vocab: VocabStudyState = VocabStudyState(),
    val diagnostics: SystemDiagnostics = SystemDiagnostics("检测中", false, false, false),
    val radarLog: List<WindowRadarEntry> = emptyList(),
    val inspectedActivities: List<String> = emptyList(),
    val vaultUnlocked: Boolean = false,
    val stockHomeBypassSec: Int = 0,
    val secretTapCount: Int = 0,
    val hapticNonce: Int = 0,
    val toastMessage: String? = null
)
