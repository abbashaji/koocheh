package org.koocheh.security

import org.koocheh.storage.UnlockResult

enum class AppMode { REAL, DECOY, LOCKED }

/**
 * Maps a KeyVault unlock outcome to the mode the app shell should render.
 *
 * WIPED and WRONG both map to LOCKED. The shell must render LOCKED exactly like
 * a fresh install's PIN screen, so a wiped device cannot be told apart from a
 * new one. DECOY is a fully working app over an empty decoy store.
 *
 * Decoy mode is a plausible-deniability aid only. It does not defeat compelled
 * unlock and must never be described as doing so.
 */
class DecoyGate {
    fun route(result: UnlockResult): AppMode = when (result) {
        UnlockResult.REAL -> AppMode.REAL
        UnlockResult.DECOY -> AppMode.DECOY
        UnlockResult.WIPED -> AppMode.LOCKED
        UnlockResult.WRONG -> AppMode.LOCKED
    }
}
