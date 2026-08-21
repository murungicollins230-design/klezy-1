package com.klezy.app.automation

import android.content.Context
import android.util.Log
import com.klezy.app.actions.DeviceActionRouter
import com.klezy.app.data.AuthManager
import com.klezy.app.data.AutomationRuleRepository
import com.klezy.app.media.MediaRuntime
import com.klezy.app.notifications.KlezyNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * AutomationEngine
 *
 * Holds the rule set and decides what fires when. Three trigger sources feed it:
 *  - KlezyNotificationListener  -> onNotification()
 *  - KlezyAccessibilityService  -> onAppOpened() (window state changes)
 *  - ScheduledTriggerWorker     -> checkTimeTriggers() (polled via WorkManager)
 *
 * Rules live in memory only for P2 — they reset if the app process dies.
 * Swap the backing list for Room/SharedPreferences-backed storage once
 * P4 persistence lands; the public API here won't need to change.
 */
object AutomationEngine : KlezyNotificationListener.Listener {

    private const val TAG = "AutomationEngine"

    private val rules = mutableListOf<AutomationRule>()
    private var appContext: Context? = null
    private var initialized = false
    private val repository = AutomationRuleRepository()
    private val scope = CoroutineScope(Dispatchers.IO)
    /**
     * Starts the engine listening for triggers. PlayTrack/PauseMedia
     * actions (P5) go through MediaRuntime.get() — the same connected
     * router the voice orb uses, instead of this engine owning a second,
     * separate connection to MediaPlaybackService.
     * Rules are still empty until loadFromCloud() also runs.
     */
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        KlezyNotificationListener.subscribe(this)
        initialized = true
        Log.d(TAG, "Automation engine initialized")
    }

    /** Loads previously saved rules from Firestore into memory. Call once after init() and sign-in. */
    suspend fun loadFromCloud() {
        val uid = AuthManager.ensureSignedIn()
        val saved = repository.loadAllRules(uid)
        rules.clear()
        rules.addAll(saved)
        Log.d(TAG, "Loaded ${saved.size} rule(s) from Firestore")
    }

    // ---------- Rule management ----------
    // Every mutation updates the in-memory list immediately (so triggers keep
    // working instantly) and persists to Firestore in the background.

    fun addRule(rule: AutomationRule) {
        rules.removeAll { it.id == rule.id }
        rules.add(rule)
        persist(rule)
    }

    fun removeRule(id: String) {
        rules.removeAll { it.id == id }
        scope.launch {
            val uid = AuthManager.currentUserId ?: return@launch
            repository.deleteRule(uid, id)
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val index = rules.indexOfFirst { it.id == id }
        if (index != -1) {
            rules[index] = rules[index].copy(enabled = enabled)
            persist(rules[index])
        }
    }

    fun getRules(): List<AutomationRule> = rules.toList()

    private fun persist(rule: AutomationRule) {
        scope.launch {
            val uid = AuthManager.ensureSignedIn()
            repository.saveRule(uid, rule)
        }
    }

    // ---------- Trigger entry points ----------

    override fun onNotification(notification: KlezyNotificationListener.KlezyNotification) {
        matchingRules<Trigger.NotificationReceived> { trigger ->
            val packageMatches = trigger.packageName == null || trigger.packageName == notification.packageName
            val keywordMatches = trigger.keyword == null ||
                notification.title.contains(trigger.keyword, ignoreCase = true) ||
                notification.text.contains(trigger.keyword, ignoreCase = true)
            packageMatches && keywordMatches
        }.forEach { execute(it) }
    }

    /** Call this from KlezyAccessibilityService.onAccessibilityEvent on window changes. */
    fun onAppOpened(packageName: String) {
        matchingRules<Trigger.AppOpened> { it.packageName == packageName }.forEach { execute(it) }
    }

    /** Call this periodically (see ScheduledTriggerWorker) to evaluate time-based rules. */
    fun checkTimeTriggers() {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)

        matchingRules<Trigger.TimeOfDay> { trigger ->
            trigger.hour == hour && trigger.minute == minute &&
                (trigger.daysOfWeek.isEmpty() || trigger.daysOfWeek.contains(dayOfWeek))
        }.forEach { execute(it) }
    }

    // ---------- Internals ----------

    private inline fun <reified T : Trigger> matchingRules(predicate: (T) -> Boolean): List<AutomationRule> =
        rules.filter { it.enabled && it.trigger is T && predicate(it.trigger as T) }

    private fun execute(rule: AutomationRule) {
        val context = appContext
        if (context == null) {
            Log.w(TAG, "Engine not initialized, skipping rule: ${rule.name}")
            return
        }
        Log.d(TAG, "Executing rule: ${rule.name}")
        rule.actions.forEach { action ->
            val result = when (action) {
                is AutomationAction.OpenApp -> DeviceActionRouter.openApp(context, action.packageName)
                is AutomationAction.SendText -> DeviceActionRouter.sendText(context, action.number, action.message)
                is AutomationAction.Call -> DeviceActionRouter.call(context, action.number, action.direct)
                is AutomationAction.TapLabel -> DeviceActionRouter.tapOnScreen(action.label)
                is AutomationAction.TypeText -> DeviceActionRouter.typeText(action.text)
                AutomationAction.GoHome -> DeviceActionRouter.goHome()
                AutomationAction.GoBack -> DeviceActionRouter.goBack()
                is AutomationAction.PlayTrack -> {
                    val played = MediaRuntime.get()?.playByTitle(action.query) ?: false
                    if (played) DeviceActionRouter.ActionResult.Success
                    else DeviceActionRouter.ActionResult.Failed("Track not found or media router not connected: ${action.query}")
                }
                AutomationAction.PauseMedia -> {
                    MediaRuntime.get()?.pause()
                    DeviceActionRouter.ActionResult.Success
                }
            }
            if (result is DeviceActionRouter.ActionResult.Failed) {
                Log.w(TAG, "Action failed in rule '${rule.name}': ${result.reason}")
            }
        }
    }
}
