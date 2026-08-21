package com.klezy.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * ApiKeyStore
 *
 * Encrypted local storage for both API keys Klezy needs. Same reasoning
 * as before: fine for a single-user personal app, not fine if this ever
 * has multiple users (route through your own backend at that point instead).
 */
object ApiKeyStore {

    private const val PREFS_NAME = "klezy_secure_prefs"
    private const val KEY_GROQ = "groq_api_key"
    private const val KEY_PICOVOICE = "picovoice_access_key"

    private fun prefs(context: Context) = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveGroqKey(context: Context, key: String) = prefs(context).edit().putString(KEY_GROQ, key).apply()
    fun getGroqKey(context: Context): String? = prefs(context).getString(KEY_GROQ, null)
    fun hasGroqKey(context: Context): Boolean = !getGroqKey(context).isNullOrBlank()

    fun savePicovoiceKey(context: Context, key: String) = prefs(context).edit().putString(KEY_PICOVOICE, key).apply()
    fun getPicovoiceKey(context: Context): String? = prefs(context).getString(KEY_PICOVOICE, null)
    fun hasPicovoiceKey(context: Context): Boolean = !getPicovoiceKey(context).isNullOrBlank()

    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY_GROQ).remove(KEY_PICOVOICE).apply()
    }
}
