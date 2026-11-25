package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VocabWordEntity::class,
        VocabProgressEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class PenDatabase : RoomDatabase() {
    abstract fun vocabDao(): VocabDao

    companion object {
        @Volatile
        private var INSTANCE: PenDatabase? = null

        fun getInstance(context: Context): PenDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PenDatabase::class.java,
                    "penos_launcher.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
