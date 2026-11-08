package com.kaizen.archive.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE deletedAt IS NULL ORDER BY lastMessageAt DESC")
    fun observeActiveChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeletedChats(): Flow<List<ChatEntity>>

    @Query("SELECT COUNT(*) FROM chats WHERE deletedAt IS NULL")
    fun observeActiveChatCount(): Flow<Int>

    @Query("SELECT * FROM chats WHERE chatKey = :chatKey LIMIT 1")
    suspend fun findByKey(chatKey: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun findById(chatId: Long): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(chat: ChatEntity): Long

    @Update
    suspend fun update(chat: ChatEntity)

    @Query("UPDATE chats SET lastMessage = :text, lastMessageAt = :timestamp WHERE id = :chatId")
    suspend fun updatePreview(chatId: Long, text: String, timestamp: Long)

    @Query("UPDATE chats SET captureEnabled = :enabled WHERE id = :chatId")
    suspend fun setCaptureEnabled(chatId: Long, enabled: Boolean)

    @Query("UPDATE chats SET deletedAt = :deletedAt WHERE id = :chatId")
    suspend fun moveToTrash(chatId: Long, deletedAt: Long)

    @Query("UPDATE chats SET deletedAt = NULL WHERE id = :chatId")
    suspend fun restore(chatId: Long)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun permanentlyDelete(chatId: Long)

    @Query("DELETE FROM chats WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("DELETE FROM chats")
    suspend fun deleteAll()

    @Query("DELETE FROM chats WHERE deletedAt < :cutoff")
    suspend fun deleteExpired(cutoff: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND deletedAt IS NULL ORDER BY timestamp ASC")
    fun observeChatMessages(chatId: Long): Flow<List<MessageEntity>>

    @Query("""
        SELECT messages.* FROM messages
        INNER JOIN chats ON chats.id = messages.chatId
        WHERE messages.deletedAt IS NOT NULL AND chats.deletedAt IS NULL
        ORDER BY messages.deletedAt DESC
    """)
    fun observeDeletedMessages(): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE deletedAt IS NULL AND chatId IN (SELECT id FROM chats WHERE deletedAt IS NULL)")
    fun observeActiveMessageCount(): Flow<Int>

    @Query("""
        SELECT messages.* FROM messages
        INNER JOIN chats ON chats.id = messages.chatId
        WHERE messages.deletedAt IS NULL
          AND chats.deletedAt IS NULL
          AND (
            messages.text LIKE '%' || :query || '%'
            OR messages.sender LIKE '%' || :query || '%'
            OR chats.title LIKE '%' || :query || '%'
          )
        ORDER BY messages.timestamp DESC
        LIMIT 200
    """)
    fun searchMessages(query: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun findById(messageId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND deletedAt IS NULL ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestActiveMessage(chatId: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Query("UPDATE messages SET deletedAt = :deletedAt WHERE id = :messageId")
    suspend fun moveToTrash(messageId: Long, deletedAt: Long)

    @Query("UPDATE messages SET deletedAt = NULL WHERE id = :messageId")
    suspend fun restore(messageId: Long)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun permanentlyDelete(messageId: Long)

    @Query("UPDATE messages SET deletedAt = :deletedAt WHERE chatId = :chatId AND deletedAt IS NULL")
    suspend fun moveChatMessagesToTrash(chatId: Long, deletedAt: Long)

    @Query("DELETE FROM messages WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("DELETE FROM messages WHERE deletedAt < :cutoff")
    suspend fun deleteExpired(cutoff: Long)
}
