package com.klezy.app.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val durationMs: Long
)

/**
 * LocalMediaLibrary
 *
 * Reads audio files already on the device via MediaStore — free, no
 * account, no API key. This is the "streaming" source for P5: Klezy plays
 * from your own library rather than a paid service.
 *
 * Requires READ_MEDIA_AUDIO (Android 13+) or READ_EXTERNAL_STORAGE
 * (older) granted at runtime before querying.
 */
object LocalMediaLibrary {

    fun getAllTracks(context: Context): List<Track> {
        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                tracks.add(
                    Track(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown artist",
                        uri = uri,
                        durationMs = cursor.getLong(durationCol)
                    )
                )
            }
        }
        return tracks
    }

    /** Simple contains-match by title — good enough for "play [song name]" voice commands. */
    fun findByTitle(context: Context, query: String): Track? =
        getAllTracks(context).firstOrNull { it.title.contains(query, ignoreCase = true) }
}
