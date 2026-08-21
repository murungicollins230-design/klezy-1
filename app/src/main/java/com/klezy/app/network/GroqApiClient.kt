package com.klezy.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * GroqApiClient
 *
 * Klezy's "online brain" — fallback for anything that isn't a clean
 * device/media command and that the offline model can't handle or isn't
 * loaded. Now retries on 429 (rate limit) and 5xx (transient server
 * errors) with exponential backoff, honoring Groq's Retry-After header
 * when they send one, before giving up and reporting failure.
 */
class GroqApiClient(private val apiKey: String) {

    companion object {
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        // Verify current model IDs at console.groq.com/docs/models — Groq
        // adds/retires models over time, this is Klezy's default as of setup.
        private const val MODEL = "llama-3.3-70b-versatile"

        private const val MAX_RETRIES = 3
        private const val BASE_BACKOFF_MS = 1000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    sealed class Result {
        data class Success(val reply: String) : Result()
        data class Failure(val reason: String) : Result()
    }

    /**
     * [history] is a list of (role, content) pairs, role being "user" or "assistant",
     * oldest first — pass recent chat history for context, or just the latest
     * message alone for a one-off command.
     */
    suspend fun chat(
        history: List<Pair<String, String>>,
        systemPrompt: String = "You are Klezy, a concise, helpful personal AI assistant running on the user's phone."
    ): Result = withContext(Dispatchers.IO) {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.forEach { (role, content) ->
                put(JSONObject().put("role", role).put("content", content))
            }
        }

        val body = JSONObject().apply {
            put("model", MODEL)
            put("messages", messages)
            put("temperature", 0.7)
            put("max_tokens", 512)
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        executeWithRetry(request, attempt = 0)
    }

    private suspend fun executeWithRetry(request: Request, attempt: Int): Result {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            // Network-level failure (no connectivity, DNS, timeout) — retry
            // the same way as a transient server error, same backoff schedule.
            return retryOrGiveUp(request, attempt, "Network error: ${e.message}", retryAfterMs = null)
        }

        response.use { resp ->
            val responseBody = resp.body?.string().orEmpty()

            if (resp.isSuccessful) {
                return try {
                    val reply = JSONObject(responseBody)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    Result.Success(reply.trim())
                } catch (e: Exception) {
                    Result.Failure("Unexpected error parsing Groq response: ${e.message}")
                }
            }

            // 429 = rate limited, 5xx = transient server-side issue — both worth retrying.
            // 4xx other than 429 (bad key, bad request) won't fix itself on retry.
            if (resp.code == 429 || resp.code >= 500) {
                val retryAfterMs = resp.header("Retry-After")?.toLongOrNull()?.times(1000)
                return retryOrGiveUp(
                    request, attempt,
                    "Groq request failed (${resp.code}): ${responseBody.take(200)}",
                    retryAfterMs
                )
            }

            return Result.Failure("Groq request failed (${resp.code}): ${responseBody.take(200)}")
        }
    }

    private suspend fun retryOrGiveUp(
        request: Request,
        attempt: Int,
        failureReason: String,
        retryAfterMs: Long?
    ): Result {
        if (attempt >= MAX_RETRIES) {
            return Result.Failure("$failureReason (gave up after ${attempt + 1} attempts)")
        }
        // Honor Groq's Retry-After if given; otherwise exponential backoff: 1s, 2s, 4s.
        val delayMs = retryAfterMs ?: (BASE_BACKOFF_MS * (1L shl attempt))
        delay(delayMs)
        return executeWithRetry(request, attempt + 1)
    }

    /** Convenience for a single message with no prior history. */
    suspend fun ask(prompt: String): Result = chat(listOf("user" to prompt))
}
