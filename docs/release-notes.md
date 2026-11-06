NFC Note 0.1.1 — smaller APK, same Android 9+ support.

- Enable R8 code shrinking, optimization and obfuscation with Android's optimized default rules.
- Remove unused resources from release builds.
- APK size reduced from approximately 22.90 MB to 2.16 MB (about 90.6% smaller, 10.6× reduction).
- Preserve R8 mapping/reports as a GitHub Actions artifact for diagnosing release crashes.
- Same application ID and release signing key: install over v0.1.0 without uninstalling.

Validation: 13 protocol unit tests, Android Lint, signature checks, installation/update and UI smoke checks on Android 9 and Android 17 emulators.

The user confirmed successful phone-to-phone NFC transfer with v0.1.0, including older phones. **NFC transfer with this R8-optimized version still needs a physical-device regression check.** This release remains an experimental prerelease. Messages remain unencrypted and unauthenticated.

Install the APK on both phones and repeat Send → Prepare to send / Receive. SHA256SUMS contains the APK checksum. See README and docs/device-test-plan.md.
