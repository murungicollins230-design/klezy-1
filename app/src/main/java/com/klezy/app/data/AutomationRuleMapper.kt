package com.klezy.app.data

import com.klezy.app.automation.AutomationAction
import com.klezy.app.automation.AutomationRule
import com.klezy.app.automation.Trigger

/**
 * AutomationRuleMapper
 *
 * Firestore stores documents as plain maps — it has no idea what to do with
 * Kotlin sealed classes. This is the translation layer, using a "type"
 * field as a discriminator so we know which case to reconstruct on read.
 */
object AutomationRuleMapper {

    fun toMap(rule: AutomationRule): Map<String, Any> = mapOf(
        "id" to rule.id,
        "name" to rule.name,
        "enabled" to rule.enabled,
        "trigger" to triggerToMap(rule.trigger),
        "actions" to rule.actions.map { actionToMap(it) }
    )

    @Suppress("UNCHECKED_CAST")
    fun fromMap(data: Map<String, Any?>): AutomationRule? {
        val id = data["id"] as? String ?: return null
        val name = data["name"] as? String ?: return null
        val enabled = data["enabled"] as? Boolean ?: true
        val triggerMap = data["trigger"] as? Map<String, Any?> ?: return null
        val actionsList = data["actions"] as? List<Map<String, Any?>> ?: emptyList()

        val trigger = triggerFromMap(triggerMap) ?: return null
        val actions = actionsList.mapNotNull { actionFromMap(it) }

        return AutomationRule(id, name, trigger, actions, enabled)
    }

    private fun triggerToMap(trigger: Trigger): Map<String, Any> = when (trigger) {
        is Trigger.NotificationReceived -> mapOf(
            "type" to "notification",
            "packageName" to (trigger.packageName ?: ""),
            "keyword" to (trigger.keyword ?: "")
        )
        is Trigger.AppOpened -> mapOf(
            "type" to "appOpened",
            "packageName" to trigger.packageName
        )
        is Trigger.TimeOfDay -> mapOf(
            "type" to "timeOfDay",
            "hour" to trigger.hour,
            "minute" to trigger.minute,
            "daysOfWeek" to trigger.daysOfWeek.toList()
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun triggerFromMap(map: Map<String, Any?>): Trigger? = when (map["type"] as? String) {
        "notification" -> Trigger.NotificationReceived(
            packageName = (map["packageName"] as? String)?.ifBlank { null },
            keyword = (map["keyword"] as? String)?.ifBlank { null }
        )
        "appOpened" -> (map["packageName"] as? String)?.let { Trigger.AppOpened(it) }
        "timeOfDay" -> {
            // Accept any Number type here, not just Long — Firestore reads
            // give Long, but org.json (used by VoiceAutomationBuilder to
            // parse Groq's JSON output) gives Integer for small numbers.
            // A hardcoded `as? Long` would silently fail every voice-created
            // time rule, so this reads any numeric type.
            val hour = (map["hour"] as? Number)?.toInt() ?: return null
            val minute = (map["minute"] as? Number)?.toInt() ?: return null
            val days = (map["daysOfWeek"] as? List<*>)
                ?.mapNotNull { (it as? Number)?.toInt() }
                ?.toSet() ?: emptySet()
            Trigger.TimeOfDay(hour, minute, days)
        }
        else -> null
    }

    private fun actionToMap(action: AutomationAction): Map<String, Any> = when (action) {
        is AutomationAction.OpenApp -> mapOf("type" to "openApp", "packageName" to action.packageName)
        is AutomationAction.SendText -> mapOf("type" to "sendText", "number" to action.number, "message" to action.message)
        is AutomationAction.Call -> mapOf("type" to "call", "number" to action.number, "direct" to action.direct)
        is AutomationAction.TapLabel -> mapOf("type" to "tapLabel", "label" to action.label)
        is AutomationAction.TypeText -> mapOf("type" to "typeText", "text" to action.text)
        AutomationAction.GoHome -> mapOf("type" to "goHome")
        AutomationAction.GoBack -> mapOf("type" to "goBack")
        is AutomationAction.PlayTrack -> mapOf("type" to "playTrack", "query" to action.query)
        AutomationAction.PauseMedia -> mapOf("type" to "pauseMedia")
    }

    private fun actionFromMap(map: Map<String, Any?>): AutomationAction? = when (map["type"] as? String) {
        "openApp" -> (map["packageName"] as? String)?.let { AutomationAction.OpenApp(it) }
        "sendText" -> {
            val number = map["number"] as? String ?: return null
            val message = map["message"] as? String ?: return null
            AutomationAction.SendText(number, message)
        }
        "call" -> {
            val number = map["number"] as? String ?: return null
            val direct = map["direct"] as? Boolean ?: false
            AutomationAction.Call(number, direct)
        }
        "tapLabel" -> (map["label"] as? String)?.let { AutomationAction.TapLabel(it) }
        "typeText" -> (map["text"] as? String)?.let { AutomationAction.TypeText(it) }
        "goHome" -> AutomationAction.GoHome
        "goBack" -> AutomationAction.GoBack
        "playTrack" -> (map["query"] as? String)?.let { AutomationAction.PlayTrack(it) }
        "pauseMedia" -> AutomationAction.PauseMedia
        else -> null
    }
}
