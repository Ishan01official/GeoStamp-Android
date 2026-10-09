package com.geostamp.camera.environment

import com.geostamp.camera.stamps.WeatherCondition
import com.geostamp.camera.stamps.WeatherReading
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import org.json.JSONObject

/** Current conditions from Open-Meteo (no API key). Sends only rounded coordinates. */
class WeatherClient(private val fetch: (String) -> ByteArray = HttpClient::get) {
    fun current(latitude: Double, longitude: Double): WeatherReading {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code&timezone=GMT",
            latitude,
            longitude
        )
        return parse(String(fetch(url)))
    }

    companion object {
        fun parse(json: String): WeatherReading {
            val current = JSONObject(json).getJSONObject("current")
            val observedAt = LocalDateTime.parse(current.getString("time")).toInstant(ZoneOffset.UTC).toEpochMilli()
            return WeatherReading(
                temperatureCelsius = current.getDouble("temperature_2m"),
                condition = WeatherCondition.fromWmoCode(current.getInt("weather_code")),
                observedAtMillis = observedAt
            )
        }
    }
}
