package com.linda.app.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DetectionEntity::class, AllowedSenderEntity::class, SeenSenderEntity::class, ReportQueueEntity::class, BlockedNumberEntity::class, GuardianAlertEntity::class,
        MpesaReceiptEntity::class],
    version = 9, // 9: sender counts; 8: M-Pesa memory (features/learning); 7: blocked numbers stored as hashes (5.11)
    exportSchema = false,
)
abstract class LindaDatabase : RoomDatabase() {
    abstract fun detectionDao(): DetectionDao
    abstract fun senderDao(): SenderDao
    abstract fun reportQueueDao(): ReportQueueDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun guardianAlertDao(): GuardianAlertDao
    abstract fun learningDao(): LearningDao

    companion object {
        /** 7 -> 8 only adds the M-Pesa memory table, so History and everything else survive the update. (7 -> 9 runs both.) */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mpesa_receipts` (`codeHash` TEXT NOT NULL, `at` INTEGER NOT NULL, " +
                        "`amountCents` INTEGER NOT NULL, PRIMARY KEY(`codeHash`))",
                )
            }
        }

        /** 8 -> 9 only adds two counters to the senders Linda has seen (features/learning, "know your senders"). */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `seen_senders` ADD COLUMN `count` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `seen_senders` ADD COLUMN `warned` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): LindaDatabase =
            Room.databaseBuilder(context.applicationContext, LindaDatabase::class.java, "linda.db")
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
                // Prototype: for any other (older) schema, history is disposable, so a change wipes it
                // instead of crashing phones that have an older build installed.
                .fallbackToDestructiveMigration()
                .build()
    }
}
