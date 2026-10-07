package com.linda.app.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One flagged message, saved so it shows up in History and in the "scams caught this month" counter.
 * SAFE messages are never saved (privacy: Linda does not keep what it does not need).
 *
 * [level] holds the RiskLevel name ("CAUTION" or "SCAM"). It is a plain string here so core/ does
 * not depend on the detection feature. [reasonsJson] is a JSON array of {code, en, sw}.
 */
@Entity(tableName = "detections")
data class DetectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String?,        // null when the text was pasted or shared
    val body: String,
    val level: String,
    val score: Float,
    val category: String,       // sent_by_mistake, fake_mpesa, ... (same names as the radar's categories)
    val fingerprint: String,    // SimHash of the normalised text; what a report sends instead of the text
    val reasonsJson: String,
    val modelVersion: String,
    val receivedAt: Long,
    val source: String,         // "sms", "checker" or "demo"
    val markedSafe: Boolean = false,
)

/** A sender the user marked as safe. Linda stops scoring messages from it. */
@Entity(tableName = "allowed_senders")
data class AllowedSenderEntity(@PrimaryKey val sender: String)

/** A sender Linda has seen before, so it can tell "first message from this number". */
@Entity(tableName = "seen_senders")
data class SeenSenderEntity(@PrimaryKey val sender: String, val firstSeen: Long)

/** A report waiting to be sent to the radar. Survives app restarts and offline periods. No message text. */
@Entity(tableName = "report_queue")
data class ReportQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val category: String,
    val confidence: Double,
    val fingerprint: String,
    val modelVersion: String,
    val sentAt: Long,
    val attempts: Int = 0,
)
