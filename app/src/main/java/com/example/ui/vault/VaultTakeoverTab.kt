package com.example.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.system.SystemTakeoverHelper
import com.example.ui.PenOsUiState
import com.example.ui.components.MonoCard
import com.example.ui.components.MonoOutlineButton
import com.example.ui.components.MonoPrimaryButton
import com.example.ui.components.MonoSectionTitle
import com.example.ui.theme.PenTheme

@Composable
fun VaultTakeoverTab(
    state: PenOsUiState,
    onToggleBypass60s: () -> Unit,
    onToggleKeepAlive: (Boolean) -> Unit,
    onSetBlockedHomePackage: (String) -> Unit,
    onToggleBlockedActivity: (String) -> Unit,
    onBindScanFromRadar: (String, String) -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onRootDisableHomeComponent: () -> Unit
) {
    val palette = PenTheme.palette
    val context = LocalContext.current
    val diag = state.diagnostics

    var blockedPkgInput by remember(state.config.blockedHomePackage) {
        mutableStateOf(state.config.blockedHomePackage)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. 60s Temporary Stock-Home Bypass Card (User-Confirmed Essential)
        MonoCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (state.stockHomeBypassSec > 0) palette.accent else palette.hairline
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (state.stockHomeBypassSec > 0) {
                        "原厂桌面临时放行中（剩余 ${state.stockHomeBypassSec} 秒）"
                    } else {
                        "久学网原厂桌面 60 秒临时放行"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.stockHomeBypassSec > 0) palette.accent else palette.ink
                )
                Text(
                    text = "需要进入原厂系统调 Wi-Fi / 升级词典时开启，60 秒后自动重新拦截锁回。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted
                )
                MonoPrimaryButton(
                    text = if (state.stockHomeBypassSec > 0) "立即恢复拦截" else "放行原厂桌面 60 秒",
                    useAccent = state.stockHomeBypassSec > 0,
                    onClick = onToggleBypass60s,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "vault_bypass_60s"
                )
            }
        }

        // 2. System Takeover Status & System Settings Shortcuts
        MonoSectionTitle("系统接管与防锁回状态")
        MonoCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "默认桌面：${if (diag.isPenOsDefaultHome) "PenOS ✓" else diag.currentDefaultHomePackage}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.ink
                )
                Text(
                    text = "无障碍防锁回守护：${if (diag.isAccessibilityGuardActive) "运行中 ✓" else "未开启"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (diag.isAccessibilityGuardActive) palette.success else palette.danger
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonoOutlineButton(
                        text = "无障碍设置",
                        onClick = { SystemTakeoverHelper.openAccessibilitySettings(context) },
                        modifier = Modifier.weight(1f)
                    )
                    MonoOutlineButton(
                        text = "默认桌面",
                        onClick = { SystemTakeoverHelper.openHomeSettings(context) },
                        modifier = Modifier.weight(1f)
                    )
                    MonoOutlineButton(
                        text = "刷新",
                        onClick = onRefreshDiagnostics
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("低功耗防强杀常驻", style = MaterialTheme.typography.bodyMedium, color = palette.ink)
                        Text("防止 1GB 内存词典笔后台回收桌面", style = MaterialTheme.typography.labelSmall, color = palette.inkMuted)
                    }
                    Switch(
                        checked = state.config.keepAliveEnabled,
                        onCheckedChange = onToggleKeepAlive
                    )
                }
            }
        }

        // 3. Blocked Stock Home Package & Explicit HomeActivity Rules
        MonoSectionTitle("组件级拦截规则（兼容同包名扫学）")
        MonoCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = blockedPkgInput,
                    onValueChange = { blockedPkgInput = it },
                    label = { Text("原厂桌面包名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonoPrimaryButton(
                        text = "保存拦截包名",
                        onClick = { onSetBlockedHomePackage(blockedPkgInput) },
                        modifier = Modifier.weight(1f)
                    )
                    if (diag.isRootAvailable) {
                        MonoOutlineButton(
                            text = "Root 禁用组件",
                            onClick = onRootDisableHomeComponent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (state.config.blockedHomeActivities.isNotEmpty()) {
                    Text("已拉黑的原厂 Home 组件（点击移除）：", style = MaterialTheme.typography.labelSmall, color = palette.inkMuted)
                    state.config.blockedHomeActivities.forEach { comp ->
                        MonoOutlineButton(
                            text = "✕ $comp",
                            onClick = { onToggleBlockedActivity(comp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 4. Live Window Radar for Jiuxuewang Component Inspection
        if (state.radarLog.isNotEmpty()) {
            MonoSectionTitle("窗口雷达（最近捕获的前台组件）")
            state.radarLog.take(6).forEach { entry ->
                MonoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${entry.timestamp} · ${if (entry.wasIntercepted) "已拦截" else "放行"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.wasIntercepted) palette.danger else palette.inkMuted
                        )
                        Text(
                            text = entry.flatComponent,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = palette.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MonoOutlineButton(
                                text = "设为扫学入口",
                                onClick = { onBindScanFromRadar(entry.packageName, entry.className) },
                                modifier = Modifier.weight(1f)
                            )
                            MonoOutlineButton(
                                text = "拉黑此 Home",
                                onClick = { onToggleBlockedActivity(entry.flatComponent) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 5. Compact ADB Reference
        val adbScript = remember(context.packageName, state.config.blockedHomePackage, diag.detectedStockHomeComponent) {
            SystemTakeoverHelper.buildAdbTakeoverScript(
                selfPackage = context.packageName,
                stockLauncherPkg = state.config.blockedHomePackage,
                stockHomeComponent = diag.detectedStockHomeComponent
            )
        }
        MonoSectionTitle("ADB 接管指令参考")
        MonoCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = adbScript,
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkMuted
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
