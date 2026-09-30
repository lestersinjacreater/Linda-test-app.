package com.linda.app.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One scored message, saved so it shows up in History and in the
 * "scams caught this month" counter on the Home screen.
 *
 * [level] holds the RiskLevel name ("SAFE", "CAUTION", "SCAM"). It is a plain
 * string here so core/ does not depend on the detection feature.
 */
@Entity(tableName = "detections")
data class DetectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String?,        // null when the text was pasted or shared
    val body: String,
    val level: String,
    val score: Float,
    val reasonsJson: String,    // reasons are structured in Phase 1 (F2/F4)
    val modelVersion: String,
    val receivedAt: Long,
    val source: String,         // "sms", "checker", "inbox" or "demo"
    val markedSafe: Boolean = false,
)
