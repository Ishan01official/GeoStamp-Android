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
    val address: String? = null
) {
    fun ageMillis(nowMillis: Long): Long = nowMillis - measuredAtMillis

    fun isFresh(nowMillis: Long, maxAgeMillis: Long = 30_000L): Boolean =
        nowMillis >= measuredAtMillis && ageMillis(nowMillis) <= maxAgeMillis

    fun isAccurateEnough(maxAccuracyMeters: Float): Boolean =
        accuracyMeters <= maxAccuracyMeters

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
            address = address
        )
    }
}
