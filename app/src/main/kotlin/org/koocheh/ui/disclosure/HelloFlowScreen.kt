package org.koocheh.ui.disclosure

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koocheh.ui.nav.ApplySecureWindow

@Composable
fun HelloFlowScreen(
    viewModel: DisclosureViewModel,
    onWithdrawn: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ApplySecureWindow()
    val s by viewModel.state.collectAsState()

    Column(modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        if (s.withdrawn) {
            Text("You withdrew. Everything exchanged has been deleted.")
            Button(onClick = onWithdrawn, modifier = Modifier.padding(top = 12.dp)) { Text("Back") }
            return@Column
        }

        when (s.tier) {
            0 -> {
                Text("Say hello?")
                Text(
                    "They will only see that someone nearby shares some interests with them. " +
                        "Nothing more is revealed until they confirm too.",
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(onClick = viewModel::sayHello, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Say hello")
                }
            }
            1 -> {
                Text("You share ${s.sharedCount ?: 0} interests.")
                Text(
                    "Next step: you would both see a nickname, a short line and an age range. " +
                        "Nothing is revealed until they confirm too.",
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(onClick = viewModel::confirmNext, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Confirm")
                }
            }
            2 -> {
                Text(s.peerNickname ?: "")
                s.peerLine?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
                s.peerAgeRange?.let { Text(it.toString(), modifier = Modifier.padding(top = 4.dp)) }
                Text(
                    "Next step: you would both be able to reveal an optional photo. " +
                        "It stays blurred, and nothing is revealed until they confirm too.",
                    modifier = Modifier.padding(top = 8.dp),
                )
                PhotoRevealView(s.peerPhoto?.let(viewModel.photoBytes), s.tier)
                Button(onClick = viewModel::confirmNext, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Confirm")
                }
            }
            else -> {
                Text(s.peerNickname ?: "")
                s.peerLine?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
                PhotoRevealView(s.peerPhoto?.let(viewModel.photoBytes), s.tier)
                Button(onClick = onOpenChat, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Open chat")
                }
            }
        }

        // Visible at every tier.
        OutlinedButton(onClick = viewModel::withdraw, modifier = Modifier.padding(top = 24.dp)) {
            Text("Withdraw")
        }
        Text(
            "Withdrawing deletes everything exchanged.",
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
