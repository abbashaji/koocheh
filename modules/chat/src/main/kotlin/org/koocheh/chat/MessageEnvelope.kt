package org.koocheh.chat

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Default message lifetime: 24 hours. Users may only shorten it. */
const val DEFAULT_EXPIRY_HOURS: Long = 24
const val DEFAULT_EXPIRY_MS: Long = DEFAULT_EXPIRY_HOURS * 60 * 60 * 1000
const val MIN_EXPIRY_MS: Long = 60_000

/** PeerChannel allows 4096 bytes per exchange; text is capped so the envelope always fits. */
const val MAX_ENVELOPE_BYTES: Int = 4096
const val MAX_TEXT_BYTES: Int = 1500

object EnvelopeKind {
    const val MESSAGE = 0
    const val WITHDRAW = 1
    const val FIN = 2
}

/**
 * Wire format. Versioned; carries only id, times and ciphertext (base64).
 * Plaintext never appears here.
 */
@Serializable
data class MessageEnvelope(
    val v: Int = VERSION,
    val kind: Int = EnvelopeKind.MESSAGE,
    val id: String,
    val sentAtMs: Long,
    val expiresAtMs: Long,
    val ct: String,
) {
    fun ciphertext(): ByteArray = Base64.getDecoder().decode(ct)

    fun encode(): ByteArray {
        val bytes = JSON.encodeToString(this).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_ENVELOPE_BYTES) { "envelope too large" }
        return bytes
    }

    companion object {
        const val VERSION = 1
        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun of(kind: Int, id: String, sentAtMs: Long, expiresAtMs: Long, ciphertext: ByteArray) =
            MessageEnvelope(
                kind = kind,
                id = id,
                sentAtMs = sentAtMs,
                expiresAtMs = expiresAtMs,
                ct = Base64.getEncoder().encodeToString(ciphertext),
            )

        /** Returns null for malformed or unsupported-version input. */
        fun decode(bytes: ByteArray): MessageEnvelope? = try {
            JSON.decodeFromString<MessageEnvelope>(String(bytes, Charsets.UTF_8))
                .takeIf { it.v == VERSION }
        } catch (e: Exception) {
            null
        }
    }
}
