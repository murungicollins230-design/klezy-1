package com.klezy.app.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * OfflineLlmEngine
 *
 * Runs a small local model (Gemma 2B or Phi-3-mini, GGUF quantized) fully
 * on-device via llama.cpp — zero cost, zero internet dependency. This is
 * the part of P3 with real manual setup outside of code; see README for
 * the exact steps. Summary of what has to happen outside this file:
 *
 *   1. Clone llama.cpp's Android example (examples/llama.android in the
 *      ggerganov/llama.cpp repo) into this app as a module, OR add it as
 *      a git submodule — it ships the JNI/CMake glue these `external fun`
 *      declarations below expect. There is no plain Maven artifact for
 *      llama.cpp; the native build happens through Android Studio's NDK
 *      integration.
 *   2. Download a quantized GGUF model (e.g. gemma-2-2b-it-Q4_K_M.gguf,
 *      ~1.6GB) from Hugging Face on a computer, then copy it onto the
 *      phone into this app's files directory. Model files are too large
 *      to bundle in the APK or fetch from here — this has to be a manual,
 *      one-time transfer.
 *
 * Once both are in place, this class is the only thing the rest of the
 * app talks to — VoicePipelineOrchestrator calls generate() without
 * knowing anything about llama.cpp underneath.
 */
object OfflineLlmEngine {

    private const val TAG = "OfflineLlmEngine"
    private var modelLoaded = false

    init {
        // Loaded once the native module (step 1 above) is added to the project.
        // System.loadLibrary("llama-android")
    }

    // --- JNI bridge — implemented by the native module described above ---
    private external fun nativeLoadModel(modelPath: String): Long
    private external fun nativeGenerate(contextPtr: Long, prompt: String, maxTokens: Int): String
    private external fun nativeFreeModel(contextPtr: Long)

    private var contextPtr: Long = 0L

    suspend fun loadModel(context: Context, modelFileName: String = "gemma-2-2b-it-Q4_K_M.gguf"): Boolean =
        withContext(Dispatchers.IO) {
            val modelFile = File(context.filesDir, modelFileName)
            if (!modelFile.exists()) {
                Log.w(TAG, "Model file not found at ${modelFile.absolutePath} — copy it there first (see README)")
                return@withContext false
            }
            try {
                contextPtr = nativeLoadModel(modelFile.absolutePath)
                modelLoaded = contextPtr != 0L
                modelLoaded
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native llama.cpp module not built yet — see README step 1", e)
                false
            }
        }

    suspend fun generate(prompt: String, maxTokens: Int = 200): String = withContext(Dispatchers.Default) {
        if (!modelLoaded) {
            return@withContext "Offline model isn't loaded yet — check that the model file and native build are both in place."
        }
        try {
            nativeGenerate(contextPtr, prompt, maxTokens)
        } catch (e: UnsatisfiedLinkError) {
            "Offline model unavailable right now."
        }
    }

    fun release() {
        if (modelLoaded) {
            nativeFreeModel(contextPtr)
            modelLoaded = false
        }
    }

    fun isReady(): Boolean = modelLoaded
}
