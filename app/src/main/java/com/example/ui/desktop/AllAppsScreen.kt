package com.example.ui.desktop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.system.AppIconCache
import com.example.system.InstalledAppInfo
import com.example.ui.PenOsUiState
import com.example.ui.components.MonoTopBar
import com.example.ui.theme.PenTheme

@Composable
fun AllAppsScreen(
    state: PenOsUiState,
    onBack: () -> Unit,
    onRefreshApps: () -> Unit,
    onLaunchApp: (String, String) -> Unit,
    onBindScanApp: (String, String) -> Unit,
    onHideApp: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val palette = PenTheme.palette
    var query by remember { mutableStateOf("") }
    var selectedForMenu by remember { mutableStateOf<InstalledAppInfo?>(null) }

    val visibleApps = remember(state.installedApps, state.config.hiddenPackages, query) {
        val q = query.trim().lowercase()
        state.installedApps.filter { app ->
            !state.config.hiddenPackages.contains(app.packageName) &&
                (q.isBlank() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .safeDrawingPadding()
    ) {
        MonoTopBar(
            title = "全部应用",
            subtitle = "可见 ${visibleApps.size} 个 · 长按可设扫学或隐藏",
            onBack = onBack,
            trailing = {
                IconButton(
                    onClick = onRefreshApps,
                    modifier = Modifier.size(48.dp).testTag("apps_refresh_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新应用列表", tint = palette.ink)
                }
            }
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索应用名称或包名", color = palette.inkMuted) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.ink,
                unfocusedBorderColor = palette.hairline,
                focusedTextColor = palette.ink,
                unfocusedTextColor = palette.ink,
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("apps_search_input")
        )

        if (visibleApps.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isBlank()) "暂无可启动的应用" else "未找到匹配「$query」的应用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 86.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(visibleApps, key = { it.packageName }) { app ->
                    AppGridTile(
                        app = app,
                        isScanTarget = app.packageName == state.config.scanAppPackage,
                        onClick = { onLaunchApp(app.packageName, app.launchActivityName) },
                        onLongClick = { selectedForMenu = app }
                    )
                }
            }
        }
    }

    val menuApp = selectedForMenu
    if (menuApp != null) {
        AlertDialog(
            onDismissRequest = { selectedForMenu = null },
            containerColor = palette.surface,
            title = { Text(menuApp.label, color = palette.ink) },
            text = {
                Text(
                    text = menuApp.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBindScanApp(menuApp.packageName, menuApp.launchActivityName)
                        selectedForMenu = null
                    }
                ) {
                    Text("设为扫学入口", color = palette.accent)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            onHideApp(menuApp.packageName)
                            selectedForMenu = null
                        }
                    ) {
                        Text("隐藏到密室", color = palette.ink)
                    }
                    TextButton(onClick = { selectedForMenu = null }) {
                        Text("取消", color = palette.inkMuted)
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridTile(
    app: InstalledAppInfo,
    isScanTarget: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val palette = PenTheme.palette
    val context = LocalContext.current
    var iconBitmap by remember(app.packageName) { mutableStateOf<ImageBitmap?>(AppIconCache.peek(app.packageName)) }

    LaunchedEffect(app.packageName) {
        if (iconBitmap == null) {
            iconBitmap = AppIconCache.loadIcon(context, app.packageName)
        }
    }

    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, if (isScanTarget) palette.accent else palette.hairline, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val bmp = iconBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = app.label,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = palette.ink
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (isScanTarget) {
            Text(
                text = "扫学",
                style = MaterialTheme.typography.labelSmall,
                color = palette.accent,
                maxLines = 1
            )
        }
    }
}
