package org.koocheh.invites

import org.koocheh.model.ContextId

/*
 * Invite design (assumption; the spec leaves offline replay open):
 * two-step in-person ceremony.
 *  1. The new install shows its own public key as a QR code.
 *  2. The member scans it and shows back a QR holding an InviteToken bound to that
 *     public key. A photographed token is useless to anyone but the named invitee.
 *
 * ContextId is only reachable here through contextKeyOf(); the token carries the
 * resulting string so it can be decoded without constructing a ContextId.
 * Tokens and keys are never logged; toString() is redacted on purpose.
 */

const val INVITE_VERSION: Int = 1

/** Tokens are valid for 7 days from issue time. */
const val INVITE_VALIDITY_MS: Long = 7L * 24 * 60 * 60 * 1000

internal fun contextKeyOf(context: ContextId): String = context.toString()

/** Local signing contract; matches org.koocheh.crypto.Signer (sign/verify). */
interface InviteSigner {
    fun sign(secretKey: ByteArray, message: ByteArray): ByteArray
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean
}

class InviteToken(
    val version: Int,
    val contextKey: String,
    val issuerPublicKey: ByteArray,
    val inviteePublicKey: ByteArray,
    val nonce: ByteArray,
    /** Absolute expiry (issue time + 7 days). A token is expired once now >= expiresAtMs. */
    val expiresAtMs: Long,
    val signature: ByteArray,
) {
    /** The exact bytes covered by the signature. */
    fun signedBytes(): ByteArray = QrCodec.encodeBody(this)

    fun isExpired(nowMs: Long): Boolean = nowMs >= expiresAtMs

    override fun equals(other: Any?): Boolean =
        other is InviteToken &&
            version == other.version &&
            contextKey == other.contextKey &&
            issuerPublicKey.contentEquals(other.issuerPublicKey) &&
            inviteePublicKey.contentEquals(other.inviteePublicKey) &&
            nonce.contentEquals(other.nonce) &&
            expiresAtMs == other.expiresAtMs &&
            signature.contentEquals(other.signature)

    override fun hashCode(): Int = nonce.contentHashCode() xor expiresAtMs.hashCode()

    override fun toString(): String = "InviteToken(redacted)"
}

sealed class InviteResult {
    data class Valid(val token: InviteToken) : InviteResult()
    object Malformed : InviteResult()
    object BadSignature : InviteResult()
    object WrongContext : InviteResult()
    object WrongInvitee : InviteResult()
    object Expired : InviteResult()
    object Replayed : InviteResult()
}

/** Single-use nonce store. tryRecord returns false when the nonce was already recorded. */
interface NonceLedger {
    fun tryRecord(nonce: ByteArray): Boolean
}

class InMemoryNonceLedger : NonceLedger {
    private val seen = HashSet<String>()

    @Synchronized
    override fun tryRecord(nonce: ByteArray): Boolean =
        seen.add(nonce.joinToString("") { "%02x".format(it) })
}
