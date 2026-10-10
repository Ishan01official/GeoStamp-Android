package com.geostamp.camera.location

import android.location.Location
import java.util.Locale

/** Immutable capture-time location snapshot. Never fabricate missing sensor values. */
data class LocationStamp(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val measuredAtMillis: Long,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Float?,
    val provider: String? = null,
    val approximate: Boolean = false,
    val address: String? = null,
    /** GNSS direction of travel (true north); only meaningful while moving. */
    val bearingDegrees: Float? = null,
    val bearingAccuracyDegrees: Float? = null
) {
    fun ageMillis(nowMillis: Long): Long = nowMillis - measuredAtMillis

    /**
     * GNSS fixes carry satellite time, which can run a few seconds ahead of a phone clock that has drifted.
     * Such a fix is current, not "from the future", so a small negative age is tolerated.
     */
    fun isFresh(nowMillis: Long, maxAgeMillis: Long = 30_000L): Boolean =
        ageMillis(nowMillis) >= -CLOCK_SKEW_TOLERANCE_MILLIS && ageMillis(nowMillis) <= maxAgeMillis

    /** False for sources without a measured accuracy, such as EXIF from imported photos. */
    val hasAccuracy: Boolean get() = !accuracyMeters.isNaN()

    fun isAccurateEnough(maxAccuracyMeters: Float): Boolean =
        hasAccuracy && accuracyMeters <= maxAccuracyMeters

    fun distanceMetersTo(other: LocationStamp): Float = LocationStabilizer.distanceMeters(this, other)

    fun coordinates(): String = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
    fun openStreetMapUrl(): String =
        "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=17/$latitude/$longitude"

    fun googleMapsUrl(): String =
        "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"

    fun providerLabel(): String = provider?.takeIf { it.isNotBlank() } ?: "unknown"

    companion object {
        fun from(location: Location, approximate: Boolean = false, address: String? = null): LocationStamp = LocationStamp(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            measuredAtMillis = location.time,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
            provider = location.provider,
            approximate = approximate,
            address = address,
            bearingDegrees = if (location.hasBearing()) location.bearing else null,
            bearingAccuracyDegrees = if (location.hasBearingAccuracy()) location.bearingAccuracyDegrees else null
        )

        const val CLOCK_SKEW_TOLERANCE_MILLIS = 10_000L
    }
}
