package com.klezy.app.data

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * FileStorageManager
 *
 * For user files — profile picture, imported documents, exported chat logs.
 * NOT intended for the offline GGUF model from P3: Firebase Spark's free
 * tier gives 5GB total storage and 1GB/day download, which a 1.6GB+ model
 * file would eat through fast if downloaded per-install. Keep the model
 * as a manual one-time device transfer (per P3's README) and reserve
 * Firebase Storage for genuinely small user files.
 */
class FileStorageManager {

    private val storage = FirebaseStorage.getInstance()

    private fun userFolder(uid: String) = storage.reference.child("users/$uid/files")

    suspend fun upload(uid: String, fileName: String, localUri: Uri): String {
        val ref = userFolder(uid).child(fileName)
        ref.putFile(localUri).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun delete(uid: String, fileName: String) {
        userFolder(uid).child(fileName).delete().await()
    }

    suspend fun listFiles(uid: String): List<String> {
        val result = userFolder(uid).listAll().await()
        return result.items.map { it.name }
    }

    suspend fun getDownloadUrl(uid: String, fileName: String): String {
        return userFolder(uid).child(fileName).downloadUrl.await().toString()
    }
}
