package com.klezy.app.contacts

import android.content.Context
import android.provider.ContactsContract

data class Contact(val name: String, val number: String)

/**
 * ContactLookup
 *
 * Resolves a spoken name ("mum", "John") to a phone number so
 * DeviceCommandMatcher can actually call/text people instead of just
 * describing what it would do. Requires READ_CONTACTS granted at runtime
 * before querying — a normal permission dialog, not a special settings
 * screen like P1's accessibility/notification access.
 */
object ContactLookup {

    /**
     * Substring match on display name, case-insensitive. Among matches,
     * prefers a mobile number if one exists — otherwise takes the first
     * number found. Does not disambiguate between multiple people with
     * similar names; see README for that limitation.
     */
    fun findByName(context: Context, query: String): Contact? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        var best: Contact? = null
        var bestIsMobile = false

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, selection, selectionArgs, null
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val number = cursor.getString(numberCol) ?: continue
                val type = cursor.getInt(typeCol)
                val isMobile = type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE

                if (best == null || (isMobile && !bestIsMobile)) {
                    best = Contact(name, number)
                    bestIsMobile = isMobile
                }
            }
        }
        return best
    }

    fun hasPermission(context: Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
