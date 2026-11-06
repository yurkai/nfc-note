package io.github.yurkai.nfcnote.nfc

import io.github.yurkai.nfcnote.protocol.SenderSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object TransferStore {
    private val sender = SenderSession()
    private val mutableState = MutableStateFlow(SenderSession.State.IDLE)
    val state = mutableState.asStateFlow()
    @Synchronized fun prepare(text: String) { sender.prepare(text); publish() }
    @Synchronized fun clear() { sender.clear(); publish() }
    @Synchronized fun expire() { sender.expire(); publish() }
    @Synchronized fun process(command: ByteArray): ByteArray = sender.process(command).also { publish() }
    @Synchronized fun disconnected() { sender.disconnected(); publish() }
    private fun publish() { mutableState.value = sender.state }
}
