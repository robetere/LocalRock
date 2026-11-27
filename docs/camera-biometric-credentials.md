# Optional camera credential unlock (Android)

Android 11 or newer with an enrolled Class 3 biometric is required. The user must
explicitly select the save option, enter the existing robot credential, pass robot
verification, and authorize encryption in the platform biometric prompt. Subsequent
unlocks authorize decryption of the stored credential and run the normal robot
verification again. Manual entry remains available. Use Forget saved camera
credential to remove the encrypted record and its key. A changed robot PIN or a
changed biometric enrollment may require forgetting and saving again.

An AES-256-GCM key is held by Android Keystore, with per-operation strong-biometric
authorization and enrollment invalidation. Private app preferences hold only the
IV and authenticated ciphertext, under a SHA-256-derived server/robot namespace.
App backup is disabled. The key and stored record are not shared with other robots.
The application never receives fingerprint data. The existing credential is
temporarily present in process memory for normal robot authentication, and is not
logged. No server or camera authorization rules are bypassed. iOS and older
Android versions retain manual entry.

## Acceptance checks
- Manual entry works without saving; bad PIN/pattern does not offer enrollment.
- Successful opt-in prompts before saving; cancellation leaves no new stored record.
- Exit and reopen, unlock with a biometric, then confirm normal robot verification.
- Cancel unlock, background during prompt, or deny biometric: no automatic viewing.
- Forget, then check that only manual entry is offered.
- Change robot PIN, add a biometric, test another robot/server: no cross-use or bypass.
- Verify no plaintext PIN in app preferences, backups, or logs.
