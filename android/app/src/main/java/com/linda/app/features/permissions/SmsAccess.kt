package com.linda.app.features.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Why this exists: on Android 13 and newer, an app installed from a browser (like Linda, which is not in the Play
 * Store) gets "restricted settings". Since Android 15 that includes SMS: Android refuses the permission WITHOUT showing
 * any pop-up, so the person taps Allow and nothing happens. The only way in is the phone's own App info screen, through
 * a hidden menu item. This file decides which steps to show; [SmsAccessHelp] shows them.
 */
object SmsAccess {
    enum class Step { OPEN_APP_INFO, ALLOW_RESTRICTED, PERMISSIONS_SMS, COME_BACK }

    /** Restricted settings exist from Android 13 (API 33). On older phones the plain permission screen is enough. */
    const val RESTRICTED_SETTINGS_FROM_SDK = 33

    fun steps(sdkInt: Int): List<Step> = buildList {
        add(Step.OPEN_APP_INFO)
        if (sdkInt >= RESTRICTED_SETTINGS_FROM_SDK) add(Step.ALLOW_RESTRICTED)
        add(Step.PERMISSIONS_SMS)
        add(Step.COME_BACK)
    }

    /** Opens Android's App info page for Linda, where the permissions (and the hidden unlock) live. */
    fun openAppInfo(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
