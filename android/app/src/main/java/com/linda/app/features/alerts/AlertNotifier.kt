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
import com.linda.app.core.ui.theme.Palette
import com.linda.app.core.util.Prefs
import com.linda.app.features.calls.CallWarning
import com.linda.app.features.calls.VerifiedCaller
import com.linda.app.features.detection.ReasonsJson
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.detection.Verdict

/** Shows the warning. The text is in the user's chosen language and always says WHY (rule 4). */
object AlertNotifier {
    const val EXTRA_DETECTION_ID = "detection_id"
    /** What to open: the full-screen stop (a tap on the warning), the explanation ("See why") or the report sheet. */
    const val EXTRA_OPEN = "open"
    const val OPEN_TAKEOVER = "takeover"
    const val OPEN_WHY = "why"
    const val OPEN_REPORT = "report"
    private const val CHANNEL_SCAM = "linda_scam"
    private const val CHANNEL_CAUTION = "linda_caution"
    private const val CHANNEL_VERIFIED = "linda_verified"

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

        // One PendingIntent per button. Each gets its own request code, so Android keeps them apart.
        fun opening(what: String, slot: Int) = PendingIntent.getActivity(
            context, detectionId.toInt() * 3 + slot,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_DETECTION_ID, detectionId)
                .putExtra(EXTRA_OPEN, what)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = localized.getString(if (scam) R.string.alert_scam_title else R.string.alert_caution_title)
        val reason = verdict.reasons.firstOrNull()?.let { ReasonsJson.text(it, language) } ?: ""

        val builder = NotificationCompat.Builder(context, if (scam) CHANNEL_SCAM else CHANNEL_CAUTION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            // The risk colour tints the icon and app name, so the title needs no emoji (docs/design-system.md 9).
            .setColor((if (scam) Palette.SCAM_RED else Palette.CAUTION_LIGHT).toInt())
            .setContentTitle(title)
            .setContentText(reason) // the message itself is never shown on the lock screen
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setPriority(if (scam) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(opening(OPEN_TAKEOVER, 0))
            .setAutoCancel(true)
            .addAction(0, localized.getString(R.string.notif_action_why), opening(OPEN_WHY, 1))
        // Report opens the report sheet first, which shows exactly what will be sent: never a silent report.
        if (scam) builder.addAction(0, localized.getString(R.string.action_report), opening(OPEN_REPORT, 2))
        NotificationManagerCompat.from(context).notify(detectionId.toInt(), builder.build())
    }

    /** Heads-up warning for an incoming call from a flagged number. Shown before the user answers. */
    @SuppressLint("MissingPermission") // checked just below
    fun showCallWarning(context: Context, msisdn: String, warning: CallWarning) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val localized = Prefs.localized(context)
        createChannels(localized)
        val text = when (warning) {
            is CallWarning.RecentScamMessage -> localized.getString(R.string.call_warning_recent, warning.minutesAgo)
            is CallWarning.ReportedNumber -> localized.getString(R.string.call_warning_reported)
            is CallWarning.SuspectedNumber -> localized.getString(R.string.call_warning_suspected)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_SCAM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor((if (warning is CallWarning.SuspectedNumber) Palette.CAUTION_LIGHT else Palette.SCAM_RED).toInt())
            .setContentTitle(localized.getString(R.string.call_warning_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(("call" + msisdn).hashCode(), notification)
    }

    /**
     * The verified badge for a genuine caller (e.g. Safaricom Customer Care on 0722 000 000). It still
     * reminds the user never to share their PIN, because a genuine caller never needs it.
     */
    fun showVerifiedCaller(context: Context, msisdn: String, caller: VerifiedCaller) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val localized = Prefs.localized(context)
        createChannels(localized)
        val name = caller.name(Prefs.effectiveLanguage(context))
        val text = localized.getString(R.string.call_verified_text, name)
        val notification = NotificationCompat.Builder(context, CHANNEL_VERIFIED)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(Palette.GREEN_500.toInt())
            .setContentTitle(localized.getString(R.string.call_verified_title, name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(("call" + msisdn).hashCode(), notification)
    }

    private fun createChannels(localized: Context) {
        val manager = localized.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SCAM, localized.getString(R.string.channel_scam), NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CAUTION, localized.getString(R.string.channel_caution), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_VERIFIED, localized.getString(R.string.channel_verified), NotificationManager.IMPORTANCE_HIGH),
        )
    }
}
