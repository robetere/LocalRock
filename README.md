# LocalRock — camera development fork

**Local-first Roborock control, extended with a native camera cockpit, Android audio, analogue driving, Xbox controller input and foldable-aware layouts.**

[![Android CI](https://github.com/robetere/LocalRock/actions/workflows/camera-c01-baseline.yml/badge.svg?branch=feature%2Fcamera-support)](https://github.com/robetere/LocalRock/actions/workflows/camera-c01-baseline.yml)
[![iOS compilation](https://github.com/robetere/LocalRock/actions/workflows/camera-ios-validation.yml/badge.svg?branch=feature%2Fcamera-support)](https://github.com/robetere/LocalRock/actions/workflows/camera-ios-validation.yml)
[![MIT license](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![Upstream release](https://img.shields.io/github/v/release/DonSidro/LocalRock?label=upstream%20release)](https://github.com/DonSidro/LocalRock/releases/latest)

[Upstream LocalRock](https://github.com/DonSidro/LocalRock) · [Development APK builds](https://github.com/robetere/LocalRock/actions/workflows/camera-c01-baseline.yml?query=branch%3Afeature%2Fcamera-support) · [Camera documentation](docs/camera-fork-overview.md) · [Hardware acceptance](docs/camera-hardware-acceptance.md)

LocalRock is a Kotlin/Compose Multiplatform application for Android and iOS that communicates with a self-hosted [local_roborock_server](https://github.com/Python-roborock/local_roborock_server). It provides local robot control, maps, cleaning and configuration without a direct Roborock-cloud login in the app.

This independent development branch builds on the original camera work in [upstream PR #95](https://github.com/DonSidro/LocalRock/pull/95), preserving the local server, WebRTC, Compose Multiplatform and Material 3 architecture. Its principal hardware target is the Saros 10R on Android.

> [!IMPORTANT]
> This is an experimental fork, not an official LocalRock release. Camera access is opt-in and off by default. The new functionality is not verified across all robot models or on physical iOS devices. For the stable upstream application, use [official releases](https://github.com/DonSidro/LocalRock/releases).

## Contents

[Camera features](#camera-features) · [Screenshots](#screenshots) · [Foldable support](#foldable-phone-support) · [Controller support](#xbox-controller-support) · [Compatibility](#camera-compatibility) · [Safety](#driving-safety) · [Core features](#features) · [Installation](#getting-started) · [Architecture](#how-it-works) · [Testing](#validation-and-contribution) · [Privacy](#privacy--security) · [Credits](#acknowledgements)

## Camera features

| Capability | What the development branch adds |
| --- | --- |
| Saros 10R | Hardware-tested live video, manual driving and two-way Android audio |
| View mode | Uncluttered live video, robot state, battery and connection status |
| Pilot mode | Proportional analogue touchscreen joystick, optional minimap, voice call |
| Map mode | Larger read-only floor plan alongside live video and manual controls |
| Physical controller | Xbox input, two layouts, explicit enable/disarm, release-to-stop |
| Adaptive layout | Narrow cover-screen and wider unfolded layouts; orientation-aware geometry |
| Android audio | Explicit two-way calls, independent microphone and incoming-audio controls |
| Biometric unlock | Optional encrypted camera credential storage on Android 11+ with strong biometrics |
| Motion guards | Separate Saros control profile, state checks, frame-freshness monitoring and cleanup |

### Native design

The camera interface is an extension of LocalRock rather than a separate embedded application. It retains Compose Multiplatform, Material 3 components, existing navigation, typography and icon conventions, as well as the existing camera and map infrastructure. Dark camera overlays maintain contrast against live video. Technical diagnostics are kept in settings instead of cluttering the main view. Users can switch between View, Pilot and Map without deliberately restarting the camera session.

## Screenshots

The original LocalRock gallery below remains available. The [camera screenshot gallery](docs/screenshots/camera/README.md) is reserved for genuine fork captures, including cover-screen and unfolded layouts. Earlier development screenshots should be labelled with their limitations; current-build captures are still needed for full validation.

### Original LocalRock screenshots

The following screenshots are from the upstream LocalRock project, not the new camera cockpit. The core app follows the system theme; light and dark can be forced in settings.

| Map & control | Device list | Cleaning modes | Room selection |
| :---: | :---: | :---: | :---: |
| ![Map and control](docs/screenshots/hero-map.png) | ![Device list](docs/screenshots/device-list.png) | ![Cleaning modes](docs/screenshots/cleaning-modes.png) | ![Room selection](docs/screenshots/room-select.png) |

| More actions | Map editor | Maps & floors | Dock & care |
| :---: | :---: | :---: | :---: |
| ![More actions](docs/screenshots/more-actions.png) | ![Map editor](docs/screenshots/map-editor.png) | ![Map management and multi-level maps](docs/screenshots/maps.png) | ![Dock control](docs/screenshots/dock.png) |

| Cleaning history | Add vacuum | Robot settings |
| :---: | :---: | :---: |
| ![Cleaning history](docs/screenshots/history.png) | ![On-phone onboarding](docs/screenshots/onboarding.png) | ![Robot settings](docs/screenshots/settings.png) |

Light theme:

<p align="center">
  <img src="docs/screenshots/light-mode.png" alt="LocalRock in light theme" width="300">
</p>

---

## Foldable phone support

Development is focused on the HONOR Magic V5, using available window dimensions rather than hardcoding a device identifier.

- **Narrow/cover display:** compact joystick below the floor plan, with a clear video and navigation area.
- **Wider/inner display:** the map reserves dedicated side space for driving controls, preserving the central map.
- **Physical controller:** hides the touch joystick to maximise the viewing area.
- **Rotation or resize:** Saros manual input is released and controller input is deselected when the available viewport changes; the native video renderer is not intentionally recreated.

The responsive layout is implemented. Continuous streaming, safe motion cancellation and every fold/unfold transition still require explicit hardware verification. No blanket claim is made for every foldable device.

## Xbox controller support

Pair an Xbox controller in Android Bluetooth settings, open a live camera session, and select controller input in Pilot or Map mode. Move and centre the controls to initialise, then press **A** to enable. Press **A** again or **B** to disarm.

| Input | Racing layout | Joystick layout |
| --- | --- | --- |
| Right trigger | Forward acceleration | — |
| Left trigger | Reverse | — |
| Left thumbstick X | Steering | Steering |
| Left thumbstick Y | — | Forward / reverse |
| Centre/release analogue inputs | Stop | Stop |
| B | Disarm | Disarm |

Xbox input has been tested on physical Android hardware by the fork maintainer. A Bluetooth link failure without a timely OS disconnect notification remains a hardware test case, not a guaranteed fail-safe.

## Camera compatibility

Video remains off by default. Enable **Settings → Experimental video** and use the model-specific opt-in when presented.

| Model / platform | Camera | Driving | Audio |
| --- | --- | --- | --- |
| Saros 10R (`a144`), Android, firmware `02.52.86` | Reported hardware tested | Reported hardware tested, experimental | Reported hardware tested |
| S7 MaxV (`a27`) | Upstream tested | Upstream controls retained | iOS upstream tested; fork regression pending |
| Other recognised camera models | Experimental opt-in | No expanded driving support | Model/platform dependent, unverified |
| iOS with fork changes | Build validation via CI | Physical regression pending | Physical regression pending |

See the [upstream camera guide](docs/camera-live-view.md) for the full recognised model list. Unknown model IDs remain excluded. Other robots are not declared compatible solely because they have a camera.

### Camera setup

1. Activate remote viewing through the official Roborock app **before** moving the robot to the local server. Retain the existing PIN or pattern.
2. Configure the local server's camera support and reachable TURN endpoint; the robot, phone and server must be able to reach it.
3. Enable experimental video in LocalRock, open the video entry and opt in where required.
4. Enter the existing credential; wait for decoded frames before attempting manual driving.

Optional [biometric camera unlock](docs/camera-biometric-credentials.md) stores an encrypted credential using Android Keystore and strong per-operation biometric authentication. The normal robot verification is still required. It is not a PIN bypass.

## Driving safety

Saros driving is separate from the upstream S7 MaxV control path. The requested command envelope is up to 0.30 m/s forward, 0.12 m/s reverse and 0.40 rad/s steering, with reduced forward speed during sharper turns. These are requested values, not measured chassis speeds. Commands use a 500 ms requested duration.

The implementation checks robot state and task status before entering manual control, stops on release or loss of fresh video, rejects invalid analogue values, disarms on detected controller/focus changes, and does not automatically replay failed movement commands. Saros camera driving does not expose the upstream Pin-and-Go, cleaning or docking actions. The floor plan in the cockpit is read-only and may be stale; it is not a live navigation safety sensor.

> [!WARNING]
> Experimental motion control is not a certified emergency-stop system. Test with the robot in sight, in a clear area away from stairs, children and pets. If stopping is uncertain, use the robot's normal stop control and do not retry until safe.

## Why LocalRock

LocalRock enables self-hosted robot control, on-phone onboarding, maps and cleaning without a direct cloud account in the application. The core implementation uses the existing Hawk-signed REST, MQTT-over-TLS and Roborock V1 protocol against the configured local server. Some server-side services, including firmware checking, may contact Roborock on the user's behalf.



## Features

- **Live map** — real‑time robot map with zoom/pan, coloured rooms with their floor types, room names you can tap to select, cleaning path, obstacles with their names and photos, no‑go zones, walls and carpets
- **Rotate and 3D** — turn the flat map in quarter turns, or switch to a 3D view you can turn and tilt
- **Full cleaning control** — start / pause / stop / dock, room and zone cleaning; drawn zones can be moved, resized and deleted
- **Cleaning modes** — Vac & Mop, Mop or Vacuum, with suction power, water flow, route and cleaning count; **SmartPlan** and **vacuum then mop** on robots that support them
- **Per‑room settings** — give each room its own suction, water and route for one run
- **Room cleaning** — pick rooms in the order they should be cleaned, with real names pulled from your home data, and set a default cleaning order for a full clean
- **Pin & Go** — send the robot to a point on the map
- **Remote control** — manually drive the vacuum
- **Video (experimental)** — native live camera viewing; model-specific control and voice support. See [Camera compatibility](#camera-compatibility).
- **Map editor** — draw no‑go zones, invisible walls, carpets (including round ones) and thresholds; pick a room type or give a room a custom name; split and merge rooms; set floor types
- **Furniture** — on robots that report furniture support, see the furniture on the map and add, move and remove it (robots with one saved map)
- **Map management** — rename and delete maps, back up and restore a map, and send the robot out to map the home again (for example after moving the dock)
- **Multi‑level homes** — keep a map per floor, switch with the picker on the map, or let the robot recognise the floor by itself
- **Schedule** — recurring cleans at a set time and on chosen days
- **Dock controls & settings** — empty, wash and dry, tank and dust‑bag status, empty the robot's built‑in water tank through the dock, and the dock's own settings (wash mode, mop wash frequency, auto‑empty), offered per model
- **Robot settings** — volume, child lock, status LED, carpet mode and carpet boost, collision and auto‑empty levels, Smart Charging, Do Not Disturb, and the other options newer robots advertise
- **Voice Assistant** — on robots with voice control: the on/off switch, the voice commands, the voice language, volume and the robot's chat history
- **Matter** — show the robot's Matter setup code as a QR code to add it to a Matter home
- **Find my pet** — send a robot with pet patrol out to look for your pet
- **Firmware updates** — check for new firmware and install it; your server asks Roborock's cloud on your behalf, so the robot and the app stay off it
- **Robot alerts** — optional notifications when a robot gets stuck, finishes cleaning, or its dock needs attention; on Android a background watch keeps them coming while the app is closed and can show cleaning progress
- **Status & consumables** — battery, state, cleaning stats, and maintenance counted the way the official app counts it
- **Offline robots** — when a robot stops answering, where and when it was last seen; a robot the server has never heard from shows as **Not paired yet** and opens Add vacuum
- **Cleaning history** — past sessions with their run map and timeline, Cruise runs, and lifetime totals
- **Opens straight into your robot** — with one robot, or the last one you used when there are several
- **On‑phone onboarding** — provision a brand‑new vacuum over Wi‑Fi from the app
- **Light / dark / system theme**
- **What's new** — release notes shown once in the app after an update (they ship inside the app; nothing is fetched)

### Features that need a newer server

Some features rely on server changes that are not in a released version of [`local_roborock_server`](https://github.com/Python-roborock/local_roborock_server) yet. Without them the app still works; it just leaves that part out.

| Feature | Needs in `local_roborock_server` | Without it |
| --- | --- | --- |
| Last seen, Not paired yet | [#251](https://github.com/Python-roborock/local_roborock_server/pull/251) (merged, not released yet) | An unanswering robot shows as plain Offline |
| Voice language packs | [#261](https://github.com/Python-roborock/local_roborock_server/pull/261) | The Language page says "Voice packs unavailable"; the rest of Voice Assistant works |
| Matter on a locally paired robot | [#252](https://github.com/Python-roborock/local_roborock_server/pull/252) | The robot can't get a Matter setup code |
| Firmware check and install | [#255](https://github.com/Python-roborock/local_roborock_server/pull/255), with the install option turned on | No update is found, and Update says the server's install option is needed |

---

## How it works

```
┌──────────────┐      HTTPS (REST, Hawk-signed)      ┌───────────────────────┐      ┌──────────┐
│   LocalRock  │ ──────────────────────────────────▶ │  local_roborock_server │      │ Roborock │
│ (Android/iOS)│ ◀────────── MQTT over TLS ────────▶ │  (cloud emulation +    │ ◀──▶ │  vacuum  │
│              │            (V1 RPC frames)          │   MQTT broker/bridge)  │ MQTT │          │
└──────────────┘                                     └───────────────────────┘      └──────────┘
```

- **REST** — login, home data, device list. Requests are Hawk‑signed with credentials issued by the server.
- **MQTT over TLS** — the robot and the app both connect to the server's MQTT broker; the server bridges messages between them. Commands are Roborock **V1 protocol** frames (protobuf/JSON envelope). Map edits and map management run over this same channel.

The app is **local‑only by design**: there is no Roborock‑cloud login, no server discovery, and no telemetry. The server URL is configured manually.

## Requirements

- A running instance of [`local_roborock_server`](https://github.com/Python-roborock/local_roborock_server) reachable over HTTPS on your network, with your vacuum(s) imported into its inventory.
- The server's TLS certificate trusted on your device (install its CA if it's self‑signed — see [Troubleshooting](#troubleshooting)).
- **Android 7.0 (API 24)** or newer.
- **iOS** build requires macOS + Xcode (see [Platform status](#platform-status)).

---

## Getting started

### 1. Run the local server

Follow the setup instructions for [`local_roborock_server`](https://github.com/Python-roborock/local_roborock_server). Note:

- Point the app at the **server root** (e.g. `https://your-host:555`), **not** the `/admin` panel.
- Make sure your vacuum is imported into the server's inventory and connected to its MQTT broker.

### 2. Install or build the development APK

**Android:** Open [the camera branch's GitHub Actions runs](https://github.com/robetere/LocalRock/actions/workflows/camera-c01-baseline.yml?query=branch%3Afeature%2Fcamera-support), select a successful run, and download its APK artifact (GitHub sign-in may be required). These are experimental debug builds, not official releases. Alternatively, build locally:

```shell
# macOS / Linux
./gradlew :composeApp:assembleDebug

# Windows
.\gradlew.bat :composeApp:assembleDebug
```

The APK lands in `composeApp/build/outputs/apk/debug/`. Install it, or run directly from Android Studio. The application ID is shared with upstream; an APK signed with a different key cannot update the installed application. Do not uninstall an existing installation just to test without first considering configuration loss.

**iOS:** open `iosApp/` in Xcode and run (requires macOS — see [Platform status](#platform-status)).

### 3. First‑run setup

On first launch the app walks you through:

1. **Welcome / intro** screen.
2. **Server URL** — enter your server's HTTPS root URL.
3. **Login** — enter the email + login code from your **server setup** (these are your local‑server credentials, not real Roborock credentials).
4. **Device list** — your vacuums appear; tap one to open its map and controls.

To add a new, un‑provisioned vacuum, use the **Add vacuum** flow, which provisions the robot over Wi‑Fi and registers it with the server.

---

## Project structure

Single‑module Compose Multiplatform project:

```
composeApp/src/
├─ commonMain/          # shared Kotlin (UI + logic) for all targets
│  └─ kotlin/com/kodraliu/localrock/
│     ├─ shared/        # non-UI core
│     │  ├─ auth/       # login + credential model
│     │  ├─ device/     # home/device data & repository
│     │  ├─ model/      # server API data classes
│     │  ├─ vacuum/     # VacuumRepository, Commands, map parsing + map edit commands
│     │  ├─ mqtt/       # self-healing MQTT client + transport abstraction
│     │  ├─ notify/     # robot alerts: event detection, background watch, notifier
│     │  ├─ messages/   # in-app messages and errors
│     │  ├─ settings/   # app settings
│     │  ├─ onboarding/ # phone Wi-Fi provisioning + admin session API
│     │  ├─ crypto/     # RSA / AES / HMAC / MD5 helpers
│     │  ├─ protocol/   # Roborock V1 message envelope
│     │  ├─ http/       # Ktor client factories + Hawk auth
│     │  ├─ demo/       # sample data for the demo login
│     │  ├─ platform/   # small platform hooks (donation links)
│     │  └─ webrtc/     # camera peer/video (expect/actual)
│     └─ ui/            # Compose screens: devices, vacuum (map, editor,
│                       #   schedule), settings, login, intro, onboarding,
│                       #   whatsnew, navigation
├─ androidMain/         # Android actuals (OkHttp, Paho MQTT, libwebrtc)
└─ iosMain/             # iOS actuals (Darwin, Swift-bridged MQTT/WebRTC/RSA)

iosApp/                 # SwiftUI entry point + native bridges
tests/                  # protocol/contract fixtures & tests
```

---

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Kotlin 2.3.x |
| UI | Compose Multiplatform 1.10.x, Material 3 |
| Navigation | AndroidX Navigation‑Compose (typed `@Serializable` routes) |
| Networking | Ktor 3.x (OkHttp on Android, Darwin on iOS) |
| Serialization | kotlinx.serialization (JSON + Protobuf) |
| MQTT | Eclipse Paho on Android; CocoaMQTT (Swift bridge) on iOS |
| Crypto | KotlinCrypto (MD5, HMAC‑SHA2) + platform RSA |
| WebRTC (camera) | `io.getstream:stream-webrtc-android` (Android); `WebRTC.framework` (iOS) |
| Settings/persistence | multiplatform‑settings |
| Min SDK / Target | Android 24 / 36 |

---

## Platform status

| Feature | Android | iOS |
| --- | :---: | :---: |
| Core control, map, rooms | Yes | Yes |
| Map editor, map management, multi‑level maps | Yes | Yes |
| MQTT over TLS | Yes (Eclipse Paho) | Yes (CocoaMQTT bridge) |
| Camera live view (WebRTC) | Experimental; Saros 10R tested in fork | Upstream support; fork hardware testing pending |
| Two-way camera audio | Implemented, tested on Saros 10R | Existing upstream implementation; fork not hardware tested |
| Xbox gamepad / biometric unlock | Android-only fork features | Not implemented in fork |
| RSA (onboarding crypto) | Yes | Yes |
| Phone Wi‑Fi pairing (Add vacuum) | Yes | Yes |

**Map editor note:** saving zones, walls, carpets and thresholds is currently limited to robots with a **single saved map**; on a multi‑level robot those tools are disabled and the editor says so. Room edits and map management are not affected. No‑mop zones are not editable yet.

**Camera live view note:** off by default on both platforms — see [Camera compatibility](#camera-compatibility).

**iOS note:** Kotlin/Native iOS targets must be built on macOS. The iOS MQTT and WebRTC integrations rely on Swift Package Manager dependencies ([CocoaMQTT](https://github.com/emqx/CocoaMQTT), [WebRTC](https://github.com/stasel/WebRTC)) that must be added to the `iosApp` Xcode target, and the Swift bridge files (`CocoaMqttTransport.swift`, `WebRtcPeer.swift`, `VacuumPairing.swift`) added to the target. Pass `nil` for the WebRTC factory to ship without live view, or `nil` for the pairing factory to hide the "add vacuum" flow. iOS uses a custom bundle identifier that needs signing/provisioning selected in Xcode.

**iOS pairing entitlement:** joining the robot's access point uses `NEHotspotConfiguration`, which needs the **Hotspot Configuration** capability. It is declared in `iosApp/iosApp.entitlements` and wired into the target, but it only signs once the capability is enabled for the App ID in the Apple Developer portal (Certificates, Identifiers & Profiles → your App ID → Hotspot Configuration). Without it the app fails to sign; with it, the UDP handshake also triggers the local-network prompt backed by `NSLocalNetworkUsageDescription`.

---

## Building from source

**Prerequisites:** JDK 17+, Android SDK, and (for iOS) macOS + Xcode.

```shell
git clone --branch feature/camera-support https://github.com/robetere/LocalRock.git
cd LocalRock

# Android debug build
./gradlew :composeApp:assembleDebug

# Run unit tests
./gradlew :composeApp:testDebugUnitTest
```

`gradle.properties` sets `-Xss64m` on the Gradle daemon to avoid a deep‑recursion `StackOverflowError` in Gradle's free‑list allocator on some machines — keep that flag.

---

## Troubleshooting

**"No data" / everything times out on the vacuum screen**
- The app connects to the server's MQTT broker but receives nothing. Confirm the robot is connected to the *server's* broker and the server bridge is running.
- Credentials issued by the server age out. If it was working and stopped, re‑login to refresh the session.

**TLS / can't connect to server**
- If the server uses a self‑signed certificate, install its CA on the device. On iOS the app performs a real trust evaluation — the CA must be installed and trusted.
- Use the server **root** URL (`https://host:555`), not `/admin`.

**Can't connect behind a reverse proxy (Traefik and others)**
- A proxy with strict SNI picks its certificate by the host name in the TLS handshake. The app sends it on Android since 1.3.0 and on iOS since 1.4.0; on an older build, update. Traefik logs `No certificate found for domain: ""` when it is missing.

**Add vacuum can't reach the robot**
- Turn off any VPN on the phone while pairing. The phone has to join the vacuum's own Wi‑Fi, and a VPN can route that traffic away from it.

**Two devices, one works and the other times out**
- Each install generates a unique client id, so multiple devices should coexist. If you built an older revision, update — this was fixed by folding a per‑install id into the MQTT client id.

**The map editor's zone tools are greyed out**
- Saving zones, walls, carpets and thresholds is only supported on robots with a single saved map for now.

**There is no camera button**
- Turn on **Settings → Experimental video**. The button only shows on the models listed in [Camera compatibility](#camera-compatibility).

**Room names show as "Room 26"**
- Real names come from your server's `get_home_data`. If the server doesn't return room names, the map falls back to segment ids.

---

**Camera connects but driving is unavailable** — Wait for fresh decoded video, verify the robot is idle or charging and no cleaning or return task is active. Centre controls and explicitly retry when safe.

**Controller not responding** — Pair the controller in Android settings, select Pilot or Map, choose controller input, move and centre the controls to initialise, then press A to enable. B disarms. If a command cannot be confirmed, check the robot before trying again.

**Camera connection fails** — Check server camera/TURN support, existing remote-viewing activation and PIN, and that another viewer is not active. Do not reset or bypass the credential.

## Privacy & security

- No new camera analytics or cloud service is introduced by this fork. Video uses the configured local server and camera/TURN infrastructure.
- Microphone capture is explicitly requested for calls; ordinary video viewing does not attach a microphone track. Call cleanup disables local audio.
- Optional camera credential encryption uses Android Keystore AES-GCM and strong biometric authentication; the normal PIN/pattern is still verified by the robot.
- Android backup is disabled. Release HTTP body logging and native WebRTC debug logging are disabled. Do not publish raw SDP/ICE, TURN credentials, PINs, private maps or home imagery.
- A heuristic scan of the changed files found no obvious embedded private keys, tokens or personal addresses. This is not a full historical secret scan, penetration test or dependency audit.

See [privacy policy](PRIVACY.md) and [audit report](docs/camera-contribution-audit.md).

## Validation and contribution

Android CI builds and runs unit tests on the development branch. macOS CI checks shared Kotlin compilation for iOS device and simulator. A full iOS build and physical iPhone/S7 MaxV regression remain separate acceptance gates. The maintainer's hardware is Saros 10R and HONOR Magic V5; Xbox controller and Android voice calls have been reported working. Latest CI status is shown by the badges above; a green build is not a hardware test.

| Documentation | Purpose |
| --- | --- |
| [Camera fork overview](docs/camera-fork-overview.md) | Design, scope and validation status |
| [Contribution audit](docs/camera-contribution-audit.md) | Source review, safeguards and open issues |
| [Hardware acceptance](docs/camera-hardware-acceptance.md) | Repeatable Android, motion, foldable and privacy checks |
| [Biometric credentials](docs/camera-biometric-credentials.md) | Security design and tests |
| [Upstream camera guide](docs/camera-live-view.md) | Original implementation and model list |
| [Initial baseline procedure](docs/camera-c01-baseline.md) | Historical baseline testing |

Proposed upstream contributions: model compatibility, Android audio, proportional driving, camera UI and foldable adaptation, physical controller support, and optional biometric storage. These can be discussed and reviewed independently. Possible future work includes fresher map updates, snapshots, waypoint navigation and optional visual overlays; none is presented as implemented.

---



## Community

Join the [LocalRock Discord](https://discord.gg/MJDZm9zmjY) for help with setup, questions, and release announcements. Bug reports and feature requests are best filed as [GitHub issues](https://github.com/DonSidro/LocalRock/issues) so they can be tracked.

---

## Support the project

Upstream LocalRock is free, open source, and maintained by Sidon Kodraliu. There's no cloud, no subscriptions, and no analytics — just an app that does one thing for people who want their vacuum to stay on the local network.

If it's useful to you and you'd like to help keep it maintained, a small donation goes a long way and is genuinely appreciated. It's never required — the app is and will stay free.

- **GitHub Sponsors** — [github.com/sponsors/donsidro](https://github.com/sponsors/donsidro)
- **Buy Me a Coffee** — [buymeacoffee.com/sidon](https://www.buymeacoffee.com/sidon)

Not able to donate? Starring the repo, filing good bug reports, and contributing fixes help just as much.

---

## Contributing

For upstream contributions, please open an issue to discuss substantial changes first, or bring them up on [Discord](https://discord.gg/MJDZm9zmjY). This fork aims to offer small, independently reviewable changes rather than replace the upstream architecture. When submitting a PR:

- Keep the app **local‑only** — no code paths that depend on the real Roborock cloud.
- Follow the existing architecture (single `commonMain` core, `StateFlow` view models, manual DI via `AppContainer`, expect/actual for platform bits).
- Run `./gradlew :composeApp:testDebugUnitTest` before submitting.

---

## License

Released under the [MIT License](./LICENSE) — © 2026 Sidon Kodraliu. You're free to use, modify, and distribute it; the software is provided "as is", without warranty.

---

## Acknowledgements

- [Sidon Kodraliu / DonSidro](https://github.com/DonSidro/LocalRock) — creator and maintainer of LocalRock.
- [@pponce](https://github.com/pponce) — original native camera, iOS audio and S7 MaxV driving contribution ([PR #95](https://github.com/DonSidro/LocalRock/pull/95)).
- [`local_roborock_server`](https://github.com/Python-roborock/local_roborock_server) — the self‑hosted backend this client targets.
- [`python-roborock`](https://github.com/Python-roborock/python-roborock) — reference for the Roborock protocol.
- [go2rtc](https://github.com/AlexxIT/go2rtc) — reference for the camera WebRTC‑over‑MQTT signaling.

> LocalRock is an independent project and is **not affiliated with Roborock**.
