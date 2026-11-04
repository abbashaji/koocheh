package org.koocheh.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koocheh.ui.UiCopy
import org.koocheh.ui.lock.SecureScreen
import org.koocheh.ui.lock.SecureWindowApplier

@Composable
fun ProfileEditorScreen(
    store: ProfileStore,
    contexts: List<ProfileContext>,
    initialContextId: String,
    secureWindow: SecureWindowApplier,
    onPickPhoto: (contextId: String) -> Unit,
) = SecureScreen(secureWindow) {
    var contextId by remember { mutableStateOf(initialContextId) }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(UiCopy.PROFILE_TITLE, style = MaterialTheme.typography.headlineSmall)
        Text(UiCopy.PROFILE_CONTEXT_NOTE)
        ContextSwitcher(contexts, contextId, onSelect = { contextId = it })
        // key() drops all editor state on switch, so the other context's data is never shown.
        key(contextId) { ProfileForm(store, contextId, onPickPhoto) }
    }
}

@Composable
private fun ProfileForm(store: ProfileStore, contextId: String, onPickPhoto: (String) -> Unit) {
    var draft by remember { mutableStateOf(store.load(contextId)) }
    var newTag by remember { mutableStateOf("") }

    OutlinedTextField(
        value = draft.nickname, onValueChange = { draft = draft.copy(nickname = it) },
        label = { Text(UiCopy.NICKNAME_LABEL) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
    )
    OutlinedTextField(
        value = draft.ageRange, onValueChange = { draft = draft.copy(ageRange = it) },
        label = { Text(UiCopy.AGE_RANGE_LABEL) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
    )
    Text("${UiCopy.TAGS_LABEL}: ${draft.tags.joinToString(", ")}")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = newTag, onValueChange = { newTag = it },
            modifier = Modifier.weight(1f), singleLine = true,
        )
        OutlinedButton(
            onClick = { draft = draft.copy(tags = draft.tags + newTag.trim()); newTag = "" },
            enabled = newTag.isNotBlank() && draft.tags.size < UiCopy.MAX_TAGS,
        ) { Text(UiCopy.TAG_ADD) }
    }
    OutlinedTextField(
        value = draft.line,
        onValueChange = { draft = draft.copy(line = it.take(UiCopy.MAX_LINE)) },
        label = { Text(UiCopy.LINE_LABEL) }, modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Switch(checked = draft.photoEnabled, onCheckedChange = { draft = draft.copy(photoEnabled = it) })
        Text(UiCopy.PHOTO_LABEL, Modifier.padding(start = 12.dp))
    }
    Text(UiCopy.PHOTO_NOTE, style = MaterialTheme.typography.bodySmall)
    if (draft.photoEnabled) {
        OutlinedButton(onClick = { onPickPhoto(contextId) }) { Text(UiCopy.PHOTO_PICK) }
    }
    Button(onClick = { store.save(contextId, draft) }, modifier = Modifier.fillMaxWidth()) {
        Text(UiCopy.PROFILE_SAVE)
    }
}
