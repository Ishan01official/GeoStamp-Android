# GeoStamp Android

A privacy-first, ad-free, offline GPS photo camera for Android.

## Features (v0.2.0)

- Edge-to-edge camera with a large, undistorted preview. A compact GPS, heading and settings bar sits on top, with a side rail for flash, aspect, resolution, timer and grid
- Zoom pills limited to levels the camera supports, plus pinch to zoom, tap to focus with an animated ring, an exposure slider and a rule-of-thirds grid
- Photo and video modes, a timer, front and rear cameras, and a live stamp preview drawn by the same engine as saved photos
- Professional stamp card in four templates (Minimal, Classic, Map card, Professional) with per-template fields, position, size, color, opacity, custom text, logo, and date and coordinate formats
- Real-data-only stamps: missing address, map, weather or heading is omitted, never invented
- Optional, off-by-default address lookup, OpenStreetMap mini map and Open-Meteo weather. See [privacy](PRIVACY.md)
- Local gallery with photo/video and stamped/unstamped filters, a full-screen viewer, details, share, delete, open in maps and batch stamping
- Material 3 with dynamic system colors and a neutral fallback, a Follow system / Light / Dark setting, and English and Hindi
- No ads, accounts or analytics. Camera works without location

Architecture: single activity, Jetpack Compose, MVVM with StateFlow, DataStore settings, and a CameraX `LifecycleCameraController`. The image pipeline (`capture/`, `stamps/`) has no UI dependencies.

## Build

Open in Android Studio with JDK 17 and Android SDK 35, then sync Gradle and run the `app` module on Android 10+. The first milestone has no backend.

Command-line build:

```bash
./gradlew clean assembleDebug testDebugUnitTest lintDebug
```

## Roadmap

Stamped video (burned-in frames), more languages, wider device testing and a Play Store listing.

See [privacy](PRIVACY.md) and [roadmap](docs/roadmap.md).
