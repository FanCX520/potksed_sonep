package com.example.ui

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
import com.example.data.local.VocabWordWithProgress
import com.example.data.prefs.PenConfig
import com.example.data.remote.AiChatMessage
import com.example.data.remote.WeatherCityOption
import com.example.data.remote.WmoWeatherMapper
import com.example.system.InstalledAppInfo
import com.example.system.LanServerStatus
import com.example.system.SystemDiagnostics
import com.example.system.WhiteNoiseState
import com.example.system.WindowRadarEntry

enum class PenScreenMode {
    SETUP,
    DESKTOP,
    APPLE_TV_DRAWER,
    STANDBY,
    POMODORO,
    VOCAB,
    MAIL,
    LAB,
    VAULT
}

enum class DesktopOverlay {
    NONE,
    POEM,
    WEATHER,
    CALENDAR,
    TODO,
    CONTROL_CENTER,
    ALL_APPS
}

data class ClockTelemetryState(
    val timeHHmm: String = "08:30",
    val secondsStr: String = "00",
    val dateLabel: String = "11月20日 周五",
    val isoDate: String = "2026-11-20",
    val batteryPercent: Int = 85,
    val isCharging: Boolean = false,
    val isWifiConnected: Boolean = false,
    val availRamMb: Int = 420,
    val totalRamMb: Int = 1024
)

data class PomodoroState(
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val isBreakMode: Boolean = false,
    val completedSessions: Int = 0
) {
    val formattedRemaining: String
        get() {
            val mins = (remainingSeconds / 60).coerceAtLeast(0)
            val secs = (remainingSeconds % 60).coerceAtLeast(0)
            return "%02d:%02d".format(mins, secs)
        }

    val progressFraction: Float
        get() = if (totalSeconds <= 0) 0f else (1f - remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
}

data class DeviceControlState(
    val brightnessFraction: Float = 0.55f,
    val mediaVolumeFraction: Float = 0.5f,
    val canWriteSystemBrightness: Boolean = true
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
    val mistakeCount: Int = 0,
    val customCount: Int = 0,
    val totalInBook: Int = 0,
    val sessionStreak: Int = 0,
    val dailyMasteredCount: Int = 0,
    val slashEffectNonce: Int = 0
)

data class SecretTriggerState(
    val tapCount: Int = 0,
    val isArmed: Boolean = false,
    val hapticPulseNonce: Int = 0
)

data class MailDraftState(
    val to: String = "",
    val subject: String = "",
    val body: String = "",
    val isAiGenerated: Boolean = false
)

data class MailUiState(
    val accounts: List<MailAccountEntity> = emptyList(),
    val messages: List<MailMessageEntity> = emptyList(),
    val selectedMessageId: String? = null,
    val isLoading: Boolean = false,
    val isAiProcessing: Boolean = false,
    val aiSummary: String = "",
    val pendingDraft: MailDraftState? = null,
    val showAccountDialog: Boolean = false
) {
    val unreadCount: Int
        get() = messages.count { it.isUnread }
}

/**
 * PenOS 5.0 Staged Harness MCP Mutation awaiting user approval in Diff Preview mode.
 */
data class PendingMcpChange(
    val id: String,
    val toolName: String,
    val argsJson: String,
    val humanSummary: String,
    val selected: Boolean = true
)

/**
 * PenOS 5.0 1GB RAM Auto-Governance state: records auto-downgraded heavy dynamic wallpaper
 * so the user can restore it with 1 tap once memory recovers.
 */
data class MemoryHealthState(
    val isDegraded: Boolean = false,
    val previousWallpaperMode: String = "",
    val previousMediaPath: String = "",
    val availRamMb: Int = 420
)

data class LabState(
    val messages: List<AiChatMessage> = listOf(
        AiChatMessage(
            id = "welcome",
            role = "assistant",
            content = "你好！我是 PenOS 5.0 Harness AI 管家（支持 17 类声明式组件、变更预览确认与一键快照回滚）。你可以直接说：「帮我做一个晚自习一键宏+打卡计数器小组件」「把古诗词字体调大到 1.25 倍」「总结我的未读邮件并起草回复」「切换到瑞士铁路表盘」。"
        )
    ),
    val isAiLoading: Boolean = false,
    val whiteNoise: WhiteNoiseState = WhiteNoiseState(),
    val activityRadar: List<WindowRadarEntry> = emptyList(),
    val inspectedPackageActivities: List<String> = emptyList()
)

data class PenOsUiState(
    val mode: PenScreenMode = PenScreenMode.DESKTOP,
    val activeOverlay: DesktopOverlay = DesktopOverlay.NONE,
    val config: PenConfig = PenConfig(),
    val clock: ClockTelemetryState = ClockTelemetryState(),
    val pomodoro: PomodoroState = PomodoroState(),
    val deviceControl: DeviceControlState = DeviceControlState(),
    val poem: CachedPoemEntity = OfflineSeedData.defaultPoems.first(),
    val isPoemLoading: Boolean = false,
    val weather: CachedWeatherEntity = WmoWeatherMapper.fallbackWeather,
    val isWeatherLoading: Boolean = false,
    val weatherSourceStatus: String = "本地缓存",
    val citySearchResults: List<WeatherCityOption> = WmoWeatherMapper.presetCities.take(16),
    val todos: List<TodoItemEntity> = emptyList(),
    val calendarEvents: List<CalendarEventEntity> = emptyList(),
    val customWidgets: List<CustomWidgetEntity> = emptyList(),
    val drawerSlots: List<DrawerSlotEntity> = emptyList(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val vocabSession: VocabSessionState = VocabSessionState(),
    val mail: MailUiState = MailUiState(),
    val lanServer: LanServerStatus = LanServerStatus(),
    val lab: LabState = LabState(),
    val secretTrigger: SecretTriggerState = SecretTriggerState(),
    val diagnostics: SystemDiagnostics = SystemDiagnostics("检测中", false, false, false),
    val isVaultUnlocked: Boolean = false,
    val isSyncing: Boolean = false,
    val statusBannerMessage: String? = null,
    val generatedSyncCode: String? = null,
    // PenOS 5.0 Safety, Snapshots, RAM Governance & Bypass Telemetry
    val recentSnapshots: List<DesktopSnapshotEntity> = emptyList(),
    val pendingMcpChanges: List<PendingMcpChange> = emptyList(),
    val memoryHealth: MemoryHealthState = MemoryHealthState(),
    val stockHomeBypassRemainingSec: Int = 0,
    val gestureHapticNonce: Int = 0
)
