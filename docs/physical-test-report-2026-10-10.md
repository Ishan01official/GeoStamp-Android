# Physical Device Test Report - 2026-10-10

Historical report for the initial GPS/sensor implementation. Current service
defaults, detailed-address behavior and validation status are documented in
[current progress](progress-report.md#current-status--address-detail-and-service-defaults)
and [address verification](address-detail-verification-2026-10-10.md).

Device tested:
- Model: A142
- ADB serial: `00069343J001241`
- Package: `com.geostamp.camera`
- Branch: `codex/phase-3-location-sensors`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

## Commands Run

```bash
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.geostamp.camera android.permission.CAMERA
adb shell pm grant com.geostamp.camera android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.geostamp.camera android.permission.ACCESS_COARSE_LOCATION
adb shell am start -W -n com.geostamp.camera/.MainActivity
```

## Results

Passed:
- App installed successfully.
- App launched successfully with cold start result `Status: ok`.
- App stayed running after launch; no app crash or ANR was observed.
- Camera preview rendered on the real phone.
- Grid overlay rendered on the camera preview.
- Runtime camera and location permissions were granted and accepted.
- GPS status displayed on device as `gps precise GPS 2m, 0s old`.
- Compass status displayed on device as `NE 27 deg (high)` / `NE 28 deg (high)`.
- Device sensor service confirms accelerometer and magnetic-field sensors are present.
- Controlled capture through ADB tap created a new saved image:
  - `/sdcard/Pictures/GeoStamp/GeoStamp_1791574259664.jpg`
  - Pulled to `/tmp/GeoStamp_1791574259664.jpg`
  - Image size: `3072x4080`
- Saved image contains a visible burned-in stamp:
  - `10 Oct 2026 01:00:59`
  - `Lat: 29.007952  Lon: 77.767760`
  - `Accuracy: +/-2 m`
- System-bar overlap found during the first run was fixed by applying system-bar insets to the root layout.
- Rebuilt, reinstalled, and retested after the system-bar fix.

Warnings observed:
- CameraX / device camera stack logged vendor warnings about missing physical camera IDs `3` and `4`.
- CameraX / device camera stack logged a thermal-info `NullPointerException` warning inside platform Camera2 code.
- These warnings did not crash the app and did not block preview or capture during this test.

Not fully validated:
- Approximate location mode could not be validated through ADB in this session.
- Weak GPS signal could not be physically reproduced in the current indoor test conditions.
- Device master location toggle did not switch off through `cmd location set-location-enabled false --user 0`; it remained `true`.
- Provider-disabled UI state still needs manual Settings validation.
- True-heading correctness was not externally verified against a calibrated compass; the app did display high-accuracy heading while GPS was available.
- Front-camera capture, flash behavior, timer capture, exposure slider, and resolution/aspect changes still need manual hands-on validation.

## Evidence Files

Local pulled artifacts:
- `/tmp/geostamp_launch.png`
- `/tmp/geostamp_insets_fixed.png`
- `/tmp/geostamp_after_capture.png`
- `/tmp/GeoStamp_1791574259664.jpg`

Device artifacts:
- `/sdcard/Pictures/GeoStamp/GeoStamp_1791574259664.jpg`
- `/sdcard/Download/geostamp_launch.png`
- `/sdcard/Download/geostamp_insets_fixed.png`
- `/sdcard/Download/geostamp_after_capture.png`

