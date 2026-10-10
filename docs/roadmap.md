# Development roadmap

Updated: 2026-10-10. Checkmarks mean implemented in source; device coverage and
release readiness are tracked separately in [current progress](progress-report.md).

## Camera and capture

- [x] Android 10+ project, CameraX preview and local MediaStore saving
- [x] Front/rear switching, flash, focus, zoom, exposure, grid and timer
- [x] Photo aspect ratio and requested resolution presets
- [x] JPEG rotation, configurable stamping, optional original and GPS EXIF controls
- [x] Stamped video up to 60 seconds with audio/orientation verification and fallback
- [x] Dual Photo / Dual Video on concurrent-camera hardware
- [x] Movable/resizable dual inset and capability diagnostics
- [ ] Broader front/rear, flash, timer, resolution, lifecycle and memory device tests
- [ ] Process-death and background-stamping hardening
- [ ] Additional concurrent-camera devices and unsupported-device validation

## Location, addresses and environment

- [x] Optional foreground precise/approximate location and freshness/accuracy states
- [x] Compass heading, true/magnetic/course labels and no-compass fallback
- [x] Complete provider addresses with Detailed / Standard / Short formatting
- [x] Stabilization, component caching and manual address correction
- [x] Individually switchable address lookup and map thumbnails, enabled by default
- [x] Opt-in weather, provider disclosure, map attribution and offline fallback
- [x] Google Maps/OpenStreetMap links in the photo viewer when GPS metadata is available
- [ ] Approximate-only, weak-signal, provider-disabled and walking device tests
- [ ] External heading validation and hardware without a magnetometer
- [ ] Broader map/weather device and network-failure coverage

## Gallery, stamps and interface

- [x] Four stamp templates, per-template fields, position, size, color and opacity
- [x] Date/coordinate formats, custom text and logo selection
- [x] Gallery filters, full-screen viewer, share, delete, metadata and batch stamping
- [x] Video player with seeking, mute and full screen
- [x] Compose/Material 3, system/light/dark theme and Simple Camera Mode
- [x] English/Hindi, TalkBack labels and large touch targets
- [ ] Batch stamping, deletion-consent and manual re-stamping device review
- [ ] Full accessibility, landscape and varied screen-size review
- [ ] General photo import through Photo Picker (logo selection already uses it)

## Verification and release

- [x] Gradle wrapper, JVM tests, instrumented photo/address/video pipeline tests
- [x] CI build, unit tests, lint, wrapper validation and debug APK artifact
- [x] A142 physical-device reports and source privacy documentation
- [ ] Confirm remote CI for the published change
- [ ] Performance, battery, emulator and broader physical-device matrix
- [ ] Release signing and signed Android App Bundle
- [ ] Store screenshots, feature graphic, descriptions and Data Safety review
- [ ] Internal/closed testing and final release/privacy review
