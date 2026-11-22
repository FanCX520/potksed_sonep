package com.example.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.system.SystemTakeoverHelper
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme

/**
 * Jiuxuewang (久学网) Same-Package Multi-Activity Anti-Lock & System Takeover Tab.
 * Supports picking exact Scan Activity + component-level HomeActivity interception & single-component Root disable.
 */
@Composable
internal fun TakeoverAndScanTab(
    state: PenOsUiState,
    onBindScanTarget: (pkg: String, activityClass: String) -> Unit,
    onInspectPackageActivities: (String) -> Unit,
    onBindBlockedLauncher: (String) -> Unit,
    onToggleBlockedHomeActivity: (String) -> Unit,
    onAddBlockedHomeActivity: (String) -> Unit,
    onToggleKeepAlive: (Boolean) -> Unit,
    onRootSetHome: () -> Unit,
    onRootDisableHomeComponent: (String) -> Unit,
    onRootFreezeStock: (Boolean) -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onOpenLabRadar: () -> Unit,
    onToggleStockHomeBypass60s: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = PenTheme.colors
    val diag = state.diagnostics
    val cfg = state.config
    var showPickerMode by remember { mutableIntStateOf(0) } // 1=ScanPkg, 2=BlockedPkg, 3=ScanActivityList
    var manualHomeComp by remember(diag.detectedStockHomeComponent) {
        mutableStateOf(diag.detectedStockHomeComponent)
    }

    val adbScript = remember(context.packageName, cfg.blockedStockLauncherPackage, diag.detectedStockHomeComponent) {
        SystemTakeoverHelper.buildAdbTakeoverScript(
            selfPackage = context.packageName,
            stockLauncherPkg = cfg.blockedStockLauncherPackage,
            stockHomeComponent = diag.detectedStockHomeComponent
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = colors.chipSurface.copy(alpha = 0.55f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "默认桌面: ${if (diag.isPenOsDefaultHome) "PenOS(已接管)" else diag.currentDefaultHomePackage} | 无障碍: ${if (diag.isAccessibilityGuardActive) "运行中" else "未开"} | Root: ${if (diag.isRootAvailable) "已检测" else "无"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "刷新",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.linkAccent,
                    modifier = Modifier
                        .clickable(onClick = onRefreshDiagnostics)
                        .padding(horizontal = 6.dp)
                )
            }
        }

        // PenOS 5.0: 60-Second Temporary Stock Home Bypass (for stock settings / OTA updates)
        val bypassSec = state.stockHomeBypassRemainingSec
        val bypassActive = bypassSec > 0
        Button(
            onClick = onToggleStockHomeBypass60s,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (bypassActive) colors.dangerRose else colors.standByAmber.copy(alpha = 0.92f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .testTag("vault_stock_home_bypass_btn")
        ) {
            Text(
                text = if (bypassActive) {
                    "已临时放行原厂桌面：剩余 ${bypassSec}s（点此立即恢复防锁拦截）"
                } else {
                    "临时放行原厂桌面 60 秒（用于进原厂调系统设置/升级）"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (bypassActive) Color.White else Color(0xFF1A1405),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Jiuxuewang Component-Level Binding Banner
        Text(
            text = "久学网同包名多 Activity 精准隔离（解决扫学按返回掉回原厂桌面）",
            style = MaterialTheme.typography.labelMedium,
            color = colors.badgeEmber
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { showPickerMode = if (showPickerMode == 1) 0 else 1 },
                shape = RoundedCornerShape(980.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            ) {
                Text(
                    text = "扫学包: ${cfg.scanAppPackage.substringAfterLast('.').ifBlank { "未绑" }}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = {
                    if (cfg.scanAppPackage.isNotBlank()) {
                        onInspectPackageActivities(cfg.scanAppPackage)
                        showPickerMode = if (showPickerMode == 3) 0 else 3
                    }
                },
                shape = RoundedCornerShape(980.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            ) {
                Text(
                    text = "扫学页面: ${cfg.scanActivityClass.substringAfterLast('.').ifBlank { "自动" }}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = { showPickerMode = if (showPickerMode == 2) 0 else 2 },
                shape = RoundedCornerShape(980.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            ) {
                Text(
                    text = "拦截包: ${cfg.blockedStockLauncherPackage.substringAfterLast('.').ifBlank { "未选" }}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showPickerMode == 1 || showPickerMode == 2) {
            Surface(shape = RoundedCornerShape(10.dp), color = colors.chipSurface.copy(alpha = 0.65f)) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (showPickerMode == 1) "选择「学王扫学」所属应用包：" else "选择同包名/原厂桌面包名：",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primaryAction
                    )
                    state.installedApps.take(14).forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    if (showPickerMode == 1) {
                                        onBindScanTarget(app.packageName, app.launchActivityName)
                                        onInspectPackageActivities(app.packageName)
                                    } else {
                                        onBindBlockedLauncher(app.packageName)
                                        if (app.homeActivityName.isNotBlank()) {
                                            onAddBlockedHomeActivity("${app.packageName}/${app.homeActivityName}")
                                        }
                                    }
                                    showPickerMode = 0
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(app.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        }
                    }
                }
            }
        } else if (showPickerMode == 3) {
            Surface(shape = RoundedCornerShape(10.dp), color = colors.chipSurface.copy(alpha = 0.65f)) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "选择「${cfg.scanAppPackage}」内精确的扫学 Activity：",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.vocabEmerald
                    )
                    if (state.lab.inspectedPackageActivities.isEmpty()) {
                        Text("未扫描到导出 Activity，可在实验室「久学雷达」中打开一次扫学自动捕获！", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    } else {
                        state.lab.inspectedPackageActivities.take(15).forEach { cls ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        onBindScanTarget(cfg.scanAppPackage, cls)
                                        showPickerMode = 0
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cls.substringAfterLast('.'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("点击绑定", style = MaterialTheme.typography.labelSmall, color = colors.linkAccent)
                            }
                        }
                    }
                }
            }
        }

        // Blocked HomeActivity List + Manual Add + Lab Radar Jump
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = manualHomeComp,
                onValueChange = { manualHomeComp = it },
                placeholder = { Text("原厂桌面组件 包名/HomeActivity", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { if (manualHomeComp.isNotBlank()) onAddBlockedHomeActivity(manualHomeComp) },
                shape = RoundedCornerShape(980.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.dangerRose),
                modifier = Modifier.height(34.dp)
            ) {
                Text("拉黑组件", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = onOpenLabRadar,
                shape = RoundedCornerShape(980.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("实时雷达", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (cfg.blockedHomeActivities.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                cfg.blockedHomeActivities.forEach { comp ->
                    Surface(shape = RoundedCornerShape(8.dp), color = colors.dangerRose.copy(alpha = 0.14f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("已精准拦截：$comp", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.dangerRose, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "移除拦截组件",
                                tint = colors.dangerRose,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onToggleBlockedHomeActivity(comp) }
                            )
                        }
                    }
                }
            }
        }

        Text("方案 A：无 Root 无障碍组件级守护与低功耗保活", style = MaterialTheme.typography.labelMedium, color = colors.primaryAction)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { SystemTakeoverHelper.openHomeSettings(context) }, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(34.dp)) {
                Text("设为主屏幕", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = { SystemTakeoverHelper.openAccessibilitySettings(context) }, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(34.dp)) {
                Text("开启无障碍防锁", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("后台常驻", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = cfg.keepAliveEnabled, onCheckedChange = onToggleKeepAlive)
        }

        Text("方案 B：Root 单组件禁用原厂桌面（保留扫学！）/ PC 端 ADB", style = MaterialTheme.typography.labelMedium, color = colors.badgeEmber)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onRootSetHome, enabled = diag.isRootAvailable, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(34.dp)) {
                Text("Root 默认桌面", style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = { onRootDisableHomeComponent(manualHomeComp) },
                enabled = diag.isRootAvailable,
                colors = ButtonDefaults.buttonColors(containerColor = colors.dangerRose),
                shape = RoundedCornerShape(980.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("仅禁用Home组件", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = { onRootFreezeStock(true) }, enabled = diag.isRootAvailable, shape = RoundedCornerShape(980.dp), modifier = Modifier.height(34.dp)) {
                Text("整包冻结", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("PenOS ADB", adbScript))
                },
                shape = RoundedCornerShape(980.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("复制 ADB", style = MaterialTheme.typography.labelSmall)
            }
        }

        Surface(shape = RoundedCornerShape(10.dp), color = colors.chipSurface.copy(alpha = 0.6f)) {
            Text(
                text = adbScript,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
