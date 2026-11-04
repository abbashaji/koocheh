package org.koocheh.crypto

import org.koocheh.model.ContextId
import org.koocheh.model.EphemeralId

interface EphemeralIdProvider {
    /** The id to advertise at [nowMs] for [context]. */
    fun current(context: ContextId, nowMs: Long): EphemeralId

    /** When the transport must re-advertise: the first rotation instant strictly after [nowMs]. */
    fun nextRotationAtMs(context: ContextId, nowMs: Long): Long
}

/**
 * Pure epoch math. Epoch length is 10 to 15 minutes and the phase is an offset, both taken
 * from per-context entropy, so rotation instants are not a fixed global grid.
 */
class EpochSchedule(val lengthMs: Long, val offsetMs: Long) {
    init {
        require(lengthMs in MIN_EPOCH_MS..MAX_EPOCH_MS) { "epoch length out of range" }
        require(offsetMs in 0 until lengthMs) { "epoch offset out of range" }
    }

    fun epochAt(nowMs: Long): Long = Math.floorDiv(nowMs - offsetMs, lengthMs)

    fun nextRotationAtMs(nowMs: Long): Long = (epochAt(nowMs) + 1) * lengthMs + offsetMs

    companion object {
        const val MIN_EPOCH_MS = 10L * 60_000L
        const val MAX_EPOCH_MS = 15L * 60_000L

        /** Both inputs are unsigned 32-bit values held in a Long. */
        fun fromEntropy(lengthEntropy: Long, offsetEntropy: Long): EpochSchedule {
            val length = MIN_EPOCH_MS + lengthEntropy % (MAX_EPOCH_MS - MIN_EPOCH_MS + 1)
            return EpochSchedule(length, offsetEntropy % length)
        }
    }
}

/** id = HKDF(secret(context), salt = context, info = label || epoch), 16 bytes. */
class HkdfEphemeralIdProvider(
    private val secrets: ContextSecretSource,
    private val backend: CryptoBackend,
) : EphemeralIdProvider {

    override fun current(context: ContextId, nowMs: Long): EphemeralId {
        val secret = secretFor(context)
        val epoch = scheduleFor(context, secret).epochAt(nowMs)
        val id = Hkdf.derive(backend, secret, salt(context), ID_INFO + u64be(epoch), ID_BYTES)
        return EphemeralId(id)
    }

    override fun nextRotationAtMs(context: ContextId, nowMs: Long): Long =
        scheduleFor(context, secretFor(context)).nextRotationAtMs(nowMs)

    private fun secretFor(context: ContextId): ByteArray {
        val secret = secrets.secret(context)
        require(secret.size == SECRET_BYTES) { "context secret must be 32 bytes" }
        return secret
    }

    private fun scheduleFor(context: ContextId, secret: ByteArray): EpochSchedule {
        val salt = salt(context)
        val len = readU32be(Hkdf.derive(backend, secret, salt, LEN_INFO, 4))
        val off = readU32be(Hkdf.derive(backend, secret, salt, OFFSET_INFO, 4))
        return EpochSchedule.fromEntropy(len, off)
    }

    private fun salt(context: ContextId): ByteArray = context.name.toByteArray(Charsets.UTF_8)

    private companion object {
        const val SECRET_BYTES = 32
        const val ID_BYTES = 16
        val ID_INFO = "koocheh/eid/v1".toByteArray(Charsets.UTF_8)
        val LEN_INFO = "koocheh/epoch-len/v1".toByteArray(Charsets.UTF_8)
        val OFFSET_INFO = "koocheh/epoch-offset/v1".toByteArray(Charsets.UTF_8)
    }
}
