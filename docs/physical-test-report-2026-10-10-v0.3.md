# Physical device test report: v0.3.0, 10 October 2026

Status note: this report records the v0.3.0 test run before the address-completeness
and default-service updates. The earlier short address and disabled defaults below
are historical observations. Current source preserves provider address detail and
defaults address lookup and map thumbnails to on; saved service choices remain
respected. See [current progress](progress-report.md#current-status--address-detail-and-service-defaults)
and [address verification](address-detail-verification-2026-10-10.md).

Device: Nothing Phone (2a), model A142 (Pacman), Android 16 (API 36), ADB serial `00069343J001241`.
Branch: `fix/video-stamp-dual-capture-release`. Every result below comes from this branch's builds, installed with `adb install -r` so app data was kept.

## Build checks

| Command | Result |
|---|---|
| `./gradlew clean assembleDebug testDebugUnitTest lintDebug` | Passes. 111 unit tests, 0 failures. Lint 0 errors; 20 warnings, all "newer library version available" |
| `am instrument` (all instrumented tests) | `ImagePipelineTest` 4/4 and `VideoStampPipelineTest` 1/1 pass |

Gradle's `connectedDebugAndroidTest` was not used because it uninstalls the app afterwards and would wipe the tester's settings.

## Stamped video

The on-device pipeline test used 5, 30 and 60 s clips cut losslessly from a real GeoStamp recording (H.264 1080x1920 upright, rotation 90, AAC). Each output was compared with its input using ffprobe.

| Clip | Video frames in/out | Audio duration in/out | Orientation | Time to stamp |
|---|---|---|---|---|
| 5 s | 149 / 149 | 4.957 / 4.957 s | same | 4.4 s |
| 30 s | 868 / 868 | 29.963 / 29.963 s | same | 21.9 s |
| 60 s | 1741 / 1741 | 59.951 / 59.951 s | same | 37.8 s |

Audio starts at the same offset (0.056 s) in input and output. Frames extracted at the start, middle and end of every clip show the Map Card stamp, and the clock advances once per second (for example 09:30:00, 09:30:30, 09:30:59).

In-app recording:
- A recording left running stopped itself at 59.99 s. The progress card showed "Adding stamp to video… 61%" with Cancel, then "Stamped video saved". Frames at 0.5, 30 and 59.5 s are stamped and read 09:45:37, 09:46:07 and 09:46:36.
- A 12 s recording showed "REC 00:03 / 01:00" with a progress bar at the top of the picture, and was saved stamped.
- The in-app player plays the stamped MP4 (0:02 / 0:16 shown while playing). The stamp is visible because it is in the frames.
- The old pipeline's half-written `.pending` files on this phone have no moov atom, which confirms the original failure.

## Dual Capture

Camera diagnostics on this phone:
- Concurrent camera feature: yes. Concurrent set: [0+1]. Front + rear pair: rear 0 + front 1.
- Dual Photo and Dual Video: supported. Last start of DUAL_PHOTO and DUAL_VIDEO: started.
- Camera 0: rear, flash, LEVEL_3, sensor 90°. Camera 1: front, no flash, LEVEL_3, sensor 270°.

Results:
- Dual Photo saves a 1440x1920 photo with the front view in a framed inset. The size is limited by the concurrent stream combination.
- Dual Video records one composed MP4 (13.4 s test clip, H.264 + AAC) with the front camera as picture-in-picture and the stamp in every frame.
- The inset moves and resizes in both modes. In a recorded clip it landed exactly where requested and stayed clear of the stamp.

Defects found and fixed during these tests:
1. The app crashed when switching from a dual mode back to Photo or Video ("Unbind UseCase is not supported in concurrent camera mode"). This happened 6 times in hands-on use. After the fix, the app process stayed alive across repeated switches.
2. The Dual Photo preview did not redraw after Move or Size.
3. Dual Video kept its first inset position: CameraX reuses composition settings per lifecycle owner.
4. Dual Video composition offsets are in upright display coordinates, not sensor coordinates. Measured: offsets (-0.6,-0.6), (-0.6,0.6) and (0,-0.6) put the inset centre at (0.2,0.8), (0.2,0.2) and (0.5,0.8).
5. The REC badge was hidden behind a snackbar that never went away.

## Other features

- **Flash:** after revoking camera access, relaunching and granting it in the system dialog, the flash control was enabled.
- **Location denied:** "Location off" chip; the camera keeps working with a date-only stamp. Tapping the chip explains the state and offers "Allow location". Once allowed, the chip shows "Location ready". Permissions were restored to granted after the test.
- **Address:** while stationary for about 30 minutes the detected address stayed "Ganga Nagar, Meerut, Uttar Pradesh 250001, India", with no house number. A photo with a typed address ("H46 Nav Shakti Dham…") has `GeoStamp:stamped;address=manual` in its EXIF comment.
- **Live stamp:** coordinates appear on the live stamp. Before the clock-skew fix, the live stamp sometimes showed only the date.
- **Layout:** gallery tiles are 3:4 with stamps visible, filters wrap, and all four mode labels fit.
- **Hindi:** the camera screen, mode labels, Settings and the stamp (date and geocoded address) display in Hindi. The app language was set with `cmd locale set-app-locales` and reset to the system default afterwards.
- **Sensors:** accelerometer, magnetometer, rotation vector and game rotation vector are present. The compass uses the rotation vector.

## Not verified on this phone

- The no-magnetometer path (this phone has a magnetometer). It is covered by unit tests with fake sensor capabilities.
- Approximate-only location, and device location switched off.
- Recovery of a recording after the app process is killed mid-stamping.
- GPS drift while walking, and course-over-ground direction.
- First-install onboarding on a clean install. A clean install would erase the tester's data, so only the camera-permission grant was replayed.

## Known limitations

- With the phone lying flat, CameraX may record in landscape. The stamp then lays out as a landscape card, which is correct for the file but may not be what the user expects.
- If Android kills the app while a video is being stamped, the recording is recovered at the next launch but saved unstamped. A foreground service would be needed to finish stamping in the background.
- Camera diagnostics labels are in English in every language. The report is a technical one meant for sharing.
