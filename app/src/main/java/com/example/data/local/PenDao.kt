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
    suspend fun upsertWords(words: List<VocabWordEntity>)

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
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY dateIso ASC, timeHHmm ASC, createdAt ASC")
    fun observeAllEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT COUNT(*) FROM calendar_events")
    suspend fun getEventCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteById(id: Long)
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

@Dao
interface CustomWidgetDao {
    @Query("SELECT * FROM custom_widgets ORDER BY sortOrder ASC, updatedAt DESC")
    fun observeAllCustomWidgets(): Flow<List<CustomWidgetEntity>>

    @Query("SELECT * FROM custom_widgets ORDER BY sortOrder ASC, updatedAt DESC")
    suspend fun getAllSnapshot(): List<CustomWidgetEntity>

    @Query("SELECT * FROM custom_widgets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CustomWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(widget: CustomWidgetEntity)

    @Query("DELETE FROM custom_widgets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM custom_widgets")
    suspend fun getCount(): Int
}

@Dao
interface DrawerSlotDao {
    @Query("SELECT * FROM drawer_slots ORDER BY sortOrder ASC")
    fun observeAllSlots(): Flow<List<DrawerSlotEntity>>

    @Query("SELECT * FROM drawer_slots ORDER BY sortOrder ASC")
    suspend fun getAllSnapshot(): List<DrawerSlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(slot: DrawerSlotEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(slots: List<DrawerSlotEntity>)

    @Query("DELETE FROM drawer_slots WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM drawer_slots WHERE refId = :refId AND type = :type")
    suspend fun deleteByRef(type: String, refId: String)

    @Query("SELECT COUNT(*) FROM drawer_slots")
    suspend fun getCount(): Int
}

@Dao
interface MailDao {
    @Query("SELECT * FROM mail_accounts ORDER BY isDefault DESC, label ASC")
    fun observeAccounts(): Flow<List<MailAccountEntity>>

    @Query("SELECT * FROM mail_accounts ORDER BY isDefault DESC, label ASC")
    suspend fun getAccountsSnapshot(): List<MailAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: MailAccountEntity)

    @Query("DELETE FROM mail_accounts WHERE id = :id")
    suspend fun deleteAccount(id: String)

    @Query("DELETE FROM mail_messages WHERE accountId = :accountId")
    suspend fun deleteMessagesForAccount(accountId: String)

    @Query("SELECT * FROM mail_messages ORDER BY timestampMs DESC LIMIT 60")
    fun observeRecentMessages(): Flow<List<MailMessageEntity>>

    @Query("SELECT * FROM mail_messages ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecentMessagesSnapshot(limit: Int): List<MailMessageEntity>

    @Query("SELECT * FROM mail_messages WHERE accountId = :accountId ORDER BY timestampMs DESC LIMIT 40")
    suspend fun getMessagesByAccount(accountId: String): List<MailMessageEntity>

    @Query("SELECT * FROM mail_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MailMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<MailMessageEntity>)

    @Query("UPDATE mail_messages SET isUnread = :unread WHERE id = :id")
    suspend fun setUnread(id: String, unread: Boolean)

    @Query("UPDATE mail_accounts SET lastSyncedAt = :timestamp WHERE id = :accountId")
    suspend fun updateAccountSyncedAt(accountId: String, timestamp: Long)
}

@Dao
interface DesktopSnapshotDao {
    @Query("SELECT * FROM desktop_snapshots ORDER BY createdAt DESC LIMIT 10")
    fun observeRecentSnapshots(): Flow<List<DesktopSnapshotEntity>>

    @Query("SELECT * FROM desktop_snapshots ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestSnapshot(): DesktopSnapshotEntity?

    @Query("SELECT * FROM desktop_snapshots WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DesktopSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: DesktopSnapshotEntity): Long

    @Query("DELETE FROM desktop_snapshots WHERE id NOT IN (SELECT id FROM desktop_snapshots ORDER BY createdAt DESC LIMIT 10)")
    suspend fun trimToLatestTen()

    @Query("DELETE FROM desktop_snapshots WHERE id = :id")
    suspend fun deleteById(id: Long)
}

