package com.geostamp.camera.location

import org.junit.Assert.assertFalse
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
}
