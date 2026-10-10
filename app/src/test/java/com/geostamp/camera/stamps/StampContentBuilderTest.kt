package com.geostamp.camera.stamps

import com.geostamp.camera.location.LocationStamp
import com.geostamp.camera.sensors.CompassAccuracy
import com.geostamp.camera.sensors.HeadingKind
import com.geostamp.camera.sensors.HeadingSnapshot
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StampContentBuilderTest {
    private val builder = StampContentBuilder(Locale.US, TimeZone.getTimeZone("UTC"))
    private val location = LocationStamp(
        latitude = 29.007953,
        longitude = 77.767663,
        accuracyMeters = 5f,
        measuredAtMillis = 0L,
        altitudeMeters = 220.4,
        speedMetersPerSecond = 1.5f
    )
    private val heading = HeadingSnapshot(25, HeadingKind.TRUE_HEADING, CompassAccuracy.HIGH)

    @Test
    fun professionalTemplateBuildsHierarchyFromRealData() {
        val prefs = StampPreferences(template = StampTemplate.PROFESSIONAL)
        val content = builder.build(
            StampData(capturedAtMillis = 0L, location = location, heading = heading, address = "Meerut, Uttar Pradesh, India"),
            prefs,
            mapAvailable = true
        )

        assertEquals("1 January 1970 · 00:00:00", content.dateTime)
        assertEquals("Meerut, Uttar Pradesh, India", content.headline)
        assertEquals("29.007953, 77.767663", content.details[0].text)
        assertEquals("GPS ±5 m · NE 25° true", content.details[1].text)
        assertTrue(content.showMap)
        assertFalse(content.compact)
    }

    @Test
    fun missingDataIsOmittedNotInvented() {
        val prefs = StampPreferences(template = StampTemplate.PROFESSIONAL)
        val content = builder.build(StampData(capturedAtMillis = 0L, location = location), prefs, mapAvailable = false)

        assertNull(content.headline)
        assertEquals(MapPanel.COORDINATES, content.mapPanel)
        assertEquals(listOf("29.007953, 77.767663", "GPS ±5 m", "Alt 220 m · 5.4 km/h"), content.details.map { it.text })
    }

    @Test
    fun mapCardWithoutTileShowsCoordinatePanelInsteadOfFakeMap() {
        val content = builder.build(StampData(0L, location = location), StampPreferences(template = StampTemplate.MAP_CARD), mapAvailable = false)
        assertEquals(MapPanel.COORDINATES, content.mapPanel)
        assertEquals(listOf("29.00795° N", "77.76766° E"), content.panelCoordinates)
    }

    @Test
    fun mapCardWithTileShowsRealMap() {
        val content = builder.build(StampData(0L, location = location), StampPreferences(template = StampTemplate.MAP_CARD), mapAvailable = true)
        assertEquals(MapPanel.TILE, content.mapPanel)
    }

    @Test
    fun mapNeverShownWithoutLocation() {
        val content = builder.build(StampData(0L), StampPreferences(template = StampTemplate.MAP_CARD), mapAvailable = true)
        assertFalse(content.showMap)
        assertTrue(content.details.isEmpty())
    }

    @Test
    fun minimalTemplateIsCompactWithDateAndCoordinatesOnly() {
        val content = builder.build(
            StampData(0L, location, heading, address = "Somewhere"),
            StampPreferences(template = StampTemplate.MINIMAL)
        )
        assertTrue(content.compact)
        assertNull(content.headline)
        assertEquals(listOf("29.007953, 77.767663"), content.details.map { it.text })
    }

    @Test
    fun magneticHeadingIsLabelledWhenTrueNorthUnknown() {
        val prefs = StampPreferences(template = StampTemplate.MINIMAL)
            .let { it.withFields(it.fields.copy(heading = true)) }
        val content = builder.build(StampData(0L, location, HeadingSnapshot(270, HeadingKind.MAGNETIC_HEADING, CompassAccuracy.HIGH)), prefs)
        assertEquals("W 270° magnetic", content.details[1].text)
    }

    @Test
    fun courseIsLabelledAsCourseNotHeading() {
        val prefs = StampPreferences(template = StampTemplate.MINIMAL)
            .let { it.withFields(it.fields.copy(heading = true)) }
        val content = builder.build(StampData(0L, location, HeadingSnapshot(92, HeadingKind.COURSE, CompassAccuracy.UNKNOWN)), prefs)
        assertEquals("Course E 92°", content.details[1].text)
    }

    @Test
    fun optionalAltitudeSpeedAndWeatherRenderWhenEnabled() {
        val prefs = StampPreferences(template = StampTemplate.CLASSIC, temperatureUnit = TemperatureUnit.FAHRENHEIT)
            .let { it.withFields(it.fields.copy(altitude = true, speed = true, weather = true)) }
        val weather = WeatherReading(20.0, WeatherCondition.RAIN, observedAtMillis = 3_600_000L)
        val content = builder.build(StampData(0L, location, weather = weather), prefs)

        assertEquals("Alt 220 m · 5.4 km/h", content.details[2].text)
        assertEquals("68°F · Rain · Open-Meteo 01:00", content.details[3].text)
    }

    @Test
    fun stationarySpeedNoiseIsHidden() {
        val prefs = StampPreferences(template = StampTemplate.CLASSIC)
            .let { it.withFields(it.fields.copy(altitude = false, speed = true)) }
        val content = builder.build(StampData(0L, location.copy(speedMetersPerSecond = 0.06f)), prefs)

        assertTrue(content.details.none { it.text.contains("km/h") })
    }

    @Test
    fun accuracyWithoutMeasurementIsSkipped() {
        val prefs = StampPreferences(template = StampTemplate.CLASSIC)
        val exifLocation = location.copy(accuracyMeters = Float.NaN)
        val content = builder.build(StampData(0L, exifLocation), prefs)
        assertEquals(listOf("29.007953, 77.767663"), content.details.map { it.text })
    }

    @Test
    fun customTextAndDisabledStamp() {
        val prefs = StampPreferences(customText = "  Site A  ")
        assertEquals("Site A", builder.build(StampData(0L), prefs).details.last().text)
        assertTrue(builder.build(StampData(0L), prefs.copy(enabled = false)).isEmpty)
    }

    @Test
    fun perTemplateFieldOverridesAreIndependent() {
        val prefs = StampPreferences(template = StampTemplate.CLASSIC)
            .let { it.withFields(it.fields.copy(coordinates = false)) }
        assertFalse(prefs.fields.coordinates)
        assertTrue(prefs.copy(template = StampTemplate.MINIMAL).fields.coordinates)
    }

    @Test
    fun qrLocationTemplateEncodesTheCapturedCoordinates() {
        val prefs = StampPreferences(template = StampTemplate.QR_LOCATION)
        val content = builder.build(StampData(capturedAtMillis = 0L, location = location, address = "Meerut, Uttar Pradesh, India"), prefs)
        assertEquals("https://www.google.com/maps/search/?api=1&query=29.007953,77.767663", content.qrPayload)
        assertEquals("Lat 29.007953° Long 77.767663°", content.details[0].text)
        assertEquals("Meerut, Uttar Pradesh, India", content.headline)
        assertTrue(content.showQr)
    }

    @Test
    fun qrIsOmittedWithoutAFixAndWhenTheFieldIsOff() {
        val prefs = StampPreferences(template = StampTemplate.QR_LOCATION)
        assertNull(builder.build(StampData(capturedAtMillis = 0L), prefs).qrPayload)
        val off = prefs.withFields(prefs.fields.copy(qrCode = false))
        assertNull(builder.build(StampData(capturedAtMillis = 0L, location = location), off).qrPayload)
        assertNull(builder.build(StampData(capturedAtMillis = 0L, location = location), StampPreferences(template = StampTemplate.CLASSIC)).qrPayload)
    }

    @Test
    fun localeChangesWordsButNotCoordinateDigits() {
        val french = StampContentBuilder(Locale.FRANCE, TimeZone.getTimeZone("UTC"))
        val content = french.build(StampData(capturedAtMillis = 0L, location = location), StampPreferences(template = StampTemplate.MINIMAL))
        assertEquals("1 janvier 1970 · 00:00:00", content.dateTime)
        assertEquals("29.007953, 77.767663", content.details[0].text)
    }

    @Test
    fun temperatureUnitIsIndependentOfLanguage() {
        val weather = WeatherReading(20.0, WeatherCondition.CLEAR, 0L)
        val prefs = StampPreferences(template = StampTemplate.PROFESSIONAL, temperatureUnit = TemperatureUnit.FAHRENHEIT)
        val german = StampContentBuilder(Locale.GERMANY, TimeZone.getTimeZone("UTC"))
        val line = german.build(StampData(capturedAtMillis = 0L, location = location, weather = weather), prefs).details.first { it.icon == StampIcon.WEATHER }
        assertTrue(line.text, line.text.startsWith("68°F"))
    }
}
