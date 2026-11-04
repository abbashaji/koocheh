package org.koocheh.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koocheh.ui.UiCopy
import org.koocheh.ui.lock.SecureScreen
import org.koocheh.ui.lock.SecureWindowApplier

enum class ScanMode { PASSIVE, NORMAL, COMMUTE }

/** Minutes since midnight, start inclusive, end exclusive. */
data class TimeWindow(val startMinute: Int, val endMinute: Int)

/** Defaults are the safest values: Passive scanning, conservative thresholds, no windows. */
data class ScanSettings(
    val mode: ScanMode = ScanMode.PASSIVE,
    val commuteWindows: List<TimeWindow> = emptyList(),
    val tier0Threshold: Int = 3,
    val familiarStrangerThreshold: Int = 5,
)

interface SettingsStore {
    fun load(): ScanSettings
    fun save(settings: ScanSettings)
}

@Composable
fun SettingsScreen(
    store: SettingsStore,
    secureWindow: SecureWindowApplier,
    onAddWindow: () -> Unit,
    onOpenSecurity: () -> Unit,
) = SecureScreen(secureWindow) {
    var settings by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(store.load()) }
    fun update(next: ScanSettings) { settings = next; store.save(next) }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(UiCopy.SETTINGS_TITLE, style = MaterialTheme.typography.headlineSmall)
        Text(UiCopy.SCAN_MODE_LABEL)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ScanMode.PASSIVE to UiCopy.SCAN_PASSIVE,
                ScanMode.NORMAL to UiCopy.SCAN_NORMAL,
                ScanMode.COMMUTE to UiCopy.SCAN_COMMUTE,
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = settings.mode == mode,
                    onClick = { update(settings.copy(mode = mode)) },
                    label = { Text(label) },
                )
            }
        }
        if (settings.mode == ScanMode.COMMUTE) {
            Text(UiCopy.SCAN_COMMUTE_NOTE)
            settings.commuteWindows.forEach { Text(formatWindow(it)) }
            OutlinedButton(onClick = onAddWindow) { Text(UiCopy.WINDOW_ADD) }
        }
        Text("${UiCopy.TIER0_LABEL}: ${settings.tier0Threshold}")
        Slider(
            value = settings.tier0Threshold.toFloat(),
            onValueChange = { update(settings.copy(tier0Threshold = it.toInt())) },
            valueRange = 1f..10f, steps = 8,
        )
        Text("${UiCopy.FAMILIAR_LABEL}: ${settings.familiarStrangerThreshold}")
        Slider(
            value = settings.familiarStrangerThreshold.toFloat(),
            onValueChange = { update(settings.copy(familiarStrangerThreshold = it.toInt())) },
            valueRange = 2f..20f, steps = 17,
        )
        OutlinedButton(onClick = onOpenSecurity) { Text(UiCopy.SECURITY_TITLE) }
    }
}

private fun formatWindow(w: TimeWindow): String {
    fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
    return "${hm(w.startMinute)} - ${hm(w.endMinute)}"
}
