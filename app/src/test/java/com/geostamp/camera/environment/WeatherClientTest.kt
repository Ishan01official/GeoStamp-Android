package com.geostamp.camera.environment

import com.geostamp.camera.stamps.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherClientTest {
    @Test
    fun parsesOpenMeteoCurrentConditions() {
        val json = """{"current":{"time":"2026-10-10T01:00","interval":900,"temperature_2m":24.3,"weather_code":2}}"""
        val reading = WeatherClient.parse(json)
        assertEquals(24.3, reading.temperatureCelsius, 0.0)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, reading.condition)
        assertEquals(1_791_594_000_000L, reading.observedAtMillis)
    }

    @Test
    fun requestsOnlyRoundedCoordinates() {
        var requested = ""
        WeatherClient { url ->
            requested = url
            """{"current":{"time":"2026-10-10T01:00","temperature_2m":1,"weather_code":0}}""".toByteArray()
        }.current(29.0079531, 77.7676629)
        assertTrue(requested, requested.contains("latitude=29.008&longitude=77.768"))
    }

    @Test
    fun mapsWmoCodes() {
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromWmoCode(0))
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherCondition.fromWmoCode(95))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWmoCode(42))
    }
}
