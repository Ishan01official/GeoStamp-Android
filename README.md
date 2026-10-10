# GeoStamp Android

A privacy-first, ad-free GPS camera for Android. Photos and videos carry a stamp with the date, time, place and coordinates, and the camera works fully offline.

## Features (v0.3.0)

- **Stamped video.** Videos record for up to 60 seconds and then get the same stamp as photos, burned into every frame. The clock ticks each second. Audio, orientation and length are kept, and the result is checked before it reaches the gallery. If stamping fails, the video is still saved and clearly marked as unstamped.
- **In-app video player** with play/pause, 10-second skips, a seek bar, mute and full screen.
- **Dual Capture** (Dual Photo and Dual Video) on phones that can run the front and rear cameras at the same time. The front camera shows as a movable, resizable inset that never covers the stamp. On other phones the modes stay visible and explain why they are unavailable. A second camera is never faked.
- **Map Card stamp by default.** Address lookup and OpenStreetMap thumbnails are enabled by default once location permission is granted. It shows date and time, a mini map, the address and coordinates. Without a map tile (offline, disabled, or while loading) it shows a plain coordinate panel instead of a fake map.
- **Complete addresses.** Detailed (default) keeps available provider house/building, street, colony, locality and region information, including details supplied only in address lines. Settings → Location → Address detail also offers Standard and Short. Cached components are reformatted immediately when the choice changes; missing information is never invented. Conflicting addresses remain stabilized while stationary. You can correct the address for the next capture or for the session, and corrected addresses are marked as entered manually. Existing photos can be re-stamped from their unstamped original.
- **Honest direction.** One heading value is shared by the screen and the stamp, labelled true, magnetic or course. Phones without a compass sensor show travel direction only while moving. Speeds under 1 km/h are hidden.
- **Camera first.** The app asks for the camera first. Location is offered afterwards as optional, and the location chip always offers a way to turn it on.
- Local gallery with portrait thumbnails, filters, details, share, delete and batch stamping.
- Large text and touch targets, TalkBack labels, a Simple Camera Mode, English and Hindi.
- Settings > Camera diagnostics lists the phone's cameras, concurrent-camera support and sensors, and can be shared.

Architecture: single activity, Jetpack Compose, MVVM with StateFlow, DataStore settings, CameraX (single and concurrent cameras), and AndroidX Media3 (Transformer for video stamping, ExoPlayer for playback). The stamp engine (`stamps/`), photo pipeline (`capture/`), video pipeline (`video/`), dual capture (`dual/`) and address handling (`address/`, `environment/`) have no UI dependencies.

## Online service defaults

Address lookup and map thumbnails default to **on**. Weather and GPS EXIF metadata
remain **off**. Address lookup sends coordinates to the phone's platform geocoder;
map tile requests reveal approximate location to OpenStreetMap. Location permission
is still optional, and capture works offline. Turn either service off in Settings;
saved choices are preserved across updates, including previously saved off choices.
Detailed address formatting is the default, with Standard and Short available.

## Current verification status

Address completeness and service defaults are implemented locally. The address
change passed 113 unit tests and eight physical-device tests, including live
geocoding for both supplied coordinates and the photo/video pipelines. See the
[address verification report](docs/address-detail-verification-2026-10-10.md) and
[current progress](docs/progress-report.md#current-status--address-detail-and-service-defaults).
The default-service change passes all 115 unit tests, APK build and lint (zero
errors; 20 dependency-version warnings), including enabled defaults and saved
opt-outs. The updated APK is installed on the connected phone. These changes are
committed locally; they have not been pushed or published as a release.

## Build

Requires JDK 17 and Android SDK 35. Runs on Android 10 and newer.

```bash
./gradlew clean assembleDebug testDebugUnitTest lintDebug
```

The on-device video stamping test needs input clips in the app's `files/verify` folder. See `app/src/androidTest/.../VideoStampPipelineTest.kt`.

See [privacy](PRIVACY.md), [roadmap](docs/roadmap.md), the [address verification report](docs/address-detail-verification-2026-10-10.md) and the [v0.3.0 device test report](docs/physical-test-report-2026-10-10-v0.3.md).
