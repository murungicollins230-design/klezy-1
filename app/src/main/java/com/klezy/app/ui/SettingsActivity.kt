package com.klezy.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.klezy.app.data.ApiKeyStore
import com.klezy.app.data.AuthManager
import com.klezy.app.util.PermissionHelper
import com.klezy.app.voice.VoiceOutputManager
import kotlinx.coroutines.launch

/**
 * SettingsActivity
 *
 * Everything that genuinely can't be done by voice lives here, and only
 * here — API keys need typing, permissions need a settings-screen jump
 * Android forces, voice selection benefits from hearing a preview. Only
 * reachable by saying "open settings" (see ScreenCommandMatcher).
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SettingsScreen(this) }
    }
}

@Composable
private fun SettingsScreen(activity: ComponentActivity) {
    val scope = rememberCoroutineScope()
    var groqKey by remember { mutableStateOf(ApiKeyStore.getGroqKey(activity) ?: "") }
    var picovoiceKey by remember { mutableStateOf(ApiKeyStore.getPicovoiceKey(activity) ?: "") }
    var accessibilityOn by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(activity)) }
    var notificationsOn by remember { mutableStateOf(PermissionHelper.isNotificationListenerEnabled(activity)) }

    val voiceOutput = remember { VoiceOutputManager(activity) }
    var selectedVoiceName by remember { mutableStateOf<String?>(null) }
    var femaleVoices by remember { mutableStateOf(listOf<android.speech.tts.Voice>()) }

    LaunchedEffect(Unit) {
        // Give TTS a moment to finish init before listing voices.
        kotlinx.coroutines.delay(400)
        femaleVoices = voiceOutput.listVoices().filter { it.name.contains("female", ignoreCase = true) }
            .ifEmpty { voiceOutput.listVoices() } // fall back to all voices if none are explicitly tagged female
    }

    MaterialTheme {
        Surface(color = Color(0xFF0B0D12)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text("Settings", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(24.dp))

                SectionLabel("Groq API key — online brain")
                OutlinedTextField(
                    value = groqKey,
                    onValueChange = { groqKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("gsk_...") }
                )
                Button(onClick = { ApiKeyStore.saveGroqKey(activity, groqKey) }) { Text("Save") }

                Spacer(Modifier.height(24.dp))

                SectionLabel("Picovoice access key — \"Hey Klezy\" wake word")
                OutlinedTextField(
                    value = picovoiceKey,
                    onValueChange = { picovoiceKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("access key from console.picovoice.ai") }
                )
                Button(onClick = {
                    ApiKeyStore.savePicovoiceKey(activity, picovoiceKey)
                    // Nudges the already-running foreground service to check
                    // for the key again and start wake-word listening —
                    // otherwise it'd only pick this up on next app launch.
                    com.klezy.app.automation.KlezyForegroundService.start(activity)
                }) { Text("Save") }

                Spacer(Modifier.height(24.dp))

                SectionLabel("Accessibility service — lets Klezy tap/type in apps")
                StatusRow(
                    enabled = accessibilityOn,
                    onFix = { PermissionHelper.openAccessibilitySettings(activity) }
                )

                Spacer(Modifier.height(16.dp))

                SectionLabel("Notification access — lets automations react to notifications")
                StatusRow(
                    enabled = notificationsOn,
                    onFix = { PermissionHelper.openNotificationListenerSettings(activity) }
                )

                Spacer(Modifier.height(24.dp))

                SectionLabel("Voice — pick a natural-sounding voice")
                Text(
                    "Android's built-in voices vary by phone — some sound more " +
                        "natural than others. A noticeably more human voice (ElevenLabs) " +
                        "is a paid upgrade, deliberately skipped for now.",
                    color = Color(0xFF767C8C),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                femaleVoices.take(6).forEach { voice ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedVoiceName == voice.name,
                            onClick = {
                                selectedVoiceName = voice.name
                                voiceOutput.setVoice(voice)
                                voiceOutput.speak("Hi, this is how I sound.")
                            }
                        )
                        Text(voice.name.substringBefore("-"), color = Color(0xFFEDEFF4))
                    }
                }

                Spacer(Modifier.height(32.dp))

                SectionLabel("Account")
                var uid by remember { mutableStateOf("") }
                LaunchedEffect(Unit) { uid = AuthManager.currentUserId ?: "not signed in yet" }
                Text("Signed in anonymously as: $uid", color = Color(0xFF767C8C), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color(0xFF8FA8FF), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun StatusRow(enabled: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(if (enabled) "Enabled" else "Not enabled", color = if (enabled) Color(0xFF7CD992) else Color(0xFFFF8F8F))
        Spacer(Modifier.width(12.dp))
        if (!enabled) {
            TextButton(onClick = onFix) { Text("Fix in settings") }
        }
    }
}
