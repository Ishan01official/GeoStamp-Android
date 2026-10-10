package com.geostamp.camera.sensors

import com.geostamp.camera.settings.CompassSmoothing
import org.junit.Assert.assertEquals
import org.junit.Test

class CompassSmootherTest {
    @Test
    fun smoothsAcrossNorthWraparound() {
        val smoother = CompassSmoother()
        smoother.smooth(reading(350f), CompassSmoothing.BALANCED)

        val smoothed = smoother.smooth(reading(10f), CompassSmoothing.BALANCED)

        assertEquals(357f, smoothed.displayDegrees, 0.6f)
    }

    @Test
    fun responsiveModeMovesFasterThanSmoothMode() {
        val smooth = CompassSmoother()
        val responsive = CompassSmoother()
        smooth.smooth(reading(0f), CompassSmoothing.SMOOTH)
        responsive.smooth(reading(0f), CompassSmoothing.RESPONSIVE)

        val smoothValue = smooth.smooth(reading(90f), CompassSmoothing.SMOOTH).displayDegrees
        val responsiveValue = responsive.smooth(reading(90f), CompassSmoothing.RESPONSIVE).displayDegrees

        assertEquals(16.2f, smoothValue, 0.1f)
        assertEquals(58.5f, responsiveValue, 0.1f)
    }

    private fun reading(degrees: Float) = CompassReading(
        magneticDegrees = degrees,
        trueDegrees = degrees,
        accuracy = CompassAccuracy.HIGH
    )
}
