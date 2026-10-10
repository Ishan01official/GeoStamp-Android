package com.geostamp.camera.sensors

import com.geostamp.camera.location.LocationStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingTest {
    private val compass = CompassReading(magneticDegrees = 44.4f, trueDegrees = 45.5f, accuracy = CompassAccuracy.HIGH)

    @Test
    fun `top bar and stamp share one rounded value`() {
        val snapshot = HeadingResolver.resolve(compass, location = null)!!
        assertEquals(46, snapshot.degrees)
        assertEquals("NE 46° true", HeadingFormatter.format(snapshot))
    }

    @Test
    fun `magnetic heading is labelled magnetic when declination is unknown`() {
        val snapshot = HeadingResolver.resolve(compass.copy(trueDegrees = null), location = null)!!
        assertEquals(HeadingKind.MAGNETIC_HEADING, snapshot.kind)
        assertEquals("NE 44° magnetic", HeadingFormatter.format(snapshot))
    }

    @Test
    fun `359 point 6 degrees wraps to north 0`() {
        val snapshot = HeadingSnapshot.of(359.6f, HeadingKind.TRUE_HEADING, CompassAccuracy.HIGH)
        assertEquals(0, snapshot.degrees)
        assertEquals("N", snapshot.cardinal)
    }

    @Test
    fun `phone without compass shows course only while moving`() {
        val moving = fix(speed = 3f, bearing = 92f, bearingError = 8f)
        val snapshot = HeadingResolver.resolve(compass = null, location = moving)!!
        assertEquals(HeadingKind.COURSE, snapshot.kind)
        assertEquals("Course E 92°", HeadingFormatter.format(snapshot))
    }

    @Test
    fun `stationary phone without compass has no direction`() {
        assertNull(HeadingResolver.resolve(compass = null, location = fix(speed = 0.3f, bearing = 92f, bearingError = 8f)))
    }

    @Test
    fun `unreliable bearing is not shown as course`() {
        assertNull(HeadingResolver.resolve(compass = null, location = fix(speed = 3f, bearing = 92f, bearingError = 70f)))
    }

    @Test
    fun `no compass and no bearing means direction unavailable`() {
        assertNull(HeadingResolver.resolve(compass = null, location = fix(speed = null, bearing = null, bearingError = null)))
        assertNull(HeadingResolver.resolve(compass = null, location = null))
    }

    @Test
    fun `phone without magnetometer has no compass even with gyroscope fusion`() {
        val caps = SensorCapabilities(accelerometer = true, magnetometer = false, rotationVector = false, gameRotationVector = true)
        assertEquals(CompassSource.NONE, caps.compassSource)
        assertFalse(caps.hasCompass)
    }

    @Test
    fun `rotation vector is preferred when a magnetometer exists`() {
        val caps = SensorCapabilities(accelerometer = true, magnetometer = true, rotationVector = true, gameRotationVector = true)
        assertEquals(CompassSource.ROTATION_VECTOR, caps.compassSource)
        assertTrue(caps.hasCompass)
    }

    @Test
    fun `accelerometer plus magnetometer works without rotation vector`() {
        val caps = SensorCapabilities(accelerometer = true, magnetometer = true, rotationVector = false, gameRotationVector = false)
        assertEquals(CompassSource.ACCELEROMETER_MAGNETOMETER, caps.compassSource)
    }

    @Test
    fun `phone with no motion sensors has no compass`() {
        val caps = SensorCapabilities(accelerometer = false, magnetometer = false, rotationVector = false, gameRotationVector = false)
        assertEquals(CompassSource.NONE, caps.compassSource)
    }

    private fun fix(speed: Float?, bearing: Float?, bearingError: Float?) = LocationStamp(
        latitude = 29.0, longitude = 77.0, accuracyMeters = 5f, measuredAtMillis = 0L, altitudeMeters = null,
        speedMetersPerSecond = speed, bearingDegrees = bearing, bearingAccuracyDegrees = bearingError
    )
}
