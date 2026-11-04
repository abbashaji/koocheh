package org.koocheh.ui.signals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koocheh.familiar.FamiliarPrompt

@Composable
fun FamiliarPromptCard(
    prompt: FamiliarPrompt,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Someone familiar seems to be around. You have crossed paths " +
                    "${prompt.encounters} times in the last ${prompt.windowDays} days.",
            )
            Text(
                "There is nothing you need to do. Saying hello is always your choice.",
                modifier = Modifier.padding(top = 8.dp),
            )
            Row { TextButton(onClick = onDismiss) { Text("Dismiss") } }
        }
    }
}
