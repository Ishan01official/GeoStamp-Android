# GPS Map Camera: GeoStamp

A privacy-first, ad-free GPS map camera for Android. Photos and videos can carry a visible stamp with the date, time, address, map and coordinates, while media processing stays on the device.

## Features (source v0.4.0)

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
- **Ad-free and tracker-free by design.** No advertising, analytics, crash-reporting or account SDKs are included in the current source.

Architecture: single activity, Jetpack Compose, MVVM with StateFlow, DataStore settings, CameraX (single and concurrent cameras), and AndroidX Media3 (Transformer for video stamping, ExoPlayer for playback). The stamp engine (`stamps/`), photo pipeline (`capture/`), video pipeline (`video/`), dual capture (`dual/`) and address handling (`address/`, `environment/`) have no UI dependencies.

## Online service defaults

Address lookup and map thumbnails default to **on**. Weather and GPS EXIF metadata
remain **off**. Address lookup sends coordinates to the phone's platform geocoder;
map tile requests reveal approximate location to OpenStreetMap. Location permission
is still optional, and capture works offline. Turn either service off in Settings;
saved choices are preserved across updates, including previously saved off choices.
Detailed address formatting is the default, with Standard and Short available.

## Current verification status

Address completeness and service defaults are implemented in the current source. The address
change passed 113 unit tests and eight physical-device tests, including live
geocoding for both supplied coordinates and the photo/video pipelines. See the
[address verification report](docs/address-detail-verification-2026-10-10.md) and
[current progress](docs/progress-report.md#current-status--address-detail-and-service-defaults).
The recorded default-service verification passed all 115 unit tests, APK build and lint (zero
errors; 20 dependency-version warnings), including enabled defaults and saved
opt-outs. The updated APK was installed on the test phone. These recorded results predate the
v0.4.0 Play-readiness build changes; the release branch must pass CI and the manual device
matrix in [the Play Store release checklist](docs/play-store-release-checklist.md) before publication.

## Build and install

Requires JDK 17, Android SDK Platform 36 and a compatible Android SDK Build Tools installation. Runs on Android
10 (API 29) and newer. The repository uses Android Gradle Plugin 8.10.1 and the Gradle 8.11.1 wrapper.

```bash
git clone https://github.com/Ishan01official/GeoStamp-Android.git
cd GeoStamp-Android
```

Open the project in Android Studio and configure your SDK, or set
`ANDROID_HOME` to its location. Then run:

```bash
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug bundleRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`adb install -r` requires a connected device and preserves existing app data when
signatures match. `bundleRelease` produces an unsigned release AAB unless signing is configured.
Google Play App Signing should be configured in Play Console for the production release.
See [contribution and test setup](CONTRIBUTING.md) for device-test fixtures.

## Google Play preparation

- Recommended Play title: **GPS Map Camera: GeoStamp**
- Launcher label: **GPS Map Camera**
- Store listing copy and screenshot plan: [docs/play-store-listing.md](docs/play-store-listing.md)
- Release/device checklist: [docs/play-store-release-checklist.md](docs/play-store-release-checklist.md)
- Privacy policy source: [PRIVACY.md](PRIVACY.md)

## Known limitations

- Dual Capture depends on real concurrent front/rear camera support.
- Platform geocoding can omit details or return an incorrect house number; manual
  correction is available. Detailed formatting preserves the provider's response.
- If Android kills the app during video stamping, recovery saves the recording
  unstamped at the next launch.
- A phone lying flat may record in landscape. Diagnostics remain in English.
- Approximate-only location, disabled providers, no-compass hardware and additional
  devices still need the checks listed in the roadmap and release checklist.

## Documentation

- [Documentation index](docs/README.md)
- [Privacy and third-party services](PRIVACY.md)
- [Play Store listing](docs/play-store-listing.md)
- [Play Store release checklist](docs/play-store-release-checklist.md)
- [Current progress and verification](docs/progress-report.md)
- [Remaining work](docs/roadmap.md)
- [Change history](CHANGELOG.md)
- [Contributing and running tests](CONTRIBUTING.md)
