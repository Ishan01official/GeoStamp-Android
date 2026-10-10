package com.geostamp.camera.stamps

import com.geostamp.camera.sensors.HeadingLabels

/** Render-ready stamp text with an explicit visual hierarchy. Independent of Android UI classes. */
data class StampContent(
    val dateTime: String?,
    val headline: String?,
    val details: List<StampLine>,
    val mapPanel: MapPanel,
    val showLogo: Boolean,
    val compact: Boolean,
    /** Short latitude and longitude lines for [MapPanel.COORDINATES]; never a drawing of a map. */
    val panelCoordinates: List<String> = emptyList(),
    /** Link encoded as a QR code on the stamp; null when the QR field is off or there is no fix. */
    val qrPayload: String? = null
) {
    val showMap: Boolean get() = mapPanel != MapPanel.NONE
    val showQr: Boolean get() = qrPayload != null
    val isEmpty: Boolean get() = dateTime == null && headline == null && details.isEmpty() && !showMap && !showLogo && !showQr
}

/** What fills the map slot of a stamp. */
enum class MapPanel {
    NONE,

    /** A real map thumbnail from a permitted tile provider. */
    TILE,

    /** No tile available (offline, not consented, or failed): a plain coordinate panel instead of a fake map. */
    COORDINATES
}

data class StampLine(val icon: StampIcon, val text: String, val emphasis: Emphasis = Emphasis.SECONDARY) {
    enum class Emphasis { PRIMARY, SECONDARY }
}

enum class StampIcon { TIME, PLACE, COORDINATES, ACCURACY, ALTITUDE, WEATHER, NOTE }

/** Localized fragments. English defaults keep the builder usable in JVM tests. */
data class StampLabels(
    val locationUnavailable: String = "Location unavailable",
    val accuracy: String = "GPS ±%s m",
    val heading: HeadingLabels = HeadingLabels(),
    val altitude: String = "Alt %s m",
    val speed: String = "%s km/h",
    val weatherSource: String = "Open-Meteo %s",
    /** Labelled decimal coordinates used by the QR Location template; arguments are preformatted numbers. */
    val latitudeLongitude: String = "Lat %1\$s° Long %2\$s°",
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
