package org.koocheh.psi

import org.koocheh.model.InterestTag
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * !!! INSECURE - FOR TESTS ONLY - NEVER SHIP !!!
 *
 * This is NOT private set intersection. It exchanges fixed-key SHA-256 digests of
 * the tags, which anyone can reverse by enumerating the small tag vocabulary, and
 * it computes shared tag identities even under COUNT_ONLY (it only withholds them
 * from the result). It exists so that UI and logic can be tested against the seam
 * before a real engine is chosen.
 *
 * It refuses to be constructed unless the module's BuildConfig.DEBUG is true. If
 * that cannot be determined it also refuses (fail closed).
 */
class FakePsiEngine : PsiEngine {
    init {
        FakeReleaseGuard.requireDebugBuild()
    }

    override fun newSession(localTags: Set<InterestTag>, reveal: RevealLevel): PsiSession =
        FakeSession(localTags, reveal)
}

internal object FakeReleaseGuard {
    fun requireDebugBuild() {
        check(isDebugBuild()) {
            "FakePsiEngine is insecure and test-only: refusing to run because this is not " +
                "a debug build (or BuildConfig.DEBUG could not be read)."
        }
    }

    private fun isDebugBuild(): Boolean = try {
        Class.forName("org.koocheh.psi.BuildConfig").getField("DEBUG").getBoolean(null)
    } catch (e: Exception) {
        false
    } catch (e: LinkageError) {
        false
    }
}

private class FakeSession(
    private val local: Set<InterestTag>,
    private val reveal: RevealLevel,
) : PsiSession {
    private val fixedKey = "koocheh-fake-psi-INSECURE-fixed-key".toByteArray(Charsets.UTF_8)
    private val tagByDigest: Map<String, InterestTag> = local.associateBy { digestHex(it) }
    private var sent = false
    private var received = false

    override var result: PsiResult? = null
        private set

    override fun outgoing(): ByteArray {
        if (sent) return ByteArray(0)
        sent = true
        val digests = tagByDigest.keys.sorted().map { hexToBytes(it) }
        val buf = ByteBuffer.allocate(1 + 4 + digests.size * DIGEST_LEN)
        buf.put(reveal.ordinal.toByte())
        buf.putInt(digests.size)
        digests.forEach { buf.put(it) }
        return buf.array()
    }

    override fun incoming(msg: ByteArray) {
        if (received) throw PsiProtocolException("unexpected extra message")
        if (msg.size < 5) throw PsiProtocolException("message too short")
        val buf = ByteBuffer.wrap(msg)
        if (buf.get().toInt() != reveal.ordinal) {
            throw PsiProtocolException("reveal level mismatch")
        }
        val n = buf.getInt()
        if (n < 0 || msg.size != 5 + n * DIGEST_LEN) throw PsiProtocolException("bad length")
        val remote = HashSet<String>()
        repeat(n) {
            val d = ByteArray(DIGEST_LEN)
            buf.get(d)
            remote += bytesToHex(d)
        }
        received = true
        val shared = tagByDigest.filterKeys { it in remote }.values.toSet()
        result = PsiResult(
            count = shared.size,
            sharedTags = if (reveal == RevealLevel.TAGS) shared else null,
        )
    }

    private fun digestHex(tag: InterestTag): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(fixedKey)
        md.update(tag.value.toByteArray(Charsets.UTF_8))
        return bytesToHex(md.digest())
    }

    private fun bytesToHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private companion object {
        const val DIGEST_LEN = 32
    }
}
