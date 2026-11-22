package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VocabWordEntity::class,
        VocabProgressEntity::class,
        CachedPoemEntity::class,
        TodoItemEntity::class,
        CalendarEventEntity::class,
        CachedWeatherEntity::class,
        CustomWidgetEntity::class,
        DrawerSlotEntity::class,
        MailAccountEntity::class,
        MailMessageEntity::class,
        DesktopSnapshotEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class PenDatabase : RoomDatabase() {
    abstract fun vocabDao(): VocabDao
    abstract fun poemDao(): PoemDao
    abstract fun todoDao(): TodoDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun weatherDao(): WeatherDao
    abstract fun customWidgetDao(): CustomWidgetDao
    abstract fun drawerSlotDao(): DrawerSlotDao
    abstract fun mailDao(): MailDao
    abstract fun desktopSnapshotDao(): DesktopSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: PenDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `todo_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `isDone` INTEGER NOT NULL,
                        `dueDateIso` TEXT NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_weather` (
                        `id` TEXT NOT NULL,
                        `cityName` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `tempC` REAL NOT NULL,
                        `humidity` INTEGER NOT NULL,
                        `windKmh` REAL NOT NULL,
                        `weatherCode` INTEGER NOT NULL,
                        `tempMaxC` REAL NOT NULL,
                        `tempMinC` REAL NOT NULL,
                        `dailySummary` TEXT NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `calendar_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dateIso` TEXT NOT NULL,
                        `timeHHmm` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE `vocab_words` ADD COLUMN `rootBreakdown` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `vocab_words` ADD COLUMN `visualCue` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_widgets` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `placement` TEXT NOT NULL,
                        `widthDp` INTEGER NOT NULL,
                        `accentHex` INTEGER NOT NULL,
                        `dslJson` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drawer_slots` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `refId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `subtitle` TEXT NOT NULL,
                        `accentHex` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mail_accounts` (
                        `id` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `emailAddress` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `passwordOrAuthCode` TEXT NOT NULL,
                        `imapHost` TEXT NOT NULL,
                        `imapPort` INTEGER NOT NULL,
                        `smtpHost` TEXT NOT NULL,
                        `smtpPort` INTEGER NOT NULL,
                        `useSsl` INTEGER NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `lastSyncedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mail_messages` (
                        `id` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `uid` TEXT NOT NULL,
                        `folder` TEXT NOT NULL,
                        `fromAddress` TEXT NOT NULL,
                        `toAddress` TEXT NOT NULL,
                        `subject` TEXT NOT NULL,
                        `snippet` TEXT NOT NULL,
                        `bodyText` TEXT NOT NULL,
                        `dateLabel` TEXT NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `isUnread` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `desktop_snapshots` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `label` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): PenDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PenDatabase::class.java,
                    "penos_launcher.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
