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
        CachedWeatherEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PenDatabase : RoomDatabase() {
    abstract fun vocabDao(): VocabDao
    abstract fun poemDao(): PoemDao
    abstract fun todoDao(): TodoDao
    abstract fun weatherDao(): WeatherDao

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

        fun getInstance(context: Context): PenDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PenDatabase::class.java,
                    "penos_launcher.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
