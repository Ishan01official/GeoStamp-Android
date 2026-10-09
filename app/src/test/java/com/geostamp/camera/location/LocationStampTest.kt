package com.geostamp.camera.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationStampTest {
    @Test
    fun freshnessRejectsFutureAndStaleMeasurements() {
        val stamp = LocationStamp(
            latitude = 1.0,
            longitude = 2.0,
            accuracyMeters = 3f,
            measuredAtMillis = 1_000L,
            altitudeMeters = null,
            speedMetersPerSecond = null
        )

        assertFalse(stamp.isFresh(nowMillis = 999L))
        assertTrue(stamp.isFresh(nowMillis = 31_000L))
        assertFalse(stamp.isFresh(nowMillis = 31_001L))
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

    @Test
    fun locationUpdateTextReportsStaleAndDeniedStates() {
        val stamp = LocationStamp(
            latitude = 1.0,
            longitude = 2.0,
            accuracyMeters = 30f,
            measuredAtMillis = 1_000L,
            altitudeMeters = null,
            speedMetersPerSecond = null,
            provider = "network",
            approximate = true
        )
        val providerStatus = LocationProviderStatus(
            gpsEnabled = false,
            networkEnabled = true,
            selectedProvider = "network"
        )

        assertEquals(
            "network approx GPS 30m, 4s old",
            LocationUpdate.Available(stamp, providerStatus).displayText(nowMillis = 5_000L)
        )
        assertEquals(
            "Stale network location (4s old)",
            LocationUpdate.StaleLastKnown(stamp, providerStatus).displayText(nowMillis = 5_000L)
        )
        assertEquals(
            "Location denied; camera works without GPS",
            LocationUpdate.PermissionDenied.displayText()
        )
    }
}
