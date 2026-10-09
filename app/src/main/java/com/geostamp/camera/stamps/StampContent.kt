package com.geostamp.camera.stamps

/** Render-ready stamp text with an explicit visual hierarchy. Independent of Android UI classes. */
data class StampContent(
    val dateTime: String?,
    val headline: String?,
    val details: List<StampLine>,
    val showMap: Boolean,
    val showLogo: Boolean,
    val compact: Boolean
) {
    val isEmpty: Boolean get() = dateTime == null && headline == null && details.isEmpty() && !showMap && !showLogo
}

data class StampLine(val icon: StampIcon, val text: String, val emphasis: Emphasis = Emphasis.SECONDARY) {
    enum class Emphasis { PRIMARY, SECONDARY }
}

enum class StampIcon { TIME, PLACE, COORDINATES, ACCURACY, ALTITUDE, WEATHER, NOTE }

/** Localized fragments. English defaults keep the builder usable in JVM tests. */
data class StampLabels(
    val locationUnavailable: String = "Location unavailable",
    val accuracy: String = "GPS ±%s m",
    val magneticSuffix: String = "mag",
    val altitude: String = "Alt %s m",
    val speed: String = "%s km/h",
    val weatherSource: String = "Open-Meteo %s",
    val weather: Map<WeatherCondition, String> = WeatherCondition.entries.associateWith { defaultWeatherName(it) }
) {
    companion object {
        fun defaultWeatherName(condition: WeatherCondition): String =
            when (condition) {
                WeatherCondition.CLEAR -> "Clear"
                WeatherCondition.PARTLY_CLOUDY -> "Partly cloudy"
                WeatherCondition.CLOUDY -> "Cloudy"
                WeatherCondition.FOG -> "Fog"
                WeatherCondition.DRIZZLE -> "Drizzle"
                WeatherCondition.RAIN -> "Rain"
                WeatherCondition.SNOW -> "Snow"
                WeatherCondition.SHOWERS -> "Showers"
                WeatherCondition.THUNDERSTORM -> "Thunderstorm"
                WeatherCondition.UNKNOWN -> "Weather"
            }
    }
}
