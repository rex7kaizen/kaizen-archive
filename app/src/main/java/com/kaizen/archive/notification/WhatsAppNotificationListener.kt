package com.kaizen.archive.notification

import android.app.Notification
import androidx.core.app.NotificationCompat
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kaizen.archive.KaizenArchiveApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * Captures only message text exposed in WhatsApp notifications.
 * It does not read WhatsApp databases, backups, or encrypted private files.
 */
class WhatsAppNotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var connected = false
    // Start paused until DataStore has delivered the user's saved preference.
    @Volatile private var captureEnabled = false
    private var preferenceJob: Job? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        val app = application as KaizenArchiveApp
        preferenceJob?.cancel()
        preferenceJob = serviceScope.launch {
            app.capturePreferences.captureEnabled.collectLatest { enabled ->
                captureEnabled = enabled
            }
        }
    }

    override fun onListenerDisconnected() {
        connected = false
        captureEnabled = false
        preferenceJob?.cancel()
        preferenceJob = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!connected || !captureEnabled) return
        if (sbn.packageName !in setOf("com.whatsapp", "com.whatsapp.w4b")) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val fallbackTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val chatTitle = style?.conversationTitle?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackTitle?.takeIf { it.isNotBlank() }
            ?: "WhatsApp sohbeti"

        val incoming = style?.messages.orEmpty().mapNotNull { message ->
            val body = message.text?.toString()?.trim().orEmpty()
            if (body.isBlank()) null else Triple(
                message.person?.name?.toString()?.takeIf { it.isNotBlank() }
                    ?: fallbackTitle?.takeIf { it.isNotBlank() }
                    ?: chatTitle,
                body,
                message.timestamp,
            )
        }

        val messages = if (incoming.isNotEmpty()) incoming else {
            val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
            if (body.isBlank()) emptyList() else listOf(
                Triple(fallbackTitle ?: chatTitle, body, sbn.postTime),
            )
        }

        if (messages.isEmpty()) return
        val repository = (application as KaizenArchiveApp).repository
        serviceScope.launch {
            messages.forEach { (sender, body, timestamp) ->
                repository.recordIncomingMessage(
                    chatTitle = chatTitle,
                    sender = sender,
                    text = body,
                    timestamp = timestamp,
                    sourcePackage = sbn.packageName,
                )
            }
        }
    }

    override fun onDestroy() {
        connected = false
        captureEnabled = false
        preferenceJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
