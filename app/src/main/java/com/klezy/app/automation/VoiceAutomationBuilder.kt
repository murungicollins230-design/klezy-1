package com.klezy.app.automation

import android.content.Context
import com.klezy.app.data.ApiKeyStore
import com.klezy.app.network.GroqApiClient
import org.json.JSONObject
import java.util.UUID

/**
 * VoiceAutomationBuilder
 *
 * "Every day at 10pm, pause my music" becomes a real AutomationRule
 * without ever opening a form. This is the voice-first alternative to a
 * visual builder screen — Groq turns the sentence into the exact JSON
 * shape AutomationRuleMapper already knows how to read (see
 * data/AutomationRuleMapper.kt), so no second parser had to be written.
 *
 * Two-step by design: Klezy always says back what it understood and
 * waits for a yes before saving. A wrongly-parsed automation (wrong app,
 * wrong time) is a worse failure than asking once — same philosophy as
 * ContactLookup and DeviceActionRouter failing closed elsewhere in this build.
 */
object VoiceAutomationBuilder {

    private var pendingRule: AutomationRule? = null
    private var pendingSummary: String? = null

    private val creationTriggerWords = listOf(
        "every day", "every morning", "every night", "whenever i", "when i open",
        "when i get a notification", "remind me", "automation", "automate"
    )
    private val timePattern = Regex("""\bat\s+\d{1,2}(:\d{2})?\s?(am|pm)?\b""", RegexOption.IGNORE_CASE)

    private val listPattern = Regex("""what automations|list (my )?automations|show (my )?automations""", RegexOption.IGNORE_CASE)
    private val deletePattern = Regex("""(delete|remove|turn off|disable)\s+(the\s+)?(.+?)\s+automation""", RegexOption.IGNORE_CASE)

    fun looksLikeRequest(text: String): Boolean {
        val lower = text.lowercase()
        return creationTriggerWords.any { lower.contains(it) } || timePattern.containsMatchIn(lower)
    }

    /**
     * Returns a reply if this turn was handled as an automation-related
     * command (creation, confirmation, listing, or deletion), or null if
     * it wasn't — in which case ConversationRouter should keep trying
     * other matchers/the general LLM.
     */
    suspend fun handle(context: Context, transcript: String): String? {
        if (pendingRule != null) return handleConfirmation(transcript)

        listPattern.find(transcript)?.let { return listRules() }

        deletePattern.find(transcript)?.let { match ->
            return deleteRule(match.groupValues[3].trim())
        }

        if (!looksLikeRequest(transcript)) return null

        val apiKey = ApiKeyStore.getGroqKey(context)
            ?: return "I'd need my online brain to set that up — say \"open settings\" to add a Groq key."

        return proposeRule(apiKey, transcript)
    }

    private fun listRules(): String {
        val rules = AutomationEngine.getRules()
        if (rules.isEmpty()) return "You don't have any automations set up yet."
        val names = rules.joinToString(", ") { it.name }
        return "You've got: $names."
    }

    private fun deleteRule(nameQuery: String): String {
        val match = AutomationEngine.getRules().firstOrNull {
            it.name.contains(nameQuery, ignoreCase = true)
        } ?: return "I couldn't find an automation matching \"$nameQuery.\""
        AutomationEngine.removeRule(match.id)
        return "Deleted \"${match.name}.\""
    }

    private suspend fun proposeRule(apiKey: String, transcript: String): String {
        val systemPrompt = """
            Convert the user's spoken request into a JSON automation rule.
            Respond with ONLY the JSON object, no markdown fences, no explanation.

            Schema:
            {
              "name": "short human-readable label, under 6 words",
              "trigger": { "type": "timeOfDay" | "appOpened" | "notification", ...fields },
              "actions": [ { "type": "...", ...fields } ]
            }

            Trigger fields by type:
            - timeOfDay: hour (0-23), minute (0-59), daysOfWeek (array of ints, 1=Sunday..7=Saturday, empty = every day)
            - appOpened: packageName (string)
            - notification: packageName (string, optional), keyword (string, optional)

            Action fields by type (actions is a list, usually one item):
            - openApp: packageName
            - playTrack: query (song/artist name to search the local library)
            - pauseMedia: (no fields)
            - goHome / goBack: (no fields)
            - sendText: number, message
            - call: number, direct (bool)

            Known app package names — use these exactly when the request names
            one of these apps: instagram=com.instagram.android, whatsapp=com.whatsapp,
            youtube=com.google.android.youtube, gmail=com.google.android.gm,
            settings=com.android.settings. For any other app, make a reasonable
            guess at the package name.

            If the request is too vague to build a rule (missing a time, an
            app, or an action), respond with exactly: {"error": "too vague"}
        """.trimIndent()

        val result = GroqApiClient(apiKey).chat(listOf("user" to transcript), systemPrompt)
        val raw = when (result) {
            is GroqApiClient.Result.Success -> result.reply
            is GroqApiClient.Result.Failure -> return "I couldn't reach my online brain to set that up."
        }

        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        return try {
            val json = JSONObject(cleaned)
            if (json.has("error")) {
                "I didn't catch enough detail for that — try including a time or app name."
            } else {
                val map = jsonToMap(json).toMutableMap()
                map["id"] = UUID.randomUUID().toString()
                map["enabled"] = true
                val rule = com.klezy.app.data.AutomationRuleMapper.fromMap(map)
                    ?: return "I understood the request but couldn't build a valid rule from it — try rephrasing."

                pendingRule = rule
                pendingSummary = summarize(rule)
                "${pendingSummary} Should I save that?"
            }
        } catch (e: Exception) {
            "I had trouble understanding that as an automation — try rephrasing."
        }
    }

    private fun handleConfirmation(transcript: String): String {
        val lower = transcript.trim().lowercase()
        val rule = pendingRule
        pendingRule = null
        pendingSummary = null

        return when {
            rule == null -> "Something went wrong — try setting that up again."
            lower.startsWith("yes") || lower.contains("save") || lower.contains("confirm") || lower.contains("do it") -> {
                AutomationEngine.addRule(rule)
                "Saved."
            }
            lower.startsWith("no") || lower.contains("cancel") || lower.contains("don't") || lower.contains("discard") -> {
                "Okay, discarded."
            }
            else -> "Not sure if that was a yes — discarding to be safe. Ask again if you'd like."
        }
    }

    private fun summarize(rule: AutomationRule): String {
        val triggerText = when (val t = rule.trigger) {
            is Trigger.TimeOfDay -> "every day at ${t.hour}:${t.minute.toString().padStart(2, '0')}"
            is Trigger.AppOpened -> "when you open ${t.packageName}"
            is Trigger.NotificationReceived -> "on a notification" + (t.keyword?.let { " mentioning \"$it\"" } ?: "")
        }
        val actionText = rule.actions.joinToString(" and ") { a ->
            when (a) {
                is AutomationAction.PlayTrack -> "play ${a.query}"
                AutomationAction.PauseMedia -> "pause your music"
                is AutomationAction.OpenApp -> "open ${a.packageName}"
                AutomationAction.GoHome -> "go home"
                AutomationAction.GoBack -> "go back"
                is AutomationAction.SendText -> "text ${a.number}"
                is AutomationAction.Call -> "call ${a.number}"
                is AutomationAction.TapLabel -> "tap ${a.label}"
                is AutomationAction.TypeText -> "type \"${a.text}\""
            }
        }
        return "I'll $actionText $triggerText."
    }

    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            map[key] = when (val value = json.get(key)) {
                is JSONObject -> jsonToMap(value)
                is org.json.JSONArray -> (0 until value.length()).map { i ->
                    val item = value.get(i)
                    if (item is JSONObject) jsonToMap(item) else item
                }
                else -> value
            }
        }
        return map
    }
}
