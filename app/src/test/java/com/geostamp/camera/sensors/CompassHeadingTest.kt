package com.geostamp.camera.sensors

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassHeadingTest {
    @Test fun cardinalDirections() {
        assertEquals("N", CompassHeading.cardinal(0f))
        assertEquals("NE", CompassHeading.cardinal(45f))
        assertEquals("W", CompassHeading.cardinal(-90f))
        assertEquals("N", CompassHeading.cardinal(360f))
    }
}
