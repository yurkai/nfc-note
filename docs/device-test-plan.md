# Testing with two physical phones

Physical-device testing is in progress. Successful transfers were reported for
v0.1.0, including on older phones. The exact models and Android versions were
unrecorded. The R8-optimized v0.1.1 still needs a physical-device regression check.
Testing NFC transfers requires physical devices.

For each phone, record the model, Android version and build, APK version, HCE
support, whether Secure NFC is enabled, and whether a third-party wallet is set
as the default. Use sample messages that contain no private information.

1. Verify the release APK against `SHA256SUMS` and install it on both phones.
2. Enable NFC, unlock both screens, and open the app on each phone.
3. On the first phone, select **Send**, enter `Hello 🌍`, and tap **Prepare to send**.
   On the second phone, select **Receive**.
4. Bring the NFC antennas together. Verify that the text matches and that the
   sender shows **Sent** and the receiver shows **Received**.
5. Tap **Copy**, paste into a local text field, and verify that Unicode is preserved.
   Clear the clipboard after the check.
6. Repeat with messages of 1, 224, 225, and 1024 bytes, including Cyrillic letters
   and emoji at fragment boundaries.
7. Verify that the app rejects empty messages and messages of 1025 bytes.
8. Separate the phones mid-transfer. Verify that the app shows a clear error and
   displays no partial text. If the ACK is lost, the receiver may display the complete
   message with an uncertain acknowledgment status.
9. Prepare a new message and repeat the transfer. Swap the phones' roles and repeat again.
10. Prepare a message and check each cancellation separately. Go to the home screen,
    switch apps, lock the screen, switch modes, tap **Cancel sending**, or wait 60 seconds.
    Verify that the receiver cannot read the old message after each action.
11. Rotate the screen. Verify that preparation is canceled and no text is restored from disk.
12. Turn off NFC while waiting and again during a transfer. Turn it back on and prepare a new transfer.
13. Check the UI on devices without NFC or HCE, or simulate missing hardware in an
    emulator for UI checks. Test the system light and dark themes, large font sizes,
    a small screen, the keyboard, and the settings button.
14. If a wallet or Observe Mode interferes with transfers, record the configuration
    and try a different device.
15. Install a newer version signed with the same key over the first version and
    verify that the app updates in place.

Keep the release marked as an experimental prerelease until checks 1–14 pass.
Before deciding whether to publish a stable release, add a results table with the
phone models, Android versions, APK versions, outcomes, and any limitations.
