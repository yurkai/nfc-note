package io.github.yurkai.nfcnote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yurkai.nfcnote.protocol.SenderSession

@Composable
fun NoteScreen(
    receiving: Boolean, hasNfc: Boolean, enabled: Boolean, hasHce: Boolean,
    sendState: SenderSession.State, received: String, receiveStatus: String, error: String?,
    onMode: (Boolean) -> Unit, onPrepare: (String) -> Unit, onCancel: () -> Unit,
    onSettings: () -> Unit, onCopy: () -> Unit, onClear: () -> Unit,
) {
    // Deliberately not saveable: message contents never enter saved instance state.
    var draft by remember { mutableStateOf("") }
    val size = draft.toByteArray(Charsets.UTF_8).size
    val armed = sendState == SenderSession.State.READY || sendState == SenderSession.State.TRANSFERRING
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("NFC Note", style = MaterialTheme.typography.headlineLarge)
                Text("A small note. One close connection.", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(!receiving, { onMode(false) }, { Text("Send") })
                    FilterChip(receiving, { onMode(true) }, { Text("Receive") })
                }
                if (!hasNfc) Text("This device does not have NFC.", color = MaterialTheme.colorScheme.error)
                else if (!enabled) {
                    Text("NFC is turned off.")
                    OutlinedButton(onSettings) { Text("Open NFC settings") }
                }
                if (!receiving) {
                    if (!hasHce) Text("This device cannot send: NFC card emulation (HCE) is unavailable.")
                    OutlinedTextField(draft, { if (it.length <= 2048) { onCancel(); draft = it } },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Your message") }, minLines = 5,
                        supportingText = { Text("$size / 1024 UTF-8 bytes") }, isError = size > 1024)
                    Button({ onPrepare(draft) }, enabled = hasNfc && enabled && hasHce && size in 1..1024 && !armed,
                        modifier = Modifier.fillMaxWidth()) { Text("Prepare to send") }
                    if (armed) OutlinedButton(onCancel) { Text("Cancel sending") }
                    Text(when (sendState) {
                        SenderSession.State.IDLE -> "Write a note, then prepare it."
                        SenderSession.State.READY -> "Ready — open Receive on the other phone. Expires in 60 seconds."
                        SenderSession.State.TRANSFERRING -> "Transferring — keep phones together"
                        SenderSession.State.SENT -> "Sent — receiver acknowledged the complete message"
                        SenderSession.State.ERROR -> "Error — expired or disconnected. Delivery may be uncertain; check the receiver before retrying."
                    }, style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(receiveStatus, style = MaterialTheme.typography.titleMedium)
                    if (received.isNotEmpty()) {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            SelectionContainer { Text(received, Modifier.padding(20.dp)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onCopy) { Text("Copy") }
                            OutlinedButton(onClear) { Text("Clear") }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                HorizontalDivider()
                Text("Keep both phones unlocked with this app open. Touch the NFC antenna areas, usually on the backs.")
                Text("NFC only · No history · No network access", style = MaterialTheme.typography.labelLarge)
                Text("Messages are not encrypted. NFC proximity does not guarantee privacy.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
