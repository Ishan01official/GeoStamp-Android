# Development roadmap

## Milestone 1 — source baseline
- [x] Android Gradle project and permissions
- [x] CameraX preview and JPEG capture code
- [x] Optional foreground location and visible GPS timestamp
- [x] Local MediaStore saving
- [x] Compile with Android SDK 35
- [ ] Physical-device validation

## Milestone 2 — reliable MVP
- [x] Camera controls: switch, flash modes, focus, zoom, exposure, grid, timer
- [x] Aspect ratio and requested resolution presets
- [x] Last photo thumbnail preview
- [x] Capture rotation handling for stamped bitmap output
- [ ] Capture-time location freshness and accuracy threshold
- [ ] Handle permission revocation and disabled GPS
- [ ] Optional unmodified original
- [ ] Camera rotation, EXIF orientation, lifecycle and memory tests
- [x] Automated unit tests and CI build workflow

## Milestone 3 — stamp customization
- [ ] Opt-in address geocoding
- [ ] Compass and customizable templates
- [ ] Optional map thumbnail, optional weather
- [ ] Privacy policy, store listing and release testing
