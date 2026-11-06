package org.koocheh.disclosure

import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

const val WIRE_VERSION = 1
const val MAX_WIRE_BYTES = 4096

open class ProtocolViolation(message: String) : Exception(message)

@Serializable
sealed interface WireMessage

@Serializable
@SerialName("psi")
data class PsiMsg(val round: Int, val data: String) : WireMessage

@Serializable
@SerialName("commit")
data class CommitMsg(val step: Int, val commitment: String) : WireMessage

@Serializable
@SerialName("reveal")
data class RevealMsg(val step: Int, val nonce: String, val value: String) : WireMessage

@Serializable
@SerialName("withdraw")
data class WithdrawMsg(val reason: Int = 0) : WireMessage

@Serializable
data class Envelope(val v: Int, val msg: WireMessage)

fun ByteArray.b64(): String = Base64.getEncoder().encodeToString(this)

fun String.unb64(): ByteArray = try {
    Base64.getDecoder().decode(this)
} catch (e: IllegalArgumentException) {
    throw ProtocolViolation("bad encoding")
}

object WireCodec {
    private val json = Json { classDiscriminator = "t" }

    fun encode(msg: WireMessage): ByteArray {
        val bytes = json.encodeToString(Envelope.serializer(), Envelope(WIRE_VERSION, msg)).encodeToByteArray()
        if (bytes.size > MAX_WIRE_BYTES) throw ProtocolViolation("message too large")
        return bytes
    }

    fun decode(bytes: ByteArray): WireMessage {
        if (bytes.size > MAX_WIRE_BYTES) throw ProtocolViolation("message too large")
        val env = try {
            json.decodeFromString(Envelope.serializer(), bytes.decodeToString())
        } catch (e: SerializationException) {
            throw ProtocolViolation("malformed message")
        } catch (e: IllegalArgumentException) {
            throw ProtocolViolation("malformed message")
        }
        if (env.v != WIRE_VERSION) throw ProtocolViolation("unsupported version")
        return env.msg
    }

    /** Fails early if a reveal carrying [valueSize] bytes could not fit in one wire message. */
    fun requireRevealFits(step: Int, valueSize: Int) {
        val probe = RevealMsg(step, "A".repeat(128), ByteArray(valueSize).b64())
        encode(probe)
    }
}
