package com.klezy.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.klezy.app.network.GroqApiClient
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * InsightsEngine
 *
 * Honest scoping note: this is NOT real behavioral machine learning.
 * "Learn my patterns and adapt" as actually built here means: once a day,
 * take the last 7 days of ActivityLogRepository entries, hand them to
 * Groq, and ask it to notice anything worth mentioning — a habit, a
 * good moment for a reminder, something that went well or badly last
 * time. The result is a few sentences of plain text, stored once, and
 * folded into the system prompt on future Groq calls so Klezy's answers
 * can reference it.
 *
 * That's meaningfully different from a model that's actually trained on
 * your behavior — it's closer to "Klezy re-reads its own notes once a
 * day." Good enough to feel a bit adaptive at personal-use scale, worth
 * knowing it isn't more than that.
 */
class InsightsEngine(private val apiKey: String) {

    private val db = FirebaseFirestore.getInstance()
    private val activityLog = ActivityLogRepository()

    private fun insightsDoc(uid: String) =
        db.collection("users").document(uid).collection("insights").document("latest")

    suspend fun refresh(uid: String) {
        val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val entries = activityLog.recent(uid, sevenDaysAgo)
        if (entries.size < 5) return // not enough history yet to say anything meaningful

        val logText = entries.reversed().joinToString("\n") { entry ->
            val minutesAgo = (System.currentTimeMillis() - entry.timestamp) / 60000
            "- ${entry.summary} (${minutesAgo}m ago)"
        }

        val prompt = """
            Here's a log of commands a personal assistant app executed for its
            user over the last 7 days:

            $logText

            In 2-3 short sentences, note anything a helpful assistant might
            genuinely want to remember about this person's habits or routine —
            only if something's actually there. If nothing stands out, just say
            "Nothing notable yet." Don't invent patterns from too little data.
        """.trimIndent()

        val result = GroqApiClient(apiKey).ask(prompt)
        if (result is GroqApiClient.Result.Success) {
            insightsDoc(uid).set(
                mapOf("text" to result.reply, "generatedAt" to System.currentTimeMillis())
            ).await()
        }
    }

    suspend fun getLatest(uid: String): String? {
        val doc = insightsDoc(uid).get().await()
        val text = doc.getString("text") ?: return null
        return if (text.startsWith("Nothing notable")) null else text
    }
}
