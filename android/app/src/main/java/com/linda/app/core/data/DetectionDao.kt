package com.linda.app.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionDao {
    @Insert
    suspend fun insert(detection: DetectionEntity): Long

    @Query("SELECT * FROM detections ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<DetectionEntity>>

    @Query("SELECT * FROM detections WHERE id = :id")
    suspend fun getById(id: Long): DetectionEntity?

    /** The id of an already saved message from this sender at this time, so scanning the inbox twice adds nothing twice. */
    @Query("SELECT id FROM detections WHERE receivedAt = :receivedAt AND sender IS :sender LIMIT 1")
    suspend fun findId(receivedAt: Long, sender: String?): Long?

    @Query("UPDATE detections SET reportedAt = :at WHERE id = :id")
    suspend fun markReported(id: Long, at: Long)

    @Query("UPDATE detections SET markedSafe = 1 WHERE id = :id")
    suspend fun markSafe(id: Long)

    /** Undo for "Mark as safe". */
    @Query("UPDATE detections SET markedSafe = 0 WHERE id = :id")
    suspend fun unmarkSafe(id: Long)

    /** Scams Linda caught LIVE since [since] (not ones found later by "Scan my inbox"), not counting ones marked safe. */
    @Query("SELECT COUNT(*) FROM detections WHERE level = 'SCAM' AND markedSafe = 0 AND source != 'inbox' AND receivedAt >= :since")
    fun countScamsSince(since: Long): Flow<Int>

    /** Was there a scam message from this phone number since [since]? Used by call warnings. */
    @Query("SELECT * FROM detections WHERE senderMsisdn = :msisdn AND level = 'SCAM' AND markedSafe = 0 AND receivedAt >= :since ORDER BY receivedAt DESC LIMIT 1")
    suspend fun latestScamFrom(msisdn: String, since: Long): DetectionEntity?
}

@Dao
interface SenderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun allow(sender: AllowedSenderEntity)

    @Query("SELECT sender FROM allowed_senders")
    suspend fun allAllowed(): List<String>

    @Query("SELECT COUNT(*) > 0 FROM allowed_senders WHERE sender = :sender")
    suspend fun isAllowed(sender: String): Boolean

    /** Undo for "Mark as safe": Linda checks this sender again. */
    @Query("DELETE FROM allowed_senders WHERE sender = :sender")
    suspend fun disallow(sender: String)

    /** "Forget everything Linda learned" also forgets the senders marked as safe. */
    @Query("DELETE FROM allowed_senders")
    suspend fun forgetAllAllowed()

    /** Returns -1 when the sender was already there (then use [countAgain]). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(sender: SeenSenderEntity): Long

    @Query("UPDATE seen_senders SET count = count + 1, warned = warned + :warned WHERE sender = :sender")
    suspend fun countAgain(sender: String, warned: Int)

    @Query("SELECT COUNT(*) > 0 FROM seen_senders WHERE sender = :sender")
    suspend fun hasSeen(sender: String): Boolean

    @Query("SELECT * FROM seen_senders WHERE sender = :sender")
    suspend fun find(sender: String): SeenSenderEntity?

    /** "Forget everything Linda learned" (features/learning). */
    @Query("DELETE FROM seen_senders")
    suspend fun forgetAll()
}

@Dao
interface ReportQueueDao {
    @Insert
    suspend fun insert(report: ReportQueueEntity): Long

    @Query("SELECT * FROM report_queue ORDER BY id LIMIT :limit")
    suspend fun pending(limit: Int): List<ReportQueueEntity>

    /** Automatic reports waiting when the person switches reporting off are dropped, not sent. Reports they confirmed by hand stay. */
    @Query("DELETE FROM report_queue WHERE userConfirmed = 0")
    suspend fun deleteUnconfirmed()

    @Query("DELETE FROM report_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE report_queue SET attempts = attempts + 1 WHERE id = :id")
    suspend fun countAttempt(id: Long)

    @Query("SELECT COUNT(*) FROM report_queue")
    fun observeCount(): Flow<Int>
}

@Dao
interface BlockedNumberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(numbers: List<BlockedNumberEntity>)

    @Query("DELETE FROM blocked_numbers WHERE hash IN (:hashes)")
    suspend fun deleteAll(hashes: List<String>)

    /** Pass PhoneHash.of(msisdn), never the number. */
    @Query("SELECT * FROM blocked_numbers WHERE hash = :hash")
    suspend fun find(hash: String): BlockedNumberEntity?

    @Query("SELECT COUNT(*) FROM blocked_numbers")
    fun observeCount(): Flow<Int>
}

@Dao
interface GuardianAlertDao {
    @Insert
    suspend fun insert(alert: GuardianAlertEntity): Long

    /** When the guardian was last told about this scammer. Failed sends and tests do not count against the limit. */
    @Query("SELECT MAX(sentAt) FROM guardian_alerts WHERE senderKey = :senderKey AND status = 'sent' AND isTest = 0")
    suspend fun lastSentAt(senderKey: String): Long?

    @Query("SELECT * FROM guardian_alerts ORDER BY sentAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<GuardianAlertEntity>>

    @Query("DELETE FROM guardian_alerts")
    suspend fun clearAll()
}

/** What Linda learns on this phone (features/learning). Nothing here ever leaves the phone. */
@Dao
interface LearningDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addReceipts(receipts: List<MpesaReceiptEntity>)

    @Query("SELECT * FROM mpesa_receipts WHERE at >= :since ORDER BY at DESC")
    suspend fun receiptsSince(since: Long): List<MpesaReceiptEntity>

    @Query("DELETE FROM mpesa_receipts WHERE at < :before")
    suspend fun forgetReceiptsBefore(before: Long)

    @Query("DELETE FROM mpesa_receipts")
    suspend fun forgetAllReceipts()

    @Query("SELECT COUNT(*) FROM mpesa_receipts")
    fun observeReceiptCount(): Flow<Int>

    // ---- The inbox story ----
    @Query("SELECT * FROM sender_stories WHERE sender = :sender")
    suspend fun story(sender: String): SenderStoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStory(story: SenderStoryEntity)

    @Query("UPDATE sender_stories SET reported = 1 WHERE sender = :sender")
    suspend fun markStoryReported(sender: String)

    @Query("DELETE FROM sender_stories")
    suspend fun forgetAllStories()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCodes(codes: List<MpesaCodeEntity>)

    @Query("SELECT COUNT(*) FROM mpesa_codes WHERE codeHash IN (:hashes)")
    suspend fun countKnownCodes(hashes: List<String>): Int

    @Query("SELECT codeHash FROM mpesa_codes")
    suspend fun allCodes(): List<String>

    @Query("DELETE FROM mpesa_codes WHERE at < :before")
    suspend fun forgetCodesBefore(before: Long)

    @Query("DELETE FROM mpesa_codes")
    suspend fun forgetAllCodes()
}
