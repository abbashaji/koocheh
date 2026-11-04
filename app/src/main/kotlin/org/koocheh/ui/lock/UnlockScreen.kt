package org.koocheh.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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

/** Local mirror of org.koocheh.storage.UnlockResult. */
enum class UnlockOutcome { REAL, DECOY, WIPED, WRONG }

/** Local mirror of the DecoyGate AppMode. */
enum class GateMode { REAL, DECOY, LOCKED }

/** Adapter over org.koocheh.storage.KeyVault.unlock(pin). */
interface UnlockVault {
    fun unlock(pin: CharArray): UnlockOutcome
}

/** Adapter over org.koocheh.security.DecoyGate.route(result). */
interface DecoyGateRouter {
    fun route(result: UnlockOutcome): GateMode
}

@Composable
fun UnlockScreen(
    vault: UnlockVault,
    decoyGate: DecoyGateRouter,
    secureWindow: SecureWindowApplier,
    onRouted: (GateMode) -> Unit,
    onStartSetup: () -> Unit,
) = SecureScreen(secureWindow) {
    var pin by remember { mutableStateOf("") }
    var wiped by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    if (wiped) {
        FreshInstallScreen(onStartSetup)
        return@SecureScreen
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text(UiCopy.UNLOCK_TITLE, style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it; showError = false },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
        )
        if (showError) Text(UiCopy.UNLOCK_ERROR, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                val chars = pin.toCharArray()
                val result = vault.unlock(chars)
                chars.fill('\u0000')
                pin = ""
                val mode = decoyGate.route(result)
                when (result) {
                    UnlockOutcome.WIPED -> wiped = true
                    UnlockOutcome.WRONG -> showError = true
                    else -> onRouted(mode)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = pin.isNotEmpty(),
        ) { Text(UiCopy.UNLOCK_ACTION) }
    }
}

@Composable
fun FreshInstallScreen(onStartSetup: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text(UiCopy.FRESH_INSTALL_TITLE, style = MaterialTheme.typography.headlineSmall)
        Text(UiCopy.FRESH_INSTALL_BODY)
        Button(onClick = onStartSetup, modifier = Modifier.fillMaxWidth()) {
            Text(UiCopy.FRESH_INSTALL_ACTION)
        }
    }
}
