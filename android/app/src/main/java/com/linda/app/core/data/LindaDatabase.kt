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
    version = 8, // 8: M-Pesa memory (features/learning); 7: blocked numbers stored as hashes (contract 5.11)
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
        /** 7 -> 8 only adds the M-Pesa memory table, so History and everything else survive the update. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mpesa_receipts` (`codeHash` TEXT NOT NULL, `at` INTEGER NOT NULL, " +
                        "`amountCents` INTEGER NOT NULL, PRIMARY KEY(`codeHash`))",
                )
            }
        }

        fun create(context: Context): LindaDatabase =
            Room.databaseBuilder(context.applicationContext, LindaDatabase::class.java, "linda.db")
                .addMigrations(MIGRATION_7_8)
                // Prototype: for any other (older) schema, history is disposable, so a change wipes it
                // instead of crashing phones that have an older build installed.
                .fallbackToDestructiveMigration()
                .build()
    }
}
