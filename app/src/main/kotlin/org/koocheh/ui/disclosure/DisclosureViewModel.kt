package org.koocheh.ui.disclosure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koocheh.disclosure.DisclosureSession
import org.koocheh.disclosure.PeerPhoto
import org.koocheh.disclosure.TierState

/**
 * Drives a DisclosureSession through Tiers 1 to 3.
 * [photoBytes] maps the contract's PeerPhoto to sanitized bytes; it is injected so this
 * view-model does not depend on PeerPhoto's internal shape.
 */
class DisclosureViewModel(
    private val session: DisclosureSession,
    val photoBytes: (PeerPhoto) -> ByteArray?,
) : ViewModel() {

    val state: StateFlow<TierState> = session.state

    fun sayHello() { viewModelScope.launch { session.sayHello() } }

    fun confirmNext() { viewModelScope.launch { session.confirmNext() } }

    /** Deletes everything exchanged. Available at every tier. */
    fun withdraw() { viewModelScope.launch { session.withdraw() } }
}
