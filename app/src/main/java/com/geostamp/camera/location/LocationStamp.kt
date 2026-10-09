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
    val speedMetersPerSecond: Float?
) {
    fun isFresh(nowMillis: Long, maxAgeMillis: Long = 30_000L): Boolean =
        nowMillis >= measuredAtMillis && nowMillis - measuredAtMillis <= maxAgeMillis

    fun coordinates(): String = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
    fun mapUrl(): String = "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=17/$latitude/$longitude"

    companion object {
        fun from(location: Location): LocationStamp = LocationStamp(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            measuredAtMillis = location.time,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            speedMetersPerSecond = if (location.hasSpeed()) location.speed else null
        )
    }
}
