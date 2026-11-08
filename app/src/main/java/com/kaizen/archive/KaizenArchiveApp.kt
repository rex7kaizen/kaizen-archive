package com.kaizen.archive

import android.app.Application
import com.kaizen.archive.data.ArchiveDatabase
import com.kaizen.archive.data.ArchiveRepository
import com.kaizen.archive.data.CapturePreferences

class KaizenArchiveApp : Application() {
    val database: ArchiveDatabase by lazy { ArchiveDatabase.getInstance(this) }
    val capturePreferences: CapturePreferences by lazy { CapturePreferences(this) }
    val repository: ArchiveRepository by lazy {
        ArchiveRepository(
            chatDao = database.chatDao(),
            messageDao = database.messageDao(),
            capturePreferences = capturePreferences,
            contentResolver = contentResolver,
        )
    }
}
