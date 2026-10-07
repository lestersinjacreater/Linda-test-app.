package com.linda.app.features.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.linda.app.MainActivity
import com.linda.app.R
import com.linda.app.core.util.Prefs
import com.linda.app.features.detection.ReasonsJson
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.detection.Verdict

/** Shows the warning. The text is in the user's chosen language and always says WHY (rule 4). */
object AlertNotifier {
    const val EXTRA_DETECTION_ID = "detection_id"
    private const val CHANNEL_SCAM = "linda_scam"
    private const val CHANNEL_CAUTION = "linda_caution"

    @SuppressLint("MissingPermission") // checked just below
    fun show(context: Context, detectionId: Long, verdict: Verdict) {
        if (verdict.level == RiskLevel.SAFE) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val localized = Prefs.localized(context)
        val language = Prefs.effectiveLanguage(context)
        val scam = verdict.level == RiskLevel.SCAM
        createChannels(localized)

        val open = PendingIntent.getActivity(
            context, detectionId.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_DETECTION_ID, detectionId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = localized.getString(if (scam) R.string.alert_scam_title else R.string.alert_caution_title)
        val reason = verdict.reasons.firstOrNull()?.let { ReasonsJson.text(it, language) } ?: ""

        val notification = NotificationCompat.Builder(context, if (scam) CHANNEL_SCAM else CHANNEL_CAUTION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(reason) // the message itself is never shown on the lock screen
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setPriority(if (scam) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(detectionId.toInt(), notification)
    }

    private fun createChannels(localized: Context) {
        val manager = localized.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SCAM, localized.getString(R.string.channel_scam), NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CAUTION, localized.getString(R.string.channel_caution), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}
