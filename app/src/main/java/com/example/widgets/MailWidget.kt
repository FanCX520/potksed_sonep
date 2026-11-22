package com.example.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme

object MailInboxWidgetPlugin : PenWidgetPlugin {
    override val id: String = "mail_inbox"
    override val title: String = "邮箱收发"
    override val subtitle: String = "未读预览与 AI 摘要"
    override val preferredWidth: Dp = 186.dp
    override val lowPowerNote: String = "离线缓存 · IMAP/SMTP"

    @Composable
    override fun Render(
        state: PenOsUiState,
        actions: WidgetActionBridge,
        modifier: Modifier
    ) {
        MailInboxPreviewCard(
            state = state,
            onOpenMail = actions.onOpenMail,
            modifier = modifier
        )
    }
}

@Composable
fun MailInboxPreviewCard(
    state: PenOsUiState,
    onOpenMail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PenTheme.colors
    val accent = colors.glacierBlue
    val messages = state.mail.messages
    val unreadCount = messages.count { it.isUnread }
    val latest = messages.firstOrNull()

    AdaptiveWidgetCard(
        modifier = modifier.testTag("widget_mail_inbox"),
        darkBorderColor = accent.copy(alpha = 0.34f),
        onClick = onOpenMail
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Icon(
                            imageVector = Icons.Rounded.Mail,
                            contentDescription = "邮箱",
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                                    .testTag("mail_widget_unread_dot")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "邮箱",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (unreadCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = Color(0xFFEF4444)
                    ) {
                        Text(
                            text = "${unreadCount}未读",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                } else {
                    Text(
                        text = if (state.mail.accounts.isEmpty()) "待配置" else "已同步",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (latest == null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (state.mail.accounts.isEmpty()) "点击配置 IMAP/SMTP" else "收件箱暂无邮件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "支持 QQ / 163 / 阿里 / AI 总结起草",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (latest.isUnread) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = latest.subject,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = latest.fromAddress.substringBefore("<").trim().ifBlank { latest.fromAddress },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = latest.snippet,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
