package com.geostamp.camera.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationStampTest {
    @Test
    fun freshnessRejectsFarFutureAndStaleMeasurements() {
        val stamp = LocationStamp(
            latitude = 1.0,
            longitude = 2.0,
            accuracyMeters = 3f,
            measuredAtMillis = 20_000L,
            altitudeMeters = null,
            speedMetersPerSecond = null
        )

        assertFalse(stamp.isFresh(nowMillis = 9_999L))
        assertTrue(stamp.isFresh(nowMillis = 50_000L))
        assertFalse(stamp.isFresh(nowMillis = 50_001L))
    }

    @Test
    fun gnssFixSlightlyAheadOfPhoneClockIsStillFresh() {
        val stamp = LocationStamp(1.0, 2.0, 3f, measuredAtMillis = 20_000L, altitudeMeters = null, speedMetersPerSecond = null)

        assertTrue(stamp.isFresh(nowMillis = 18_500L))
    }

    @Test
    fun formatsCoordinatesAndMapLinks() {
        val stamp = LocationStamp(
            latitude = 12.345678,
            longitude = 77.123456,
            accuracyMeters = 8f,
            measuredAtMillis = 1_000L,
            altitudeMeters = null,
            speedMetersPerSecond = null,
            provider = "gps"
        )

        assertEquals("12.345678, 77.123456", stamp.coordinates())
        assertEquals(
            "https://www.openstreetmap.org/?mlat=12.345678&mlon=77.123456#map=17/12.345678/77.123456",
            stamp.openStreetMapUrl()
        )
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=12.345678,77.123456",
            stamp.googleMapsUrl()
        )
        assertEquals("gps", stamp.providerLabel())
        assertTrue(stamp.isAccurateEnough(10f))
        assertFalse(stamp.isAccurateEnough(5f))
    }
}
