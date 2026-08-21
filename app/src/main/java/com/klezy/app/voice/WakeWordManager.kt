package com.klezy.app.voice

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import android.util.Log

/**
 * WakeWordManager
 *
 * Wraps Picovoice Porcupine for always-on "Hey Klezy" detection.
 * Runs fully offline once set up — this is why Porcupine was chosen over
 * a cloud wake-word service.
 *
 * REQUIRES MANUAL SETUP BEFORE THIS WILL COMPILE/RUN — see README:
 *  1. Free Picovoice account -> AccessKey (console.picovoice.ai)
 *  2. Train a custom "Hey Klezy" wake word there -> download the .ppn file
 *  3. Drop the .ppn into app/src/main/assets/
 * Porcupine's free tier does NOT include stock keywords for arbitrary
 * phrases like "Hey Klezy" — custom wake words are trained per-user on
 * their console and are free for personal/non-commercial use.
 */
class WakeWordManager(
    private val context: Context,
    private val accessKey: String,
    private val keywordAssetPath: String = "hey-klezy.ppn",
    private val onWakeWordDetected: () -> Unit
) {
    private var porcupineManager: PorcupineManager? = null

    fun start() {
        if (porcupineManager != null) return
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(keywordAssetPath)
                .setSensitivity(0.6f)
                .setErrorCallback { e -> Log.e("WakeWordManager", "Porcupine error", e) }
                .build(context, PorcupineManagerCallback { _ -> onWakeWordDetected() })
            porcupineManager?.start()
            Log.d("WakeWordManager", "Listening for wake word")
        } catch (e: PorcupineException) {
            Log.e("WakeWordManager", "Failed to start Porcupine — check AccessKey and .ppn path", e)
        }
    }

    fun stop() {
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: PorcupineException) {
            Log.e("WakeWordManager", "Failed to stop Porcupine", e)
        } finally {
            porcupineManager = null
        }
    }
}
