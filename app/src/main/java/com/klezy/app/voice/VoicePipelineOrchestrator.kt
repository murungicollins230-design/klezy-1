package com.klezy.app.voice

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import com.klezy.app.ui.OrbActivity

/**
 * VoicePipelineOrchestrator
 *
 * Owns ONLY wake-word detection now. Runs inside KlezyForegroundService
 * (see automation/KlezyForegroundService.kt) rather than any Activity —
 * that's the fix for wake word needing to work continuously, including
 * with the app backgrounded or the phone locked. An Activity-owned
 * pipeline dies with the Activity; a service-owned one doesn't.
 *
 * All actual listening/routing/speaking now lives in OrbActivity +
 * ConversationRouter, triggered fresh on every wake word — nothing
 * persists between wake-ups here.
 */
class VoicePipelineOrchestrator(
    private val context: Context,
    private val picovoiceAccessKey: String
) {
    private val wakeWord = WakeWordManager(
        context = context,
        accessKey = picovoiceAccessKey,
        onWakeWordDetected = ::onWakeWordDetected
    )

    fun start() {
        wakeWord.start()
    }

    fun stop() {
        wakeWord.stop()
    }

    private fun onWakeWordDetected() {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val locked = keyguard.isKeyguardLocked

        context.startActivity(
            Intent(context, OrbActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(OrbActivity.EXTRA_LOCKED, locked)
        )
    }
}
