package com.example.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val bookId: String, // middle, highschool, cet4, cet6, daily, custom
    val accentHex: Long,
    val rootBreakdown: String = "",
    val visualCue: String = ""
)

@Immutable
@Entity(tableName = "vocab_progress")
data class VocabProgressEntity(
    @PrimaryKey val wordId: String,
    val masteryLevel: Int = 0, // 0=new, 1=learning, 2=familiar, 3=killed/mastered
    val wrongCount: Int = 0,
    val lastReviewedAt: Long = 0L,
    val nextReviewAt: Long = 0L
)
