package com.example.ui.mail

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MailAccountEntity
import com.example.data.local.MailMessageEntity
import com.example.data.remote.ImapSmtpMailClient
import com.example.ui.MailDraftState
import com.example.ui.PenOsUiState
import com.example.ui.theme.PenTheme

/**
 * PenOS 4.0 Built-in IMAP/SMTP Email Client tailored for 640x172 landscape dictionary pens.
 * Supports QQ / 163 / 126 / Aliyun / Outlook / Gmail / Custom IMAP+SMTP accounts,
 * AI inbox summarization, and AI reply drafting with mandatory user confirmation before sending.
 */
@Composable
fun MailScreen(
    state: PenOsUiState,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onSelectMessage: (String) -> Unit,
    onSummarizeInbox: () -> Unit,
    onDraftReplyWithAi: (MailMessageEntity) -> Unit,
    onOpenCompose: (String, String, String) -> Unit,
    onCloseCompose: () -> Unit,
    onSendDraft: (String, String, String) -> Unit,
    onSetShowAccountDialog: (Boolean) -> Unit,
    onSaveAccount: (MailAccountEntity) -> Unit,
    onDeleteAccount: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        when {
            state.mail.pendingDraft != null -> onCloseCompose()
            state.mail.showAccountDialog -> onSetShowAccountDialog(false)
            else -> onBack()
        }
    }

    val colors = MaterialTheme.colorScheme
    val mailState = state.mail
    val selectedMsg = mailState.messages.firstOrNull { it.id == mailState.selectedMessageId } ?: mailState.messages.firstOrNull()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("mail_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Top Action Bar (26dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.surfaceVariant,
                        modifier = Modifier
                            .clickable { onBack() }
                            .testTag("mail_back_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回桌面", modifier = Modifier.size(13.dp))
                            Text("返回", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Icon(Icons.Rounded.Mail, contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
                    Text(
                        text = "笔端邮箱 (${mailState.accounts.size}账号)",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    MailTopActionChip(
                        label = if (mailState.isLoading) "同步中…" else "收信",
                        icon = Icons.Rounded.Refresh,
                        accent = false,
                        testTag = "mail_sync_button",
                        onClick = onSync
                    )
                    MailTopActionChip(
                        label = if (mailState.isAiProcessing) "AI分析中…" else "AI总结",
                        icon = Icons.Rounded.AutoAwesome,
                        accent = true,
                        testTag = "mail_ai_summarize_button",
                        onClick = onSummarizeInbox
                    )
                    MailTopActionChip(
                        label = "写信",
                        icon = Icons.Rounded.Create,
                        accent = false,
                        testTag = "mail_compose_button",
                        onClick = { onOpenCompose("", "", "") }
                    )
                    MailTopActionChip(
                        label = "账号",
                        icon = Icons.Rounded.Settings,
                        accent = false,
                        testTag = "mail_account_button",
                        onClick = { onSetShowAccountDialog(true) }
                    )
                }
            }

            // Two-Pane Body
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                // Left Pane: AI Summary + Inbox List
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, PenTheme.colors.glassBorder),
                    modifier = Modifier
                        .width(236.dp)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (mailState.aiSummary.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.primaryContainer.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "AI 总结：${mailState.aiSummary}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onPrimaryContainer,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }

                        if (mailState.messages.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = if (mailState.accounts.isEmpty()) "尚未绑定 IMAP/SMTP 邮箱" else "暂无邮件，请点击「收信」",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = colors.primary,
                                        modifier = Modifier.clickable {
                                            if (mailState.accounts.isEmpty()) onSetShowAccountDialog(true) else onSync()
                                        }
                                    ) {
                                        Text(
                                            text = if (mailState.accounts.isEmpty()) "配置邮箱账号" else "立即收信",
                                            color = colors.onPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(mailState.messages, key = { it.id }) { msg ->
                                    val isSel = msg.id == selectedMsg?.id
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSel) colors.primaryContainer.copy(alpha = 0.55f) else colors.surfaceVariant.copy(alpha = 0.45f),
                                        border = if (isSel) BorderStroke(1.dp, colors.primary) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectMessage(msg.id) }
                                    ) {
                                        Column(modifier = Modifier.padding(6.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (msg.isUnread) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(colors.primary)
                                                    )
                                                }
                                                Text(
                                                    text = msg.fromAddress,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.onSurface,
                                                    fontWeight = if (msg.isUnread) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Text(
                                                text = msg.subject,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Pane: Reader + AI Reply Drafting
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, PenTheme.colors.glassBorder),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (selectedMsg == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "选择左侧邮件查看正文 · 支持 QQ/163/阿里/Outlook 与 AI 起草回复",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedMsg.subject,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = selectedMsg.fromAddress,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    MailTopActionChip(
                                        label = "回复",
                                        icon = Icons.AutoMirrored.Rounded.Reply,
                                        accent = false,
                                        testTag = "mail_reply_button",
                                        onClick = {
                                            val subj = if (selectedMsg.subject.startsWith("Re:", true)) selectedMsg.subject else "Re: ${selectedMsg.subject}"
                                            val replyEmail = Regex("<([^>]+@[^>]+)>").find(selectedMsg.fromAddress)?.groupValues?.getOrNull(1)?.trim()
                                                ?: selectedMsg.fromAddress.trim()
                                            onOpenCompose(replyEmail, subj, "")
                                        }
                                    )
                                    MailTopActionChip(
                                        label = "AI起草",
                                        icon = Icons.Rounded.AutoAwesome,
                                        accent = true,
                                        testTag = "mail_ai_draft_button",
                                        onClick = { onDraftReplyWithAi(selectedMsg) }
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = selectedMsg.bodyText.ifBlank { selectedMsg.snippet },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurface,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Compose / Confirm-Send Modal (also opened when AI/MCP drafts an email!)
        mailState.pendingDraft?.let { draft ->
            MailComposeOverlay(
                initialDraft = draft,
                isSending = mailState.isLoading,
                onDismiss = onCloseCompose,
                onConfirmSend = onSendDraft
            )
        }

        // IMAP/SMTP Account Setup Modal
        if (mailState.showAccountDialog) {
            MailAccountSetupOverlay(
                existingAccounts = mailState.accounts,
                onDismiss = { onSetShowAccountDialog(false) },
                onSave = onSaveAccount,
                onDelete = onDeleteAccount
            )
        }
    }
}

@Composable
private fun MailTopActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (accent) colors.primaryContainer else colors.surfaceVariant,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(icon, contentDescription = label, tint = if (accent) colors.primary else colors.onSurface, modifier = Modifier.size(12.dp))
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = if (accent) colors.primary else colors.onSurface)
        }
    }
}

@Composable
private fun MailComposeOverlay(
    initialDraft: MailDraftState,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onConfirmSend: (String, String, String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var to by remember(initialDraft) { mutableStateOf(initialDraft.to) }
    var subject by remember(initialDraft) { mutableStateOf(initialDraft.subject) }
    var body by remember(initialDraft) { mutableStateOf(initialDraft.body) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .padding(horizontal = 24.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("撰写邮件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (initialDraft.isAiGenerated) {
                            Surface(shape = RoundedCornerShape(6.dp), color = colors.primaryContainer) {
                                Text(
                                    "AI 已起草 · 请人工确认后发送",
                                    fontSize = 9.sp,
                                    color = colors.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.surfaceVariant,
                            modifier = Modifier.clickable { onDismiss() }
                        ) {
                            Text("取消", fontSize = 10.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.primary,
                            modifier = Modifier
                                .clickable(enabled = !isSending) { onConfirmSend(to, subject, body) }
                                .testTag("mail_send_confirm_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(11.dp))
                                Text(if (isSending) "发送中…" else "确认发送", fontSize = 10.sp, color = colors.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactInputField("收件人", to, { to = it }, Modifier.weight(1f))
                    CompactInputField("主题", subject, { subject = it }, Modifier.weight(1f))
                }
                CompactInputField("邮件正文", body, { body = it }, Modifier.fillMaxWidth().weight(1f), singleLine = false)
            }
        }
    }
}

@Composable
private fun MailAccountSetupOverlay(
    existingAccounts: List<MailAccountEntity>,
    onDismiss: () -> Unit,
    onSave: (MailAccountEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var selectedPreset by remember { mutableStateOf(ImapSmtpMailClient.providerPresets.first()) }
    var email by remember { mutableStateOf(existingAccounts.firstOrNull()?.emailAddress.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var imapHost by remember { mutableStateOf(existingAccounts.firstOrNull()?.imapHost ?: selectedPreset.imapHost) }
    var smtpHost by remember { mutableStateOf(existingAccounts.firstOrNull()?.smtpHost ?: selectedPreset.smtpHost) }
    var imapPort by remember { mutableStateOf((existingAccounts.firstOrNull()?.imapPort ?: selectedPreset.imapPort).toString()) }
    var smtpPort by remember { mutableStateOf((existingAccounts.firstOrNull()?.smtpPort ?: selectedPreset.smtpPort).toString()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .padding(horizontal = 18.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, PenTheme.colors.glassBorder),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("IMAP / SMTP 邮箱配置（支持国内主流邮箱）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.surfaceVariant,
                            modifier = Modifier.clickable { onDismiss() }
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "关闭", modifier = Modifier.padding(4.dp).size(14.dp))
                        }
                    }
                }

                // 1-Tap Provider Presets
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    ImapSmtpMailClient.providerPresets.forEach { preset ->
                        val active = preset.id == selectedPreset.id
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (active) colors.primary else colors.surfaceVariant,
                            modifier = Modifier.clickable {
                                selectedPreset = preset
                                imapHost = preset.imapHost
                                smtpHost = preset.smtpHost
                                imapPort = preset.imapPort.toString()
                                smtpPort = preset.smtpPort.toString()
                            }
                        ) {
                            Text(
                                text = preset.name,
                                fontSize = 9.sp,
                                color = if (active) colors.onPrimary else colors.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactInputField("邮箱地址", email, { email = it }, Modifier.weight(1f))
                    CompactInputField("授权码 / 密码", password, { password = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactInputField("IMAP 主机", imapHost, { imapHost = it }, Modifier.weight(1.2f))
                    CompactInputField("端口", imapPort, { imapPort = it }, Modifier.width(56.dp))
                    CompactInputField("SMTP 主机", smtpHost, { smtpHost = it }, Modifier.weight(1.2f))
                    CompactInputField("端口", smtpPort, { smtpPort = it }, Modifier.width(56.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedPreset.hint,
                        fontSize = 9.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        existingAccounts.firstOrNull()?.let { firstAcc ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.errorContainer,
                                modifier = Modifier.clickable { onDelete(firstAcc.id) }
                            ) {
                                Text("删除旧账号", fontSize = 9.sp, color = colors.onErrorContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.primary,
                            modifier = Modifier
                                .clickable {
                                    if (email.contains("@") && password.isNotBlank()) {
                                        onSave(
                                            MailAccountEntity(
                                                id = "mail_${email.trim().lowercase().hashCode().toUInt()}",
                                                label = email.substringBefore("@"),
                                                emailAddress = email.trim(),
                                                username = email.trim(),
                                                passwordOrAuthCode = password.trim(),
                                                imapHost = imapHost.trim(),
                                                imapPort = imapPort.toIntOrNull() ?: 993,
                                                smtpHost = smtpHost.trim(),
                                                smtpPort = smtpPort.toIntOrNull() ?: 465,
                                                useSsl = true,
                                                isDefault = true
                                            )
                                        )
                                    }
                                }
                                .testTag("mail_save_account_button")
                        ) {
                            Text("保存并收信", fontSize = 10.sp, color = colors.onPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactInputField(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 10.sp, color = colors.onSurfaceVariant.copy(alpha = 0.7f))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(color = colors.onSurface, fontSize = 10.sp),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
