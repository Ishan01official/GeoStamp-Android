# GeoStamp Android

A privacy-first, ad-free, offline GPS photo camera for Android.

## First implementation (v0.1.0)

- CameraX rear-camera preview and capture
- Optional precise/coarse foreground location
- Timestamp, GPS coordinates and accuracy burned into JPEG
- Local saving to Pictures/GeoStamp using MediaStore
- No account, no ads, no analytics SDK and no INTERNET permission
- Camera works even when location is denied

**Status:** Initial source committed; compilation and on-device testing are not yet verified. Do not treat this as a Play Store-ready release.
**Current verification:** `./gradlew --no-daemon clean assembleDebug testDebugUnitTest lintDebug` passes locally. A debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Build

Open in Android Studio with JDK 17 and Android SDK 35, then sync Gradle and run the `app` module on Android 10+. The first milestone has no backend.

Command-line build:

```bash
./gradlew clean assembleDebug testDebugUnitTest lintDebug
```

## Roadmap

Full address via opt-in reverse geocoding, compass, adjustable stamp templates, original-photo option, optional map thumbnail, optional weather, automated tests, accessibility, translations and Play Store listing.

See [privacy](PRIVACY.md) and [roadmap](docs/roadmap.md).
