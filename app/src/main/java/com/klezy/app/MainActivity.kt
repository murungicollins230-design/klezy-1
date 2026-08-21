package com.klezy.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.klezy.app.automation.KlezyForegroundService
import com.klezy.app.automation.ScheduledTriggerWorker
import com.klezy.app.data.ApiKeyStore
import com.klezy.app.data.InsightsWorker
import com.klezy.app.ui.SettingsActivity

/**
 * MainActivity
 *
 * This is deliberately quiet — no nav bar, no button grid. Chat and
 * Settings only ever appear because you asked for them by voice ("open
 * chat", "open settings" — see ScreenCommandMatcher), and the orb only
 * appears on a wake word (see OrbActivity). This screen exists because
 * Android requires a launcher activity for the app icon to open
 * something; it just doesn't try to be the app.
 *
 * Everything it actually does is startup plumbing:
 *  1. Request runtime permissions (mic, contacts, media, notifications)
 *  2. Start KlezyForegroundService — this is what now owns BOTH
 *     automations (P2/P4) and wake-word listening (P3), so both keep
 *     working when this Activity isn't open at all
 *  3. Schedule the time-trigger poller and daily insights worker
 *
 * Accessibility Service and Notification Listener access still can't be
 * requested here — Android forces those through system settings. Once you
 * build real onboarding copy explaining why, wire PermissionHelper's
 * open*Settings() calls to a button here or in Settings.
 */
class MainActivity : ComponentActivity() {

    private val runtimePermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_MEDIA_AUDIO,
        // Was declared in the manifest but never actually requested here —
        // without this, "call mum" silently fell back to opening the
        // dialer instead of calling directly. See DeviceCommandMatcher.
        Manifest.permission.CALL_PHONE
    )
    private val permissionRequestCode = 4001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRuntimePermissionsIfNeeded()

        KlezyForegroundService.start(this)
        ScheduledTriggerWorker.schedule(this)
        InsightsWorker.schedule(this)

        setContent {
            StatusScreen(
                hasPicovoiceKey = !ApiKeyStore.getPicovoiceKey(this).isNullOrBlank(),
                onOpenSettings = {
                    startActivity(android.content.Intent(this, SettingsActivity::class.java))
                }
            )
        }
    }

    private fun requestRuntimePermissionsIfNeeded() {
        val missing = runtimePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), permissionRequestCode)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            // Re-trigger the service check in case RECORD_AUDIO was just
            // granted and a Picovoice key already exists from a prior run.
            KlezyForegroundService.start(this)
        }
    }
}

@Composable
private fun StatusScreen(hasPicovoiceKey: Boolean, onOpenSettings: () -> Unit) {
    MaterialTheme {
        Surface(color = Color(0xFF0B0D12)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .let { if (!hasPicovoiceKey) it.clickable(onClick = onOpenSettings) else it },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFF8FA8FF), Color(0xFF5B6FD6), Color(0xFF2A2F6B)),
                                    center = Offset(0.35f, 0.3f)
                                ),
                                shape = CircleShape
                            )
                    )
                    Spacer(Modifier.height(20.dp))
                    Text("Klezy", color = Color(0xFFEDEFF4), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (hasPicovoiceKey) "Say \"Hey Klezy\" anytime"
                        else "Tap to add a Picovoice key and enable the wake word",
                        color = Color(0xFF767C8C),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
