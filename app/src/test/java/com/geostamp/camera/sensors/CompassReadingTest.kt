package com.geostamp.camera.sensors

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassReadingTest {
    @Test
    fun usesTrueHeadingWhenAvailable() {
        val reading = CompassReading(
            magneticDegrees = 20f,
            trueDegrees = 44.8f,
            accuracy = CompassAccuracy.HIGH
        )

        assertEquals(44.8f, reading.displayDegrees, 0.0f)
        assertEquals("NE 44 deg (high)", reading.displayText())
    }

    @Test
    fun fallsBackToMagneticHeadingWithoutLocationReference() {
        val reading = CompassReading(
            magneticDegrees = 270f,
            trueDegrees = null,
            accuracy = CompassAccuracy.UNRELIABLE
        )

        assertEquals(270f, reading.displayDegrees, 0.0f)
        assertEquals("W 270 deg (calibrate)", reading.displayText())
    }
}
