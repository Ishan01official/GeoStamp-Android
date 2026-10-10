package com.geostamp.camera.stamps

import com.geostamp.camera.address.AddressSource
import com.geostamp.camera.location.LocationStamp
import com.geostamp.camera.sensors.CompassReading

/** Real measurements captured at shutter time. Null means "not measured" and is never filled with guesses. */
data class StampData(
    val capturedAtMillis: Long,
    val location: LocationStamp? = null,
    val heading: CompassReading? = null,
    val address: String? = null,
    val weather: WeatherReading? = null,
    /** Manual addresses are recorded as such in metadata and never treated as GPS-derived. */
    val addressSource: AddressSource = AddressSource.DETECTED
)

data class WeatherReading(
    val temperatureCelsius: Double,
    val condition: WeatherCondition,
    val observedAtMillis: Long
)

enum class WeatherCondition {
    CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, SHOWERS, THUNDERSTORM, UNKNOWN;

    companion object {
        /** Maps WMO weather interpretation codes used by Open-Meteo. */
        fun fromWmoCode(code: Int): WeatherCondition =
            when (code) {
                0 -> CLEAR
                1, 2 -> PARTLY_CLOUDY
                3 -> CLOUDY
                45, 48 -> FOG
                in 51..57 -> DRIZZLE
                in 61..67 -> RAIN
                in 71..77, 85, 86 -> SNOW
                in 80..82 -> SHOWERS
                in 95..99 -> THUNDERSTORM
                else -> UNKNOWN
            }
    }
}
