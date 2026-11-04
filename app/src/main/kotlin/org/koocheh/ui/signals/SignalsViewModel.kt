package org.koocheh.ui.signals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koocheh.familiar.FamiliarPrompt

/** Remembers which familiar prompts were dismissed, keyed per prompt and per week. */
interface DismissedPrompts {
    fun isDismissed(key: String): Boolean
    fun dismiss(key: String)
}

class InMemoryDismissedPrompts : DismissedPrompts {
    private val keys = mutableSetOf<String>()
    override fun isDismissed(key: String) = key in keys
    override fun dismiss(key: String) { keys.add(key) }
}

/** Tier 0 row: carries only a shared-interest count. No identity, no timestamp. */
data class SignalRow(val sharedCount: Int)

data class SignalsUiState(
    val rows: List<SignalRow> = emptyList(),
    val prompts: List<FamiliarPrompt> = emptyList(),
)

class SignalsViewModel(
    sharedCounts: Flow<List<Int>>,
    threshold: Flow<Int>,
    private val loadPrompts: suspend (today: Int) -> List<FamiliarPrompt>,
    private val today: () -> Int,
    private val dismissed: DismissedPrompts = InMemoryDismissedPrompts(),
) : ViewModel() {

    private val prompts = MutableStateFlow<List<FamiliarPrompt>>(emptyList())

    val state: StateFlow<SignalsUiState> =
        combine(sharedCounts, threshold, prompts) { counts, min, p ->
            SignalsUiState(
                rows = counts.filter { it >= min }.sortedDescending().map { SignalRow(it) },
                prompts = p,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignalsUiState())

    init { refreshPrompts() }

    fun refreshPrompts() {
        viewModelScope.launch {
            prompts.value = loadPrompts(today()).filterNot { dismissed.isDismissed(keyOf(it)) }
        }
    }

    fun dismiss(prompt: FamiliarPrompt) {
        dismissed.dismiss(keyOf(prompt))
        val gone = keyOf(prompt)
        prompts.value = prompts.value.filterNot { keyOf(it) == gone }
    }

    // Same prompt, same week => same key => never shown again that week.
    private fun keyOf(p: FamiliarPrompt) = "${p.encounters}:${p.windowDays}:${today() / 7}"
}
