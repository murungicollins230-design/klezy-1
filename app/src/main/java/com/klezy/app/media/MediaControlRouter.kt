package com.klezy.app.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

/**
 * MediaControlRouter
 *
 * Same role as P1's DeviceActionRouter, but for playback — this is the
 * single entry point the chat/voice layer calls to control music.
 * Under the hood it talks to MediaPlaybackService through a MediaController,
 * which works whether or not the app's UI is currently open.
 */
class MediaControlRouter(private val context: Context) {

    private var controller: MediaController? = null

    fun connect(onReady: () -> Unit = {}) {
        val sessionToken = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            controller = future.get()
            // Build the queue from the local library once, so next()/previous() have something to move through.
            val tracks = LocalMediaLibrary.getAllTracks(context)
            if (tracks.isNotEmpty()) {
                controller?.setMediaItems(tracks.map { MediaItem.fromUri(it.uri) })
            }
            onReady()
        }, MoreExecutors.directExecutor())
    }

    fun playByTitle(query: String): Boolean {
        val track = LocalMediaLibrary.findByTitle(context, query) ?: return false
        controller?.setMediaItem(MediaItem.fromUri(track.uri))
        controller?.prepare()
        controller?.play()
        return true
    }

    fun pause() = controller?.pause()
    fun resume() = controller?.play()
    fun stop() = controller?.stop()
    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()
    fun isPlaying(): Boolean = controller?.isPlaying == true

    fun release() {
        controller?.release()
        controller = null
    }
}
