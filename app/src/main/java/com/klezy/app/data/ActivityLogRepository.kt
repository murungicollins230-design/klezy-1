package com.klezy.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

data class ActivityLogEntry(
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * ActivityLogRepository
 *
 * Raw material for the "learn my patterns and give advice" ask — every
 * command Klezy actually executes gets a one-line entry here (what, when).
 * This file only collects data; InsightsEngine is what turns it into
 * anything resembling advice. Kept separate so logging never breaks if
 * insight generation changes.
 */
class ActivityLogRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun logCollection(uid: String) =
        db.collection("users").document(uid).collection("activity_log")

    fun log(uid: String, summary: String) {
        // Fire-and-forget — a missed log entry shouldn't ever block or
        // slow down the action itself.
        logCollection(uid).add(mapOf("summary" to summary, "timestamp" to System.currentTimeMillis()))
    }

    suspend fun recent(uid: String, sinceMillis: Long, limit: Long = 300): List<ActivityLogEntry> {
        val snapshot = logCollection(uid)
            .whereGreaterThan("timestamp", sinceMillis)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val summary = doc.getString("summary") ?: return@mapNotNull null
            val ts = doc.getLong("timestamp") ?: 0L
            ActivityLogEntry(summary, ts)
        }
    }
}
