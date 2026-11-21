package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabDao {
    @Transaction
    @Query("SELECT * FROM vocab_words ORDER BY id ASC")
    fun observeAllWordsWithProgress(): Flow<List<VocabWordWithProgress>>

    @Transaction
    @Query("SELECT * FROM vocab_words WHERE bookId = :bookId ORDER BY id ASC")
    fun observeWordsByBook(bookId: String): Flow<List<VocabWordWithProgress>>

    @Query("SELECT COUNT(*) FROM vocab_words")
    suspend fun getWordCount(): Int

    @Query("SELECT * FROM vocab_progress")
    suspend fun getAllProgressSnapshot(): List<VocabProgressEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWords(words: List<VocabWordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: VocabProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllProgress(progressList: List<VocabProgressEntity>)

    @Query("DELETE FROM vocab_progress")
    suspend fun clearAllProgress()
}

@Dao
interface PoemDao {
    @Query("SELECT * FROM cached_poems ORDER BY fetchedAt DESC LIMIT 1")
    fun observeLatestPoem(): Flow<CachedPoemEntity?>

    @Query("SELECT * FROM cached_poems ORDER BY fetchedAt DESC LIMIT 20")
    suspend fun getCachedPoems(): List<CachedPoemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoem(poem: CachedPoemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPoems(poems: List<CachedPoemEntity>)

    @Query("SELECT COUNT(*) FROM cached_poems")
    suspend fun getPoemCount(): Int
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_items ORDER BY isDone ASC, priority DESC, createdAt DESC")
    fun observeAllTodos(): Flow<List<TodoItemEntity>>

    @Query("SELECT COUNT(*) FROM todo_items")
    suspend fun getTodoCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: TodoItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTodos(todos: List<TodoItemEntity>)

    @Query("UPDATE todo_items SET isDone = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM todo_items WHERE isDone = 1")
    suspend fun clearCompleted()
}

@Dao
interface WeatherDao {
    @Query("SELECT * FROM cached_weather WHERE id = 'current' LIMIT 1")
    fun observeCurrentWeather(): Flow<CachedWeatherEntity?>

    @Query("SELECT * FROM cached_weather WHERE id = 'current' LIMIT 1")
    suspend fun getCurrentWeather(): CachedWeatherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWeather(weather: CachedWeatherEntity)
}
