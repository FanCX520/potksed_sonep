package com.example.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Immutable
@Entity(tableName = "vocab_words")
data class VocabWordEntity(
    @PrimaryKey val id: String,
    val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val meaningZh: String,
    val mnemonicTag: String,
    val exampleEn: String,
    val exampleZh: String,
    val bookId: String,
    val accentHex: Long,
    val rootBreakdown: String = "",
    val visualCue: String = ""
)

@Immutable
@Entity(tableName = "vocab_progress")
data class VocabProgressEntity(
    @PrimaryKey val wordId: String,
    val status: String = STATUS_NEW,
    val familiarity: Int = 0,
    val nextReviewAt: Long = 0L,
    val wrongCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_NEW = "NEW"
        const val STATUS_LEARNING = "LEARNING"
        const val STATUS_MASTERED = "MASTERED"
    }
}

@Immutable
data class VocabWordWithProgress(
    @Embedded val word: VocabWordEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "wordId"
    )
    val progress: VocabProgressEntity?
)

@Immutable
@Entity(tableName = "cached_poems")
data class CachedPoemEntity(
    @PrimaryKey val id: String,
    val content: String,
    val title: String,
    val dynasty: String,
    val author: String,
    val fullContent: String,
    val fetchedAt: Long
)

@Immutable
@Entity(tableName = "todo_items")
data class TodoItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val isDone: Boolean = false,
    val dueDateIso: String = "", // yyyy-MM-dd
    val priority: Int = 1, // 0=low, 1=normal, 2=high
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateIso: String, // yyyy-MM-dd
    val timeHHmm: String, // HH:mm
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(tableName = "cached_weather")
data class CachedWeatherEntity(
    @PrimaryKey val id: String = "current",
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val tempC: Double,
    val humidity: Int,
    val windKmh: Double,
    val weatherCode: Int,
    val tempMaxC: Double,
    val tempMinC: Double,
    val dailySummary: String,
    val fetchedAt: Long
)

/**
 * PenOS 4.0 Declarative Custom Widget authored by AI Harness MCP or LAN Web Console.
 * Strictly data-driven JSON DSL (zero executable code / zero DEX).
 */
@Immutable
@Entity(tableName = "custom_widgets")
data class CustomWidgetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val placement: String = PLACEMENT_BOTH, // DESKTOP | DRAWER | BOTH
    val widthDp: Int = 188,
    val accentHex: Long = 0xFF38BDF8,
    val dslJson: String,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val PLACEMENT_DESKTOP = "DESKTOP"
        const val PLACEMENT_DRAWER = "DRAWER"
        const val PLACEMENT_BOTH = "BOTH"
    }
}

/**
 * Apple TV Up-Swipe Drawer custom slot item.
 * Types: CUSTOM_WIDGET | BUILTIN_CLOCK | BUILTIN_MAIL | AI_SWITCHER | APP_SHORTCUT | ACTION_TILE
 */
@Immutable
@Entity(tableName = "drawer_slots")
data class DrawerSlotEntity(
    @PrimaryKey val id: String,
    val type: String,
    val refId: String,
    val title: String,
    val subtitle: String = "",
    val accentHex: Long = 0xFF38BDF8,
    val sortOrder: Int = 0
) {
    companion object {
        const val TYPE_CUSTOM_WIDGET = "CUSTOM_WIDGET"
        const val TYPE_BUILTIN_CLOCK = "BUILTIN_CLOCK"
        const val TYPE_BUILTIN_MAIL = "BUILTIN_MAIL"
        const val TYPE_AI_SWITCHER = "AI_SWITCHER"
        const val TYPE_APP_SHORTCUT = "APP_SHORTCUT"
        const val TYPE_ACTION_TILE = "ACTION_TILE"
    }
}

/**
 * Multi-account IMAP/SMTP Email configuration stored strictly locally on device.
 */
@Immutable
@Entity(tableName = "mail_accounts")
data class MailAccountEntity(
    @PrimaryKey val id: String,
    val label: String,
    val emailAddress: String,
    val username: String,
    val passwordOrAuthCode: String,
    val imapHost: String,
    val imapPort: Int = 993,
    val smtpHost: String,
    val smtpPort: Int = 465,
    val useSsl: Boolean = true,
    val isDefault: Boolean = false,
    val lastSyncedAt: Long = 0L
)

/**
 * Local offline cache of fetched emails for instant reading & AI/MCP summarization.
 */
@Immutable
@Entity(tableName = "mail_messages")
data class MailMessageEntity(
    @PrimaryKey val id: String, // "$accountId:$uid"
    val accountId: String,
    val uid: String,
    val folder: String = "INBOX",
    val fromAddress: String,
    val toAddress: String,
    val subject: String,
    val snippet: String,
    val bodyText: String,
    val dateLabel: String,
    val timestampMs: Long,
    val isUnread: Boolean = true
)

/**
 * PenOS 5.0 Desktop Configuration Snapshot for instant Harness MCP undo/rollback
 * and full configuration backup/restore.
 */
@Immutable
@Entity(tableName = "desktop_snapshots")
data class DesktopSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val source: String = "MCP", // MCP | MANUAL | WEBDAV | LAN
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

