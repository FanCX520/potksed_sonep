package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

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
    val accentHex: Long
)

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

data class VocabWordWithProgress(
    @Embedded val word: VocabWordEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "wordId"
    )
    val progress: VocabProgressEntity?
)

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

@Entity(tableName = "todo_items")
data class TodoItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val isDone: Boolean = false,
    val dueDateIso: String = "", // yyyy-MM-dd
    val priority: Int = 1, // 0=low, 1=normal, 2=high
    val createdAt: Long = System.currentTimeMillis()
)

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
    val dailySummary: String, // "明 晴 18°/8°|后 多云 16°/7°"
    val fetchedAt: Long
)
