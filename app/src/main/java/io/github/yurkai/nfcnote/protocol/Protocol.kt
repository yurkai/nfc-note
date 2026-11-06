package io.github.yurkai.nfcnote.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

class ProtocolException(message: String) : Exception(message)

object Protocol {
    const val MAX_BYTES = 1024
    const val CHUNK = 224
    val select: ByteArray get() = byteArrayOf(0, 0xA4.toByte(), 4, 0, 9) +
        byteArrayOf(0xF0.toByte(), 0x4E, 0x46, 0x43, 0x4E, 0x4F, 0x54, 0x45, 1) + byteArrayOf(0)
    val metadata: ByteArray get() = byteArrayOf(0x80.toByte(), 0x10, 0, 0, 0)
    fun read(offset: Int) = byteArrayOf(0x80.toByte(), 0x20, (offset shr 8).toByte(), offset.toByte(), 0)
    fun ack(hash: ByteArray) = byteArrayOf(0x80.toByte(), 0x30, 0, 0, 32) + hash
    fun hash(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    fun encode(text: String): ByteArray {
        val buffer = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .encode(java.nio.CharBuffer.wrap(text))
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        require(bytes.size in 1..MAX_BYTES) { "Message must contain 1–1024 UTF-8 bytes" }
        return bytes
    }
    fun decode(bytes: ByteArray): String {
        if (bytes.size !in 1..MAX_BYTES) throw ProtocolException("Invalid message size")
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) { throw ProtocolException("Invalid UTF-8") }
    }
    fun response(bytes: ByteArray = byteArrayOf(), status: Int = 0x9000) =
        bytes + byteArrayOf((status shr 8).toByte(), status.toByte())
    fun unwrap(bytes: ByteArray): ByteArray {
        if (bytes.size < 2 || bytes.size > CHUNK + 2) throw ProtocolException("Invalid APDU response length")
        val status = ((bytes[bytes.size - 2].toInt() and 255) shl 8) or (bytes.last().toInt() and 255)
        if (status != 0x9000) throw ProtocolException("Sender rejected request (%04X)".format(status))
        return bytes.copyOf(bytes.size - 2)
    }
}

/** One-shot session. All entry points share a lock with lifecycle cancellation. */
class SenderSession(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    enum class State { IDLE, READY, TRANSFERRING, SENT, ERROR }
    var state = State.IDLE; private set
    private var payload: ByteArray? = null
    private var deadline = 0L
    private var selected = false
    private var nextOffset = 0
    private var acknowledgedHash: ByteArray? = null
    @Synchronized fun prepare(text: String) {
        clear()
        payload = Protocol.encode(text)
        deadline = now() + 60_000
        state = State.READY
    }
    @Synchronized fun clear() {
        payload?.fill(0); payload = null; acknowledgedHash = null
        selected = false; nextOffset = 0; state = State.IDLE
    }
    @Synchronized fun expire(): Boolean {
        if ((payload != null || acknowledgedHash != null) && now() >= deadline) {
            clear(); state = State.ERROR; return true
        }
        return false
    }
    @Synchronized fun disconnected() {
        if (state == State.TRANSFERRING) { clear(); state = State.ERROR }
        selected = false
    }
    @Synchronized fun process(command: ByteArray): ByteArray {
        expire()
        if (command.size > 64) return Protocol.response(status = 0x6700)
        if (command.contentEquals(Protocol.select)) {
            selected = false
            if (payload == null) return Protocol.response(status = 0x6985)
            selected = true; nextOffset = 0; state = State.TRANSFERRING
            return Protocol.response()
        }
        if (!selected) return Protocol.response(status = 0x6985)
        if (command.size >= 2 && command[0] == 0x80.toByte() && command[1] == 0x30.toByte()) {
            if (command.size != 37 || command[2] != 0.toByte() || command[3] != 0.toByte() || command[4] != 32.toByte())
                return Protocol.response(status = 0x6700)
            val hash = command.copyOfRange(5, 37)
            if (state == State.SENT && acknowledgedHash?.contentEquals(hash) == true) return Protocol.response()
            val bytes = payload ?: return Protocol.response(status = 0x6985)
            if (nextOffset != bytes.size || !Protocol.hash(bytes).contentEquals(hash)) return Protocol.response(status = 0x6985)
            acknowledgedHash = hash; bytes.fill(0); payload = null; state = State.SENT
            return Protocol.response()
        }
        val bytes = payload ?: return Protocol.response(status = 0x6985)
        if (command.contentEquals(Protocol.metadata)) return Protocol.response(
            byteArrayOf(1, (bytes.size shr 8).toByte(), bytes.size.toByte()) + Protocol.hash(bytes))
        if (command.size == 5 && command[0] == 0x80.toByte() && command[1] == 0x20.toByte() && command[4] == 0.toByte()) {
            val offset = ((command[2].toInt() and 255) shl 8) or (command[3].toInt() and 255)
            if (offset != nextOffset || offset >= bytes.size) return Protocol.response(status = 0x6B00)
            val end = minOf(offset + Protocol.CHUNK, bytes.size)
            nextOffset = end
            return Protocol.response(bytes.copyOfRange(offset, end))
        }
        return Protocol.response(status = 0x6D00)
    }
}

data class ReceivedNote(val text: String, val acknowledged: Boolean)

object Receiver {
    /** Transport failures propagate; incomplete data is never returned or acknowledged. */
    fun receive(exchange: (ByteArray) -> ByteArray): ReceivedNote {
        if (Protocol.unwrap(exchange(Protocol.select)).isNotEmpty()) throw ProtocolException("Invalid SELECT response")
        val meta = Protocol.unwrap(exchange(Protocol.metadata))
        if (meta.size != 35 || meta[0] != 1.toByte()) throw ProtocolException("Unsupported metadata")
        val size = ((meta[1].toInt() and 255) shl 8) or (meta[2].toInt() and 255)
        if (size !in 1..Protocol.MAX_BYTES) throw ProtocolException("Invalid message size")
        val out = ByteArrayOutputStream(size)
        while (out.size() < size) {
            val part = Protocol.unwrap(exchange(Protocol.read(out.size())))
            if (part.size != minOf(Protocol.CHUNK, size - out.size())) throw ProtocolException("Invalid fragment size")
            out.write(part)
        }
        val bytes = out.toByteArray()
        if (!Protocol.hash(bytes).contentEquals(meta.copyOfRange(3, 35))) throw ProtocolException("Message checksum mismatch")
        val text = Protocol.decode(bytes)
        val acknowledged = try { Protocol.unwrap(exchange(Protocol.ack(Protocol.hash(bytes)))).isEmpty() }
            catch (_: java.io.IOException) { false } catch (_: ProtocolException) { false }
        bytes.fill(0)
        return ReceivedNote(text, acknowledged)
    }
}
