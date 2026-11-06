First experimental NFC Note build for Android 10+.

- Send and receive up to 1024 UTF-8 bytes directly over NFC HCE/ISO-DEP.
- Explicit one-shot preparation, 60-second expiry, cancellation on backgrounding.
- Fragment validation, checksum, receipt acknowledgement and connection error handling.
- Material 3 Send/Receive interface, system light/dark theme and clipboard copy.
- No network permission, accounts, telemetry or message history.

**Physical phone-to-phone NFC transfer has not yet been verified.** This prerelease is for testing on two unlocked NFC phones; the sender must support HCE. Messages are unencrypted and unauthenticated. See README and docs/device-test-plan.md.

Install the APK on both phones. SHA256SUMS contains its SHA-256 checksum. This release uses the project's stable release signing key; keep the same signature for future updates.
