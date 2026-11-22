package com.example.data.remote

import android.util.Base64
import com.example.data.local.MailAccountEntity
import com.example.data.local.MailMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

data class MailProviderPreset(
    val id: String,
    val name: String,
    val imapHost: String,
    val imapPort: Int,
    val smtpHost: String,
    val smtpPort: Int,
    val useSsl: Boolean,
    val hint: String
)

/**
 * Pure Kotlin zero-bloat IMAP4rev1 + SMTP client built directly on TLS 1.2 sockets for Android 5.0.2 (API 21).
 * Works cleanly inside China campus networks with QQ/163/126/Aliyun/Outlook/Gmail/Custom mail servers.
 */
class ImapSmtpMailClient {

    companion object {
        val providerPresets: List<MailProviderPreset> = listOf(
            MailProviderPreset("qq", "QQ 邮箱", "imap.qq.com", 993, "smtp.qq.com", 465, true, "需在 QQ 邮箱设置开启 IMAP 并使用授权码"),
            MailProviderPreset("163", "网易 163", "imap.163.com", 993, "smtp.163.com", 465, true, "需在 163 邮箱开启 IMAP 并使用客户端授权密码"),
            MailProviderPreset("126", "网易 126", "imap.126.com", 993, "smtp.126.com", 465, true, "需使用 126 客户端授权密码"),
            MailProviderPreset("aliyun", "阿里邮箱", "imap.aliyun.com", 993, "smtp.aliyun.com", 465, true, "阿里云个人/企业邮箱标准 SSL 端口"),
            MailProviderPreset("outlook", "Outlook", "outlook.office365.com", 993, "smtp.office365.com", 587, true, "支持个人/学校 Office365 邮箱"),
            MailProviderPreset("gmail", "Gmail", "imap.gmail.com", 993, "smtp.gmail.com", 465, true, "需开启两步验证并使用应用专用密码")
        )
    }

    private fun openTlsSocket(host: String, port: Int, useSsl: Boolean, timeoutMs: Int = 15_000): Socket {
        if (!useSsl) {
            return Socket().apply {
                connect(InetSocketAddress(host, port), timeoutMs)
                soTimeout = timeoutMs
            }
        }
        val sslContext = SSLContext.getInstance("TLSv1.2").apply { init(null, null, null) }
        val raw = Socket().apply {
            connect(InetSocketAddress(host, port), timeoutMs)
            soTimeout = timeoutMs
        }
        val ssl = sslContext.socketFactory.createSocket(raw, host, port, true) as SSLSocket
        ssl.enabledProtocols = ssl.supportedProtocols.filter {
            it.equals("TLSv1.2", true) || it.equals("TLSv1.3", true) || it.equals("TLSv1.1", true)
        }.toTypedArray().ifEmpty { ssl.supportedProtocols }
        ssl.soTimeout = timeoutMs
        ssl.startHandshake()
        return ssl
    }

    suspend fun fetchRecentInbox(
        account: MailAccountEntity,
        maxCount: Int = 15
    ): Result<List<MailMessageEntity>> = withContext(Dispatchers.IO) {
        runCatching {
            require(account.imapHost.isNotBlank()) { "请填写 IMAP 服务器地址" }
            require(account.username.isNotBlank() && account.passwordOrAuthCode.isNotBlank()) { "请填写邮箱账号与授权码/密码" }

            openTlsSocket(account.imapHost.trim(), account.imapPort, account.useSsl).use { sock ->
                val reader = BufferedReader(InputStreamReader(BufferedInputStream(sock.getInputStream()), Charsets.ISO_8859_1))
                val writer = PrintWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8), true)

                fun sendCmd(tag: String, cmd: String): List<String> {
                    writer.print("$tag $cmd\r\n")
                    writer.flush()
                    val lines = mutableListOf<String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        lines.add(line)
                        if (line.startsWith("$tag ")) break
                    }
                    return lines
                }

                // Server greeting
                reader.readLine()

                val safeUser = account.username.replace("\\", "\\\\").replace("\"", "\\\"")
                val safePass = account.passwordOrAuthCode.replace("\\", "\\\\").replace("\"", "\\\"")
                val loginResp = sendCmd("A1", "LOGIN \"$safeUser\" \"$safePass\"")
                if (loginResp.lastOrNull()?.startsWith("A1 OK", ignoreCase = true) != true) {
                    val err = loginResp.lastOrNull().orEmpty()
                    throw IllegalStateException("IMAP 登录失败（请检查授权码与 IMAP 开关）：${err.take(100)}")
                }

                // Required by 163/126 IMAP servers to avoid "Unsafe Login"
                if (account.imapHost.contains("163.com", true) || account.imapHost.contains("126.com", true)) {
                    sendCmd("A1B", "ID (\"name\" \"PenOSMail\" \"version\" \"4.0\" \"vendor\" \"PenOS\")")
                }

                val selectResp = sendCmd("A2", "SELECT INBOX")
                var existsCount = 0
                selectResp.forEach { line ->
                    val m = Regex("^\\*\\s+(\\d+)\\s+EXISTS", RegexOption.IGNORE_CASE).find(line)
                    if (m != null) existsCount = m.groupValues[1].toIntOrNull() ?: 0
                }
                if (existsCount <= 0) {
                    sendCmd("A9", "LOGOUT")
                    return@runCatching emptyList()
                }

                val startSeq = (existsCount - maxCount + 1).coerceAtLeast(1)
                val fetchResp = sendCmd(
                    "A3",
                    "FETCH $startSeq:$existsCount (UID FLAGS BODY.PEEK[HEADER.FIELDS (FROM TO SUBJECT DATE)] BODY.PEEK[TEXT]<0.2800>)"
                )
                sendCmd("A9", "LOGOUT")

                parseImapFetchResponse(account.id, fetchResp).reversed()
            }
        }
    }

    suspend fun sendEmail(
        account: MailAccountEntity,
        toAddress: String,
        subject: String,
        bodyText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanTo = toAddress.trim()
            require(cleanTo.contains("@")) { "收件人邮箱格式无效" }
            require(account.smtpHost.isNotBlank()) { "请填写 SMTP 服务器地址" }
            require(account.username.isNotBlank() && account.passwordOrAuthCode.isNotBlank()) { "请填写邮箱账号与授权码" }

            val directSsl = account.useSsl && account.smtpPort != 587
            openTlsSocket(account.smtpHost.trim(), account.smtpPort, directSsl).use { sock ->
                var reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                var writer = PrintWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8), true)

                fun readResp(): String {
                    val sb = StringBuilder()
                    while (true) {
                        val line = reader.readLine() ?: break
                        sb.appendLine(line)
                        if (line.length >= 4 && line[3] == ' ') break
                    }
                    return sb.toString().trim()
                }

                fun expectCode(resp: String, expectedPrefix: String, step: String) {
                    if (!resp.lines().lastOrNull().orEmpty().startsWith(expectedPrefix)) {
                        throw IllegalStateException("SMTP $step 失败：${resp.take(120)}")
                    }
                }

                expectCode(readResp(), "220", "连接")
                writer.print("EHLO penos.local\r\n"); writer.flush()
                val ehlo = readResp()
                expectCode(ehlo, "250", "EHLO")

                // Support STARTTLS on port 587 (e.g. Outlook)
                if (!directSsl && account.useSsl && ehlo.contains("STARTTLS", ignoreCase = true)) {
                    writer.print("STARTTLS\r\n"); writer.flush()
                    expectCode(readResp(), "220", "STARTTLS")
                    val sslCtx = SSLContext.getInstance("TLSv1.2").apply { init(null, null, null) }
                    val upgraded = sslCtx.socketFactory.createSocket(sock, account.smtpHost.trim(), account.smtpPort, true) as SSLSocket
                    upgraded.startHandshake()
                    reader = BufferedReader(InputStreamReader(upgraded.getInputStream(), Charsets.UTF_8))
                    writer = PrintWriter(OutputStreamWriter(upgraded.getOutputStream(), Charsets.UTF_8), true)
                    writer.print("EHLO penos.local\r\n"); writer.flush()
                    expectCode(readResp(), "250", "EHLO-TLS")
                }

                writer.print("AUTH LOGIN\r\n"); writer.flush()
                expectCode(readResp(), "334", "AUTH")
                writer.print(Base64.encodeToString(account.username.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) + "\r\n"); writer.flush()
                expectCode(readResp(), "334", "用户名")
                writer.print(Base64.encodeToString(account.passwordOrAuthCode.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) + "\r\n"); writer.flush()
                expectCode(readResp(), "235", "认证")

                val fromAddr = account.emailAddress.ifBlank { account.username }.trim()
                writer.print("MAIL FROM:<$fromAddr>\r\n"); writer.flush()
                expectCode(readResp(), "250", "发件人")
                writer.print("RCPT TO:<$cleanTo>\r\n"); writer.flush()
                expectCode(readResp(), "250", "收件人")
                writer.print("DATA\r\n"); writer.flush()
                expectCode(readResp(), "354", "DATA")

                val encodedSubj = "=?UTF-8?B?${Base64.encodeToString(subject.ifBlank { "(无主题)" }.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}?="
                val encodedBody = Base64.encodeToString(bodyText.toByteArray(Charsets.UTF_8), Base64.DEFAULT).trim()
                val rfcDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(Date())

                val mime = buildString {
                    append("From: <$fromAddr>\r\n")
                    append("To: <$cleanTo>\r\n")
                    append("Subject: $encodedSubj\r\n")
                    append("Date: $rfcDate\r\n")
                    append("MIME-Version: 1.0\r\n")
                    append("Content-Type: text/plain; charset=\"UTF-8\"\r\n")
                    append("Content-Transfer-Encoding: base64\r\n")
                    append("\r\n")
                    append(encodedBody.replace("\n", "\r\n"))
                    append("\r\n.\r\n")
                }
                writer.print(mime); writer.flush()
                expectCode(readResp(), "250", "投递")
                writer.print("QUIT\r\n"); writer.flush()
            }
            "邮件已发送至 $cleanTo"
        }
    }

    private fun parseImapFetchResponse(accountId: String, lines: List<String>): List<MailMessageEntity> {
        val blocks = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null
        for (line in lines) {
            if (line.startsWith("* ") && line.contains("FETCH", ignoreCase = true)) {
                current = mutableListOf(line)
                blocks.add(current)
            } else if (line.startsWith("A3 ")) {
                break
            } else {
                current?.add(line)
            }
        }

        val now = System.currentTimeMillis()
        val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

        return blocks.mapIndexedNotNull { idx, block ->
            val joined = block.joinToString("\n")
            val uid = Regex("UID\\s+(\\d+)", RegexOption.IGNORE_CASE).find(joined)?.groupValues?.get(1) ?: "seq_$idx"
            val isUnread = !joined.contains("\\Seen", ignoreCase = true)

            var fromRaw = ""
            var toRaw = ""
            var subjRaw = ""
            var dateRaw = ""
            val bodyLines = mutableListOf<String>()
            var inBodyText = false
            var lastHeader = ""

            for (i in 1 until block.size) {
                val l = block[i]
                if (!inBodyText && l.contains("BODY[TEXT]", ignoreCase = true)) {
                    inBodyText = true
                    continue
                }
                if (inBodyText) {
                    if (l.trim() == ")") continue
                    bodyLines.add(l)
                } else {
                    when {
                        l.startsWith("From:", true) -> { fromRaw = l.substringAfter(":").trim(); lastHeader = "from" }
                        l.startsWith("To:", true) -> { toRaw = l.substringAfter(":").trim(); lastHeader = "to" }
                        l.startsWith("Subject:", true) -> { subjRaw = l.substringAfter(":").trim(); lastHeader = "subj" }
                        l.startsWith("Date:", true) -> { dateRaw = l.substringAfter(":").trim(); lastHeader = "date" }
                        (l.startsWith(" ") || l.startsWith("\t")) && lastHeader.isNotBlank() -> {
                            val cont = l.trim()
                            when (lastHeader) {
                                "from" -> fromRaw += " $cont"
                                "to" -> toRaw += " $cont"
                                "subj" -> subjRaw += cont
                                "date" -> dateRaw += " $cont"
                            }
                        }
                    }
                }
            }

            val decodedFrom = decodeRfc2047(fromRaw).ifBlank { "未知发件人" }
            val decodedTo = decodeRfc2047(toRaw)
            val decodedSubject = decodeRfc2047(subjRaw).ifBlank { "(无主题)" }
            val cleanBody = extractReadableBody(bodyLines.joinToString("\n"))
            val snippet = cleanBody.replace(Regex("\\s+"), " ").take(96).ifBlank { decodedSubject }
            val dateShort = formatShortDate(dateRaw).ifBlank { timeFmt.format(Date(now - idx * 60_000L)) }

            MailMessageEntity(
                id = "$accountId:$uid",
                accountId = accountId,
                uid = uid,
                folder = "INBOX",
                fromAddress = decodedFrom,
                toAddress = decodedTo,
                subject = decodedSubject,
                snippet = snippet,
                bodyText = cleanBody.ifBlank { "（该邮件为纯附件或加密 HTML 正文，建议在网页端查看完整格式）" },
                dateLabel = dateShort,
                timestampMs = now - (blocks.size - idx) * 1000L,
                isUnread = isUnread
            )
        }
    }

    internal fun decodeRfc2047(input: String): String {
        if (input.isBlank()) return ""
        val regex = Regex("=\\?([^?]+)\\?([BQ])\\?([^?]+)\\?=", RegexOption.IGNORE_CASE)
        return regex.replace(input) { m ->
            val charsetName = m.groupValues[1]
            val encoding = m.groupValues[2].uppercase(Locale.US)
            val payload = m.groupValues[3]
            val cs = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
            runCatching {
                when (encoding) {
                    "B" -> String(Base64.decode(payload, Base64.DEFAULT), cs)
                    "Q" -> String(decodeQuotedPrintable(payload.replace('_', ' ')), cs)
                    else -> m.value
                }
            }.getOrDefault(m.value)
        }.trim()
    }

    private fun decodeQuotedPrintable(input: String): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '=' && i + 2 < input.length) {
                val hex = input.substring(i + 1, i + 3)
                if (hex == "\r\n") {
                    i += 3
                    continue
                }
                val b = hex.toIntOrNull(16)
                if (b != null) {
                    out.write(b)
                    i += 3
                    continue
                }
            }
            out.write(c.code)
            i++
        }
        return out.toByteArray()
    }

    private fun extractReadableBody(raw: String): String {
        if (raw.isBlank()) return ""
        // Strip basic HTML tags & decode UTF-8 if raw came in ISO-8859-1 bytes
        val utf8Candidate = runCatching {
            String(raw.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        }.getOrDefault(raw)
        val noHeaders = utf8Candidate.lines()
            .dropWhile { it.startsWith("--") || it.startsWith("Content-", true) || it.isBlank() }
            .joinToString("\n")
        val maybeBase64 = noHeaders.trim().takeWhile { !it.isWhitespace() || it == '\n' || it == '\r' }
        val decoded = if (maybeBase64.length > 24 && maybeBase64.all { it.isLetterOrDigit() || it in "+/=\r\n" }) {
            runCatching { String(Base64.decode(maybeBase64, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(noHeaders)
        } else if (noHeaders.contains("=E") || noHeaders.contains("=3D")) {
            runCatching { String(decodeQuotedPrintable(noHeaders), Charsets.UTF_8) }.getOrDefault(noHeaders)
        } else {
            noHeaders
        }
        return decoded
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .take(1800)
    }

    private fun formatShortDate(raw: String): String {
        if (raw.isBlank()) return ""
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss Z"
        )
        for (p in patterns) {
            val parsed = runCatching { SimpleDateFormat(p, Locale.US).parse(raw.substringBefore("(").trim()) }.getOrNull()
            if (parsed != null) return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(parsed)
        }
        return raw.take(16)
    }
}
