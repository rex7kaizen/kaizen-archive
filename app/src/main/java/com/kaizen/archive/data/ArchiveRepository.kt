package com.kaizen.archive.data

import android.content.ContentResolver
import android.net.Uri
import com.kaizen.archive.importer.ParsedExportMessage
import com.kaizen.archive.importer.WhatsAppTxtParser
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ArchiveRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val capturePreferences: CapturePreferences,
    private val contentResolver: ContentResolver,
) {
    val chats: Flow<List<ChatEntity>> = chatDao.observeActiveChats()
    val deletedChats: Flow<List<ChatEntity>> = chatDao.observeDeletedChats()
    val deletedMessages: Flow<List<MessageEntity>> = messageDao.observeDeletedMessages()
    val captureEnabled: Flow<Boolean> = capturePreferences.captureEnabled
    val chatCount: Flow<Int> = chatDao.observeActiveChatCount()
    val messageCount: Flow<Int> = messageDao.observeActiveMessageCount()

    fun messagesForChat(chatId: Long): Flow<List<MessageEntity>> =
        messageDao.observeChatMessages(chatId)

    fun searchMessages(query: String): Flow<List<MessageEntity>> =
        if (query.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else messageDao.searchMessages(query.trim())

    suspend fun setCaptureEnabled(enabled: Boolean) =
        capturePreferences.setCaptureEnabled(enabled)

    suspend fun setChatCaptureEnabled(chatId: Long, enabled: Boolean) =
        chatDao.setCaptureEnabled(chatId, enabled)

    suspend fun recordIncomingMessage(
        chatTitle: String,
        sender: String,
        text: String,
        timestamp: Long,
        sourcePackage: String,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!capturePreferences.captureEnabled.first()) return@withContext false
        val cleanTitle = chatTitle.trim().ifBlank { "WhatsApp sohbeti" }
        val key = "${sourcePackage}:${normalizeChatKey(cleanTitle)}"
        var chat = chatDao.findByKey(key)
        if (chat == null) {
            chatDao.insert(
                ChatEntity(
                    chatKey = key,
                    title = cleanTitle,
                    lastMessage = text,
                    lastMessageAt = timestamp,
                ),
            )
            chat = chatDao.findByKey(key)
        }
        val existing = chat ?: return@withContext false
        if (existing.deletedAt != null) chatDao.restore(existing.id)
        val activeChat = chatDao.findById(existing.id) ?: existing
        if (!activeChat.captureEnabled) return@withContext false

        val sourceKey = sha256("$sourcePackage|${key}|$sender|$timestamp|$text")
        val inserted = messageDao.insert(
            MessageEntity(
                chatId = activeChat.id,
                sender = sender.ifBlank { cleanTitle },
                text = text,
                timestamp = timestamp,
                savedAt = System.currentTimeMillis(),
                source = "LIVE_NOTIFICATION",
                sourceKey = sourceKey,
            ),
        )
        if (inserted != -1L) chatDao.updatePreview(activeChat.id, text, timestamp)
        inserted != -1L
    }

    suspend fun importMessages(chatTitle: String, parsed: List<ParsedExportMessage>): Int =
        withContext(Dispatchers.IO) {
            if (parsed.isEmpty()) return@withContext 0
            val title = chatTitle.trim().ifBlank { "İçe aktarılan sohbet" }
            val key = "import:${normalizeChatKey(title)}"
            var chat = chatDao.findByKey(key)
            if (chat == null) {
                chatDao.insert(
                    ChatEntity(
                        chatKey = key,
                        title = title,
                        lastMessage = parsed.last().text,
                        lastMessageAt = parsed.last().timestamp,
                    ),
                )
                chat = chatDao.findByKey(key)
            }
            val target = chat ?: return@withContext 0
            if (target.deletedAt != null) chatDao.restore(target.id)
            val result = parsed.map { item ->
                MessageEntity(
                    chatId = target.id,
                    sender = item.sender,
                    text = item.text,
                    timestamp = item.timestamp,
                    savedAt = System.currentTimeMillis(),
                    source = "TXT_IMPORT",
                    sourceKey = sha256("TXT|$key|${item.sender}|${item.timestamp}|${item.text}"),
                )
            }
            var insertedCount = 0
            result.forEach { if (messageDao.insert(it) != -1L) insertedCount++ }
            val latest = parsed.maxByOrNull { it.timestamp }
            if (latest != null) chatDao.updatePreview(target.id, latest.text, latest.timestamp)
            insertedCount
        }

    suspend fun importTxt(uri: Uri, chatTitle: String): Int = withContext(Dispatchers.IO) {
        val stream = contentResolver.openInputStream(uri) ?: return@withContext 0
        val parsed = stream.bufferedReader(Charsets.UTF_8).use(WhatsAppTxtParser()::parse)
        importMessages(chatTitle, parsed)
    }

    suspend fun deleteMessage(messageId: Long) {
        val message = messageDao.findById(messageId) ?: return
        messageDao.moveToTrash(messageId, System.currentTimeMillis())
        refreshChatPreview(message.chatId)
    }

    suspend fun restoreMessage(messageId: Long) {
        val message = messageDao.findById(messageId) ?: return
        messageDao.restore(messageId)
        refreshChatPreview(message.chatId)
    }

    suspend fun permanentlyDeleteMessage(messageId: Long) = messageDao.permanentlyDelete(messageId)

    suspend fun clearChat(chatId: Long) {
        messageDao.moveChatMessagesToTrash(chatId, System.currentTimeMillis())
        chatDao.updatePreview(chatId, "Henüz mesaj yok", 0L)
    }

    private suspend fun refreshChatPreview(chatId: Long) {
        val latest = messageDao.latestActiveMessage(chatId)
        if (latest == null) {
            chatDao.updatePreview(chatId, "Henüz mesaj yok", 0L)
        } else {
            chatDao.updatePreview(chatId, latest.text, latest.timestamp)
        }
    }

    suspend fun deleteChat(chatId: Long) =
        chatDao.moveToTrash(chatId, System.currentTimeMillis())

    suspend fun restoreChat(chatId: Long) = chatDao.restore(chatId)
    suspend fun permanentlyDeleteChat(chatId: Long) = chatDao.permanentlyDelete(chatId)

    suspend fun emptyTrash() {
        chatDao.emptyTrash()
        messageDao.emptyTrash()
    }

    suspend fun softDeleteAllActiveChats() {
        val now = System.currentTimeMillis()
        chatsSnapshot().forEach { chatDao.moveToTrash(it.id, now) }
    }

    private suspend fun chatsSnapshot(): List<ChatEntity> =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            chatDao.observeActiveChats().first()
        }

    suspend fun purgeExpiredTrash(retentionDays: Int = 30) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
        chatDao.deleteExpired(cutoff)
        messageDao.deleteExpired(cutoff)
    }

    private fun normalizeChatKey(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

}
