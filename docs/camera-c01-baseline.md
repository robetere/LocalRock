# Camera support C0.1 — baseline acceptance

Baseline source: `88cbeeea362b30672ec6bf1076b396a1f4470dae` (includes upstream PR #95).
Target: Saros 10R (`roborock.vacuum.a144`), Android. Record actual firmware and local server version at test time.

## Build
Use JDK 17 or newer, Android SDK 36 and the checked-in Gradle wrapper (8.14.3).
Run `./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug`.
On Windows replace `./gradlew` with ` .\gradlew.bat` (without the initial space).
Expected APK: `composeApp/build/outputs/apk/debug/`.
The upstream application ID is unchanged; a differently signed installed LocalRock cannot be updated by this debug APK. Do not uninstall or erase an existing installation merely to test; use a spare device/profile where possible or arrange an isolated build.
Automated tests do not establish hardware compatibility.

## Preconditions
- Use only an owned robot and its existing authorized local server.
- Keep the existing server configuration, privacy announcements and camera PIN.
- Close all other live viewers. Confirm normal LocalRock robot status/map first.
- Remote viewing must already be activated through the supported official procedure; do not reset or bypass credentials.
- Start with the robot idle and safely off the dock, if needed using normal controls.
- Confirm the local server camera/TURN configuration is reachable by robot and phone.

## Hardware acceptance sequence
| ID | Action | Pass criterion |
|---|---|---|
| B01 | Open LocalRock and existing robot | Normal status/map visible |
| B02 | Enable Settings > Experimental video; open Video | Saros 10R opt-in displayed, no automatic connection |
| B03 | Opt in and enter existing PIN/pattern | Credential accepted; no exposure in diagnostics |
| B04 | Open default OEM-match HD viewer | Camera ownership confirmed; TURN, SDP, ICE and decoded video succeed |
| B05 | Observe video for 60 seconds | Live frames continue; report dimensions and any freezes |
| B06 | End video | Local peer closes and owned preview is released; no cleanup warning |
| B07 | Reopen, then background app | Viewing stops and credential is cleared; no automatic reopen |
| B08 | Repeat with SD if HD fails (after confirmed cleanup) | Record result separately |
| B09 | Optional: select connection tests B/C/D in SD one at a time if default fails | Record first failed stage; never overlap viewers |
| B10 | Check normal LocalRock map/status after exit | No regression |

Do not run movement, audio, PIN guessing, activation or ownership takeover tests in C0.1. If cleanup is uncertain, stop testing and wait for the robot's preview timeout. Do not automatically retry commands.

## Report template (sanitized)
- Build commit / app version:
- Phone OS / model:
- Robot model / firmware:
- Local server version:
- Camera activated previously: yes/no/unknown
- Normal map and status: PASS/FAIL
- PIN entry: PASS/FAIL/INCONCLUSIVE
- Default HD: PASS/FAIL/INCONCLUSIVE
- SD, test variant (if used):
- First failed stage and exact displayed error:
- Received frame dimensions, observed freezes:
- End video / background cleanup:
- Audible privacy announcement observed:
- Normal controls after test:
- Overall: PASS/FAIL/INCONCLUSIVE

Never include PINs, hashes, account credentials, local keys, addresses, TURN credentials, raw SDP/ICE, private video or complete RPC payloads. Redact screenshots before sharing.

## Audit and next gate
- Exact model a144 already allowed with explicit experimental opt-in; motion remains disabled.
- Credential check uses MD5 of the existing PIN/pattern and fails closed on unrecognized responses. Do not broaden acceptance before observing documented/validated response semantics.
- PIN checked at entry and again at session start; distinguish those stages.
- Preview ownership is checked before and after start, and cleanup targets only the owned client.
- TURN/SDP/ICE and first decoded frame are separate failure points. Streaming is not proof of motion or audio support.
- Android voice call is not implemented in the native peer; defer to C0.4.
- C0.2 starts only after a reproducible baseline result. Preserve the S7 MaxV path and tests.
