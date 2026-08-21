package com.klezy.app.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.UUID

/**
 * VoiceOutputManager
 *
 * Wraps Android's built-in TextToSpeech — free, works offline once the OS
 * has its voice pack. Quality/naturalness varies a lot by phone and
 * Android version; this class tries to default to a female-sounding
 * network-quality voice where one's available, but the honest ceiling
 * here is Android's own TTS engine. A genuinely human-sounding voice
 * (ElevenLabs) is a paid step, deliberately not built — see Settings screen.
 */
class VoiceOutputManager(context: Context) {

    private var ready = false
    private val tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            preferFemaleVoice()
        }
    }.apply {
        language = Locale.getDefault()
    }

    /** Picks the highest-quality voice whose name/locale suggests a female voice, if one exists. */
    private fun preferFemaleVoice() {
        val candidate = tts.voices
            ?.filter { !it.isNetworkConnectionRequired && it.locale == Locale.getDefault() }
            ?.filter { it.name.contains("female", ignoreCase = true) }
            ?.maxByOrNull { it.quality }
        candidate?.let { tts.voice = it }
        // If no voice is explicitly tagged "female" (common on many OEM
        // TTS engines that don't expose gender at all), this silently
        // keeps the system default rather than guessing — pick manually
        // in Settings instead, where you can hear each option first.
    }

    fun listVoices(): List<Voice> =
        tts.voices?.filter { it.locale == Locale.getDefault() && !it.isNetworkConnectionRequired }
            ?.sortedByDescending { it.quality }
            ?.toList()
            ?: emptyList()

    fun setVoice(voice: Voice) {
        tts.voice = voice
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        if (!ready) {
            onDone()
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) onDone()
            }
            @Deprecated("Deprecated in API, still required by interface")
            override fun onError(id: String?) {
                if (id == utteranceId) onDone()
            }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
    }

    fun stop() {
        tts.stop()
    }

    fun release() {
        tts.stop()
        tts.shutdown()
    }
}
