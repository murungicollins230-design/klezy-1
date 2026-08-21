package com.klezy.app.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.klezy.app.data.ApiKeyStore
import com.klezy.app.media.MediaRuntime
import com.klezy.app.voice.VoicePipelineOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * KlezyForegroundService
 *
 * Keeps AutomationEngine AND wake-word listening alive in the background.
 * Wake word used to be owned by an Activity (MainActivity) — that broke
 * the moment the app was backgrounded or the phone locked, defeating the
 * entire point of a wake word. Now it lives here, alongside automations,
 * in the one component built to survive exactly that.
 *
 * Android requires a visible notification for any foreground service —
 * this one is deliberately quiet and low-priority, but it can't be
 * hidden entirely (OS rule, not a Klezy choice).
 */
class KlezyForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "klezy_automation_channel"
        private const val NOTIFICATION_ID = 4201

        fun start(context: Context) {
            val intent = Intent(context, KlezyForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KlezyForegroundService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var voicePipeline: VoicePipelineOrchestrator? = null

    override fun onCreate() {
        super.onCreate()
        AutomationEngine.init(applicationContext)
        MediaRuntime.init(applicationContext)
        startForeground(NOTIFICATION_ID, buildNotification())
        // Rules no longer vanish on restart — this is the P4 fix for the
        // in-memory-only limitation flagged back in P2.
        serviceScope.launch { AutomationEngine.loadFromCloud() }

        startVoicePipelineIfKeyPresent()
    }

    /**
     * Starts wake-word listening once a Picovoice key exists (saved via
     * Settings — "open settings"). Safe to call again after the key is
     * added later; it just no-ops if already running or still missing a key.
     */
    private fun startVoicePipelineIfKeyPresent() {
        if (voicePipeline != null) return
        val key = ApiKeyStore.getPicovoiceKey(applicationContext) ?: return
        voicePipeline = VoicePipelineOrchestrator(applicationContext, key).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-check on every start (not just onCreate) — this is what lets
        // Settings trigger KlezyForegroundService.start() again after
        // saving a Picovoice key and actually pick it up, rather than
        // needing an app restart.
        startVoicePipelineIfKeyPresent()
        // START_STICKY: if the OS kills this to reclaim memory, it restarts
        // the service (without redelivering the last intent) once resources free up.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        voicePipeline?.stop()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Klezy automations",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps Klezy's automations running in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Klezy")
            .setContentText("Automations active")
            .setSmallIcon(android.R.drawable.ic_menu_info_details) // swap for your app icon
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }
}
