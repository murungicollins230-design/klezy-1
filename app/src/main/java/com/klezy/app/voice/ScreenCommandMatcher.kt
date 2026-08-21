package com.klezy.app.voice

import android.content.Context
import android.content.Intent
import com.klezy.app.ui.ChatActivity
import com.klezy.app.ui.SettingsActivity

/**
 * ScreenCommandMatcher
 *
 * There is no nav bar, no home screen full of buttons — Chat and Settings
 * only exist when you ask for them by name ("Klezy, open chat", "open
 * settings"). This is what makes that work: a command match that launches
 * the activity directly, same pattern as DeviceCommandMatcher/MediaCommandMatcher.
 */
object ScreenCommandMatcher {

    private val openChatPhrases = listOf("open chat", "chat with you", "let's chat", "open the chat")
    private val openSettingsPhrases = listOf("open settings", "go to settings", "open your settings")

    /** Returns a spoken confirmation if [text] matched a screen-open command, or null if it didn't. */
    fun tryHandle(context: Context, text: String): String? {
        val lower = text.trim().lowercase()

        if (openChatPhrases.any { lower.contains(it) }) {
            context.startActivity(
                Intent(context, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return "Opening chat"
        }

        if (openSettingsPhrases.any { lower.contains(it) }) {
            context.startActivity(
                Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return "Opening settings"
        }

        return null
    }
}
