package com.kaizen.archive.ui

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaizen.archive.KaizenArchiveApp
import com.kaizen.archive.data.ChatEntity
import com.kaizen.archive.data.MessageEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArchiveViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as KaizenArchiveApp).repository

    val chats: StateFlow<List<ChatEntity>> = repository.chats.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val deletedChats: StateFlow<List<ChatEntity>> = repository.deletedChats.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val deletedMessages: StateFlow<List<MessageEntity>> = repository.deletedMessages.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val captureEnabled: StateFlow<Boolean> = repository.captureEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), false,
    )
    val chatCount: StateFlow<Int> = repository.chatCount.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0,
    )
    val messageCount: StateFlow<Int> = repository.messageCount.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0,
    )

    var notificationAccessEnabled = false
        private set

    init {
        viewModelScope.launch { repository.purgeExpiredTrash() }
    }

    fun refreshNotificationAccess(context: Context) {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        notificationAccessEnabled = enabled.split(":").any {
            it.contains(context.packageName) && it.contains("WhatsAppNotificationListener")
        }
    }

    fun messages(chatId: Long) = repository.messagesForChat(chatId)
    fun search(query: String) = repository.searchMessages(query)

    fun setCaptureEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setCaptureEnabled(enabled)
    }

    fun setChatCaptureEnabled(chatId: Long, enabled: Boolean) = viewModelScope.launch {
        repository.setChatCaptureEnabled(chatId, enabled)
    }

    fun deleteMessage(id: Long) = viewModelScope.launch { repository.deleteMessage(id) }
    fun restoreMessage(id: Long) = viewModelScope.launch { repository.restoreMessage(id) }
    fun permanentlyDeleteMessage(id: Long) = viewModelScope.launch { repository.permanentlyDeleteMessage(id) }
    fun clearChat(id: Long) = viewModelScope.launch { repository.clearChat(id) }
    fun deleteChat(id: Long) = viewModelScope.launch { repository.deleteChat(id) }
    fun restoreChat(id: Long) = viewModelScope.launch { repository.restoreChat(id) }
    fun permanentlyDeleteChat(id: Long) = viewModelScope.launch { repository.permanentlyDeleteChat(id) }
    fun emptyTrash() = viewModelScope.launch { repository.emptyTrash() }
    fun softDeleteAllActiveChats() = viewModelScope.launch { repository.softDeleteAllActiveChats() }

    fun importTxt(uri: android.net.Uri, chatName: String, onResult: (Int) -> Unit) = viewModelScope.launch {
        val count = runCatching { repository.importTxt(uri, chatName) }.getOrDefault(0)
        onResult(count)
    }
}
