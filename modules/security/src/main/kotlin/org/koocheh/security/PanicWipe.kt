package org.koocheh.security

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koocheh.storage.KeyVault

fun interface DiscoveryStopper { fun stopDiscovery() }
fun interface NotificationClearer { fun clearAll() }
fun interface RecentsClearer { fun clearRecents() }
fun interface ActivityFinisher { fun finishAll() }

/**
 * Single entry point for the panic gesture and the wipe PIN.
 *
 * No network, no confirmation dialog. Key destruction runs first; every later
 * step runs even if an earlier one throws. Safe to call repeatedly: each step
 * is itself a no-op when there is nothing left to do. Nothing is logged.
 */
class PanicWipe(
    private val vault: KeyVault,
    private val discovery: DiscoveryStopper,
    private val notifications: NotificationClearer,
    private val recents: RecentsClearer,
    private val activities: ActivityFinisher
) {
    private val lock = Mutex()

    suspend fun execute() {
        lock.withLock {
            runStep { vault.destroyAll() }
            runStep { discovery.stopDiscovery() }
            runStep { notifications.clearAll() }
            runStep { recents.clearRecents() }
            runStep { activities.finishAll() }
        }
    }

    private inline fun runStep(step: () -> Unit) {
        try {
            step()
        } catch (_: Exception) {
            // Swallowed on purpose: a failed step must not block the rest of the wipe.
        }
    }
}
