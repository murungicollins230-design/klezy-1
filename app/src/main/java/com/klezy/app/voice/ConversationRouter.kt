package com.klezy.app.voice

import android.content.Context
import com.klezy.app.automation.VoiceAutomationBuilder
import com.klezy.app.data.ActivityLogRepository
import com.klezy.app.data.ApiKeyStore
import com.klezy.app.data.AuthManager
import com.klezy.app.data.ChatHistoryRepository
import com.klezy.app.data.ChatMessageRecord
import com.klezy.app.data.InsightsEngine
import com.klezy.app.media.MediaCommandMatcher
import com.klezy.app.media.MediaRuntime
import com.klezy.app.network.GroqApiClient

data class RouteResult(val reply: String, val continueListening: Boolean)

/**
 * ConversationRouter
 *
 * The actual decision logic for "what does this transcript mean and what
 * do I do about it" — pulled out of the UI (OrbActivity) so the routing
 * order lives in one place: screen commands, then media, then device,
 * then offline model, then Groq. Same order as before, just no longer
 * tangled up with how the response gets shown on screen.
 */
object ConversationRouter {

    private val chatHistory = ChatHistoryRepository()
    private val activityLog = ActivityLogRepository()
    private const val HISTORY_WINDOW = 10L

    suspend fun handle(context: Context, transcript: String): RouteResult {
        val screenReply = ScreenCommandMatcher.tryHandle(context, transcript)
        if (screenReply != null) return log(context, transcript, screenReply, continueListening = false)

        val mediaRouter = MediaRuntime.get()
        if (mediaRouter != null) {
            val mediaReply = MediaCommandMatcher.tryHandle(mediaRouter, transcript)
            if (mediaReply != null) return log(context, transcript, mediaReply, continueListening = true)
        }

        val deviceReply = DeviceCommandMatcher.tryHandle(context, transcript)
        if (deviceReply != null) return log(context, transcript, deviceReply, continueListening = true)

        // Checked before the general LLM fallback so "every day at 10,
        // pause my music" gets built into a real rule instead of just
        // being chatted about. Also handles the yes/no confirmation turn,
        // and "what automations do I have" / "delete the X automation."
        val automationReply = VoiceAutomationBuilder.handle(context, transcript)
        if (automationReply != null) return log(context, transcript, automationReply, continueListening = true)

        val answer = if (OfflineLlmEngine.isReady()) {
            OfflineLlmEngine.generate(transcript)
        } else {
            askOnlineBrain(context, transcript)
        }
        return log(context, transcript, answer, continueListening = true)
    }

    private fun log(context: Context, transcript: String, reply: String, continueListening: Boolean): RouteResult {
        AuthManager.currentUserId?.let { uid -> activityLog.log(uid, "\"$transcript\" -> $reply") }
        return RouteResult(reply, continueListening)
    }

    private suspend fun askOnlineBrain(context: Context, transcript: String): String {
        val apiKey = ApiKeyStore.getGroqKey(context)
            ?: return "I don't have an online brain connected yet — say \"open settings\" to add a Groq key."

        val uid = AuthManager.ensureSignedIn()
        val recent = chatHistory.loadRecent(uid, limit = HISTORY_WINDOW)
        val history = recent.map { record ->
            val apiRole = if (record.role == "klezy") "assistant" else "user"
            apiRole to record.text
        } + ("user" to transcript)

        val insights = InsightsEngine(apiKey).getLatest(uid)
        val systemPrompt = buildString {
            append("You are Klezy, a concise, helpful personal AI assistant running on the user's phone.")
            if (insights != null) {
                append(" Some context on this person's recent patterns, only mention if relevant: ")
                append(insights)
            }
        }

        val reply = when (val result = GroqApiClient(apiKey).chat(history, systemPrompt)) {
            is GroqApiClient.Result.Success -> result.reply
            is GroqApiClient.Result.Failure -> return "I couldn't reach my online brain just now."
        }

        chatHistory.append(uid, ChatMessageRecord(role = "user", text = transcript))
        chatHistory.append(uid, ChatMessageRecord(role = "klezy", text = reply))

        return reply
    }
}
