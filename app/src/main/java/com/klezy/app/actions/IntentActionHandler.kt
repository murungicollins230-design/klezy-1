package com.klezy.app.actions

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * IntentActionHandler
 *
 * For actions Android already exposes cleanly via Intents — no need to
 * fight the Accessibility Service for these. Use KlezyAccessibilityService
 * only for things Intents can't do (tapping inside a specific app's UI).
 */
object IntentActionHandler {

    /** Opens an installed app by package name. Returns false if not installed. */
    fun openApp(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    /** Opens the SMS app pre-filled with a number and message — no runtime permission needed. */
    fun composeSms(context: Context, number: String, message: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", message)
        }
        context.startActivity(intent)
    }

    /**
     * Places a call directly. Requires the CALL_PHONE runtime permission —
     * check/request it before calling this, or use dialNumber() to just open the dialer instead.
     */
    fun callNumber(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Opens the dialer pre-filled with a number, no permission required. */
    fun dialNumber(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
