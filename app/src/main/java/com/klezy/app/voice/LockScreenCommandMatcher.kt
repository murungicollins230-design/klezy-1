package com.klezy.app.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
import com.klezy.app.media.MediaRuntime
import com.klezy.app.media.TorchController

/**
 * LockScreenCommandMatcher
 *
 * Deliberately narrow — everything Android actually allows without
 * unlocking the device (no root): torch, media playback, volume, and
 * launching the camera. Anything else gets an honest "unlock your phone
 * for that" instead of silently failing — Android's keyguard blocks
 * interaction with app content behind it, a real OS boundary.
 */
object LockScreenCommandMatcher {

    fun tryHandle(context: Context, text: String): String {
        val lower = text.trim().lowercase()
        val mediaRouter = MediaRuntime.get()

        return when {
            lower.contains("torch") && lower.contains("off") -> {
                if (TorchController.setTorch(context, false)) "Torch off" else "Couldn't reach the torch"
            }
            lower.contains("torch") || lower.contains("flashlight") || lower.contains("light on") -> {
                if (TorchController.setTorch(context, true)) "Torch on" else "Couldn't reach the torch"
            }
            lower.contains("pause") -> { mediaRouter?.pause(); "Paused" }
            lower.contains("resume") || lower == "play" -> { mediaRouter?.resume(); "Resuming" }
            lower.contains("next") || lower.contains("skip") -> { mediaRouter?.next(); "Skipping" }
            lower.contains("previous") || lower.contains("go back") -> { mediaRouter?.previous(); "Going back" }
            lower.contains("volume up") -> { adjustVolume(context, raise = true); "Volume up" }
            lower.contains("volume down") -> { adjustVolume(context, raise = false); "Volume down" }
            lower.contains("camera") -> {
                // The one "open an app" action that works locked — Android
                // explicitly designed a lock-screen camera shortcut for this.
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Opening camera"
            }
            else -> "That needs your phone unlocked — I can't reach that from the lock screen."
        }
    }

    private fun adjustVolume(context: Context, raise: Boolean) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0
        )
    }
}
