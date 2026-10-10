# Approved GeoStamp feature scope — 2026-10-09

This document is a **requirements register**, not a claim that all items are implemented.
Last revised: 2026-10-10. See [current progress](progress-report.md) for implementation
and verification, and [roadmap](roadmap.md) for outstanding work.

## Camera
- Photo capture; front/rear cameras; flash; focus and zoom; photo resolution
- Video recording with visible stamps; grid lines; capture timer

## Location and environment
- Latitude/longitude; accuracy indicator; optional address geocoding
- Compass direction; altitude/speed when supported
- Google Maps/OpenStreetMap location link; optional map thumbnail
- Optional weather/temperature with provider disclosure and observation time

## Stamping
- Date/time; address/coordinates; custom text/logo; stamp position/font size
- Batch stamping of user-selected photos
- Separate user control for visible stamps and EXIF GPS metadata

## Privacy and experience
- No ads/trackers, login or automatic cloud upload
- Offline coordinate stamping and local gallery/sharing
- Dark/light themes; Hindi and extensible localization

## Implementation stages
1. **Foundation:** compile existing camera/GPS app, CI, permission handling, test APK.
2. **Camera MVP:** camera switching, focus, zoom, flash, resolution, timer, grid.
3. **Stamp MVP:** settings, capture-time GPS accuracy/freshness, optional EXIF removal, gallery/share.
4. **Optional services:** geocoding, compass, map links/thumbnail, weather.
5. **Advanced:** batch processing, logos, stamped video, localization and release hardening.

### Acceptance constraints
- Offline photo capture never requires network access.
- Address lookup and map thumbnails default on, per the user's 2026-10-10 revision, and remain individually switchable. Weather stays opt-in. Document third-party data sharing and retain optional foreground location permission.
- Never use stale coordinates as if fresh, or manufacture weather/compass readings.
- Stamped video needs real rendered frames, not just a viewfinder overlay.
- Preserve image orientation, quality and privacy choices.
- Only report compilation, automated tests, and device testing when actually executed.
