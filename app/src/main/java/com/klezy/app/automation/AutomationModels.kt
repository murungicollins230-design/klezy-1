package com.klezy.app.automation

/**
 * AutomationModels
 *
 * A rule is: when [Trigger] fires, run [actions] in order.
 * Persisted via AutomationRuleRepository (Firestore) — see data/AutomationRuleMapper.kt
 * for how these sealed classes convert to/from Firestore-storable maps.
 */

sealed class Trigger {
    /** Fires when a notification arrives matching packageName and/or keyword. Null = match any. */
    data class NotificationReceived(
        val packageName: String? = null,
        val keyword: String? = null
    ) : Trigger()

    /** Fires when the given app comes to the foreground. */
    data class AppOpened(val packageName: String) : Trigger()

    /**
     * Fires at a specific clock time, optionally restricted to certain days.
     * daysOfWeek uses Calendar constants (1 = Sunday .. 7 = Saturday); empty = every day.
     * Note: checked via a poller with ~15 min minimum resolution, see ScheduledTriggerWorker.
     */
    data class TimeOfDay(
        val hour: Int,
        val minute: Int,
        val daysOfWeek: Set<Int> = emptySet()
    ) : Trigger()
}

sealed class AutomationAction {
    data class OpenApp(val packageName: String) : AutomationAction()
    data class SendText(val number: String, val message: String) : AutomationAction()
    data class Call(val number: String, val direct: Boolean = false) : AutomationAction()
    data class TapLabel(val label: String) : AutomationAction()
    data class TypeText(val text: String) : AutomationAction()
    object GoHome : AutomationAction()
    object GoBack : AutomationAction()
    // Media actions (P5) — kept here rather than a separate sealed class so
    // AutomationEngine.execute() can dispatch on one type instead of two.
    data class PlayTrack(val query: String) : AutomationAction()
    object PauseMedia : AutomationAction()
}

data class AutomationRule(
    val id: String,
    val name: String,
    val trigger: Trigger,
    val actions: List<AutomationAction>,
    val enabled: Boolean = true
)
