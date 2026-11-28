# Camera hardware acceptance — Android / Saros 10R

Target: HONOR Magic V5, Saros 10R (a144), firmware 02.52.86.
Use the APK from the exact CI commit being evaluated. Record the actual versions,
and use a clear, level area with the robot in sight, away from stairs, children,
pets and fragile objects. Do not rely on the camera to supervise a safety test.
Keep access to the robot's normal stop control. Stop testing if movement is
unexpected or a stop cannot be confirmed.

## Core functionality

| ID | Action | Expected result |
| --- | --- | --- |
| A01 | Open normal robot/map screen | Existing functionality remains available |
| A02 | Disable experimental video | Experimental entry hidden |
| A03 | Enable video, opt in, authenticate | Existing credential required; view starts |
| A04 | View, Pilot, Map | Correct screen and controls; same camera session where possible |
| A05 | Move joystick gently, centre and release | Proportional movement; stop; no restart without fresh input |
| A06 | Repeat forward, reverse, turn and combined input | Correct direction and bounded response |
| A07 | Switch Pilot/Map or open settings while stationary | No unexpected motion |
| A08 | Start and end two-way audio | Audio only during explicit call; video continues |
| A09 | Exit video and reopen | Session cleanup, no ownership conflict |
| A10 | Background the app while stationary | Video and call end; no automatic restart |

## Controller and display safety

Begin with low deflection in an open area. Observe the robot directly, and release
controls immediately if a test fails.

| ID | Action | Expected result |
| --- | --- | --- |
| C01 | Connect Xbox controller; select controller mode | Controller recognized; neutral and explicit enable required |
| C02 | Drive and centre controls | Stops; no queued restart |
| C03 | Disable controller or press B | Disarms and stops |
| C04 | Turn controller off during low-speed driving | Stops promptly; reconnect does not restart |
| C05 | While stationary, fold/unfold in Map and Pilot | Layout adapts; video remains usable; controls require fresh input |
| C06 | At low speed with a second person ready to stop robot, change window size or rotate | Drive stops, controller deselected; no restart from old input |
| C07 | Switch away from the app during low-speed drive | Motion stops; returning does not resume |
| C08 | Interrupt video/connection at low speed, safely | Drive stops; restored video does not resume motion |
| C09 | Briefly interrupt controller Bluetooth link at low speed, safely | Record stop behaviour and time; if OS fails to report loss, mark fail/inconclusive |

If unsafe to perform a test, mark NOT RUN. Do not conduct dangerous tests to
obtain a pass. These checks do not certify emergency-stop performance.

## Credential and privacy checks

| ID | Action | Expected result |
| --- | --- | --- |
| P01 | Enter incorrect PIN | Rejected; no preview |
| P02 | Opt in to biometric storage; cancel prompt | No newly saved credential |
| P03 | Save correct PIN with strong biometric; reopen | Unlock requires biometric and normal robot verification |
| P04 | Cancel unlock | No preview and no exposed PIN |
| P05 | Forget saved credential | Next visit requires normal credential entry |
| P06 | Deny microphone permission; attempt call | No call; viewing remains available |
| P07 | End call / background | Microphone indicator off; no unexpected audio capture |
| P08 | Inspect sanitized app logs and diagnostics | No plaintext PIN, private connection details, raw SDP/ICE or tokens |

Do not change biometric enrollment solely for testing unless prepared to recover
other enrolled applications; otherwise mark invalidation NOT RUN.

## Screenshot checklist

Capture genuine current-build screenshots: View, Pilot, Map, controller mode,
folded and unfolded layout, and voice controls. Prefer a neutral wall or blank
floor and anonymize map names, home layout and status-bar identifiers before
publishing. Do not upload private imagery or credentials to the public fork.

## Report

- Build SHA / APK:
- Phone, Android version:
- Robot model and firmware:
- Local server version:
- Results: A01–A10, C01–C09, P01–P08 (PASS / FAIL / NOT RUN / INCONCLUSIVE)
- For stop tests: observed delay, unexpected movement, recovery behavior:
- Screenshots reviewed/redacted:
- Notes (no secrets or private images):

iOS physical testing is not available to the fork maintainer. Request separate
S7 MaxV/iOS regression testing from upstream; CI compilation is not a substitute.

## Experimental camera tasks (separate opt-in; not yet hardware validated)

Use the normal robot stop control if uncertain. Record commands and observed state; do not infer success from the UI alone.

| ID | Action | Expected result |
| --- | --- | --- |
| T01 | Leave camera tasks unchecked | No Saros autonomous controls |
| T02 | Opt in; open View and Tasks while docked | Start cleaning is available if fresh status permits |
| T03 | Start, pause, resume a clean | Correct task is preserved; manual drive refuses active cleaning |
| T04 | Select valid map target, tap Go | Fresh floor check; navigation only after explicit tap |
| T05 | Stop camera navigation | Only viewer-owned navigation is stopped |
| T06 | Request dock while viewing | Confirmation; manual session ended before return command; video closes after confirmation |
| T07 | Leave viewer during cleaning | Cleaning continues; manual movement and owned navigation are cleaned up |
| T08 | Simulate missing acknowledgement or stale task state | No replay; manual drive blocked until checking robot and reopening screen |

