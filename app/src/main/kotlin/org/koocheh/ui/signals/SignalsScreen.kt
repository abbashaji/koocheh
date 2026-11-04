package org.koocheh.ui.signals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koocheh.ui.nav.ApplySecureWindow

/**
 * Tier 0. Each row says only that someone nearby shares N of your interests.
 * No identity, no recency ordering, no unread badge, no counts of "missed" people.
 */
@Composable
fun SignalsScreen(
    viewModel: SignalsViewModel,
    onSayHello: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ApplySecureWindow()
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(state.prompts) { _, prompt ->
            FamiliarPromptCard(prompt, onDismiss = { viewModel.dismiss(prompt) })
        }
        if (state.rows.isEmpty()) {
            item { Text("Nobody to show right now. That is fine.") }
        }
        itemsIndexed(state.rows) { _, row ->
            Card(Modifier.fillMaxWidth().clickable(onClick = onSayHello)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Someone nearby shares ${row.sharedCount} of your interests.")
                    Text(
                        "Tap to consider saying hello. They see nothing about you unless they choose to as well.",
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
