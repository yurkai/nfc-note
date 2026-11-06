package io.github.yurkai.nfcnote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.cardemulation.CardEmulation
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import io.github.yurkai.nfcnote.nfc.NoteHceService
import io.github.yurkai.nfcnote.nfc.TransferStore
import io.github.yurkai.nfcnote.protocol.Receiver
import io.github.yurkai.nfcnote.protocol.SenderSession
import io.github.yurkai.nfcnote.ui.NoteScreen
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private val adapter by lazy { (getSystemService(NFC_SERVICE) as NfcManager).defaultAdapter }
    private val hasHce by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION) }
    private var receiving by mutableStateOf(false)
    private var nfcEnabled by mutableStateOf(false)
    private var received by mutableStateOf("")
    private var receiveStatus by mutableStateOf("Waiting — hold the phones together")
    private var error by mutableStateOf<String?>(null)
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    @Volatile private var generation = 0
    @Volatile private var connection: IsoDep? = null
    private var resumed = false
    private val expiry = object : Runnable {
        override fun run() { TransferStore.expire(); nfcEnabled = adapter?.isEnabled == true; handler.postDelayed(this, 500) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val sendState by TransferStore.state.collectAsState()
            NoteScreen(receiving, adapter != null, nfcEnabled, hasHce, sendState,
                received, receiveStatus, error,
                onMode = { mode ->
                    stopTransfer(); receiving = mode; received = ""; error = null
                    receiveStatus = "Waiting — hold the phones together"; configureNfc()
                },
                onPrepare = { text ->
                    error = null
                    try {
                        check(resumed && adapter?.isEnabled == true && hasHce) { "NFC sending is unavailable" }
                        TransferStore.prepare(text)
                    } catch (_: Exception) { error = "Cannot prepare message. Check NFC and the 1024-byte limit." }
                },
                onCancel = { TransferStore.clear() },
                onSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                onCopy = { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                    ClipData.newPlainText("NFC Note", received).apply {
                        description.extras = android.os.PersistableBundle().apply {
                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                        }
                    }) },
                onClear = { received = ""; receiveStatus = "Waiting — hold the phones together" })
        }
    }
    override fun onResume() {
        super.onResume(); resumed = true; nfcEnabled = adapter?.isEnabled == true
        handler.post(expiry); configureNfc()
    }
    override fun onPause() {
        resumed = false; handler.removeCallbacks(expiry)
        stopTransfer()
        try {
            adapter?.disableReaderMode(this)
            if (hasHce && adapter != null) CardEmulation.getInstance(adapter).unsetPreferredService(this)
        } catch (_: RuntimeException) { /* Device may have disabled NFC. */ }
        super.onPause()
    }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
    private fun stopTransfer() {
        generation++; TransferStore.clear()
        try { connection?.close() } catch (_: java.io.IOException) { }
    }
    private fun configureNfc() {
        if (!resumed || adapter?.isEnabled != true) return
        try {
            if (receiving) {
                if (hasHce) CardEmulation.getInstance(adapter).unsetPreferredService(this)
                adapter?.enableReaderMode(this, { tag -> readTag(tag) },
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
            } else {
                adapter?.disableReaderMode(this)
                if (hasHce && !CardEmulation.getInstance(adapter).setPreferredService(this,
                        ComponentName(this, NoteHceService::class.java))) {
                    error = "Could not prefer NFC Note. Check NFC settings and other card apps."
                }
            }
        } catch (_: RuntimeException) { error = "Cannot activate NFC. Check settings and reopen the app." }
    }
    private fun readTag(tag: Tag) {
        if (!busy.compareAndSet(false, true)) return
        val token = generation
        executor.execute {
            var iso: IsoDep? = null
            val timeout = Runnable { try { iso?.close() } catch (_: java.io.IOException) { } }
            try {
                if (token != generation) return@execute
                iso = IsoDep.get(tag) ?: throw java.io.IOException("Not ISO-DEP")
                connection = iso
                handler.post { if (token == generation) { received = ""; receiveStatus = "Receiving…" } }
                handler.postDelayed(timeout, 12_000)
                iso.connect(); iso.timeout = 2500
                val transport = iso
                val note = Receiver.receive { command ->
                    if (token != generation) throw java.io.IOException("Cancelled")
                    transport.transceive(command)
                }
                handler.post {
                    if (token == generation && resumed && receiving) {
                        received = note.text
                        receiveStatus = if (note.acknowledged) "Received — sender confirmed"
                            else "Received — sender confirmation uncertain. Check the other phone."
                    }
                }
            } catch (_: Exception) {
                handler.post { if (token == generation && resumed) receiveStatus =
                    "Error — no complete message. Keep phones together and prepare the sender again." }
            } finally {
                handler.removeCallbacks(timeout)
                try { iso?.close() } catch (_: java.io.IOException) { }
                connection = null; busy.set(false)
            }
        }
    }
}
