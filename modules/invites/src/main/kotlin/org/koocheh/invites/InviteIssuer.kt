package org.koocheh.invites

import java.security.SecureRandom
import org.koocheh.model.ContextId

/**
 * Issues single-use invites signed with the issuer's key. The local ledger remembers
 * every nonce handed out so the same nonce is never issued twice.
 */
class InviteIssuer(
    private val signer: InviteSigner,
    private val issuerPublicKey: ByteArray,
    private val issuerSecretKey: ByteArray,
    private val issuedLedger: NonceLedger = InMemoryNonceLedger(),
    private val random: SecureRandom = SecureRandom(),
) {
    fun issue(context: ContextId, inviteePublicKey: ByteArray, nowMs: Long): InviteToken =
        issueForKey(contextKeyOf(context), inviteePublicKey, nowMs)

    internal fun issueForKey(contextKey: String, inviteePublicKey: ByteArray, nowMs: Long): InviteToken {
        var nonce = ByteArray(NONCE_BYTES)
        var attempts = 0
        while (true) {
            random.nextBytes(nonce)
            if (issuedLedger.tryRecord(nonce)) break
            check(++attempts < MAX_ATTEMPTS) { "could not draw a fresh nonce" }
            nonce = ByteArray(NONCE_BYTES)
        }
        val unsigned = InviteToken(
            version = INVITE_VERSION,
            contextKey = contextKey,
            issuerPublicKey = issuerPublicKey.copyOf(),
            inviteePublicKey = inviteePublicKey.copyOf(),
            nonce = nonce,
            expiresAtMs = nowMs + INVITE_VALIDITY_MS,
            signature = ByteArray(0),
        )
        val signature = signer.sign(issuerSecretKey, unsigned.signedBytes())
        return InviteToken(
            unsigned.version, unsigned.contextKey, unsigned.issuerPublicKey,
            unsigned.inviteePublicKey, unsigned.nonce, unsigned.expiresAtMs, signature,
        )
    }

    private companion object {
        const val NONCE_BYTES = 16
        const val MAX_ATTEMPTS = 3
    }
}
