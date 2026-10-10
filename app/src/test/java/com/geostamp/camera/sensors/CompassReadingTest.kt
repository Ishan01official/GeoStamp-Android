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
    }

    @Test
    fun fallsBackToMagneticHeadingWithoutLocationReference() {
        val reading = CompassReading(
            magneticDegrees = 270f,
            trueDegrees = null,
            accuracy = CompassAccuracy.UNRELIABLE
        )

        assertEquals(270f, reading.displayDegrees, 0.0f)
    }
}
