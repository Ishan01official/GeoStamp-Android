package com.geostamp.camera.stamps

import com.geostamp.camera.qr.QrLocationEncoder
import com.geostamp.camera.sensors.HeadingFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Turns real capture data and user preferences into [StampContent]. Missing data is omitted, never invented. */
class StampContentBuilder(
    private val locale: Locale = Locale.getDefault(),
    private val timeZone: TimeZone = TimeZone.getDefault(),
    private val labels: StampLabels = StampLabels()
) {
    fun build(
        data: StampData,
        preferences: StampPreferences,
        mapAvailable: Boolean = false,
        logoAvailable: Boolean = false
    ): StampContent {
        val fields = preferences.fields
        if (!preferences.enabled) return EMPTY

        val details = buildList {
            coordinatesLine(data, fields, preferences)?.let(::add)
            measurementLine(data, fields)?.let(::add)
            motionLine(data, fields)?.let(::add)
            weatherLine(data, fields, preferences.temperatureUnit)?.let(::add)
            preferences.customText.trim()
                .takeIf { fields.customText && it.isNotEmpty() }
                ?.let { add(StampLine(StampIcon.NOTE, it)) }
        }

        return StampContent(
            dateTime = if (fields.dateTime) formatDate(data.capturedAtMillis, preferences.dateFormat) else null,
            headline = data.address?.trim()?.takeIf { fields.address && it.isNotEmpty() },
            details = details,
            mapPanel = when {
                !fields.map || data.location == null -> MapPanel.NONE
                mapAvailable -> MapPanel.TILE
                else -> MapPanel.COORDINATES
            },
            showLogo = fields.logo && logoAvailable,
            compact = preferences.template.compact,
            panelCoordinates = data.location?.let { CoordinateFormatter.panelLines(it.latitude, it.longitude) }.orEmpty(),
            qrPayload = data.location?.takeIf { fields.qrCode }?.let { QrLocationEncoder.payload(it.latitude, it.longitude) }
        )
    }

    fun formatDate(millis: Long, format: StampDateFormat): String =
        SimpleDateFormat(format.pattern, locale).apply { timeZone = this@StampContentBuilder.timeZone }
            .format(Date(millis))

    private fun coordinatesLine(data: StampData, fields: StampFields, preferences: StampPreferences): StampLine? {
        if (!fields.coordinates) return null
        val location = data.location ?: return null
        val text = if (preferences.template == StampTemplate.QR_LOCATION && preferences.coordinateFormat == CoordinateFormat.DECIMAL) {
            labels.latitudeLongitude.format(formatNumber(location.latitude, 6), formatNumber(location.longitude, 6))
        } else {
            CoordinateFormatter.format(location.latitude, location.longitude, preferences.coordinateFormat)
        }
        return StampLine(StampIcon.COORDINATES, text, StampLine.Emphasis.PRIMARY)
    }

    private fun measurementLine(data: StampData, fields: StampFields): StampLine? {
        val parts = buildList {
            val location = data.location
            if (fields.accuracy && location != null && location.hasAccuracy) {
                add(labels.accuracy.format(formatNumber(location.accuracyMeters.toDouble(), 0)))
            }
            val heading = data.heading
            if (fields.heading && heading != null) add(HeadingFormatter.format(heading, labels.heading))
        }
        return parts.takeIf { it.isNotEmpty() }?.let { StampLine(StampIcon.ACCURACY, it.joinToString(SEPARATOR)) }
    }

    private fun motionLine(data: StampData, fields: StampFields): StampLine? {
        val location = data.location ?: return null
        val parts = buildList {
            location.altitudeMeters?.takeIf { fields.altitude }?.let {
                add(labels.altitude.format(formatNumber(it, 0)))
            }
            location.speedMetersPerSecond?.takeIf { fields.speed }?.let(::formatSpeed)?.let {
                add(labels.speed.format(it))
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.let { StampLine(StampIcon.ALTITUDE, it.joinToString(SEPARATOR)) }
    }

    private fun weatherLine(data: StampData, fields: StampFields, unit: TemperatureUnit): StampLine? {
        val weather = data.weather?.takeIf { fields.weather } ?: return null
        val temperature = when (unit) {
            TemperatureUnit.CELSIUS -> "${formatNumber(weather.temperatureCelsius, 0)}°C"
            TemperatureUnit.FAHRENHEIT -> "${formatNumber(weather.temperatureCelsius * 9 / 5 + 32, 0)}°F"
        }
        val condition = labels.weather[weather.condition] ?: StampLabels.defaultWeatherName(weather.condition)
        val observed = SimpleDateFormat("HH:mm", locale).apply { timeZone = this@StampContentBuilder.timeZone }
            .format(Date(weather.observedAtMillis))
        return StampLine(StampIcon.WEATHER, "$temperature$SEPARATOR$condition$SEPARATOR${labels.weatherSource.format(observed)}")
    }

    private fun formatNumber(value: Double, decimals: Int): String = "%.${decimals}f".format(Locale.US, value)

    /** Below about 1 km/h a GPS speed is drift, not movement, so it is omitted rather than shown as 0.2. */
    private fun formatSpeed(metersPerSecond: Float): String? {
        val kmh = metersPerSecond * METERS_PER_SECOND_TO_KMH
        return if (kmh < STATIONARY_SPEED_KMH) null else formatNumber(kmh, 1)
    }

    companion object {
        const val SEPARATOR = " · "
        private const val METERS_PER_SECOND_TO_KMH = 3.6
        private const val STATIONARY_SPEED_KMH = 1.0
        private val EMPTY = StampContent(null, null, emptyList(), mapPanel = MapPanel.NONE, showLogo = false, compact = true)
    }
}
