package com.geostamp.camera.environment

import org.junit.Assert.assertEquals
import org.junit.Test

class MapTileRendererTest {
    @Test
    fun worldPixelMatchesWebMercator() {
        val (x, y) = MapTileRenderer.worldPixel(0.0, 0.0, 1)
        assertEquals(256.0, x, 1e-6)
        assertEquals(256.0, y, 1e-6)
        val (tx, _) = MapTileRenderer.worldPixel(29.007953, 77.767663, 16)
        assertEquals(46_925, (tx / 256).toInt())
    }
}
