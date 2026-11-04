package org.koocheh.psi

import org.koocheh.model.InterestTag

/**
 * Private set intersection SEAM (spec 5.3). This file defines the contract only;
 * it contains no cryptography. Each side must learn only what the reveal level
 * allows about the tags both sides hold, and nothing about unmatched tags.
 *
 * Plain hashing of tags is NOT an acceptable implementation: the tag vocabulary
 * is small, so unsalted or fixed-key hashes can be reversed by enumeration.
 *
 * Rules for every implementation:
 *  - no network code, no logging of tags (not in messages, exceptions or toString);
 *  - COUNT_ONLY must be enforced by the protocol (a cardinality-only exchange), not
 *    by merely hiding fields in [PsiResult]: a session at this level must never
 *    compute or hold the identities of shared tags;
 *  - the reveal level is part of the protocol: it is carried in the first message
 *    and a peer running a different level is rejected with [PsiProtocolException].
 */
enum class RevealLevel {
    /** Tier 0: only the number of shared tags is learned. */
    COUNT_ONLY,

    /** Tier 1: the names of the shared tags are learned. */
    TAGS,
}

/**
 * Outcome of a finished session. [sharedTags] is null under [RevealLevel.COUNT_ONLY].
 * toString is redacted on purpose so a stray log line cannot leak tags.
 */
data class PsiResult(val count: Int, val sharedTags: Set<InterestTag>?) {
    init {
        require(count >= 0) { "count must not be negative" }
        require(sharedTags == null || sharedTags.size == count) {
            "sharedTags size must equal count"
        }
    }

    override fun toString(): String =
        "PsiResult(count=$count, sharedTags=" +
            (if (sharedTags == null) "null" else "<redacted:${sharedTags.size}>") + ")"
}

/** Malformed or incompatible peer message. The message text must never contain tags. */
class PsiProtocolException(message: String) : Exception(message)

/**
 * One run of the protocol on one device. Message convention:
 *  - [outgoing] returns the next message to send to the peer, or an empty array
 *    when nothing is pending; it may be called repeatedly (multi-round protocols);
 *  - [incoming] delivers one message received from the peer;
 *  - [result] stays null until the protocol has finished, then holds the outcome.
 * Transport (BLE, Wi-Fi Direct, ...) is the caller's concern and is out of scope here.
 */
interface PsiSession {
    fun outgoing(): ByteArray

    @Throws(PsiProtocolException::class)
    fun incoming(msg: ByteArray)

    val result: PsiResult?
}

interface PsiEngine {
    fun newSession(localTags: Set<InterestTag>, reveal: RevealLevel): PsiSession
}
