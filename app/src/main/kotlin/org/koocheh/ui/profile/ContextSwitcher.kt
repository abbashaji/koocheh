package org.koocheh.ui.profile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ProfileContext(val id: String, val label: String)

/** Per-context profile data. Each context has its own, separate draft. */
data class ProfileDraft(
    val nickname: String = "",
    val ageRange: String = "",
    val tags: List<String> = emptyList(),
    val line: String = "",
    val photoEnabled: Boolean = false, // off by default
    val photoRef: String? = null,
)

/** Storage seam. load(contextId) must only ever return that context's data. */
interface ProfileStore {
    fun load(contextId: String): ProfileDraft
    fun save(contextId: String, draft: ProfileDraft)
}

@Composable
fun ContextSwitcher(
    contexts: List<ProfileContext>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        contexts.forEach { ctx ->
            FilterChip(
                selected = ctx.id == selectedId,
                onClick = { onSelect(ctx.id) },
                label = { Text(ctx.label) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}
