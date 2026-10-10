package com.geostamp.camera.settings

import com.geostamp.camera.capture.CaptureTimer
import com.geostamp.camera.environment.AddressDetail
import com.geostamp.camera.capture.FlashMode
import com.geostamp.camera.capture.PhotoAspectRatio
import com.geostamp.camera.capture.PhotoResolution
import com.geostamp.camera.maps.MapType
import com.geostamp.camera.stamps.StampPreferences

data class AppSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val camera: CameraSettings = CameraSettings(),
    val location: LocationSettings = LocationSettings(),
    val stamp: StampPreferences = StampPreferences(),
    val services: OnlineServices = OnlineServices(),
    val storage: StorageSettings = StorageSettings(),
    val onboarding: OnboardingState = OnboardingState()
)

/** Remembers which permission explanations were shown, so Android's "don't ask again" state can be told apart from "never asked". */
data class OnboardingState(
    val cameraRequested: Boolean = false,
    val locationPromptShown: Boolean = false,
    val locationRequested: Boolean = false
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
    val tapToFocus: Boolean = true,
    val simpleMode: Boolean = false
)

data class LocationSettings(
    val maxAccuracyMeters: Int = 100,
    val maxAgeSeconds: Int = 120,
    val displayRefresh: LocationDisplayRefresh = LocationDisplayRefresh.STABLE,
    val compassSmoothing: CompassSmoothing = CompassSmoothing.SMOOTH,
    val addressDetail: AddressDetail = AddressDetail.DETAILED
) {
    companion object {
        val ACCURACY_CHOICES = listOf(10, 25, 50, 100, 200)
        val AGE_CHOICES = listOf(30, 60, 120, 300)
    }
}

enum class LocationDisplayRefresh(val holdMillis: Long) {
    STABLE(30_000L),
    BALANCED(10_000L),
    LIVE(0L)
}

enum class CompassSmoothing(val alpha: Float) {
    SMOOTH(0.18f),
    BALANCED(0.35f),
    RESPONSIVE(0.65f)
}

/** Address and map services default on; weather remains opt-in. */
data class OnlineServices(
    val addressLookup: Boolean = true,
    val mapTiles: Boolean = true,
    val weather: Boolean = false,
    /** Which app opens a location. Only a default: a choice stored by an earlier version is kept. */
    val mapLinkProvider: MapLinkProvider = MapLinkProvider.DEFAULT,
    val mapType: MapType = MapType.DEFAULT
) {
    val anyEnabled: Boolean get() = addressLookup || mapTiles || weather
}

enum class MapLinkProvider {
    OPEN_STREET_MAP,
    GOOGLE_MAPS;

    companion object {
        val DEFAULT = GOOGLE_MAPS
    }
}

data class StorageSettings(
    val saveOriginal: Boolean = false,
    val jpegQuality: Int = 93
) {
    companion object {
        val QUALITY_RANGE = 70..100
    }
}
