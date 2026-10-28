package com.linda.app.features.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import com.linda.app.features.detection.ContactClaims

/** Is this sender in the user's contacts? Reads nothing else, and never leaves the phone. */
object ContactsLookup {
    /**
     * "Hi, it's Mum" from a number that is not saved: is there a saved contact called Mum? Asks Android's contacts only
     * for the names the text itself claims (ContactClaims), never the whole address book. Returns the saved first name,
     * or null (no claim, no match, or no permission). Only names are read, never numbers, and nothing leaves the phone.
     */
    fun claimedContact(context: Context, body: String): String? {
        val claimed = ContactClaims.claimedNames(body)
        if (claimed.isEmpty() || !granted(context)) return null
        return try {
            val names = claimed.flatMap { name ->
                val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(name))
                context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { c ->
                    buildList { while (c.moveToNext()) c.getString(0)?.let { add(it) } }
                } ?: emptyList()
            }
            ContactClaims.matchSaved(claimed, names)
        } catch (e: Exception) {
            null
        }
    }

    private fun granted(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** true / false, or null when we cannot tell (no permission, or the sender is a name like "MPESA", not a number). */
    fun isInContacts(context: Context, sender: String?): Boolean? {
        if (sender.isNullOrBlank() || sender.none { it.isDigit() }) return null
        if (!granted(context)) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sender))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.count > 0 }
        } catch (e: Exception) {
            null
        }
    }
}
