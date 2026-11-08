package com.kaizen.archive.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chats",
    indices = [Index(value = ["chatKey"], unique = true)],
)
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatKey: String,
    val title: String,
    val lastMessage: String,
    val lastMessageAt: Long,
    val captureEnabled: Boolean = true,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("chatId"),
        Index("timestamp"),
        Index(value = ["sourceKey"], unique = true),
        Index("deletedAt"),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val savedAt: Long,
    val source: String,
    val sourceKey: String,
    val deletedAt: Long? = null,
)
