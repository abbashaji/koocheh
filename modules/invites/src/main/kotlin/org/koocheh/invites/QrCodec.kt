package org.koocheh.invites

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Compact binary codec. Layout (big endian, length-prefixed):
 * version(1) ctxLen(2) ctx issuerLen(1) issuer inviteeLen(1) invitee nonceLen(1) nonce
 * expiresAt(8) sigLen(1) sig
 * The signature covers everything before sigLen.
 */
object QrCodec {
    private const val MAX_FIELD = 255

    fun encode(token: InviteToken): ByteArray {
        require(token.signature.size <= MAX_FIELD)
        val out = ByteArrayOutputStream()
        out.write(encodeBody(token))
        out.write(token.signature.size)
        out.write(token.signature)
        return out.toByteArray()
    }

    internal fun encodeBody(token: InviteToken): ByteArray {
        val ctx = token.contextKey.toByteArray(Charsets.UTF_8)
        require(ctx.size <= 0xFFFF)
        require(token.issuerPublicKey.size <= MAX_FIELD)
        require(token.inviteePublicKey.size <= MAX_FIELD)
        require(token.nonce.size <= MAX_FIELD)
        val out = ByteArrayOutputStream()
        out.write(token.version)
        out.write(ctx.size ushr 8)
        out.write(ctx.size and 0xFF)
        out.write(ctx)
        out.write(token.issuerPublicKey.size)
        out.write(token.issuerPublicKey)
        out.write(token.inviteePublicKey.size)
        out.write(token.inviteePublicKey)
        out.write(token.nonce.size)
        out.write(token.nonce)
        for (shift in 56 downTo 0 step 8) out.write(((token.expiresAtMs ushr shift) and 0xFF).toInt())
        return out.toByteArray()
    }

    /** Returns null for any malformed, truncated or oversized input. Never throws. */
    fun decode(bytes: ByteArray): InviteToken? = try {
        val r = Reader(bytes)
        val version = r.u8()
        if (version != INVITE_VERSION) return null
        val ctxLen = (r.u8() shl 8) or r.u8()
        val ctx = String(r.take(ctxLen), Charsets.UTF_8)
        val issuer = r.take(r.u8())
        val invitee = r.take(r.u8())
        val nonce = r.take(r.u8())
        var expires = 0L
        repeat(8) { expires = (expires shl 8) or r.u8().toLong() }
        val sig = r.take(r.u8())
        if (!r.atEnd()) null else InviteToken(version, ctx, issuer, invitee, nonce, expires, sig)
    } catch (e: IllegalArgumentException) {
        null
    }

    /** Text form for QR payloads (base64url, no padding). */
    fun encodeText(token: InviteToken): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(encode(token))

    fun decodeText(text: String): InviteToken? = try {
        decode(Base64.getUrlDecoder().decode(text.trim()))
    } catch (e: IllegalArgumentException) {
        null
    }

    /** QR matrix for display; the caller renders it. */
    fun toMatrix(token: InviteToken, size: Int = 512): BitMatrix =
        QRCodeWriter().encode(
            encodeText(token),
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 1),
        )

    private class Reader(private val b: ByteArray) {
        private var pos = 0
        fun u8(): Int {
            require(pos < b.size) { "truncated" }
            return b[pos++].toInt() and 0xFF
        }

        fun take(n: Int): ByteArray {
            require(n >= 0 && pos + n <= b.size) { "truncated" }
            val r = b.copyOfRange(pos, pos + n)
            pos += n
            return r
        }

        fun atEnd(): Boolean = pos == b.size
    }
}
