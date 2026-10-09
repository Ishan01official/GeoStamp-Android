package com.geostamp.camera.settings

import com.geostamp.camera.capture.CaptureTimer
import com.geostamp.camera.capture.FlashMode
import com.geostamp.camera.capture.PhotoAspectRatio
import com.geostamp.camera.capture.PhotoResolution
import com.geostamp.camera.stamps.StampPreferences

data class AppSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val camera: CameraSettings = CameraSettings(),
    val location: LocationSettings = LocationSettings(),
    val stamp: StampPreferences = StampPreferences(),
    val services: OnlineServices = OnlineServices(),
    val storage: StorageSettings = StorageSettings()
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true
)

data class CameraSettings(
    val flashMode: FlashMode = FlashMode.OFF,
    val timer: CaptureTimer = CaptureTimer.OFF,
    val aspectRatio: PhotoAspectRatio = PhotoAspectRatio.FOUR_THREE,
    val resolution: PhotoResolution = PhotoResolution.DEFAULT,
    val gridEnabled: Boolean = false,
    val liveStampPreview: Boolean = true,
    val tapToFocus: Boolean = true
)

data class LocationSettings(
    val maxAccuracyMeters: Int = 100,
    val maxAgeSeconds: Int = 120
) {
    companion object {
        val ACCURACY_CHOICES = listOf(10, 25, 50, 100, 200)
        val AGE_CHOICES = listOf(30, 60, 120, 300)
    }
}

/** Network features. All are off by default and only run after the user opts in. */
data class OnlineServices(
    val addressLookup: Boolean = false,
    val mapTiles: Boolean = false,
    val weather: Boolean = false,
    val mapLinkProvider: MapLinkProvider = MapLinkProvider.OPEN_STREET_MAP
) {
    val anyEnabled: Boolean get() = addressLookup || mapTiles || weather
}

enum class MapLinkProvider { OPEN_STREET_MAP, GOOGLE_MAPS }

data class StorageSettings(
    val saveOriginal: Boolean = false,
    val jpegQuality: Int = 93
) {
    companion object {
        val QUALITY_RANGE = 70..100
    }
}
