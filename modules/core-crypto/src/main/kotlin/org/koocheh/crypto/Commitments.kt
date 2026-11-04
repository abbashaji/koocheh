package org.koocheh.crypto

import java.security.MessageDigest

/** Commitment plus the nonce needed to open it. toString never prints either. */
class Commit(commitment: ByteArray, nonce: ByteArray) {
    private val c: ByteArray = commitment.copyOf()
    private val n: ByteArray = nonce.copyOf()

    val commitment: ByteArray get() = c.copyOf()
    val nonce: ByteArray get() = n.copyOf()

    override fun toString(): String = "Commit(redacted)"
}

/**
 * Commit-then-reveal helper. commitment = H(domain || nonce || value) with a fixed-length
 * random nonce, so the encoding is unambiguous. Binding comes from the hash, hiding from
 * the 32-byte random nonce.
 */
class Commitments(private val backend: CryptoBackend) {
    fun commit(value: ByteArray): Commit {
        val nonce = backend.randomBytes(NONCE_BYTES)
        return Commit(compute(nonce, value), nonce)
    }

    fun verify(commitment: ByteArray, nonce: ByteArray, value: ByteArray): Boolean {
        if (nonce.size != NONCE_BYTES) return false
        return MessageDigest.isEqual(compute(nonce, value), commitment)
    }

    private fun compute(nonce: ByteArray, value: ByteArray): ByteArray =
        backend.hash(DOMAIN + nonce + value, HASH_BYTES)

    private companion object {
        const val NONCE_BYTES = 32
        const val HASH_BYTES = 32
        val DOMAIN = "koocheh/commit/v1".toByteArray(Charsets.UTF_8)
    }
}
