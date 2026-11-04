package org.koocheh.invites

import org.koocheh.model.ContextId

/**
 * Checks a token on the invitee's side. Never throws on bad input.
 * Order: version, signature, context, invitee key, expiry, then nonce single-use.
 * The nonce is consumed only when every other check passed.
 */
class InviteVerifier internal constructor(
    private val signer: InviteSigner,
    private val expectedContextKey: String,
    private val usedLedger: NonceLedger = InMemoryNonceLedger(),
) {
    constructor(
        signer: InviteSigner,
        expectedContext: ContextId,
        usedLedger: NonceLedger = InMemoryNonceLedger(),
    ) : this(signer, contextKeyOf(expectedContext), usedLedger)

    fun verify(token: InviteToken, ownPublicKey: ByteArray, nowMs: Long): InviteResult {
        return try {
            if (token.version != INVITE_VERSION) return InviteResult.Malformed
            if (!signer.verify(token.issuerPublicKey, token.signedBytes(), token.signature)) {
                return InviteResult.BadSignature
            }
            if (token.contextKey != expectedContextKey) return InviteResult.WrongContext
            if (!token.inviteePublicKey.contentEquals(ownPublicKey)) return InviteResult.WrongInvitee
            if (token.isExpired(nowMs)) return InviteResult.Expired
            if (!usedLedger.tryRecord(token.nonce)) return InviteResult.Replayed
            InviteResult.Valid(token)
        } catch (e: RuntimeException) {
            InviteResult.Malformed
        }
    }

    /** Convenience for scanned bytes: undecodable input is Malformed. */
    fun verifyBytes(bytes: ByteArray, ownPublicKey: ByteArray, nowMs: Long): InviteResult {
        val token = QrCodec.decode(bytes) ?: return InviteResult.Malformed
        return verify(token, ownPublicKey, nowMs)
    }
}
