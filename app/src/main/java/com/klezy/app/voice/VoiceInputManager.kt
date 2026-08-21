package com.klezy.app.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * VoiceInputManager
 *
 * Wraps Android's built-in SpeechRecognizer — free, no API key, works
 * on-device on most modern phones (falls back to network recognition
 * automatically on some devices; that's an OS/OEM behavior, not ours to control).
 */
class VoiceInputManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition not available on this device")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: android.os.Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onResult(text) else onError("Didn't catch that")
                }

                override fun onError(error: Int) {
                    onError("Recognition error code: $error")
                }

                // Unused callbacks — present to satisfy the interface
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer?.startListening(intent)
        Log.d("VoiceInputManager", "Listening...")
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
    }
}
