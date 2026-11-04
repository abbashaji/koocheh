package org.koocheh.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import org.koocheh.chat.ChatService
import org.koocheh.ui.nav.ApplySecureWindow

/** Messages are shown with their expiry time. Nothing here posts to notifications. */
@Composable
fun ChatScreen(
    service: ChatService,
    peerKey: String,
    modifier: Modifier = Modifier,
) {
    ApplySecureWindow()
    val messages by remember(peerKey) { service.conversation(peerKey) }
        .collectAsState(initial = emptyList())
    var draft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val timeFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.id }) { m ->
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start,
                ) {
                    Text(m.text)
                    Text("Disappears ${timeFormat.format(Date(m.expiresAtMs))}")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                label = { Text("Message") },
            )
            Button(
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        draft = ""
                        scope.launch { service.send(peerKey, text) }
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Send") }
        }
    }
}
