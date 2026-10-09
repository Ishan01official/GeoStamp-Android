package com.geostamp.camera.stamps

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateFormatterTest {
    @Test
    fun decimalUsesSixPlaces() {
        assertEquals("29.007953, 77.767663", CoordinateFormatter.format(29.0079531, 77.7676629, CoordinateFormat.DECIMAL))
    }

    @Test
    fun dmsHandlesHemispheres() {
        assertEquals("29°00'28.6\"N  77°46'03.6\"E", CoordinateFormatter.format(29.007953, 77.767663, CoordinateFormat.DMS))
        assertEquals("33°52'07.7\"S", CoordinateFormatter.toDms(-33.8688, 'N', 'S'))
        assertEquals("0°00'00.0\"E", CoordinateFormatter.toDms(0.0, 'E', 'W'))
    }
}
