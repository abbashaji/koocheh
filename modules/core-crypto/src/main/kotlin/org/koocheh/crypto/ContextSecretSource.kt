package org.koocheh.crypto

import org.koocheh.model.ContextId

/**
 * Supplies the per-context root secret. Implemented later by the storage KeyVault.
 * The returned array is exactly 32 bytes. Callers must not log it.
 */
interface ContextSecretSource {
    fun secret(context: ContextId): ByteArray
}
