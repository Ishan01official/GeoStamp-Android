package com.geostamp.camera.maps

import com.geostamp.camera.settings.MapLinkProvider
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationUriBuilderTest {
    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(originalLocale)

    @Test
    fun googleMapsLinkUsesUniversalSearchUrl() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=28.613900,77.209000",
            LocationUriBuilder.googleMaps(28.6139, 77.209)
        )
    }

    @Test
    fun southernAndWesternHemispheresKeepTheirSign() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=-33.856784,-151.215297",
            LocationUriBuilder.googleMaps(-33.856784, -151.215297)
        )
        assertEquals("geo:-33.856784,-151.215297?q=-33.856784,-151.215297", LocationUriBuilder.geo(-33.856784, -151.215297))
    }

    @Test
    fun linksUseDotDecimalsInEveryLocale() {
        for (tag in listOf("de-DE", "fr-FR", "ar-EG", "hi-IN", "pt-BR")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            assertEquals(tag, "https://www.google.com/maps/search/?api=1&query=48.858370,2.294481", LocationUriBuilder.googleMaps(48.85837, 2.294481))
        }
    }

    @Test
    fun openStreetMapLinkCentersOnThePoint() {
        assertEquals(
            "https://www.openstreetmap.org/?mlat=51.500700&mlon=-0.124600#map=17/51.500700/-0.124600",
            LocationUriBuilder.openStreetMap(51.5007, -0.1246)
        )
    }

    @Test
    fun webLinkFollowsProvider() {
        assertTrue(LocationUriBuilder.web(MapLinkProvider.GOOGLE_MAPS, 1.0, 2.0).startsWith("https://www.google.com/maps/"))
        assertTrue(LocationUriBuilder.web(MapLinkProvider.OPEN_STREET_MAP, 1.0, 2.0).startsWith("https://www.openstreetmap.org/"))
    }

    @Test
    fun rejectsImpossibleCoordinates() {
        assertFalse(LocationUriBuilder.isValid(91.0, 0.0))
        assertFalse(LocationUriBuilder.isValid(0.0, 181.0))
        assertFalse(LocationUriBuilder.isValid(Double.NaN, 0.0))
        assertTrue(LocationUriBuilder.isValid(-90.0, 180.0))
    }
}
