package com.geostamp.camera.location

import com.geostamp.camera.settings.LocationDisplayRefresh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationStabilizerTest {
    private var now = 100_000L
    private val policy = LocationStabilizationPolicy(
        maxAccuracyMeters = 50f,
        maxAgeMillis = 120_000L,
        displayRefresh = LocationDisplayRefresh.STABLE
    )

    @Test
    fun keepsDisplayLocationStableWithinHoldWindowForGpsNoise() {
        val stabilizer = LocationStabilizer { now }
        val initial = fix(latitude = 29.0, longitude = 77.0, measuredAt = now)
        val displayed = stabilizer.update(initial, policy)

        now += 5_000L
        val noisy = fix(latitude = 29.000015, longitude = 77.000015, measuredAt = now, speed = 0.2f)

        assertSame(displayed, stabilizer.update(noisy, policy))
    }

    @Test
    fun acceptsReliableLocationAfterStableWindow() {
        val stabilizer = LocationStabilizer { now }
        val initial = fix(latitude = 29.0, longitude = 77.0, measuredAt = now)
        stabilizer.update(initial, policy)

        now += 31_000L
        val newer = fix(latitude = 29.00001, longitude = 77.00001, measuredAt = now, speed = 0.2f, altitude = 101.0)
        val displayed = stabilizer.update(newer, policy)

        assertEquals(newer.latitude, displayed?.latitude)
        assertEquals(0f, displayed?.speedMetersPerSecond)
        assertEquals(initial.altitudeMeters, displayed?.altitudeMeters)
    }

    @Test
    fun acceptsMeaningfulMovementBeforeStableWindow() {
        val stabilizer = LocationStabilizer { now }
        stabilizer.update(fix(latitude = 29.0, longitude = 77.0, measuredAt = now), policy)

        now += 4_000L
        val moved = fix(latitude = 29.0003, longitude = 77.0003, measuredAt = now, speed = 2.4f)
        val displayed = stabilizer.update(moved, policy)

        assertEquals(moved.latitude, displayed?.latitude)
        assertEquals(moved.longitude, displayed?.longitude)
    }

    @Test
    fun rejectsStaleOrInaccurateLocations() {
        val stabilizer = LocationStabilizer { now }

        assertNull(stabilizer.update(fix(measuredAt = now - 121_000L), policy))
        assertNull(stabilizer.update(fix(measuredAt = now, accuracy = 75f), policy))
    }

    @Test
    fun liveModeAcceptsEveryReliableRawUpdate() {
        val stabilizer = LocationStabilizer { now }
        val livePolicy = policy.copy(displayRefresh = LocationDisplayRefresh.LIVE)
        stabilizer.update(fix(latitude = 29.0, longitude = 77.0, measuredAt = now), livePolicy)

        now += 2_000L
        val next = fix(latitude = 29.00001, longitude = 77.00001, measuredAt = now)
        val displayed = stabilizer.update(next, livePolicy)

        assertEquals(next.latitude, displayed?.latitude)
        assertTrue(LocationStabilizer.distanceMeters(fix(), next) > 0f)
    }

    private fun fix(
        latitude: Double = 29.0,
        longitude: Double = 77.0,
        measuredAt: Long = now,
        accuracy: Float = 5f,
        altitude: Double? = 100.0,
        speed: Float? = null
    ) = LocationStamp(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        measuredAtMillis = measuredAt,
        altitudeMeters = altitude,
        speedMetersPerSecond = speed,
        provider = "gps"
    )
}
