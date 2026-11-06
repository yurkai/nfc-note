package io.github.yurkai.nfcnote.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

class NoteHceService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray =
        TransferStore.process(commandApdu)
    override fun onDeactivated(reason: Int) = TransferStore.disconnected()
}
