package com.linda.app.features.calls

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.linda.app.R
import com.linda.app.core.ui.components.categoryRes
import com.linda.app.core.ui.theme.Palette
import com.linda.app.core.util.PhoneNumbers
import com.linda.app.core.util.Prefs

/**
 * The Linda card shown on top of the ringing screen (design idea from Kelvin's prototype; docs/design-system.md 7.19).
 * No app can replace Android's own call screen, and Linda must not: answer and decline stay the phone's own. This card
 * sits in the middle of the screen (the phone's own caller banner is at the top, its buttons at the bottom), a big pill says what Linda knows ("Reported for fraud", "Verified: Safaricom Customer Care"), and
 * touches outside it go straight to the call screen underneath. It closes itself after [SHOW_MS], or on "Got it".
 *
 * It needs Android's "Display over other apps" permission ([canShow]). Without it nothing changes: the heads-up
 * notification (AlertNotifier) is still shown. Plain Android views, not Compose: a card drawn from the call-screening
 * service has no screen of its own to host Compose, and these few views are simpler to explain.
 */
object CallCardOverlay {
    private const val SHOW_MS = 30_000L
    private val main by lazy { Handler(Looper.getMainLooper()) } // lazy: unit tests use [look] without Android
    private var shown: View? = null

    fun canShow(context: Context): Boolean = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    /** Opens Android's "Display over other apps" switch for Linda. */
    fun askPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** What the card says, kept free of Android views so it can be unit tested. */
    data class Look(val pill: Int, val tone: Tone, val line: Int, val lineArg: Any?)
    /** NEUTRAL: a fact from this phone (a saved contact), so no risk colour and never the verified green. */
    enum class Tone { SCAM, CAUTION, SAFE, NEUTRAL }

    fun look(label: CallLabel): Look = when (label) {
        is CallWarning.ReportedNumber -> Look(R.string.call_card_reported, Tone.SCAM, R.string.call_warning_reported, null)
        is CallWarning.RecentScamMessage -> Look(R.string.call_card_recent, Tone.SCAM, R.string.call_warning_recent, label.minutesAgo)
        is CallWarning.SuspectedNumber -> Look(R.string.call_card_suspected, Tone.CAUTION, R.string.call_warning_suspected, null)
        is VerifiedCall -> Look(R.string.call_card_verified, Tone.SAFE, R.string.call_verified_text, null)
        SavedContact -> Look(R.string.call_card_contact, Tone.NEUTRAL, R.string.call_contact_text, null)
    }

    fun show(context: Context, msisdn: String, label: CallLabel) {
        if (!canShow(context)) return
        val app = Prefs.localized(context.applicationContext)
        main.post {
            try {
                hide(app)
                val card = build(app, msisdn, label)
                app.getSystemService(WindowManager::class.java).addView(card, params())
                shown = card
                main.postDelayed({ hide(app) }, SHOW_MS)
            } catch (e: Exception) {
                // The permission was taken away mid-call, or the phone refused the window: the notification still shows.
            }
        }
    }

    private fun hide(context: Context) {
        val card = shown ?: return
        shown = null
        try { context.getSystemService(WindowManager::class.java).removeView(card) } catch (e: Exception) { }
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        // Not focusable and not modal: the keyboard, back button and every touch outside the card belong to the call.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.CENTER } // the middle: the phone's own caller banner sits at the top, its buttons at the bottom

    private fun build(c: Context, msisdn: String, label: CallLabel): View {
        val look = look(label)
        val (accent, tint) = when (look.tone) {
            Tone.SCAM -> Palette.SCAM_RED_TEXT_LIGHT to Palette.SCAM_TINT_LIGHT
            Tone.CAUTION -> Palette.CAUTION_LIGHT to Palette.CAUTION_TINT_LIGHT
            Tone.SAFE -> Palette.GREEN_700 to Palette.GREEN_50
            Tone.NEUTRAL -> Palette.TEXT_PRIMARY_LIGHT to Palette.SURFACE_RAISED_LIGHT
        }
        val name = (label as? VerifiedCall)?.caller?.name(Prefs.effectiveLanguage(c))
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 14))
            background = GradientDrawable().apply { setColor(Palette.WHITE.toInt()); cornerRadius = dp(c, 24).toFloat() }
            elevation = dp(c, 8).toFloat()
        }
        card.addView(text(c, c.getString(R.string.call_card_from_linda), 13f, Palette.TEXT_SECONDARY_LIGHT, bold = true))
        card.addView(text(c, PhoneNumbers.pretty(msisdn), 26f, Palette.TEXT_PRIMARY_LIGHT, bold = true).apply { setPadding(0, dp(c, 4), 0, dp(c, 10)) })
        val pillText = if (name != null) c.getString(look.pill, name) else c.getString(look.pill)
        card.addView(text(c, pillText, 17f, accent, bold = true).apply {
            background = GradientDrawable().apply { setColor(tint.toInt()); cornerRadius = dp(c, 100).toFloat() }
            setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val line = when {
            name != null -> c.getString(look.line, name)
            look.lineArg != null -> c.getString(look.line, look.lineArg)
            else -> c.getString(look.line)
        }
        val category = (label as? CallWarning.ReportedNumber)?.category ?: (label as? CallWarning.SuspectedNumber)?.category
        val type = category?.let { categoryText(c, it) }
        card.addView(text(c, if (type != null) "$type · $line" else line, 15f, Palette.TEXT_PRIMARY_LIGHT).apply { setPadding(0, dp(c, 10), 0, 0) })
        card.addView(text(c, c.getString(R.string.call_card_you_decide), 13f, Palette.TEXT_SECONDARY_LIGHT).apply { setPadding(0, dp(c, 8), 0, 0) })
        card.addView(text(c, c.getString(R.string.call_card_close), 16f, Palette.GREEN_700, bold = true).apply {
            gravity = Gravity.END
            setPadding(dp(c, 8), dp(c, 12), dp(c, 4), dp(c, 4))
            setOnClickListener { hide(c) }
        })
        return LinearLayout(c).apply {
            setPadding(dp(c, 12), 0, dp(c, 12), 0)
            addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            contentDescription = pillText
        }
    }

    private fun categoryText(c: Context, category: String): String =
        c.getString(categoryRes(category)).replaceFirstChar { it.uppercaseChar() }

    private fun text(c: Context, s: String, sp: Float, color: Long, bold: Boolean = false) = TextView(c).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color.toInt())
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
}
