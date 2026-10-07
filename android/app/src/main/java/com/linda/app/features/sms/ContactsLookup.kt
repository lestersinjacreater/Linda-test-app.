package com.linda.app.features.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

/** Is this sender in the user's contacts? Reads nothing else, and never leaves the phone. */
object ContactsLookup {
    /** true / false, or null when we cannot tell (no permission, or the sender is a name like "MPESA", not a number). */
    fun isInContacts(context: Context, sender: String?): Boolean? {
        if (sender.isNullOrBlank() || sender.none { it.isDigit() }) return null
        val granted = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sender))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.count > 0 }
        } catch (e: Exception) {
            null
        }
    }
}
