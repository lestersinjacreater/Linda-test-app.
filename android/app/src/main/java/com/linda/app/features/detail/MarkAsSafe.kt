package com.linda.app.features.detail

import android.content.Context
import com.linda.app.LindaApp
import com.linda.app.core.data.AllowedSenderEntity
import com.linda.app.core.data.DetectionEntity
import com.linda.app.features.learning.LearningStore

/** What "Mark as safe" changed, kept so Undo can put everything back exactly. */
data class SafeMark(
    val detectionId: Long,
    val sender: String?,
    val senderWasAllowed: Boolean,
    val tapsBefore: String?,
)

/**
 * "Mark as safe" on a warning, with Undo (docs/design-system.md 7.12). Undo instead of an "Are you sure?" dialog:
 * the tap is quick, a slip is easy to take back, and nothing is lost meanwhile.
 */
object MarkAsSafe {
    suspend fun apply(context: Context, d: DetectionEntity): SafeMark {
        val db = (context.applicationContext as LindaApp).database
        val mark = SafeMark(
            detectionId = d.id,
            sender = d.sender,
            senderWasAllowed = d.sender?.let { db.senderDao().isAllowed(it) } ?: false,
            tapsBefore = LearningStore.tapsSnapshot(context),
        )
        db.detectionDao().markSafe(d.id)
        d.sender?.let { db.senderDao().allow(AllowedSenderEntity(it)) } // stop scoring this sender
        // Learn from your taps: messages that look like this one, from other senders, worry Linda less.
        LearningStore.learnFromTap(context, d.body, d.sender, isScam = false)
        return mark
    }

    suspend fun undo(context: Context, mark: SafeMark) {
        val db = (context.applicationContext as LindaApp).database
        db.detectionDao().unmarkSafe(mark.detectionId)
        // Only take the sender off the safe list if this tap put it there.
        if (mark.sender != null && !mark.senderWasAllowed) db.senderDao().disallow(mark.sender)
        LearningStore.restoreTaps(context, mark.tapsBefore)
    }
}
