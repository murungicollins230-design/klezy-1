package com.klezy.app.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.klezy.app.voice.ConversationRouter
import com.klezy.app.voice.LockScreenCommandMatcher
import com.klezy.app.voice.VoiceInputManager
import com.klezy.app.voice.VoiceOutputManager
import kotlinx.coroutines.launch

enum class OrbState { LISTENING, THINKING }

/**
 * OrbActivity
 *
 * This is the ONLY thing that appears when "Hey Klezy" fires — a small
 * floating orb, not the full app. It uses a dialog-styled window
 * (Theme.Klezy.Orb) sized to wrap its own small content instead of
 * filling the screen, so whatever you were doing stays visible behind it.
 * It closes itself the moment the exchange is done.
 *
 * Two modes, same UI, different capability ceiling:
 *  - Locked:   routes through LockScreenCommandMatcher only (torch, media,
 *              volume, camera) — everything else gets a spoken "unlock
 *              your phone for that." Real Android OS boundary, not a
 *              missing feature.
 *  - Unlocked: routes through ConversationRouter — screens, media, device
 *              actions, offline/online model, full conversation memory.
 *              Continues listening for a follow-up (no repeated wake
 *              word) until a command that closes the loop.
 */
class OrbActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LOCKED = "locked"
    }

    private lateinit var voiceInput: VoiceInputManager
    private lateinit var voiceOutput: VoiceOutputManager
    private var uiState by mutableStateOf(OrbState.LISTENING)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val locked = intent.getBooleanExtra(EXTRA_LOCKED, false)
        if (locked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // Theme.Klezy.Orb sizes the window to wrap this small content —
        // see themes.xml. This is what keeps it from covering the screen.
        window.setLayout(280.dpToPx(), 280.dpToPx())

        voiceInput = VoiceInputManager(this)
        voiceOutput = VoiceOutputManager(this)

        setContent { OrbScreen(uiState) }

        listenOnce(locked)
    }

    private fun listenOnce(locked: Boolean) {
        uiState = OrbState.LISTENING
        voiceInput.startListening(
            onResult = { transcript -> handleResult(transcript, locked) },
            onError = { finish() }
        )
    }

    private fun handleResult(transcript: String, locked: Boolean) {
        uiState = OrbState.THINKING
        if (locked) {
            val reply = LockScreenCommandMatcher.tryHandle(this, transcript)
            voiceOutput.speak(reply) { finish() }
            return
        }

        lifecycleScope.launch {
            val result = ConversationRouter.handle(this@OrbActivity, transcript)
            voiceOutput.speak(result.reply) {
                if (result.continueListening) listenOnce(locked = false) else finish()
            }
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        voiceInput.release()
        voiceOutput.release()
        super.onDestroy()
    }
}

@Composable
private fun OrbScreen(state: OrbState) {
    MaterialTheme {
        Surface(color = Color.Transparent) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PulsingOrb(thinking = state == OrbState.THINKING)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (state == OrbState.THINKING) "…" else "Listening…",
                        color = Color(0xFFEDEFF4)
                    )
                }
            }
        }
    }
}

@Composable
private fun PulsingOrb(thinking: Boolean) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (thinking) 1.22f else 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (thinking) 700 else 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    Box(
        Modifier
            .size(88.dp)
            .scale(scale)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF8FA8FF), Color(0xFF5B6FD6), Color(0xFF2A2F6B)),
                    center = Offset(0.35f, 0.3f)
                ),
                shape = CircleShape
            )
    )
}
