# Camera development fork — contribution overview

This document describes the experimental `feature/camera-support` branch,
not the official LocalRock release. The upstream camera guide remains available
in [camera-live-view.md](camera-live-view.md) as a description of the original
implementation.

## Design and adaptive displays

The camera interface uses LocalRock's native Compose Multiplatform and Material 3
components. The three camera modes (View, Pilot and Map) separate passive viewing
from active navigation, with technical information kept out of the primary view
where practical.

The development device is an HONOR Magic V5 foldable. Layout decisions depend on
available window dimensions rather than a specific phone model. On narrow
displays, Map mode reserves space below the floor plan for touch driving controls.
On sufficiently wide displays (at least 520 dp and wider than 0.78 times the
available height), it reserves side space for touch controls. Selecting physical
controller input hides the joystick and leaves more room for the floor plan.
Safe drawing insets are used for expanded overlays.

This is adaptive layout support. Continuous video and safe input cancellation
across fold/unfold, rotation, backgrounding and window focus changes require
explicit acceptance testing; do not infer a pass from the responsive layout alone.

## Reported hardware validation

| Item | Status |
| --- | --- |
| Saros 10R, firmware 02.52.86 | Development robot |
| Live video | Reported tested on physical robot |
| Manual proportional driving | Reported tested on physical robot |
| Android two-way audio | Reported tested on physical robot |
| Xbox controller input | Reported tested by fork maintainer |
| Folded/unfolded layout transitions | Dedicated acceptance record pending |
| iOS and other robot models | Not newly verified by this fork |

The reported results are not a comprehensive regression or safety certification.
Record the build SHA, device, firmware and observed behaviour when repeating tests.

## Screenshot and demonstration checklist

The upstream screenshot gallery is retained. Add current, genuine screenshots
after checking them for private home imagery, room labels, addresses and
credentials. Do not publish sensitive screenshots without review.

- [ ] View mode, folded/cover display
- [ ] Pilot mode, folded/cover display
- [ ] Map mode, folded/cover display
- [ ] Map mode, unfolded/inner display with reserved side controls
- [ ] Landscape view
- [ ] Physical controller mode
- [ ] Android voice-call controls
- [ ] Optional short Xbox controller demonstration

## Optional biometric credentials

Android 11+ devices with a strong enrolled biometric can opt in to encrypted
camera credential storage. See [credential design and limitations](camera-biometric-credentials.md).
This is separate from camera compatibility and should be reviewed independently.

## Contribution scope

Discuss scope with upstream maintainers before preparing independently reviewable
changes. Prioritize model compatibility and Android audio; propose driving,
adaptive layout and gamepad changes separately. Preserve existing S7 MaxV and
iOS behaviour, local-only operation, model restrictions and motion safeguards.
