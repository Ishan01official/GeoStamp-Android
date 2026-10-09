package com.geostamp.camera.stamps

import com.geostamp.camera.location.LocationStamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class StampTextFormatter(
    private val locale: Locale = Locale.getDefault(),
    private val timeZone: TimeZone = TimeZone.getDefault(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    fun lines(
        location: LocationStamp?,
        preferences: StampPreferences = StampPreferences()
    ): List<String> {
        if (!preferences.enabled) return emptyList()

        val lines = mutableListOf(formatTimestamp(nowMillis()))
        preferences.customText.trim().takeIf { it.isNotEmpty() }?.let(lines::add)

        if (preferences.includeCoordinates) {
            lines += location?.let { "Lat: %.6f  Lon: %.6f".format(Locale.US, it.latitude, it.longitude) }
                ?: "Location unavailable"
        }

        if (preferences.includeAccuracy) {
            lines += location?.let { "Accuracy: ±%.0f m".format(Locale.US, it.accuracyMeters) }
                ?: "GeoStamp • offline"
        }

        if (preferences.includeAltitude && location?.altitudeMeters != null) {
            lines += "Altitude: %.1f m".format(Locale.US, location.altitudeMeters)
        }

        if (preferences.includeSpeed && location?.speedMetersPerSecond != null) {
            lines += "Speed: %.1f m/s".format(Locale.US, location.speedMetersPerSecond)
        }

        return lines
    }

    private fun formatTimestamp(timestampMillis: Long): String =
        SimpleDateFormat("dd MMM yyyy HH:mm:ss", locale).apply {
            timeZone = this@StampTextFormatter.timeZone
        }.format(Date(timestampMillis))
}
