package com.geostamp.camera.stamps

import java.util.Locale
import kotlin.math.abs

object CoordinateFormatter {
    fun format(latitude: Double, longitude: Double, format: CoordinateFormat): String =
        when (format) {
            CoordinateFormat.DECIMAL -> String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
            CoordinateFormat.DMS -> "${toDms(latitude, 'N', 'S')}  ${toDms(longitude, 'E', 'W')}"
        }

    fun toDms(value: Double, positive: Char, negative: Char): String {
        val hemisphere = if (value >= 0) positive else negative
        val totalSeconds = Math.round(abs(value) * 36_000.0) / 10.0
        val degrees = (totalSeconds / 3600).toInt()
        val minutes = ((totalSeconds - degrees * 3600) / 60).toInt()
        val seconds = totalSeconds - degrees * 3600 - minutes * 60
        return String.format(Locale.US, "%d°%02d'%04.1f\"%c", degrees, minutes, seconds, hemisphere)
    }
}
