package org.koocheh.model

/** Rotating ephemeral identifier. Equality is by content; toString never prints the bytes. */
class EphemeralId(bytes: ByteArray) {
    private val data: ByteArray = bytes.copyOf()

    val bytes: ByteArray get() = data.copyOf()

    override fun equals(other: Any?): Boolean =
        this === other || (other is EphemeralId && data.contentEquals(other.data))

    override fun hashCode(): Int = data.contentHashCode()

    override fun toString(): String = "EphemeralId(redacted)"
}

/** A single observation of a nearby device. Never persisted. */
class Sighting(
    val ephemeralId: EphemeralId,
    val rssiDbm: Int,
    val seenAtElapsedMs: Long,
) {
    override fun toString(): String = "Sighting(redacted, rssiDbm=$rssiDbm)"
}
