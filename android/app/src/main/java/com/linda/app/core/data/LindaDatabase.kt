package com.linda.app.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DetectionEntity::class, AllowedSenderEntity::class, SeenSenderEntity::class, ReportQueueEntity::class, BlockedNumberEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class LindaDatabase : RoomDatabase() {
    abstract fun detectionDao(): DetectionDao
    abstract fun senderDao(): SenderDao
    abstract fun reportQueueDao(): ReportQueueDao
    abstract fun blockedNumberDao(): BlockedNumberDao

    companion object {
        fun create(context: Context): LindaDatabase =
            Room.databaseBuilder(context.applicationContext, LindaDatabase::class.java, "linda.db")
                // Prototype: history is disposable, so a schema change wipes it
                // instead of crashing phones that have an older build installed.
                .fallbackToDestructiveMigration()
                .build()
    }
}
