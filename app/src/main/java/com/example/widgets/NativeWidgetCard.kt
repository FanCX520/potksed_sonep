package com.example.widgets

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.system.NativeWidgetHostManager
import com.example.ui.theme.PenTheme

/**
 * Hosts a real Android Native System AppWidget inside PenOS's adaptive card container
 * (supports both 1+2 Mixed Mode and dedicated Native Widget tab with live width tuning).
 */
@Composable
fun NativeSystemWidgetCard(
    appWidgetId: Int,
    widthDp: Int,
    hostManager: NativeWidgetHostManager,
    showManageBar: Boolean,
    onAdjustWidth: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val penColors = PenTheme.colors
    val shape = RoundedCornerShape(penColors.cardCornerRadius)
    val info = remember(appWidgetId) { hostManager.getProviderInfo(appWidgetId) }
    val label = remember(info) { info?.let { hostManager.resolveProviderLabel(it) } ?: "系统小组件 #$appWidgetId" }

    Surface(
        modifier = modifier
            .width(widthDp.dp)
            .fillMaxHeight()
            .clip(shape)
            .testTag("native_widget_$appWidgetId"),
        shape = shape,
        color = penColors.glassCard,
        border = if (penColors.showCardBorder) BorderStroke(1.dp, penColors.glassBorder) else null,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showManageBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(penColors.chipSurface)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Widgets,
                            contentDescription = null,
                            tint = penColors.linkAccent,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(penColors.glassCard)
                                .clickable { onAdjustWidth(-24) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Remove, "缩窄", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(12.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(penColors.glassCard)
                                .clickable { onAdjustWidth(24) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Add, "加宽", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(12.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(penColors.dangerRose.copy(alpha = 0.22f))
                                .clickable { onRemove() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Close, "移除", tint = penColors.dangerRose, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            if (info == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "原生组件已失效",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "点击移除",
                            style = MaterialTheme.typography.labelSmall,
                            color = penColors.dangerRose,
                            modifier = Modifier.clickable { onRemove() }
                        )
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val hostView = hostManager.createHostView(
                                activityContext = context,
                                appWidgetId = appWidgetId,
                                widthDp = widthDp,
                                heightDp = if (showManageBar) 114 else 140
                            )
                            if (hostView != null) {
                                addView(
                                    hostView,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            }
        }
    }
}
