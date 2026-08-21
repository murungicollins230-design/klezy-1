package com.klezy.app.media

/**
 * MediaCommandMatcher
 *
 * Same philosophy as P3's DeviceCommandMatcher — catch clear media
 * commands with simple patterns and act instantly, no LLM needed.
 * Wire this into VoicePipelineOrchestrator.handleTranscript() alongside
 * DeviceCommandMatcher (check media commands first, since "play" and
 * "open" phrasing can otherwise overlap).
 */
object MediaCommandMatcher {

    private val playPattern = Regex("""play\s+(.+)""", RegexOption.IGNORE_CASE)

    fun tryHandle(router: MediaControlRouter, text: String): String? {
        val trimmed = text.trim()

        playPattern.find(trimmed)?.let { match ->
            val query = match.groupValues[1].trim()
            return if (router.playByTitle(query)) "Playing $query"
            else "Couldn't find \"$query\" in your library"
        }

        return when {
            trimmed.equals("pause", ignoreCase = true) || trimmed.equals("stop the music", ignoreCase = true) -> {
                router.pause(); "Paused"
            }
            trimmed.equals("resume", ignoreCase = true) || trimmed.equals("play", ignoreCase = true) -> {
                router.resume(); "Resuming"
            }
            trimmed.equals("next", ignoreCase = true) || trimmed.equals("skip", ignoreCase = true) -> {
                router.next(); "Skipping"
            }
            trimmed.equals("previous", ignoreCase = true) || trimmed.equals("go back", ignoreCase = true) -> {
                router.previous(); "Going back"
            }
            else -> null
        }
    }
}
