package org.koocheh.storage

import org.koocheh.model.DisclosureTier

class PeerRecord(val peerKey: String, val tier: DisclosureTier, val payload: ByteArray)

class StoredMessage(val peerKey: String, val expiresAtMs: Long, val cipher: ByteArray)

/**
 * One logical store for one context and one namespace (real or decoy).
 * There is no cross-context query. Peer keys, link keys and ciphertext never reach a log.
 */
interface ContextStore {
    suspend fun putPeerRecord(peerKey: String, tier: DisclosureTier, payload: ByteArray)
    suspend fun getPeerRecord(peerKey: String): PeerRecord?

    /** Removes everything exchanged with this peer (record and messages). */
    suspend fun deletePeer(peerKey: String)

    /** Adds one encounter for [linkKey] on epoch day [day]. */
    suspend fun incrementEncounter(linkKey: ByteArray, day: Int)

    /** Epoch days (ascending, one entry per day with at least one encounter) >= [sinceDay]. */
    suspend fun encountersFor(linkKey: ByteArray, sinceDay: Int): List<Int>

    suspend fun putMessage(peerKey: String, expiresAtMs: Long, cipher: ByteArray)
    suspend fun messages(peerKey: String): List<StoredMessage>

    /** Applies [RetentionPolicy]: old encounters and expired messages are deleted. */
    suspend fun purgeExpired(nowMs: Long)
}
