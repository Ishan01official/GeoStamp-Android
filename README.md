# GeoStamp Android

A privacy-first, ad-free GPS camera for Android. Photos and videos carry a stamp with the date, time, place and coordinates, and the camera works fully offline.

## Features (v0.3.0)

- **Stamped video.** Videos record for up to 60 seconds and then get the same stamp as photos, burned into every frame. The clock ticks each second. Audio, orientation and length are kept, and the result is checked before it reaches the gallery. If stamping fails, the video is still saved and clearly marked as unstamped.
- **In-app video player** with play/pause, 10-second skips, a seek bar, mute and full screen.
- **Dual Capture** (Dual Photo and Dual Video) on phones that can run the front and rear cameras at the same time. The front camera shows as a movable, resizable inset that never covers the stamp. On other phones the modes stay visible and explain why they are unavailable. A second camera is never faked.
- **Map Card stamp by default.** It shows date and time, a mini map, the address and coordinates. Without a map tile (offline, or before map tiles are allowed) it shows a plain coordinate panel instead of a fake map.
- **Trustworthy addresses.** Reverse-geocoded house numbers are hidden unless repeated lookups agree, and the address stays steady while you stand still. You can correct the address for the next capture or for the session, and corrected addresses are marked as entered manually. Existing photos can be re-stamped from their unstamped original.
- **Honest direction.** One heading value is shared by the screen and the stamp, labelled true, magnetic or course. Phones without a compass sensor show travel direction only while moving. Speeds under 1 km/h are hidden.
- **Camera first.** The app asks for the camera first. Location is offered afterwards as optional, and the location chip always offers a way to turn it on.
- Local gallery with portrait thumbnails, filters, details, share, delete and batch stamping.
- Large text and touch targets, TalkBack labels, a Simple Camera Mode, English and Hindi.
- Settings > Camera diagnostics lists the phone's cameras, concurrent-camera support and sensors, and can be shared.

Architecture: single activity, Jetpack Compose, MVVM with StateFlow, DataStore settings, CameraX (single and concurrent cameras), and AndroidX Media3 (Transformer for video stamping, ExoPlayer for playback). The stamp engine (`stamps/`), photo pipeline (`capture/`), video pipeline (`video/`), dual capture (`dual/`) and address handling (`address/`, `environment/`) have no UI dependencies.

## Build

Requires JDK 17 and Android SDK 35. Runs on Android 10 and newer.

```bash
./gradlew clean assembleDebug testDebugUnitTest lintDebug
```

The on-device video stamping test needs input clips in the app's `files/verify` folder. See `app/src/androidTest/.../VideoStampPipelineTest.kt`.

See [privacy](PRIVACY.md), [roadmap](docs/roadmap.md) and the [latest device test report](docs/physical-test-report-2026-10-10-v0.3.md).
