package org.koocheh.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.koocheh.ui.UiCopy
import org.koocheh.ui.lock.SecureScreen
import org.koocheh.ui.lock.SecureWindowApplier

/** UI callback into the invites contract. The screen never calls invites code directly. */
interface InviteActivationCallback {
    fun activate(code: String, onResult: (success: Boolean) -> Unit)
}

private enum class Step { BRIEFING, INVITE, PIN }

private const val MIN_PIN = 6

@Composable
fun OnboardingScreen(
    invites: InviteActivationCallback,
    secureWindow: SecureWindowApplier,
    onPinCreated: (CharArray) -> Unit,
) = SecureScreen(secureWindow) {
    var step by remember { mutableStateOf(Step.BRIEFING) }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (step) {
            Step.BRIEFING -> BriefingStep { step = Step.INVITE }
            Step.INVITE -> InviteStep(invites) { step = Step.PIN }
            Step.PIN -> PinStep(onPinCreated)
        }
    }
}

@Composable
private fun BriefingStep(onContinue: () -> Unit) {
    var acknowledged by remember { mutableStateOf(false) }
    Text(UiCopy.BRIEFING_TITLE, style = MaterialTheme.typography.headlineSmall)
    UiCopy.BRIEFING_POINTS.forEach { Text(it) }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
        Text(UiCopy.BRIEFING_ACK)
    }
    Button(onClick = onContinue, enabled = acknowledged, modifier = Modifier.fillMaxWidth()) {
        Text(UiCopy.BRIEFING_CONTINUE)
    }
}

@Composable
private fun InviteStep(invites: InviteActivationCallback, onActivated: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    Text(UiCopy.INVITE_TITLE, style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = code,
        onValueChange = { code = it; failed = false },
        label = { Text(UiCopy.INVITE_HINT) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    if (failed) Text(UiCopy.INVITE_FAILED, color = MaterialTheme.colorScheme.error)
    Button(
        onClick = {
            invites.activate(code.trim()) { ok -> if (ok) onActivated() else failed = true }
        },
        enabled = code.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(UiCopy.INVITE_ACTIVATE) }
}

@Composable
private fun PinStep(onPinCreated: (CharArray) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val error = when {
        pin.isNotEmpty() && pin.length < MIN_PIN -> UiCopy.PIN_TOO_SHORT
        confirm.isNotEmpty() && pin != confirm -> UiCopy.PIN_MISMATCH
        else -> null
    }
    val numeric = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    Text(UiCopy.PIN_TITLE, style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = pin, onValueChange = { pin = it }, label = { Text(UiCopy.PIN_HINT) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = numeric,
    )
    OutlinedTextField(
        value = confirm, onValueChange = { confirm = it }, label = { Text(UiCopy.PIN_CONFIRM_HINT) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = numeric,
    )
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
    Button(
        onClick = {
            val chars = pin.toCharArray()
            pin = ""; confirm = ""
            onPinCreated(chars)
        },
        enabled = pin.length >= MIN_PIN && pin == confirm,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(UiCopy.PIN_SAVE) }
}
