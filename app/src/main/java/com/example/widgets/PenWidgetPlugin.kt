package com.example.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.PenOsUiState

/**
 * Callbacks exposed to modular widget plugins so widgets remain decoupled from ViewModel internals.
 */
data class WidgetActionBridge(
    val onLaunchScanApp: () -> Unit,
    val onLaunchApp: (String) -> Unit,
    val onRefreshPoem: () -> Unit,
    val onOpenPoemDetail: () -> Unit,
    val onOpenVocabStudy: () -> Unit,
    val onEnterStandBy: () -> Unit
)

/**
 * Modular Widget Plugin contract.
 * Adding a new desktop widget in the future only requires implementing this interface
 * and registering one entry in [WidgetRegistry.allPlugins].
 */
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
        JinrishiciWidgetPlugin,
        VocabProgressWidgetPlugin,
        PinnedAppsWidgetPlugin,
        PenStatusWidgetPlugin
    )

    private val pluginMap: Map<String, PenWidgetPlugin> = allPlugins.associateBy { it.id }

    fun resolveEnabled(enabledIds: List<String>): List<PenWidgetPlugin> {
        val resolved = enabledIds.mapNotNull { pluginMap[it] }
        return resolved.ifEmpty { listOf(QuickScanWidgetPlugin, JinrishiciWidgetPlugin) }
    }

    fun findById(id: String): PenWidgetPlugin? = pluginMap[id]
}
