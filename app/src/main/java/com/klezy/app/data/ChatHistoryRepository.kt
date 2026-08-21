package com.klezy.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

data class ChatMessageRecord(
    val role: String, // "user" or "klezy"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * ChatHistoryRepository
 *
 * Persists the chat screen's conversation so it survives app restarts —
 * previously every message vanished when the process died, matching the
 * in-memory-only limitation the automation rules had before this phase.
 */
class ChatHistoryRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun messagesCollection(uid: String) =
        db.collection("users").document(uid).collection("chat_history")

    suspend fun append(uid: String, message: ChatMessageRecord) {
        messagesCollection(uid).add(
            mapOf(
                "role" to message.role,
                "text" to message.text,
                "timestamp" to message.timestamp
            )
        ).await()
    }

    suspend fun loadRecent(uid: String, limit: Long = 50): List<ChatMessageRecord> {
        val snapshot = messagesCollection(uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val role = doc.getString("role") ?: return@mapNotNull null
            val text = doc.getString("text") ?: return@mapNotNull null
            val timestamp = doc.getLong("timestamp") ?: 0L
            ChatMessageRecord(role, text, timestamp)
        }.reversed() // oldest first for display
    }

    suspend fun clear(uid: String) {
        val snapshot = messagesCollection(uid).get().await()
        snapshot.documents.forEach { it.reference.delete() }
    }
}
