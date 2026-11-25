package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabDao {
    @Query("SELECT * FROM vocab_words WHERE bookId = :bookId ORDER BY id ASC")
    fun observeWordsByBook(bookId: String): Flow<List<VocabWordEntity>>

    @Query("SELECT * FROM vocab_words ORDER BY id ASC")
    suspend fun getAllWords(): List<VocabWordEntity>

    @Query("SELECT COUNT(*) FROM vocab_words")
    suspend fun countWords(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<VocabWordEntity>)

    @Query("SELECT * FROM vocab_progress")
    fun observeAllProgress(): Flow<List<VocabProgressEntity>>

    @Query("SELECT * FROM vocab_progress")
    suspend fun getAllProgress(): List<VocabProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: VocabProgressEntity)

    @Query("SELECT COUNT(*) FROM vocab_progress WHERE lastReviewedAt >= :dayStartMs")
    fun observeReviewedSince(dayStartMs: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM vocab_progress WHERE masteryLevel >= 3")
    fun observeMasteredCount(): Flow<Int>
}
