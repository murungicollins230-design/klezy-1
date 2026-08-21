package com.klezy.app.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.klezy.app.accessibility.KlezyAccessibilityService

/**
 * PermissionHelper
 *
 * Accessibility and Notification-listener access can't be requested with a normal
 * runtime permission dialog — Android forces the user into a system settings screen.
 * These helpers open the right screen and let you check current status.
 */
object PermissionHelper {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = "${context.packageName}/${KlezyAccessibilityService::class.java.name}"
        return enabled.split(":").any { it.equals(expected, ignoreCase = true) }
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabled.contains(context.packageName)
    }

    fun openNotificationListenerSettings(context: Context) {
        context.startActivity(
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
