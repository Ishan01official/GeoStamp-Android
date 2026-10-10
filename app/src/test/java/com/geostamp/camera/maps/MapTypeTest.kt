package com.geostamp.camera.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTypeTest {
    @Test
    fun normalIsTheDefault() = assertEquals(MapType.NORMAL, MapType.DEFAULT)

    @Test
    fun everyTypeFallsBackToNormalAndNormalEndsTheChain() {
        MapType.entries.filter { it != MapType.NORMAL }.forEach { assertEquals(MapType.NORMAL, it.fallback) }
        assertNull(MapType.NORMAL.fallback)
    }

    @Test
    fun tileUrlsPutCoordinatesInEachProvidersOrder() {
        assertEquals("https://tile.openstreetmap.org/16/46925/27015.png", TileSource.OSM_STANDARD.url(16, 46925, 27015))
        assertEquals("https://tile.opentopomap.org/15/1/2.png", TileSource.OPEN_TOPO_MAP.url(15, 1, 2))
        // WMTS addresses tiles as row (y) before column (x).
        assertEquals(
            "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2023_3857/default/g/15/2/1.jpg",
            TileSource.EOX_CLOUDLESS.url(15, 1, 2)
        )
    }

    @Test
    fun hybridLayersStreetsOverImageryAndCreditsBoth() {
        val style = MapType.HYBRID.style
        assertEquals(TileSource.EOX_CLOUDLESS, style.base)
        assertEquals(TileSource.OSM_STANDARD, style.overlay)
        assertTrue(style.attribution.contains("OpenStreetMap"))
        assertTrue(style.attribution.contains("EOx"))
    }

    @Test
    fun providersUseSeparateCacheFolders() {
        assertEquals(TileSource.entries.size, TileSource.entries.map { it.folder }.toSet().size)
    }
}
