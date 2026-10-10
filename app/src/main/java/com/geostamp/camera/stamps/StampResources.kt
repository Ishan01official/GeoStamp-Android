package com.geostamp.camera.stamps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.geostamp.camera.R
import com.geostamp.camera.sensors.HeadingLabels
import java.io.File

/** Android resources for the stamp engine: localized labels, icons and the user's logo. */
class StampResources(private val context: Context) {
    fun labels(): StampLabels {
        val res = context.resources
        return StampLabels(
            locationUnavailable = res.getString(R.string.stamp_location_unavailable),
            accuracy = res.getString(R.string.stamp_accuracy_format),
            heading = headingLabels(),
            altitude = res.getString(R.string.stamp_altitude_format),
            speed = res.getString(R.string.stamp_speed_format),
            weatherSource = res.getString(R.string.stamp_weather_source_format),
            weather = WeatherCondition.entries.associateWith { res.getString(weatherName(it)) }
        )
    }

    fun headingLabels(): HeadingLabels {
        val res = context.resources
        return HeadingLabels(
            trueHeading = res.getString(R.string.heading_true_format),
            magneticHeading = res.getString(R.string.heading_magnetic_format),
            course = res.getString(R.string.heading_course_format)
        )
    }

    fun contentBuilder(): StampContentBuilder = StampContentBuilder(labels = labels())

    fun renderer(): StampRenderer =
        StampRenderer(
            iconProvider = { icon -> iconDrawable(icon) },
            mapAttribution = context.getString(R.string.map_attribution)
        )

    fun loadLogo(path: String?, maxSize: Int = LOGO_MAX_SIZE): Bitmap? {
        val file = path?.let(::File)?.takeIf { it.exists() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSize * 2 || bounds.outHeight / sample > maxSize * 2) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun iconDrawable(icon: StampIcon): Drawable? =
        ContextCompat.getDrawable(
            context,
            when (icon) {
                StampIcon.TIME -> R.drawable.ic_stamp_time
                StampIcon.PLACE -> R.drawable.ic_stamp_place
                StampIcon.COORDINATES -> R.drawable.ic_stamp_coordinates
                StampIcon.ACCURACY -> R.drawable.ic_stamp_accuracy
                StampIcon.ALTITUDE -> R.drawable.ic_stamp_altitude
                StampIcon.WEATHER -> R.drawable.ic_stamp_weather
                StampIcon.NOTE -> R.drawable.ic_stamp_note
            }
        )

    private fun weatherName(condition: WeatherCondition): Int =
        when (condition) {
            WeatherCondition.CLEAR -> R.string.weather_clear
            WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
            WeatherCondition.CLOUDY -> R.string.weather_cloudy
            WeatherCondition.FOG -> R.string.weather_fog
            WeatherCondition.DRIZZLE -> R.string.weather_drizzle
            WeatherCondition.RAIN -> R.string.weather_rain
            WeatherCondition.SNOW -> R.string.weather_snow
            WeatherCondition.SHOWERS -> R.string.weather_showers
            WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
            WeatherCondition.UNKNOWN -> R.string.weather_unknown
        }

    private companion object {
        const val LOGO_MAX_SIZE = 512
    }
}
