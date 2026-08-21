package com.klezy.app.media

import android.content.Context

/**
 * MediaRuntime
 *
 * One MediaControlRouter, connected once when the foreground service
 * starts, reused everywhere (the orb, AutomationEngine) instead of each
 * caller reconnecting to MediaPlaybackService separately.
 */
object MediaRuntime {
    private var router: MediaControlRouter? = null

    fun init(context: Context) {
        if (router != null) return
        router = MediaControlRouter(context.applicationContext).also { it.connect() }
    }

    fun get(): MediaControlRouter? = router
}
