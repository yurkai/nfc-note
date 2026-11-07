# NFC Note

A small Android app for sending short messages directly between two phones **over NFC**.
Built with Kotlin, Jetpack Compose, and Material 3. Works without accounts, servers,
ads, analytics, Bluetooth, or Wi-Fi. Licensed under MIT.

**[Download the APK from GitHub Releases](https://github.com/yurkai/nfc-note/releases)**

> Experimental MVP. Builds and unit tests run automatically. Phone-to-phone transfers
> have worked on physical devices, including older phones. The exact models and Android
> versions were unrecorded, and full compatibility testing is still pending. Releases
> are marked as prereleases. Testing NFC transfers requires physical devices.

## How to use

1. Download the APK from Releases on both phones and allow installation from the chosen source.
2. Enable NFC, unlock both phones, and open NFC Note on each.
3. On the first phone, select **Send**, enter a message, and tap **Prepare to send**.
4. On the second phone, select **Receive**. Bring the phones' NFC antennas together
   (usually on the back) and hold them in place until you see the result.
5. The receiver shows the message and a **Copy** button. The sender shows **Sent** after receiving an acknowledgment (ACK).

For example, enter `Hello 🌍` on the first phone, tap **Prepare to send**, and bring
both antennas together. The second phone displays the message once it arrives in full.
If contact breaks during a transfer, tap **Prepare to send** to try again.
If the acknowledgment is lost, the message may still have arrived.
Check the second phone before retrying to avoid duplicates. Tap **Clear** to remove
received text from the screen.

The limit is **1024 UTF-8 bytes**. Characters such as Cyrillic letters and emoji
can take several bytes each. Each preparation is valid for one transfer and expires
after 60 seconds. It is canceled if you leave the app, rotate or lock the phone,
switch modes, edit the message, or tap **Cancel sending**. Tap **Prepare to send** again to retry.

## Compatibility

- Android 9+ (minSdk 28), target and compile SDK 37 (Android 17).
- The receiver needs NFC with ISO-DEP. The sender also needs **NFC host card emulation (HCE)**.
  Phones with NFC but without HCE can receive messages. The app displays a message on phones without NFC.
- Transfers have been observed to fail when **Android Beam is enabled on either phone**.
  Turn off Android Beam wherever the setting is available and leave NFC enabled on both phones.
- NFC Note transfers messages over ISO-DEP/APDU. The sender uses `HostApduService`.
  The receiver calls `enableReaderMode()` with NFC-A and `FLAG_READER_SKIP_NDEF_CHECK`.
  Reader mode disables card emulation on the receiver.
- HCE uses the `other` category, so NFC Note can send without being set as the default wallet.
  The app requests `setPreferredService` in the foreground and clears prepared text when it goes into the background.
- Both screens must be on and unlocked. Secure NFC, a wallet's Observe Mode, antenna placement,
  firmware, and the manufacturer can affect availability and reliability.
- APDU exchanges run off the UI thread. Each `transceive` call has a 2.5-second timeout,
  and the entire session has a 12-second timeout. When the app goes into the background,
  it closes the connection and ignores late results.

The implementation follows the Android documentation for
[HCE](https://developer.android.com/develop/connectivity/nfc/hce),
[Reader Mode](https://developer.android.com/reference/android/nfc/NfcAdapter#enableReaderMode(android.app.Activity,%20android.nfc.NfcAdapter.ReaderCallback,%20int,%20android.os.Bundle)),
and [IsoDep](https://developer.android.com/reference/android/nfc/tech/IsoDep).
Device compatibility still needs [testing on physical phones](docs/device-test-plan.md).

## Privacy and security limitations

The app's only system permission is `android.permission.NFC`. AndroidX also adds
an app-specific signature permission that protects internal dynamically registered
broadcast receivers and grants no access to user data or the network.
The app has no `INTERNET` permission, third-party SDKs, telemetry, privileged APIs,
or message history. The app keeps messages in process memory and excludes them from
saved instance state, logs, and disk storage. Backups are disabled, and `FLAG_SECURE`
protects against screenshots and previews in the recent apps list.

When you tap **Copy**, the app places the text on the system clipboard and marks
it as sensitive. Other apps may access clipboard contents under Android's rules.
The keyboard and operating system can process your input independently of NFC Note.

**Messages are unencrypted and neither phone authenticates the other. Avoid sending secrets.**
Any compatible reader within NFC range can read a prepared message. SHA-256 checks
payload integrity only. An ACK confirms receipt at the protocol level and gives
no assurance of recipient identity or exactly-once delivery. Clearing references
and zeroing buffers may leave copies of the text in JVM memory.

## Build

You need JDK 17+, Android SDK Platform `android-37.0`, and Build Tools `36.0.0`.
The project pins AGP 9.4.1, built-in Kotlin and the Compose compiler at 2.4.21,
Gradle 9.6.0, Compose BOM 2026.09.00, and Activity Compose 1.13.0.

Release builds use R8 to optimize and shrink code, and remove unused resources.
In v0.1.1, the APK shrank from about 22.90 MB to 2.16 MB (a 90.6% reduction).
The `release-r8-reports` Actions artifact includes R8 mappings and reports for
troubleshooting optimized builds.

```sh
git clone git@github.com:yurkai/nfc-note.git
cd nfc-note
export ANDROID_HOME="$HOME/Library/Android/sdk" # Adjust to match your SDK location
# Point JAVA_HOME to JDK 17 or later. Android Studio's bundled JBR also works.
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Find the debug APK at `app/build/outputs/apk/debug/app-debug.apk`. The debug app
uses the `.debug` application ID suffix and can be installed alongside the release app.
Install the same build on both phones when testing transfers.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

To build a signed release, set `NFC_NOTE_KEYSTORE` to the absolute keystore path
and set `NFC_NOTE_STORE_PASSWORD`, `NFC_NOTE_KEY_ALIAS`, and `NFC_NOTE_KEY_PASSWORD`.
Keep these values out of version control. Then run `./gradlew assembleRelease`.
Without a key, the build produces an unsigned APK. The publishing workflow requires
the release key and verifies the signature with `apksigner`.

## GitHub Actions and signing

Pushes and pull requests run unit tests, Android Lint, and a debug build. The APK
and reports are available as Actions artifacts. A version tag such as `v0.1.0`
triggers the release workflow, which runs tests and lint, builds and verifies the
signed APK, generates `SHA256SUMS`, and publishes a GitHub prerelease with release notes.
The tag version must match `versionName` in `app/build.gradle.kts`. For a new release,
increment `versionCode` and update `versionName` and the release notes.

The release workflow requires these GitHub Secrets.

| Secret | Value |
| --- | --- |
| `NFC_NOTE_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `NFC_NOTE_STORE_PASSWORD` | Keystore password |
| `NFC_NOTE_KEY_ALIAS` | Key alias |
| `NFC_NOTE_KEY_PASSWORD` | Key password |

Run `apksigner verify --print-certs <APK>` and compare the SHA-256 fingerprint of
its signing certificate with `39f4578a251303a5207f8605d71fd5d3c031b26be6c763c56fa6085765d1ea22`.

Create and store the keystore **outside the repository**. Keep an encrypted backup
of the keystore and passwords in a safe place. GitHub Secrets does not let you
retrieve stored values. You need the same signing key to publish compatible updates.
Restrict Actions access and protect changes to the release workflow, which has
access to the signing key.

Run `sha256sum -c SHA256SUMS` (macOS `shasum -a 256 -c SHA256SUMS`) to check that
the downloaded APK matches the published checksum. The checksum is only as
trustworthy as its source.

## Code and tests

- `ui/NoteScreen.kt` implements the Compose UI and follows the system theme.
- `MainActivity.kt` handles the activity lifecycle, reader mode, I/O, and NFC settings.
- `nfc/TransferStore.kt` synchronizes in-memory sender state.
- `nfc/NoteHceService.kt` connects Android HCE to the transfer logic.
- `protocol/Protocol.kt` implements Android-independent encoding and the APDU state machine.

Unit tests cover Unicode, size limits, fragmentation, invalid commands, incorrect
AIDs, request order, checksums, acknowledgments, preparation expiry, cancellation,
transport interruptions, and lost acknowledgments.
See the [protocol specification](docs/protocol.md) and the [physical-device test plan](docs/device-test-plan.md).
