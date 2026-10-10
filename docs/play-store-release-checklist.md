# Play Store release checklist

Prepared: 2026-10-11

This checklist is for the first Google Play release of **GPS Map Camera: GeoStamp**.

## 1. Build and policy baseline

- [x] `compileSdk = 36`
- [x] `targetSdk = 36`
- [x] Android Gradle Plugin upgraded to an API-36-capable release
- [x] Gradle wrapper upgraded for the selected AGP version
- [x] Version bumped to `0.4.0` / version code `4`
- [x] CI builds APK, runs unit tests, runs lint and builds a release AAB
- [ ] Configure Play App Signing in Play Console
- [ ] Upload the final release AAB through Play Console

Google Play requires new phone/tablet apps submitted after 31 August 2026 to target Android 16 / API 36 or newer.

## 2. Branding and listing

- [x] New adaptive app icon: camera + location marker
- [x] Dedicated monochrome icon for themed Android launchers
- [x] Round icon configured
- [x] Launcher label changed to `GPS Map Camera`
- [x] Recommended Play title: `GPS Map Camera: GeoStamp`
- [x] Store copy and screenshot plan documented in `docs/play-store-listing.md`
- [ ] Create final 512 × 512 Play Store icon export from the approved launcher artwork
- [ ] Capture privacy-safe Play Store screenshots from the release build
- [ ] Create final feature graphic

## 3. Permissions and privacy

Verify on a clean install, not only after an app update.

- [ ] Camera permission requested only when required for camera use
- [ ] Location remains optional
- [ ] App works when location permission is denied
- [ ] Approximate-only location does not crash or block capture
- [ ] No background location request
- [ ] Video can record muted when microphone access is off/denied where supported
- [ ] Microphone state is clearly visible in Video and Dual Video
- [ ] Internet permission is used only for documented address/map/weather services
- [ ] No advertising SDKs
- [ ] No analytics/tracking SDKs
- [ ] No crash-reporting SDK unless the privacy policy and Data safety declaration are updated first
- [ ] Public privacy-policy URL is reachable without login
- [ ] Play Console Data safety answers match the shipped binary

## 4. Photo regression matrix

Run each item with Photo and, where supported, Dual Photo.

- [ ] 4:3 portrait
- [ ] 16:9 portrait
- [ ] Landscape left
- [ ] Landscape right
- [ ] Front camera
- [ ] Rear camera
- [ ] Flash off / auto / on on supported hardware
- [ ] Grid on/off
- [ ] Timer off / 3 s / 10 s
- [ ] Tap-to-focus
- [ ] 1× and available zoom levels
- [ ] Long detailed address wraps without overlap
- [ ] Address edit button remains reachable and does not cover text
- [ ] QR code does not overlap text, map or edit control
- [ ] Mini map available
- [ ] Mini map unavailable/offline fallback
- [ ] GPS disabled
- [ ] GPS stale/low accuracy
- [ ] No compass hardware
- [ ] Weather disabled
- [ ] Weather enabled and temporarily unavailable
- [ ] Saved image opens correctly in Gallery
- [ ] EXIF GPS on/off behaviour matches setting
- [ ] Keep original on/off behaves correctly

## 5. Video regression matrix

Run each item with Video and, where supported, Dual Video.

- [ ] Portrait 9:16 live preview has no stamp/control overlap
- [ ] Landscape recording
- [ ] Microphone ON includes audio
- [ ] Microphone OFF records silently
- [ ] Microphone permission denied is handled gracefully
- [ ] 60-second maximum is enforced correctly
- [ ] Recording can be stopped early
- [ ] Live time increments correctly
- [ ] Saved burned-in stamp stays in the safe area
- [ ] Front-camera PiP is rounded and not stretched
- [ ] Dual Video PiP does not cover stamp or capture controls
- [ ] Rotation/orientation metadata is correct
- [ ] Video opens and plays in the in-app player
- [ ] Play/pause, seek, ±10 s, mute and full-screen controls work
- [ ] Interrupted/failed stamping preserves the original recording as designed

## 6. Device coverage before production

Minimum practical release gate:

- [ ] One Android 10/11 device (oldest supported range)
- [ ] One Android 13/14 device
- [ ] One Android 15 device
- [ ] One Android 16 device
- [ ] One Samsung device
- [ ] One Motorola device
- [ ] One Nothing/Pixel/AOSP-like device
- [ ] At least one phone without concurrent-camera support
- [ ] At least one phone with concurrent-camera support, if available

For each physical device record: model, Android version, app version, camera modes tested, pass/fail and any device-specific issue.

## 7. Crash and quality gate

- [ ] GitHub Actions passes on the release commit
- [ ] Unit tests pass
- [ ] Android lint has no release-blocking errors
- [ ] Debug APK installs on a clean device
- [ ] Release AAB is generated successfully
- [ ] No crash during 20 consecutive photo captures
- [ ] No crash during 10 consecutive video captures
- [ ] No crash while rapidly opening/closing Settings and Gallery
- [ ] No crash after revoking permissions from Android Settings and returning to the app
- [ ] No crash after switching camera modes repeatedly
- [ ] No ANR observed during video post-processing

## 8. Play Console setup

- [ ] Complete app access declaration
- [ ] Complete Ads declaration: **No ads**
- [ ] Complete Data safety form from the released binary behaviour
- [ ] Complete content rating questionnaire
- [ ] Set target audience accurately
- [ ] Add privacy policy URL
- [ ] Add support email
- [ ] Add website if available
- [ ] Upload app icon, feature graphic and screenshots
- [ ] Add release notes
- [ ] Upload AAB to internal testing first
- [ ] Test Play-installed build on at least two devices
- [ ] Move to closed testing if required by the developer-account testing rules
- [ ] Production rollout only after Play pre-launch report and manual checks are reviewed

## Release rule

Do not treat a successful Gradle build as proof that camera, microphone, GPS, geocoding, concurrent camera, video encoding or Media3 behaviour works on real hardware. The production gate is **CI pass + physical-device regression pass + Play-installed build pass**.
