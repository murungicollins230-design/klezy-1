package com.klezy.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.klezy.app.automation.AutomationRule
import kotlinx.coroutines.tasks.await

/**
 * AutomationRuleRepository
 *
 * This is what makes P2's automations survive an app restart or reinstall —
 * the missing piece flagged as a known limitation back in P2.
 *
 * Firestore's client SDK caches data locally and syncs when back online by
 * default, so rules stay readable/writable offline too — no extra code
 * needed for that part, it's built into the Spark (free) plan.
 */
class AutomationRuleRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun rulesCollection(uid: String) =
        db.collection("users").document(uid).collection("automation_rules")

    suspend fun saveRule(uid: String, rule: AutomationRule) {
        rulesCollection(uid).document(rule.id).set(AutomationRuleMapper.toMap(rule)).await()
    }

    suspend fun deleteRule(uid: String, ruleId: String) {
        rulesCollection(uid).document(ruleId).delete().await()
    }

    suspend fun loadAllRules(uid: String): List<AutomationRule> {
        val snapshot = rulesCollection(uid).get().await()
        return snapshot.documents.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            AutomationRuleMapper.fromMap(doc.data as? Map<String, Any?> ?: return@mapNotNull null)
        }
    }

    /** Live updates — call this if you want the UI or engine to react to changes made on another device. */
    fun listenForChanges(uid: String, onUpdate: (List<AutomationRule>) -> Unit): ListenerRegistration {
        return rulesCollection(uid).addSnapshotListener { snapshot, _ ->
            val rules = snapshot?.documents?.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                AutomationRuleMapper.fromMap(doc.data as? Map<String, Any?> ?: return@mapNotNull null)
            } ?: emptyList()
            onUpdate(rules)
        }
    }
}
