package org.koocheh.disclosure

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Withdrawal at any tier: tell the peer (best effort), delete everything exchanged from the
 * store, and discard in-memory state. Deletion always runs, even if notifying the peer fails.
 */
class WithdrawalHandler(private val store: ContextStore, private val peerKey: String) {
    suspend fun withdraw(notifyPeer: suspend () -> Unit, discardMemory: () -> Unit) {
        withContext(NonCancellable) {
            try {
                notifyPeer()
            } catch (e: Exception) {
                // Peer may already be gone; local deletion below is what matters.
            } finally {
                try {
                    store.deletePeer(peerKey)
                } finally {
                    discardMemory()
                }
            }
        }
    }
}
