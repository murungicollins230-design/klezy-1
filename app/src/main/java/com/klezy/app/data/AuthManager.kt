package com.klezy.app.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * AuthManager
 *
 * Klezy is a single-user personal app, so anonymous auth is the sensible
 * default — it gives every install a stable Firestore user ID with zero
 * login screen. Email/password is here as an optional upgrade if you
 * ever want the same data to follow you across a phone reset or a second device.
 *
 * Firebase Spark (free) plan covers auth at this scale with no cost.
 */
object AuthManager {

    private val auth = FirebaseAuth.getInstance()

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val isSignedIn: Boolean
        get() = auth.currentUser != null

    /** Call once at app startup. Creates a stable anonymous identity if none exists yet. */
    suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: error("Anonymous sign-in returned no user")
    }

    /**
     * Upgrades the current anonymous account to a permanent email/password one,
     * preserving the same uid (and therefore all existing Firestore data).
     */
    suspend fun linkEmail(email: String, password: String) {
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
        auth.currentUser?.linkWithCredential(credential)?.await()
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    fun signOut() {
        auth.signOut()
    }
}
