package com.klezy.app.media

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * MediaPlaybackService
 *
 * Media3's MediaSessionService gives background playback + system media
 * controls (lock screen, notification, Bluetooth/headset buttons) for
 * free — it builds the notification automatically from the current
 * MediaItem's metadata, no manual notification-building needed like the
 * old MediaBrowserService days.
 */
class MediaPlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            // Stop the service (and free the player) once playback finishes
            // and nothing else is queued, rather than lingering in the background.
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && mediaItemCount == 0) {
                        stopSelf()
                    }
                }
            })
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        mediaSession.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
}
