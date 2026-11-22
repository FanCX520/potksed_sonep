package com.example.ui.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomWidgetEntity
import com.example.data.local.DrawerSlotEntity
import com.example.system.PenDynamicWallpaperBackdrop
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme
import com.example.ui.theme.springPressScale
import com.example.widgets.CustomDslWidgetCard
import com.example.widgets.MailInboxPreviewCard
import com.example.widgets.MultiStyleClockCard

/**
 * Apple TV-style floating shelf drawer triggered by the first swipe-up from Desktop.
 * - Second swipe-up inside the drawer transitions seamlessly into Full-Screen Pomodoro Focus!
 * - Swipe-down returns to Desktop.
 * - Hosts MCP-authored declarative Custom Widgets, Multi-Style Clock, Mail Preview, Multi-AI Quick Switcher,
 *   and custom user/MCP action tiles.
 */
@Composable
fun AppleTvDrawerScreen(
    state: PenOsUiState,
    onClose: () -> Unit,
    onEnterPomodoro: () -> Unit,
    onCycleClockStyle: () -> Unit,
    onOpenMail: () -> Unit,
    onOpenLab: () -> Unit,
    onSelectAiProfile: (String) -> Unit,
    onToggleCustomChecklist: (CustomWidgetEntity, Int, Int) -> Unit,
    onCustomWidgetAction: (String, String) -> Unit,
    onAddDrawerSlot: (String, String, String) -> Unit,
    onRemoveDrawerSlot: (String) -> Unit,
    onGestureHaptic: () -> Unit = {},
    onMutateCustomDsl: (CustomWidgetEntity, String) -> Unit = { _, _ -> },
    onRunWidgetMacro: (List<String>) -> Unit = {},
    onRefreshHttpBlock: (CustomWidgetEntity, Int, String, String) -> Unit = { _, _, _, _ -> },
    onInstallTemplate: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }

    val isLight = state.config.isAppleLightDesktop()
    val colors = MaterialTheme.colorScheme
    val swipeThresholdPx = with(LocalDensity.current) { state.config.swipeSensitivityDp.dp.toPx() }
    var editMode by remember { mutableStateOf(false) }
    var dragAccum by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("apple_tv_drawer_screen")
            .pointerInput(swipeThresholdPx) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onVerticalDrag = { _, dragAmount -> dragAccum += dragAmount },
                    onDragEnd = {
                        when {
                            dragAccum < -swipeThresholdPx -> {
                                onGestureHaptic()
                                onEnterPomodoro()
                            }
                            dragAccum > swipeThresholdPx -> {
                                onGestureHaptic()
                                onClose()
                            }
                        }
                        dragAccum = 0f
                    }
                )
            }
    ) {
        PenDynamicWallpaperBackdrop(
            config = state.config,
            isAppleLight = isLight
        )
        // Frosted Apple TV glass scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isLight) Color(0xD9F8FAFC) else Color(0xCC070B14))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Top Header Strip (24dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.8f))
                    )
                    Text(
                        text = "Apple TV 灵感抽屉",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .springPressScale { onEnterPomodoro() }
                            .testTag("drawer_to_pomodoro_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "再次上滑进番茄专注",
                                tint = colors.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "再上滑进番茄专注",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (editMode) colors.primary else colors.surfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier
                            .clickable { editMode = !editMode }
                            .testTag("drawer_edit_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "编辑抽屉",
                                tint = if (editMode) colors.onPrimary else colors.onSurface,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (editMode) "完成" else "定制",
                                fontSize = 9.sp,
                                color = if (editMode) colors.onPrimary else colors.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = colors.surfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { onClose() }
                            .testTag("drawer_close_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭抽屉",
                                tint = colors.onSurface,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            // Horizontal Apple TV Floating Shelf (weight 1f so it fills 172dp cleanly!)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
            ) {
                items(state.drawerSlots, key = { it.id }) { slot ->
                    Box(modifier = Modifier.fillMaxHeight()) {
                        when (slot.type) {
                            DrawerSlotEntity.TYPE_CUSTOM_WIDGET -> {
                                val widget = state.customWidgets.firstOrNull { it.id == slot.refId }
                                if (widget != null) {
                                    CustomDslWidgetCard(
                                        widget = widget,
                                        onToggleChecklist = { _, bIdx, iIdx -> onToggleCustomChecklist(widget, bIdx, iIdx) },
                                        onInvokeAction = onCustomWidgetAction,
                                        onMutateDsl = onMutateCustomDsl,
                                        onRunMacro = onRunWidgetMacro,
                                        onRefreshHttpBlock = onRefreshHttpBlock,
                                        onDelete = if (editMode) {
                                            { onRemoveDrawerSlot(slot.id) }
                                        } else null,
                                        modifier = Modifier
                                            .width(widget.widthDp.dp)
                                            .fillMaxHeight()
                                    )
                                } else {
                                    MissingSlotCard(
                                        title = slot.title,
                                        onRemove = { onRemoveDrawerSlot(slot.id) }
                                    )
                                }
                            }

                            DrawerSlotEntity.TYPE_BUILTIN_CLOCK -> {
                                MultiStyleClockCard(
                                    clockConfig = state.config.multiClock,
                                    onCycleStyle = onCycleClockStyle,
                                    modifier = Modifier
                                        .width(176.dp)
                                        .fillMaxHeight()
                                )
                            }

                            DrawerSlotEntity.TYPE_BUILTIN_MAIL -> {
                                MailInboxPreviewCard(
                                    state = state,
                                    onOpenMail = onOpenMail,
                                    modifier = Modifier
                                        .width(196.dp)
                                        .fillMaxHeight()
                                )
                            }

                            DrawerSlotEntity.TYPE_AI_SWITCHER -> {
                                AiProfileQuickSwitchCard(
                                    state = state,
                                    onSelectProfile = onSelectAiProfile,
                                    onOpenLab = onOpenLab,
                                    modifier = Modifier
                                        .width(196.dp)
                                        .fillMaxHeight()
                                )
                            }

                            else -> {
                                DrawerActionTileCard(
                                    slot = slot,
                                    onClick = { onCustomWidgetAction(slot.refId, "") },
                                    modifier = Modifier
                                        .width(134.dp)
                                        .fillMaxHeight()
                                )
                            }
                        }

                        if (editMode && slot.type != DrawerSlotEntity.TYPE_CUSTOM_WIDGET) {
                            Surface(
                                shape = CircleShape,
                                color = colors.error,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(18.dp)
                                    .clickable { onRemoveDrawerSlot(slot.id) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "移除槽位",
                                        tint = colors.onError,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Append quick add-to-drawer card in edit mode or at tail
                item(key = "add_drawer_card") {
                    DrawerAddTileCard(
                        onAddClock = { onAddDrawerSlot(DrawerSlotEntity.TYPE_BUILTIN_CLOCK, "multi_clock", "多样式时钟") },
                        onAddMail = { onAddDrawerSlot(DrawerSlotEntity.TYPE_BUILTIN_MAIL, "mail_inbox", "笔端邮箱") },
                        onOpenAiBuild = onOpenLab,
                        onInstallTemplate = onInstallTemplate,
                        modifier = Modifier
                            .width(172.dp)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun AiProfileQuickSwitchCard(
    state: PenOsUiState,
    onSelectProfile: (String) -> Unit,
    onOpenLab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val profiles = state.config.aiProfiles
    val activeId = state.config.activeAiProfileId

    Surface(
        modifier = modifier.testTag("drawer_ai_switcher_card"),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, PenTheme.colors.glassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(9.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "AI 模型快切",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "实验室>",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    modifier = Modifier.clickable { onOpenLab() }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                profiles.take(3).forEach { p ->
                    val selected = p.id == activeId
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.55f),
                        border = if (selected) BorderStroke(1.dp, colors.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectProfile(p.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = p.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) colors.primary else colors.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (p.apiKey.isNotBlank()) "就绪" else "未填Key",
                                fontSize = 8.sp,
                                color = if (p.apiKey.isNotBlank()) PenTheme.colors.vocabEmerald else colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerActionTileCard(
    slot: DrawerSlotEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val icon = when (slot.refId.lowercase()) {
        "lan_admin" -> Icons.Rounded.Language
        "theme_toggle" -> Icons.Rounded.Palette
        "mail" -> Icons.Rounded.Mail
        else -> Icons.Rounded.AutoAwesome
    }
    Surface(
        modifier = modifier.springPressScale(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, PenTheme.colors.glassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                shape = CircleShape,
                color = colors.primaryContainer,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = slot.title, tint = colors.primary, modifier = Modifier.size(16.dp))
                }
            }
            Column {
                Text(
                    text = slot.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "轻触立即触发",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun DrawerAddTileCard(
    onAddClock: () -> Unit,
    onAddMail: () -> Unit,
    onOpenAiBuild: () -> Unit,
    onInstallTemplate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(13.dp))
                Text("扩展抽屉 & 模板", style = MaterialTheme.typography.labelMedium, color = colors.onSurface, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = colors.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAiBuild() }
                ) {
                    Text(
                        text = "让 AI MCP 写组件",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onInstallTemplate("tpl_evening_study_macro") }
                    ) {
                        Text("+晚自习宏", fontSize = 8.sp, color = colors.primary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onInstallTemplate("tpl_habit_mood_tracker") }
                    ) {
                        Text("+打卡条", fontSize = 8.sp, color = PenTheme.colors.vocabEmerald, modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onAddClock() }
                    ) {
                        Text("+时钟", fontSize = 8.sp, color = colors.onSurface, modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onAddMail() }
                    ) {
                        Text("+邮箱", fontSize = 8.sp, color = colors.onSurface, modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingSlotCard(title: String, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .width(124.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 2)
            Text(
                text = "移除空槽",
                style = MaterialTheme.typography.labelSmall,
                color = colors.error,
                modifier = Modifier.clickable { onRemove() }
            )
        }
    }
}
