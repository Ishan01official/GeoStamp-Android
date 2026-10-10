# GeoStamp Android

A privacy-first, ad-free GPS camera for Android. Photos and videos carry a stamp with the date, time, place and coordinates, and the camera works fully offline.

## Features (source v0.5.0)

- **Stamped video.** Videos record for up to 60 seconds and then get the same stamp as photos, burned into every frame. The clock ticks each second. Audio, orientation and length are kept, and the result is checked before it reaches the gallery. If stamping fails, the video is still saved and clearly marked as unstamped.
- **Microphone switch** in Video and Dual Video. Muted videos record without asking for microphone access.
- **In-app video player** with play/pause, 10-second skips, a seek bar, mute and full screen.
- **Dual Capture** (Dual Photo and Dual Video, with the same rounded front-camera window in both, also in the saved video) on phones that can run the front and rear cameras at the same time. The front camera shows as a movable, resizable inset that never covers the stamp. On other phones the modes stay visible and explain why they are unavailable. A second camera is never faked.
- **Map types.** Settings → Maps → Map type offers Normal (default), Satellite, Terrain and Hybrid for stamp mini maps. All use keyless providers (see [Online services](#online-services)). If a style cannot be loaded, the stamp falls back to Normal and then to the coordinate panel, so stamping never fails because of a map.
- **QR Location stamp.** A template with the mini map, address, labelled latitude and longitude, date, time and a QR code. Scanning the code with Android Camera, Google Lens, the iPhone Camera or any QR reader opens the exact coordinates as a Google Maps link. Codes are generated on the phone with ZXing, black on white with the standard quiet zone and error-correction level Q, and are sized to survive messaging-app recompression. The QR can also be turned on for any template under Stamp settings → Information.
- **Show location QR.** Tap the GPS chip when a precise fix is ready, or the QR button on a photo that has GPS metadata, to see the code with Open in Maps and Share QR actions.
- **Google Maps by default.** Locations open in the Google Maps app when installed, then in any app that handles `geo:` links, then in the browser. OpenStreetMap remains selectable. An earlier saved choice is kept.
- **In-app Privacy Policy** that works offline and follows the device language.
- **Map Card stamp by default.** Address lookup and map thumbnails are enabled by default once location permission is granted. It shows date and time, a mini map, the address and coordinates. Without a map tile (offline, disabled, or while loading) it shows a plain coordinate panel instead of a fake map.
- **Complete addresses.** Detailed (default) keeps available provider house/building, street, colony, locality and region information, including details supplied only in address lines. Settings → Location → Address detail also offers Standard and Short. Cached components are reformatted immediately when the choice changes; missing information is never invented. Conflicting addresses remain stabilized while stationary. You can correct the address for the next capture or for the session, and corrected addresses are marked as entered manually. Existing photos can be re-stamped from their unstamped original.
- **Honest direction.** One heading value is shared by the screen and the stamp, labelled true, magnetic or course. Phones without a compass sensor show travel direction only while moving. Speeds under 1 km/h are hidden.
- **Camera first.** The app asks for the camera first. Location is offered afterwards as optional, and the location chip always offers a way to turn it on.
- Local gallery with portrait thumbnails, filters, details, share, delete and batch stamping.
- Large text and touch targets, TalkBack labels and a Simple Camera Mode.
- **Follows the system language**, or the one chosen in Settings > Language. English (fallback), Hindi, Spanish, French, German, Portuguese, Japanese, Korean, Simplified Chinese and Arabic, with right-to-left layout for Arabic. Android 13+ also lists these languages under per-app language settings. Dates use the device language, while coordinates, links and the temperature unit stay independent of it. Addresses are formatted from normalized components, so any country's structure works and empty or placeholder values never appear.
- Settings > Camera diagnostics lists the phone's cameras, concurrent-camera support and sensors, and can be shared.

Architecture: single activity, Jetpack Compose, MVVM with StateFlow, DataStore settings, CameraX (single and concurrent cameras), and AndroidX Media3 (Transformer for video stamping, ExoPlayer for playback). The stamp engine (`stamps/`), photo pipeline (`capture/`), video pipeline (`video/`), dual capture (`dual/`) and address handling (`address/`, `environment/`) have no UI dependencies.

## Online services

GeoStamp has no ads, analytics, telemetry or accounts. Network access is used only for these features:

| Feature | Default | Provider | Sent |
|---|---|---|---|
| Address lookup | On | The phone's platform geocoder, usually Google | Current coordinates |
| Map thumbnails, Normal | On | OpenStreetMap (`tile.openstreetmap.org`) | Tile requests around the position |
| Map thumbnails, Terrain | When selected | OpenTopoMap (`tile.opentopomap.org`) | Tile requests around the position |
| Map thumbnails, Satellite and Hybrid | When selected | EOxCloudless 2023 (`tiles.maps.eox.at`), plus OpenStreetMap for Hybrid | Tile requests around the position |
| Weather | Off | Open-Meteo (`api.open-meteo.com`) | Coordinates rounded to 3 decimals |

Satellite imagery is EOxCloudless (Sentinel-2) under CC BY-NC-SA 4.0, which allows non-commercial use. Commercial use of satellite thumbnails needs a licence from EOX. No keyless label-only layer is available, so Hybrid draws OpenStreetMap at partial opacity over the imagery instead of Google-style labels. Credits are printed on each thumbnail and listed in the in-app Privacy Policy. Opening a location or sharing a QR code sends coordinates only to the app the user picks.

Address lookup and map thumbnails default to **on**. Weather and GPS EXIF metadata
remain **off**. Location permission
is still optional, and capture works offline. Turn either service off in Settings;
saved choices are preserved across updates, including previously saved off choices.
Detailed address formatting is the default, with Standard and Short available.

## Current verification status

Version 0.4.0 (map types, QR location, Google Maps opener, in-app privacy policy and
localization) passes `clean`, `assembleDebug`, all 149 unit tests and lint with zero errors.
The tests cover the new defaults, preference migration, link and QR payload generation,
ZXing decoding of the generated codes, international address formatting and placeholder
checks for every translation. On-device checks of the new screens, RTL layout and
cross-phone QR scanning are still pending.

Earlier verification:

Address completeness and service defaults are implemented in the current source. The address
change passed 113 unit tests and eight physical-device tests, including live
geocoding for both supplied coordinates and the photo/video pipelines. See the
[address verification report](docs/address-detail-verification-2026-10-10.md) and
[current progress](docs/progress-report.md#current-status--address-detail-and-service-defaults).
The recorded default-service verification passed all 115 unit tests, APK build and lint (zero
errors; 20 dependency-version warnings), including enabled defaults and saved
opt-outs. The updated APK was installed on the test phone. These changes are
included in the current source. Device evidence describes the recorded test runs;
release publication and broader device coverage are tracked separately.

## Build and install

Requires JDK 17, Android SDK Platform 35 and Build Tools 35.0.0. Runs on Android
10 (API 29) and newer. The repository includes the Gradle 8.9 wrapper.

```bash
git clone https://github.com/Ishan01official/GeoStamp-Android.git
cd GeoStamp-Android
```

Open the project in Android Studio and configure your SDK, or set
`ANDROID_HOME` to its location. Then run:

```bash
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`adb install -r` requires a connected device and preserves existing app data when
signatures match. Build output is a debug APK, not a signed store release.
See [contribution and test setup](CONTRIBUTING.md) for device-test fixtures.

## Known limitations

- Dual Capture depends on real concurrent front/rear camera support.
- Platform geocoding can omit details or return an incorrect house number; manual
  correction is available. Detailed formatting preserves the provider's response.
- If Android kills the app during video stamping, recovery saves the recording
  unstamped at the next launch.
- A phone lying flat may record in landscape. Diagnostics remain in English.
- Translations other than Hindi were written without native-speaker review yet.
- Satellite thumbnails are low resolution (Sentinel-2, about 10 m per pixel) and licensed for non-commercial use only.
- Approximate-only location, disabled providers, no-compass hardware and additional
  devices still need the checks listed in the roadmap.

## Documentation

- [Documentation index](docs/README.md)
- [Privacy and third-party services](PRIVACY.md)
- [Current progress and verification](docs/progress-report.md)
- [Remaining work](docs/roadmap.md)
- [Change history](CHANGELOG.md)
- [Contributing and running tests](CONTRIBUTING.md)
