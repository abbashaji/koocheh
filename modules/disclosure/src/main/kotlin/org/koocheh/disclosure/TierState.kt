package org.koocheh.disclosure

import org.koocheh.model.AgeRange
import org.koocheh.model.DisclosureTier
import org.koocheh.model.InterestTag

class PeerPhoto(val bytes: ByteArray, val blurred: Boolean) {
    override fun equals(other: Any?): Boolean =
        other is PeerPhoto && blurred == other.blurred && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + blurred.hashCode()

    override fun toString(): String = "PeerPhoto(blurred=$blurred)"
}

data class TierState(
    val tier: DisclosureTier,
    val sharedCount: Int? = null,
    val sharedTags: Set<InterestTag>? = null,
    val peerNickname: String? = null,
    val peerLine: String? = null,
    val peerAgeRange: AgeRange? = null,
    val peerPhoto: PeerPhoto? = null,
    val withdrawn: Boolean = false,
) {
    // Redacted on purpose: tags and nicknames must never reach logs.
    override fun toString(): String = "TierState(tier=$tier, withdrawn=$withdrawn)"

    companion object {
        fun initial(): TierState = TierState(DisclosureTier.T0_SIGNAL)
        fun withdrawn(): TierState = TierState(DisclosureTier.T0_SIGNAL, withdrawn = true)
    }
}
