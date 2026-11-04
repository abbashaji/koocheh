package org.koocheh.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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

data class LauncherAliasOption(val id: String, val label: String)

/** Seam over KeyVault.enrollDecoyPin/enrollWipePin, the panic setting and LauncherAliasManager.select. */
interface SecurityActions {
    fun enrollDecoyPin(pin: CharArray)
    fun enrollWipePin(pin: CharArray)
    fun setPanicGestureEnabled(enabled: Boolean)
    fun selectLauncherAlias(aliasId: String)
}

@Composable
fun SecuritySettingsScreen(
    actions: SecurityActions,
    aliases: List<LauncherAliasOption>,
    initialAliasId: String,
    secureWindow: SecureWindowApplier,
    panicInitiallyEnabled: Boolean = true, // safest default: on
) = SecureScreen(secureWindow) {
    var panic by remember { mutableStateOf(panicInitiallyEnabled) }
    var aliasId by remember { mutableStateOf(initialAliasId) }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(UiCopy.SECURITY_TITLE, style = MaterialTheme.typography.headlineSmall)
        PinEnrollRow(UiCopy.DECOY_PIN_LABEL, null, actions::enrollDecoyPin)
        PinEnrollRow(UiCopy.WIPE_PIN_LABEL, UiCopy.WIPE_PIN_NOTE, actions::enrollWipePin)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = panic, onCheckedChange = { panic = it; actions.setPanicGestureEnabled(it) })
            Text(UiCopy.PANIC_LABEL, Modifier.padding(start = 12.dp))
        }
        Text(UiCopy.PANIC_NOTE, style = MaterialTheme.typography.bodySmall)
        Text(UiCopy.ALIAS_LABEL)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            aliases.forEach { opt ->
                FilterChip(
                    selected = aliasId == opt.id,
                    onClick = { aliasId = opt.id; actions.selectLauncherAlias(opt.id) },
                    label = { Text(opt.label) },
                )
            }
        }
        Text(UiCopy.ALIAS_NOTE, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PinEnrollRow(label: String, note: String?, onEnroll: (CharArray) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Text(label)
    if (note != null) Text(note, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = pin, onValueChange = { pin = it }, modifier = Modifier.weight(1f), singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        OutlinedButton(
            onClick = { val c = pin.toCharArray(); pin = ""; onEnroll(c) },
            enabled = pin.length >= 6,
        ) { Text(UiCopy.PIN_SET) }
    }
}
