package org.koocheh.familiar

import org.koocheh.model.ContextId
import org.koocheh.model.InterestTag
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Turns a completed Tier 0 match into a linkKey so repeat encounters can be counted.
 *
 * OPEN DESIGN POINT: ephemeral ids rotate every 10 to 15 minutes, so there is no stable handle
 * to link encounters, and adding one would break unlinkability. The only thing that can be
 * linked is the match result itself, i.e. the set of shared interest tags.
 *
 * Default [HmacEncounterLinker]: linkKey = HMAC-SHA256(local secret, context + sorted shared tags).
 * The day is deliberately IGNORED here (it stays a parameter for other linkers); the day goes to
 * storage separately, so a count is the number of distinct days the same overlap was seen.
 * Known limits, accepted: it can conflate different people who share an identical overlap, and
 * it cannot link a person whose overlap changes between encounters.
 *
 * The context is part of the input because the store is keyed by linkKey alone; without it two
 * contexts with the same overlap would share one count. Keys and tags are never logged.
 * [UNVERIFIED] Tag text is taken from InterestTag.toString(), assumed stable and normalized.
 */
interface EncounterLinker {
    fun linkKey(context: ContextId, sharedTags: Set<InterestTag>, day: Int): ByteArray
}

class HmacEncounterLinker(secret: ByteArray) : EncounterLinker {
    private val keySpec: SecretKeySpec

    init {
        require(secret.size >= MIN_SECRET_BYTES) { "secret must be at least $MIN_SECRET_BYTES bytes" }
        keySpec = SecretKeySpec(secret, ALGORITHM)
    }

    override fun linkKey(context: ContextId, sharedTags: Set<InterestTag>, day: Int): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(keySpec)
        feed(mac, context.toString())
        val tags = sharedTags.map { it.toString() }.sorted()
        mac.update(intBytes(tags.size))
        tags.forEach { feed(mac, it) }
        return mac.doFinal()
    }

    // Length-prefixed fields so different tag sets can never produce the same byte stream.
    private fun feed(mac: Mac, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        mac.update(intBytes(bytes.size))
        mac.update(bytes)
    }

    private fun intBytes(n: Int): ByteArray =
        byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())

    companion object {
        private const val ALGORITHM = "HmacSHA256"
        const val MIN_SECRET_BYTES = 16

        /** Fresh 32-byte secret. The caller keeps it in the key vault; never log it. */
        fun newSecret(random: SecureRandom = SecureRandom()): ByteArray =
            ByteArray(32).also { random.nextBytes(it) }
    }
}
