# Changelog

This file describes source changes. Version names match `app/build.gradle.kts`;
entries do not imply that a signed release was published.

## Unreleased

- Preserve full provider address lines and components instead of reducing them to locality.
- Add Detailed (default), Standard and Short address formatting across capture and gallery paths.
- Cache address components, accept compatible enrichment and stabilize conflicting nearby results.
- Enable address lookup and map thumbnails by default while preserving saved opt-outs.
- Keep weather and GPS EXIF writing off by default; location permission remains optional.
- Refresh documentation, development instructions and current implementation status.

## 0.3.0 — 2026-10-10

- Burn stamps into video frames with a ticking clock, duration/audio/orientation checks and unstamped fallback.
- Add in-app video playback and real concurrent-camera Dual Photo / Dual Video.
- Fix switching out of dual modes, inset updates/composition placement and recording-badge visibility.
- Improve manual address editing, direction labels, permission flow and camera diagnostics.
- Record A142 device results for 5/30/60-second video stamping and dual capture.

## 0.2.0 — 2026-10-10

- Introduce Compose/Material 3 navigation, themes and English/Hindi interfaces.
- Add four configurable stamp templates, online services, EXIF controls and original saving.
- Add local gallery, sharing, deletion, metadata and batch stamping.
- Harden image decoding/rotation and add physical image-pipeline checks.

See [historical progress](docs/historical-progress-report.md) for earlier phase records.
