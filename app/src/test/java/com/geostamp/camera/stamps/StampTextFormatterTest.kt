package com.geostamp.camera.stamps

import com.geostamp.camera.location.LocationStamp
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StampTextFormatterTest {
    private val formatter = StampTextFormatter(
        locale = Locale.US,
        timeZone = TimeZone.getTimeZone("UTC"),
        nowMillis = { 0L }
    )

    @Test
    fun formatsCoreOfflineLinesWhenLocationIsMissing() {
        val lines = formatter.lines(location = null)

        assertEquals(
            listOf(
                "01 Jan 1970 00:00:00",
                "Location unavailable",
                "GeoStamp • offline"
            ),
            lines
        )
    }

    @Test
    fun formatsLocationAccuracyAltitudeAndSpeedWhenEnabled() {
        val location = LocationStamp(
            latitude = 12.3456789,
            longitude = 98.7654321,
            accuracyMeters = 4.4f,
            measuredAtMillis = 100L,
            altitudeMeters = 1510.25,
            speedMetersPerSecond = 1.5f
        )
        val preferences = StampPreferences(
            includeAltitude = true,
            includeSpeed = true,
            customText = " Site A "
        )

        val lines = formatter.lines(location, preferences)

        assertEquals("01 Jan 1970 00:00:00", lines[0])
        assertEquals("Site A", lines[1])
        assertEquals("Lat: 12.345679  Lon: 98.765432", lines[2])
        assertEquals("Accuracy: ±4 m", lines[3])
        assertEquals("Altitude: 1510.3 m", lines[4])
        assertEquals("Speed: 1.5 m/s", lines[5])
    }

    @Test
    fun returnsNoLinesWhenStampingIsDisabled() {
        val lines = formatter.lines(location = null, preferences = StampPreferences(enabled = false))

        assertTrue(lines.isEmpty())
    }
}
