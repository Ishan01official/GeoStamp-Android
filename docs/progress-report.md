# GeoStamp Progress Report

Updated: 2026-10-10

This file reports actual implementation status. "Build verified" means the local command below passed:

```bash
./gradlew --no-daemon clean assembleDebug testDebugUnitTest lintDebug
```

Debug APK path:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Current Summary

- Phase 1 build repair: implemented and build verified.
- Main branch verification: `main` is updated to `origin/main` and build verified after the Phase 2 merge.
- Phase 2 camera MVP: implemented in code and build verified, but still needs physical-device validation.
- Phase 3 GPS and sensor core: implemented in code and build verified, but still needs physical-device validation.
- Physical testing started on device `A142`; camera preview, GPS, compass display, and one stamped capture were validated.
- Later phases are not complete yet. Some supporting primitives exist, but they are not release-ready features.

## Phase 1 - Repository Audit And Build Repair

Status: Done, build verified.

Completed:
- Android Gradle wrapper added.
- Android Gradle Plugin and Kotlin build compile with JDK 17.
- SDK 35 build works locally.
- JUnit unit test dependency added.
- Debug APK generation verified.
- Unit tests run.
- Android lint runs.
- GitHub Actions workflow added for build, unit tests, lint, wrapper validation, and debug APK upload.

Still left:
- Validate CI result on GitHub after pull request or branch workflow runs.
- Clean up local Android SDK corruption warning for `/home/ishan/Android/Sdk/build-tools/35.0.0/package.xml` if it appears again.

## Phase 2 - Camera Implementation

Status: Implemented in code, build verified, partially physical-device tested.

Completed:
- CameraX live preview.
- Photo capture to JPEG.
- Front and rear camera switching.
- Flash modes: off, auto, on.
- Tap-to-focus with autofocus and auto-exposure metering.
- Pinch-to-zoom.
- Zoom slider.
- Exposure compensation slider where the camera reports support.
- Photo aspect ratio selection: 4:3 and 16:9.
- Photo resolution presets: auto, balanced, high.
- Grid lines with on/off toggle.
- Capture timer: off, 3 seconds, 10 seconds.
- Target rotation is applied before capture.
- Captured bitmap is rotated upright before stamping and saving.
- Last photo thumbnail preview after save.
- Camera permission denial keeps the app in a clear error state.
- Camera capability checks for flash, zoom, exposure, and front-camera fallback.

Partially done:
- Resolution selection uses requested CameraX target sizes with fallback to auto. It does not yet show the exact supported resolution list from each physical camera.
- Autofocus uses tap-to-focus. Continuous AF behavior is left to CameraX defaults.
- Last photo preview shows a thumbnail only. It does not yet open a full-screen preview.
- Orientation is handled for still bitmap stamping, but EXIF orientation and advanced metadata handling are not complete.

Needs physical-device validation:
- Rear camera preview and capture. Basic rear preview and one capture passed on A142.
- Front camera preview and capture.
- Flash behavior on real hardware.
- Tap-to-focus accuracy.
- Pinch-to-zoom smoothness.
- Zoom slider behavior.
- Exposure slider behavior on devices that support exposure compensation.
- Timer countdown and delayed capture.
- Aspect ratio and resolution output.
- Saved stamped photo orientation. One portrait capture saved upright at 3072x4080.
- Motorola Edge 70 Fusion behavior.

## Phase 3 - GPS And Sensor System

Status: Core offline GPS and compass implementation is done, build verified, partially physical-device tested.

Completed:
- Continuous foreground fine/coarse location updates while the camera screen is active.
- Camera remains usable when location is denied.
- Latitude, longitude, accuracy, timestamp, altitude, speed, provider, approximate-location flag, and optional address field model exists.
- Freshness and accuracy helpers exist for location snapshots.
- Capture now refuses stale or weak location data instead of silently stamping it.
- OpenStreetMap URL helper exists.
- Google Maps URL helper exists.
- Provider status model exists for GPS/network/selected provider.
- GPS disabled, permission denied, stale last-known location, provider unavailable, approximate location, and weak accuracy states have explicit UI text.
- Foreground location tracker removes updates when the activity stops.
- Compass sensor monitor exists using accelerometer and magnetometer.
- Compass UI reports cardinal direction, degrees, and calibration accuracy.
- Magnetic heading is corrected toward true heading when a location reference is available.
- Compass unavailable state is handled.
- Unit tests cover location map links, freshness, location update text, compass cardinal directions, and compass reading display.

Still left:
- Physical-device validation for network provider fallback, approximate location, stale location, and weak signal.
- Physical-device validation for compass accuracy changes and external true-heading correctness.
- Full address support is not implemented because online reverse geocoding must remain opt-in.
- Google Maps/OpenStreetMap links exist in the model but are not exposed in the UI yet.
- More automated tests around Android permission/provider failure paths need fakes or instrumentation tests.

Physical-device notes:
- A142 displayed `gps precise GPS 2m, 0s old`.
- A142 displayed compass status such as `NE 27 deg (high)`.
- A controlled capture saved a stamped image with latitude, longitude, and +/-2 m accuracy.
- See `docs/physical-test-report-2026-10-10.md`.

## Phase 4 - Professional Photo Stamping

Status: Partially implemented.

Completed:
- Visible stamp is burned into saved JPEG.
- Timestamp, coordinates, and accuracy can be formatted.
- Reusable stamp text formatter exists.
- Unit tests cover core stamp text formatting.

Still left:
- Stamp templates: Minimal, Classic GPS, Map Card, Professional Field Inspection.
- Stamp position editor.
- Font size, text color, opacity, padding, and coordinate precision controls.
- Address, compass, altitude, speed, weather, custom logo, and map thumbnail rendering.
- EXIF GPS stripping control.
- Save unstamped original option.
- Orientation and quality hardening for all camera outputs.

## Phase 5 - Maps And Weather

Status: Not implemented.

Still left:
- Optional OpenStreetMap-compatible map thumbnail.
- Attribution handling.
- Offline fallback.
- Opt-in provider settings.
- Optional weather provider integration.
- Weather privacy disclosure and stale observation handling.

## Phase 6 - Gallery And Batch Stamping

Status: Not implemented.

Still left:
- In-app gallery.
- Full-screen preview.
- Share and delete.
- Metadata view.
- Open photo location in maps.
- Save stamped and unstamped versions.
- Android Photo Picker import.
- Batch stamping.

## Phase 7 - Video Recording With Stamps

Status: Not implemented.

Still left:
- CameraX video recording.
- Microphone control.
- Encoded visible video stamps.
- Video orientation and audio synchronization.
- Performance and battery testing.

## Phase 8 - UI/UX Design

Status: Early prototype only.

Completed:
- Functional native Android view UI for camera MVP controls.

Still left:
- Jetpack Compose and Material 3 interface.
- Camera, gallery, stamp editor, templates, settings, privacy, and about screens.
- Dark/light/system theme.
- Hindi and English localization.
- Accessibility and screen reader pass.
- Responsive layout for different screen sizes.

## Phase 9 - Privacy And Security

Status: Partially implemented.

Completed:
- No ads.
- No analytics SDK.
- No login.
- No automatic cloud upload.
- No `INTERNET` permission in the current manifest.
- Offline camera and coordinate stamping path exists.

Still left:
- Independent user controls for visible GPS stamp, EXIF GPS metadata, online geocoding, maps, weather, and original saving.
- Secure sharing flow.
- Permission documentation review.
- Automated privacy checks.

## Phase 10 - Testing

Status: Early unit tests plus build/lint verification.

Completed:
- Unit tests for compass cardinal formatting.
- Unit tests for location freshness.
- Unit tests for location map links and location status text.
- Unit tests for compass reading display.
- Unit tests for stamp text formatting.
- Local build/test/lint command passes.

Still left:
- Camera tests.
- GPS tests.
- Image stamping tests.
- EXIF tests.
- Compose UI tests after UI migration.
- Android instrumentation tests.
- Emulator validation.
- Physical-device validation.

## Phase 11 - GitHub Actions

Status: Implemented, pending remote run confirmation.

Completed:
- Workflow checks out repo.
- Sets up JDK 17.
- Sets up Android SDK.
- Validates Gradle wrapper.
- Runs build, unit tests, and lint.
- Uploads debug APK.

Still left:
- Confirm green workflow on GitHub after push/PR.

## Phase 12 - Google Play Store Preparation

Status: Not implemented.

Still left:
- App icon.
- Feature graphic.
- Store screenshots.
- Short and full descriptions.
- Privacy policy finalization.
- Data Safety documentation.
- Content rating.
- Release notes.
- Signed Android App Bundle.
- Internal and closed testing setup.
- Current Google Play requirement verification.

## Phase 13 - GitHub Commits

Status: In progress.

Completed:
- Phase 1 and Phase 2 work is merged into `main`.
- `main` was fetched, fast-forwarded locally, and build verified on 2026-10-10.
- Phase 3 work is on branch `codex/phase-3-location-sensors`.

Current local changes after this report:
- Phase 3 GPS and compass implementation.
- Updated progress report and roadmap.

Still left:
- Commit and push Phase 3 after final verification.

## Phase 14 - Required Deliverables

Status: Not complete.

Available now:
- Source code.
- Debug APK.
- Local build command.
- Unit test and lint result.
- CI workflow file.
- This progress report.

Still left:
- Full feature completion report after later phases.
- Privacy and security review after feature completion.
- Known issues and limitations after device testing.
- Google Play release documentation.
- Release-ready signed AAB.
