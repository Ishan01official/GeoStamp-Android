# GeoStamp Progress Report

Updated: 2026-10-10. Source version: 0.3.0 (version code 3).

## Current status — address detail and service defaults

The current implementation includes stamped photos and videos, dual capture on
supported phones, a local gallery and player, configurable stamps, and English
and Hindi interfaces. The source version does not establish release publication.

| Area | Implemented behavior | Verification / remaining work |
| --- | --- | --- |
| Camera | CameraX preview, photo/video, flash, focus, zoom, exposure, timer, grid and resolution presets | Preview/capture and flash checked on A142; broader camera/device coverage remains |
| Stamps | Four templates, fields, position, font size, colors, opacity, logo, date/coordinate formats, EXIF controls and original saving | Unit and device image-pipeline tests; more orientation and memory testing remains |
| Location | Optional foreground location, freshness/accuracy checks, stabilized heading and travel direction fallback | GPS/compass display checked; approximate-only, disabled-provider and weak-signal device checks remain |
| Addresses | Provider lines/components, Detailed / Standard / Short, compatible enrichment, component cache and manual correction | 113 unit tests and eight device tests in the address verification run |
| Online services | Address and map defaults on; weather off; saved opt-outs retained | 115 unit tests plus build/lint in the subsequent defaults verification run |
| Gallery | Filters, viewer, sharing, deletion, metadata, location links, video playback and batch stamping | Player/layout checked on A142; batch and delete-consent checks remain |
| Video | Up to 60 seconds, stamp rendered in frames, ticking time, audio/orientation validation, unstamped fallback | 5/30/60-second device fixtures and in-app recordings checked |
| Dual capture | Real concurrent front/rear streams, movable/resizable inset and composition | Dual Photo/Video checked on A142; other supported/unsupported phones remain |
| Interface | Compose/Material 3, system/light/dark theme, Simple Camera Mode, English/Hindi and accessibility labels | Hindi/layout checked; full TalkBack and screen-size review remains |
| Privacy | No ads/accounts/analytics SDKs, local processing, no automatic upload, backup disabled | Source disclosure in PRIVACY.md; independent audit/store preparation remain |

Address formatting is shared by camera, photo, video and gallery paths. Detailed
is the default; missing information is never invented. Service defaults apply
only to missing preference keys, so existing saved choices are preserved.

## Recorded validation

- Address verification: 113 unit tests and eight device tests passed on A142 /
  Android 16, including live geocoding and photo/video stamping.
- Subsequent service-default verification: 115 unit tests passed;
  `testDebugUnitTest assembleDebug lintDebug` passed with zero lint errors and
  20 dependency-version warnings. The updated APK was installed with `adb install -r`.
- The v0.3.0 recording tests preserved frame counts, audio duration and upright
  orientation for 5, 30 and 60-second inputs.

These are recorded results from the linked reports, not claims of a new device
run during documentation maintenance. See [address verification](address-detail-verification-2026-10-10.md),
[v0.3.0 device testing](physical-test-report-2026-10-10-v0.3.md) and
[initial device testing](physical-test-report-2026-10-10.md).

## Remaining work

- Complete the device matrix and permission/provider failure tests in the [roadmap](roadmap.md).
- Validate process-death recovery, landscape behavior, sustained performance and battery use.
- A recording interrupted during stamping is recovered unstamped at next launch;
  finishing work in the background would require additional implementation.
- Prepare signing, release AAB, store assets and release/privacy review.
- Confirm GitHub Actions results for the published branch or pull request.

## Build and records

```bash
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
CI runs on pull requests and pushes to `main`; a feature-branch push alone does
not trigger the current workflow. Build and device-test setup are in
[CONTRIBUTING.md](../CONTRIBUTING.md).

Earlier phase descriptions are retained in the [historical progress archive](historical-progress-report.md).
