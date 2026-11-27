package org.koocheh.integration

import kotlinx.coroutines.channels.Channel
import org.koocheh.model.Sighting
import org.koocheh.transport.PeerChannel
import org.koocheh.transport.PeerLink

/**
 * In-memory radio between two phones. Each side gets a PeerLink whose open() returns its end of one
 * shared pair of queues. close() ends only the sender's own direction, so the peer still reads what
 * was sent before the close (for example a withdraw notice) and then sees the end of the stream.
 */
class FakeRadioLink {
    private val aToB = Channel<ByteArray>(Channel.UNLIMITED)
    private val bToA = Channel<ByteArray>(Channel.UNLIMITED)

    val linkA: PeerLink = FakePeerLink(End(aToB, bToA))
    val linkB: PeerLink = FakePeerLink(End(bToA, aToB))

    private class FakePeerLink(private val end: PeerChannel) : PeerLink {
        override suspend fun open(sighting: Sighting): PeerChannel = end
    }

    private class End(private val out: Channel<ByteArray>, private val inbound: Channel<ByteArray>) : PeerChannel {
        override suspend fun send(bytes: ByteArray) = out.send(bytes.copyOf())
        override suspend fun receive(): ByteArray = inbound.receive()
        override fun close() {
            out.close()
        }
    }
}
