# NFC Note APDU protocol v1

Transport: ISO-DEP over NFC-A. All lengths and offsets are unsigned big endian.
Experimental proprietary AID: `F04E46434E4F544501` (not a registered payment AID).
Use this AID only for NFC Note experiments; no claim of global uniqueness.

Only these exact short APDU forms are supported. `Le=00` means up to 256 bytes;
responses intentionally stay below that. Extended APDUs and extra bytes are rejected.

| Command | Bytes | Success response data (before SW) |
| --- | --- | --- |
| SELECT | `00 A4 04 00 09 F0 4E 46 43 4E 4F 54 45 01 00` | empty |
| META | `80 10 00 00 00` | version `01`, length u16, SHA-256 32 bytes |
| READ | `80 20 offset_hi offset_lo 00` | min(224, remaining) bytes |
| ACK | `80 30 00 00 20` + SHA-256 32 bytes | empty |

SW: `9000` success; `6985` unavailable/not selected/incomplete/invalid ACK;
`6700` invalid length; `6B00` invalid/out-of-order offset; `6D00` unsupported command.
Payload: strict UTF-8, 1–1024 bytes. No padding, no NUL terminator. Whitespace is valid.
READ starts at 0 and proceeds strictly in order; fragments can split UTF-8 code points.
Decode only after full assembly and checksum validation. Total response maximum: 226 bytes.

Sender requires explicit Prepare, foreground/unlocked activity and a 60-second lease.
SELECT begins a session and returns no payload if unarmed. Re-SELECT while armed
restarts reading from offset 0. ACK is accepted only after all bytes were served
and the digest matches. On ACK sender discards the payload and sets Sent; duplicate
ACK in the same selected session is idempotent. New SELECT after ACK is rejected.
Disconnect during transfer disarms and reports Error; retry requires Prepare.
Unknown commands never expose text. Lifecycle cancellation serializes with APDU handling.

Receiver SELECTs, reads META, checks version/length, reads exact-size fragments,
validates SHA-256 and strict UTF-8, then ACKs. No incomplete/invalid text is returned.
An ACK failure after valid assembly returns text with `acknowledged=false`.
If the ACK response is lost, sender may already show Sent; if the ACK command was
lost, sender may show Error. Exactly-once delivery cannot be guaranteed.

No encryption, authentication, anti-relay protection or authorization beyond
explicit preparation. SHA-256 is a consistency check, not a MAC.
