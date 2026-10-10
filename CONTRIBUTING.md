# Contributing to GeoStamp

## Development setup

Use JDK 17 and Android SDK Platform 35 with Build Tools 35.0.0. Open the project
in Android Studio or configure `ANDROID_HOME` / a local `local.properties` file
with your SDK path. `local.properties`, generated builds, APKs and AABs are ignored.
Use the checked-in Gradle wrapper; a separate Gradle installation is unnecessary.

```bash
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug
```

Unit test results: `app/build/reports/tests/testDebugUnitTest/index.html`.
Lint results: `app/build/reports/lint-results-debug.html`.
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
For cached dependencies only, append `--offline`.

## Device tests

A connected Android 10+ device or emulator is required. Camera, concurrent-camera,
sensor and codec behavior needs physical hardware validation too.

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r \
  com.geostamp.camera.test/androidx.test.runner.AndroidJUnitRunner
```

Direct instrumentation keeps the installed app and settings. Gradle's
`connectedDebugAndroidTest` can uninstall the app afterwards; use a dedicated
test device if running it. Ordinary address tests use local fixtures. Live
geocoding is explicitly enabled with `-e liveGeocoding true`, sends the report's
coordinates to the device geocoder and can produce different provider responses.

`VideoStampPipelineTest` skips when no `input_*.mp4` fixtures exist. Its default
fixture directory is the app's external-files `verify` folder; alternatively use
app-private files to avoid external-storage access restrictions:

```bash
adb shell run-as com.geostamp.camera mkdir -p files/verify
adb shell -T 'run-as com.geostamp.camera sh -c "cat > files/verify/input_5s.mp4"' < /path/to/input_5s.mp4
adb shell am instrument -w -r \
  -e videoFixtureDirectory /data/user/0/com.geostamp.camera/files/verify \
  com.geostamp.camera.test/androidx.test.runner.AndroidJUnitRunner
```

Replace `/path/to/input_5s.mp4` with a real recording. Add 30/60-second fixtures
when checking the full recording window. The test writes `output_*.mp4` alongside
inputs and validates audio, duration and orientation. Inspect extracted frames
for stamp placement and ticking time; report skipped tests explicitly.
See [device evidence](docs/physical-test-report-2026-10-10-v0.3.md) and
[address-test details](docs/address-detail-verification-2026-10-10.md).

## Changes and review

Keep camera, capture, stamp, location and environment work in their existing
packages. Update English and Hindi strings together when changing user-facing
text, and update privacy disclosures when service behavior changes. Preserve
stored choices, optional location permission and the offline capture path.

Run checks appropriate to the change and `git diff --check`. State which checks
were executed, distinguish unit results from device observations, and include
known limitations. Documentation-only changes need link and consistency checks.

The Android GitHub Actions workflow runs on pull requests and pushes to `main`.
It validates the wrapper, builds the APK, runs unit tests/lint and uploads a debug
APK artifact. Signed release and store preparation remain on the [roadmap](docs/roadmap.md).
