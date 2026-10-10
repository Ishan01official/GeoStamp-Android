# Development roadmap

Current implementation status (2026-10-10): detailed address formatting, selectable
detail levels, map thumbnails, stamped video, templates and manual address editing
are implemented. Address lookup and map thumbnails now default on; weather remains
off. Saved service choices and optional location permission are preserved. The
milestones below retain outstanding device and release work.

## Milestone 1 — source baseline
- [x] Android Gradle project and permissions
- [x] CameraX preview and JPEG capture code
- [x] Optional foreground location and visible GPS timestamp
- [x] Local MediaStore saving
- [x] Compile with Android SDK 35
- [x] Initial physical-device install, launch, preview and capture validation

## Milestone 2 — reliable MVP
- [x] Camera controls: switch, flash modes, focus, zoom, exposure, grid, timer
- [x] Aspect ratio and requested resolution presets
- [x] Last photo thumbnail preview
- [x] Capture rotation handling for stamped bitmap output
- [x] Capture-time location freshness and accuracy threshold
- [x] Handle permission denial and disabled GPS
- [x] Initial real-device stamped capture test
- [x] Optional unmodified original
- [ ] Camera rotation, EXIF orientation, lifecycle and memory tests
- [x] Automated unit tests and CI build workflow

## Milestone 3 — GPS and sensors
- [x] Foreground GPS/network location updates
- [x] Provider status, stale location, approximate location and weak accuracy states
- [x] OpenStreetMap and Google Maps link helpers
- [x] Compass sensor integration with calibration status
- [x] Magnetic heading with true-heading correction when location is available
- [x] Initial real-device GPS and compass display test
- [x] Complete provider addresses with Detailed / Standard / Short formatting; lookup enabled by default
- [ ] Expose map links in the UI
- [ ] Physical-device validation for approximate location, weak signal and provider-disabled states

## Milestone 4 — stamp customization
- [x] Switchable address geocoding enabled by default
- [x] Compass and customizable templates
- [x] Switchable map thumbnail enabled by default; opt-in weather
- [ ] Privacy policy, store listing and release testing
