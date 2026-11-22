package com.example.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.data.local.TodoItemEntity
import com.example.ui.PenOsUiState

data class WidgetActionBridge(
    val onLaunchScanApp: () -> Unit,
    val onLaunchApp: (String) -> Unit,
    val onOpenAllApps: () -> Unit,
    val onRefreshPoem: () -> Unit,
    val onOpenPoemDetail: () -> Unit,
    val onOpenVocabStudy: () -> Unit,
    val onEnterStandBy: () -> Unit,
    val onOpenPomodoro: () -> Unit,
    val onOpenLab: () -> Unit,
    val onOpenControlCenter: () -> Unit,
    val onOpenWeather: () -> Unit,
    val onRefreshWeather: () -> Unit,
    val onOpenCalendar: () -> Unit,
    val onOpenTodo: () -> Unit,
    val onToggleTodoDone: (TodoItemEntity) -> Unit,
    val onOpenMail: () -> Unit = {},
    val onCycleClockStyle: () -> Unit = {}
)

interface PenWidgetPlugin {
    val id: String
    val title: String
    val subtitle: String
    val preferredWidth: Dp
    val lowPowerNote: String

    @Composable
    fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    )
}

object WidgetRegistry {
    val allPlugins: List<PenWidgetPlugin> = listOf(
        QuickScanWidgetPlugin,
        MultiStyleClockWidgetPlugin,
        WeatherWidgetPlugin,
        MailInboxWidgetPlugin,
        CalendarWidgetPlugin,
        TodoWidgetPlugin,
        JinrishiciWidgetPlugin,
        VocabProgressWidgetPlugin,
        PinnedAppsWidgetPlugin,
        PenStatusWidgetPlugin
    )

    private val pluginMap: Map<String, PenWidgetPlugin> = allPlugins.associateBy { it.id }

    fun resolveEnabled(enabledIds: List<String>): List<PenWidgetPlugin> {
        val resolved = enabledIds.mapNotNull { pluginMap[it] }
        return resolved.ifEmpty { listOf(QuickScanWidgetPlugin, MultiStyleClockWidgetPlugin, WeatherWidgetPlugin) }
    }
}
