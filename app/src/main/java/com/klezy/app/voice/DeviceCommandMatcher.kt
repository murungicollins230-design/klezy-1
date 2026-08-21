package com.klezy.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.klezy.app.actions.DeviceActionRouter
import com.klezy.app.contacts.ContactLookup

/**
 * DeviceCommandMatcher
 *
 * Catches obvious device commands ("open Instagram", "call mum", "text
 * John saying I'm on my way") and routes them straight to
 * DeviceActionRouter — no LLM involved.
 */
object DeviceCommandMatcher {

    private val openAppPattern = Regex("""open\s+(.+)""", RegexOption.IGNORE_CASE)
    private val callPattern = Regex("""call\s+(.+)""", RegexOption.IGNORE_CASE)
    private val textPattern = Regex("""text\s+(.+?)\s+(?:saying|that)\s+(.+)""", RegexOption.IGNORE_CASE)

    // Maps spoken app names to package names — extend as needed.
    private val appPackages = mapOf(
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "gmail" to "com.google.android.gm",
        "settings" to "com.android.settings"
    )

    /** Returns a spoken confirmation if [text] matched a device command, or null if it didn't. */
    fun tryHandle(context: Context, text: String): String? {
        textPattern.find(text)?.let { match ->
            val (contactName, message) = match.destructured
            if (!ContactLookup.hasPermission(context)) {
                return "I need contacts access to text $contactName — grant that in settings first."
            }
            val contact = ContactLookup.findByName(context, contactName.trim())
                ?: return "I couldn't find $contactName in your contacts."
            DeviceActionRouter.sendText(context, contact.number, message.trim())
            return "Texting ${contact.name}"
        }

        callPattern.find(text)?.let { match ->
            val target = match.groupValues[1].trim()
            if (!ContactLookup.hasPermission(context)) {
                return "I need contacts access to call $target — grant that in settings first."
            }
            val contact = ContactLookup.findByName(context, target)
                ?: return "I couldn't find $target in your contacts."

            // Direct, hands-free calling needs CALL_PHONE granted at
            // runtime — requested in MainActivity's permission list.
            // Without it, this correctly falls back to opening the dialer
            // pre-filled (needs one manual tap) rather than crashing, and
            // says so honestly instead of claiming the call already started.
            return if (hasCallPermission(context)) {
                DeviceActionRouter.call(context, contact.number, direct = true)
                "Calling ${contact.name}"
            } else {
                DeviceActionRouter.call(context, contact.number, direct = false)
                "Opening the dialer for ${contact.name} — grant phone access in settings for hands-free calling."
            }
        }

        openAppPattern.find(text)?.let { match ->
            val appName = match.groupValues[1].trim().lowercase()
            val packageName = appPackages[appName]
            return if (packageName != null) {
                val result = DeviceActionRouter.openApp(context, packageName)
                if (result is DeviceActionRouter.ActionResult.Success) "Opening $appName"
                else "Couldn't open $appName"
            } else {
                null // unknown app name — let it fall through to the LLM
            }
        }

        return null
    }

    private fun hasCallPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
}
