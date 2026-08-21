package com.klezy.app.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * KlezyNotificationListener
 *
 * Lets Klezy read incoming notifications (app, title, text) and dismiss them.
 * User must enable manually: Settings > Apps > Special access > Notification access.
 * See PermissionHelper.openNotificationListenerSettings.
 */
class KlezyNotificationListener : NotificationListenerService() {

    data class KlezyNotification(
        val packageName: String,
        val title: String,
        val text: String,
        val postTime: Long
    )

    interface Listener {
        fun onNotification(notification: KlezyNotification)
    }

    companion object {
        // Simple subscriber list — the chat/automation layers register here.
        // Swap for a proper event bus once P2 automation engine lands.
        private val subscribers = mutableListOf<Listener>()

        fun subscribe(listener: Listener) {
            if (!subscribers.contains(listener)) subscribers.add(listener)
        }

        fun unsubscribe(listener: Listener) {
            subscribers.remove(listener)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Skip Klezy's own notifications and empty/system noise.
        if (sbn.packageName == packageName || (title.isBlank() && text.isBlank())) return

        val note = KlezyNotification(
            packageName = sbn.packageName,
            title = title,
            text = text,
            postTime = sbn.postTime
        )
        subscribers.forEach { it.onNotification(note) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Available for future use — e.g. automation triggers on dismissal.
    }

    /** Dismisses a specific active notification by key, if Klezy has access to it. */
    fun dismiss(key: String) {
        cancelNotification(key)
    }
}
