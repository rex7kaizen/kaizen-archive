package com.kaizen.archive.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChatEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ArchiveDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var instance: ArchiveDatabase? = null

        fun getInstance(context: Context): ArchiveDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArchiveDatabase::class.java,
                    "kaizen_archive.db",
                ).build().also { instance = it }
            }
    }
}
