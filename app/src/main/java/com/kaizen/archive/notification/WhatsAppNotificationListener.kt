package com.kaizen.archive.notification

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kaizen.archive.KaizenArchiveApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Captures only message text exposed by WhatsApp notifications.
 * It never reads WhatsApp databases, backups, or private app files.
 */
class WhatsAppNotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var connected = false

    // Stay paused until DataStore has emitted the persisted user preference.
    @Volatile
    private var captureEnabled = false

    private var preferenceJob: Job? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        Log.i(TAG, "Notification listener connected")

        val app = application as KaizenArchiveApp
        preferenceJob?.cancel()
        preferenceJob = serviceScope.launch {
            app.capturePreferences.captureEnabled.collectLatest { enabled ->
                captureEnabled = enabled
                Log.i(TAG, "Capture preference loaded: enabled=$enabled")
            }
        }
    }

    override fun onListenerDisconnected() {
        connected = false
        captureEnabled = false
        preferenceJob?.cancel()
        preferenceJob = null
        Log.w(TAG, "Notification listener disconnected; requesting rebind")

        // Android requires a rebind request if a disconnected listener should reconnect.
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(this, WhatsAppNotificationListener::class.java),
            )
        }.onFailure { error ->
            Log.e(TAG, "Could not request notification-listener rebind", error)
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val sourcePackage = sbn.packageName
        if (sourcePackage != WHATSAPP_PACKAGE && sourcePackage != WHATSAPP_BUSINESS_PACKAGE) return

        if (!connected) {
            Log.w(TAG, "WhatsApp notification arrived while listener was not connected")
            return
        }
        if (!captureEnabled) {
            Log.d(TAG, "WhatsApp notification ignored: capture switch is off")
            return
        }

        val notification = sbn.notification ?: return
        val extras = runCatching { notification.extras }.getOrNull()
        if (extras == null) {
            Log.w(TAG, "WhatsApp notification had no extras")
            return
        }

        val extracted = runCatching { extractMessages(sbn, notification, extras) }
            .onFailure { error -> Log.e(TAG, "Could not parse WhatsApp notification", error) }
            .getOrNull()

        if (extracted == null || extracted.messages.isEmpty()) {
            // Do not log message text; only metadata useful for diagnosing notification formats.
            Log.w(
                TAG,
                "WhatsApp notification had no readable message text; extras=${extras.keySet().sorted()} " +
                    "style=${runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification) != null }.getOrDefault(false)}",
            )
            return
        }

        val repository = (application as KaizenArchiveApp).repository
        serviceScope.launch {
            var savedCount = 0
            extracted.messages.forEach { message ->
                try {
                    val inserted = repository.recordIncomingMessage(
                        chatTitle = extracted.chatTitle,
                        sender = message.sender,
                        text = message.text,
                        timestamp = message.timestamp,
                        sourcePackage = sourcePackage,
                    )
                    if (inserted) savedCount++
                } catch (error: Exception) {
                    Log.e(TAG, "Database write failed for a WhatsApp notification", error)
                }
            }
            Log.i(
                TAG,
                "WhatsApp notification processed: parsed=${extracted.messages.size}, saved=$savedCount, " +
                    "package=$sourcePackage",
            )
        }
    }

    private fun extractMessages(
        sbn: StatusBarNotification,
        notification: Notification,
        extras: Bundle,
    ): ExtractedNotification {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val title = style?.conversationTitle?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.takeIf { it.isNotBlank() }
            ?: "WhatsApp sohbeti"

        val parsed = mutableListOf<CapturedMessage>()

        // Preferred path: AndroidX understands MessagingStyle notifications across API versions.
        style?.messages.orEmpty().forEach { message ->
            val body = message.text?.toString()?.trim().orEmpty()
            if (body.isNotBlank()) {
                parsed += CapturedMessage(
                    sender = message.person?.name?.toString()?.takeIf { it.isNotBlank() } ?: title,
                    text = body,
                    timestamp = message.timestamp.takeIf { it > 0L } ?: sbn.postTime,
                )
            }
        }

        // Fallback: some WhatsApp versions expose the MessagingStyle Bundle array directly.
        if (parsed.isEmpty()) {
            val rawMessages = runCatching {
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            }.getOrNull().orEmpty()

            rawMessages.forEach { item ->
                val bundle = item as? Bundle ?: return@forEach
                val body = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
                if (body.isBlank()) return@forEach

                val senderFromPerson = runCatching {
                    bundle.getBundle("sender_person")?.getCharSequence("name")?.toString()
                }.getOrNull()
                val sender = senderFromPerson?.takeIf { it.isNotBlank() }
                    ?: bundle.getCharSequence("sender")?.toString()?.takeIf { it.isNotBlank() }
                    ?: title
                val timestamp = bundle.getLong("time", sbn.postTime).takeIf { it > 0L } ?: sbn.postTime
                parsed += CapturedMessage(sender, body, timestamp)
            }
        }

        // Some notification layouts provide visible lines instead of MessagingStyle messages.
        if (parsed.isEmpty()) {
            val lines = runCatching {
                extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            }.getOrNull().orEmpty()

            lines.forEach { line ->
                val body = line?.toString()?.trim().orEmpty()
                if (body.isNotBlank()) parsed += CapturedMessage(title, body, sbn.postTime)
            }
        }

        // Last fallback for the ordinary collapsed/expanded notification layout.
        if (parsed.isEmpty()) {
            val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
            if (body.isNotBlank()) parsed += CapturedMessage(title, body, sbn.postTime)
        }

        // MessagingStyle and raw Bundle data can overlap; remove duplicates before writing.
        val uniqueMessages = parsed.distinctBy { "${it.sender}|${it.timestamp}|${it.text}" }
        return ExtractedNotification(chatTitle = title, messages = uniqueMessages)
    }

    override fun onDestroy() {
        connected = false
        captureEnabled = false
        preferenceJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private data class CapturedMessage(
        val sender: String,
        val text: String,
        val timestamp: Long,
    )

    private data class ExtractedNotification(
        val chatTitle: String,
        val messages: List<CapturedMessage>,
    )

    private companion object {
        const val TAG = "KaizenCapture"
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    }
}
