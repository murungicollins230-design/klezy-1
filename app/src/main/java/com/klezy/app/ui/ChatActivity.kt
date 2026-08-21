package com.klezy.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.klezy.app.data.ApiKeyStore
import com.klezy.app.data.AuthManager
import com.klezy.app.data.ChatHistoryRepository
import com.klezy.app.data.ChatMessageRecord
import com.klezy.app.network.GroqApiClient
import kotlinx.coroutines.launch

/**
 * ChatActivity
 *
 * Only reachable by saying "open chat" (see ScreenCommandMatcher) — no
 * persistent nav entry. Shows real persisted history from P4's
 * ChatHistoryRepository, and includes the erase button for freeing
 * storage if Firestore's free tier ever gets close to its cap.
 */
class ChatActivity : ComponentActivity() {

    private val repository = ChatHistoryRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChatScreen(repository, applicationContext) }
    }
}

@Composable
private fun ChatScreen(repository: ChatHistoryRepository, appContext: android.content.Context) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf(listOf<ChatMessageRecord>()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var showEraseConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val uid = AuthManager.ensureSignedIn()
        messages = repository.loadRecent(uid, limit = 100)
    }

    MaterialTheme {
        Surface(color = Color(0xFF0B0D12)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Klezy", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { showEraseConfirm = true }) {
                        Text("Erase chats", color = Color(0xFFFF8F8F))
                    }
                }

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    Modifier.weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { msg -> MessageBubble(msg) }
                }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message Klezy") }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !sending && input.isNotBlank(),
                        onClick = {
                            val text = input.trim()
                            input = ""
                            sending = true
                            scope.launch {
                                val uid = AuthManager.ensureSignedIn()
                                repository.append(uid, ChatMessageRecord("user", text))
                                messages = messages + ChatMessageRecord("user", text)

                                val apiKey = ApiKeyStore.getGroqKey(appContext)
                                val reply = if (apiKey.isNullOrBlank()) {
                                    "Add a Groq key in Settings first — say \"open settings\"."
                                } else {
                                    val history = messages.map {
                                        (if (it.role == "klezy") "assistant" else "user") to it.text
                                    }
                                    when (val result = GroqApiClient(apiKey).chat(history)) {
                                        is GroqApiClient.Result.Success -> result.reply
                                        is GroqApiClient.Result.Failure -> "Couldn't reach the online brain just now."
                                    }
                                }
                                repository.append(uid, ChatMessageRecord("klezy", reply))
                                messages = messages + ChatMessageRecord("klezy", reply)
                                sending = false
                            }
                        }
                    ) { Text(if (sending) "…" else "Send") }
                }
            }

            if (showEraseConfirm) {
                AlertDialog(
                    onDismissRequest = { showEraseConfirm = false },
                    title = { Text("Erase all chat history?") },
                    text = { Text("This frees up storage but can't be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                val uid = AuthManager.ensureSignedIn()
                                repository.clear(uid)
                                messages = emptyList()
                                showEraseConfirm = false
                            }
                        }) { Text("Erase", color = Color(0xFFFF8F8F)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEraseConfirm = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessageRecord) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) Color(0xFF2A2E3A) else Color(0xFF171A21),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                msg.text,
                color = Color(0xFFEDEFF4),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
