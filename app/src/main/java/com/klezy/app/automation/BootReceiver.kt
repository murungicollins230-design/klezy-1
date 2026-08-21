package com.klezy.app.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BootReceiver
 *
 * Without this, every reboot silently kills all automations until the user
 * manually reopens the app. Requires RECEIVE_BOOT_COMPLETED in the manifest.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            KlezyForegroundService.start(context)
        }
    }
}
