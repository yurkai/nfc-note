package io.github.yurkai.nfcnote.protocol

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ProtocolTest {
    private fun sender(text: String) = SenderSession().apply { prepare(text) }
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }
    private fun badResponse(block: () -> Unit) {
        try { block(); fail("Expected protocol failure") } catch (_: ProtocolException) { }
    }
    @Test fun utf8RoundTripAcrossFragmentBoundaries() {
        val text = "Привет 🌍\n".repeat(45)
        assertTrue(Protocol.encode(text).size > Protocol.CHUNK)
        val session = sender(text)
        val note = Receiver.receive(session::process)
        assertEquals(text, note.text); assertTrue(note.acknowledged)
        assertEquals(SenderSession.State.SENT, session.state)
        assertEquals(0x69.toByte(), session.process(Protocol.select)[0])
    }
    @Test fun boundariesAndEmptyMessage() {
        rejects { sender("") }; rejects { sender("a".repeat(1025)) }
        rejects { sender("я".repeat(513)) }
        val text = "я".repeat(512)
        assertEquals(text, Receiver.receive(sender(text)::process).text)
        assertEquals("a", Receiver.receive(sender("a")::process).text)
    }
    @Test fun invalidUnicodeRejected() {
        badResponse { Protocol.decode(byteArrayOf(0xC3.toByte(), 0x28)) }
        try { Protocol.encode("\uD800"); fail() } catch (_: java.nio.charset.CharacterCodingException) { }
    }
    @Test fun malformedAndUnknownCommandsAreRejected() {
        val session = sender("private")
        for (command in listOf(byteArrayOf(), Protocol.metadata, Protocol.read(0), Protocol.ack(ByteArray(32))))
            assertFalse(session.process(command).contentEquals(Protocol.response()))
        Protocol.unwrap(session.process(Protocol.select))
        for (command in listOf(byteArrayOf(0x80.toByte()), ByteArray(1000),
            byteArrayOf(0x80.toByte(), 0x40, 0, 0, 0), Protocol.select.copyOf(5),
            Protocol.metadata + byteArrayOf(0))) {
            badResponse { Protocol.unwrap(session.process(command)) }
        }
    }
    @Test fun selectRequiresExactAid() {
        val session = sender("note")
        val wrong = Protocol.select.also { it[8] = 0 }
        badResponse { Protocol.unwrap(session.process(wrong)) }
        badResponse { Protocol.unwrap(session.process(Protocol.metadata)) }
    }
    @Test fun sequentialReadsAndAckValidation() {
        val session = sender("a".repeat(300))
        Protocol.unwrap(session.process(Protocol.select))
        badResponse { Protocol.unwrap(session.process(Protocol.read(224))) }
        badResponse { Protocol.unwrap(session.process(Protocol.ack(Protocol.hash("a".repeat(300).toByteArray())))) }
        Protocol.unwrap(session.process(Protocol.read(0)))
        badResponse { Protocol.unwrap(session.process(Protocol.read(0))) }
        Protocol.unwrap(session.process(Protocol.read(224)))
        badResponse { Protocol.unwrap(session.process(Protocol.ack(ByteArray(32)))) }
        val ack = Protocol.ack(Protocol.hash("a".repeat(300).toByteArray()))
        Protocol.unwrap(session.process(ack)); Protocol.unwrap(session.process(ack))
    }
    @Test fun disconnectionDisarmsSession() {
        val session = sender("private")
        Protocol.unwrap(session.process(Protocol.select)); session.disconnected()
        assertEquals(SenderSession.State.ERROR, session.state)
        badResponse { Protocol.unwrap(session.process(Protocol.select)) }
        session.prepare("new"); assertEquals("new", Receiver.receive(session::process).text)
    }
    @Test fun lifetimeAndCancellation() {
        var now = 0L
        val session = SenderSession { now }
        session.prepare("private"); now = 60_000
        badResponse { Protocol.unwrap(session.process(Protocol.select)) }
        assertEquals(SenderSession.State.ERROR, session.state)
        session.prepare("private"); session.clear()
        badResponse { Protocol.unwrap(session.process(Protocol.select)) }
    }
    @Test fun noPartialMessageOnTransportFailure() {
        val session = sender("a".repeat(500))
        var requests = 0
        try {
            Receiver.receive { if (++requests == 4) throw IOException("lost") else session.process(it) }
            fail()
        } catch (_: IOException) { }
        assertNotEquals(SenderSession.State.SENT, session.state)
    }
    @Test fun ackFailurePreservesCompleteTextWithUncertainStatus() {
        val session = sender("hello")
        val note = Receiver.receive {
            val response = session.process(it)
            if (it[1] == 0x30.toByte()) throw IOException("ACK response lost")
            response
        }
        assertEquals("hello", note.text); assertFalse(note.acknowledged)
        assertEquals(SenderSession.State.SENT, session.state)
    }
    @Test fun invalidIncomingMetadataAndFragments() {
        for (size in listOf(0, 1025, 65535)) badResponse {
            Receiver.receive {
                if (it.contentEquals(Protocol.select)) Protocol.response()
                else Protocol.response(byteArrayOf(1, (size shr 8).toByte(), size.toByte()) + ByteArray(32))
            }
        }
        for (payload in listOf(byteArrayOf(), ByteArray(225), ByteArray(2))) badResponse {
            val session = sender("hello")
            Receiver.receive { if (it[1] == 0x20.toByte()) Protocol.response(payload) else session.process(it) }
        }
    }
    @Test fun checksumAndMalformedResponses() {
        val session = sender("hello")
        badResponse { Receiver.receive { if (it[1] == 0x20.toByte()) Protocol.response("world".toByteArray()) else session.process(it) } }
        for (bytes in listOf(byteArrayOf(), byteArrayOf(0x90.toByte()), ByteArray(300), byteArrayOf(0x6D, 0)))
            badResponse { Protocol.unwrap(bytes) }
    }
    @Test fun arbitraryApdusNeverThrow() {
        val random = java.util.Random(42)
        val session = sender("test")
        repeat(5000) {
            val bytes = ByteArray(random.nextInt(100)).also(random::nextBytes)
            assertTrue(session.process(bytes).size >= 2)
        }
    }
}
