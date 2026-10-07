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

    @Query("UPDATE detections SET markedSafe = 1 WHERE id = :id")
    suspend fun markSafe(id: Long)

    /** Scams Linda caught since [since], not counting ones the user marked as safe. */
    @Query("SELECT COUNT(*) FROM detections WHERE level = 'SCAM' AND markedSafe = 0 AND receivedAt >= :since")
    fun countScamsSince(since: Long): Flow<Int>

    /** Was there a scam message from this phone number since [since]? Used by call warnings. */
    @Query("SELECT * FROM detections WHERE senderMsisdn = :msisdn AND level = 'SCAM' AND markedSafe = 0 AND receivedAt >= :since ORDER BY receivedAt DESC LIMIT 1")
    suspend fun latestScamFrom(msisdn: String, since: Long): DetectionEntity?
}

@Dao
interface SenderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun allow(sender: AllowedSenderEntity)

    @Query("SELECT COUNT(*) > 0 FROM allowed_senders WHERE sender = :sender")
    suspend fun isAllowed(sender: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(sender: SeenSenderEntity)

    @Query("SELECT COUNT(*) > 0 FROM seen_senders WHERE sender = :sender")
    suspend fun hasSeen(sender: String): Boolean
}

@Dao
interface ReportQueueDao {
    @Insert
    suspend fun insert(report: ReportQueueEntity): Long

    @Query("SELECT * FROM report_queue ORDER BY id LIMIT :limit")
    suspend fun pending(limit: Int): List<ReportQueueEntity>

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

    @Query("DELETE FROM blocked_numbers WHERE msisdn IN (:msisdns)")
    suspend fun deleteAll(msisdns: List<String>)

    @Query("SELECT * FROM blocked_numbers WHERE msisdn = :msisdn")
    suspend fun find(msisdn: String): BlockedNumberEntity?

    @Query("SELECT COUNT(*) FROM blocked_numbers")
    fun observeCount(): Flow<Int>
}
