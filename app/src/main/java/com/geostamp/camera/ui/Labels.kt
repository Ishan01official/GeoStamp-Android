package com.geostamp.camera.ui

import androidx.annotation.StringRes
import com.geostamp.camera.R
import com.geostamp.camera.capture.CaptureTimer
import com.geostamp.camera.capture.CaptureMode
import com.geostamp.camera.capture.FlashMode
import com.geostamp.camera.capture.PhotoAspectRatio
import com.geostamp.camera.capture.PhotoResolution
import com.geostamp.camera.settings.MapLinkProvider
import com.geostamp.camera.settings.CompassSmoothing
import com.geostamp.camera.settings.LocationDisplayRefresh
import com.geostamp.camera.settings.ThemeMode
import com.geostamp.camera.stamps.CoordinateFormat
import com.geostamp.camera.stamps.StampPosition
import com.geostamp.camera.stamps.StampTemplate
import com.geostamp.camera.stamps.StampTextColor
import com.geostamp.camera.stamps.TemperatureUnit

/** Single place that maps domain enums to localized labels. */
@get:StringRes
val FlashMode.labelRes: Int
    get() = when (this) {
        FlashMode.OFF -> R.string.flash_off
        FlashMode.AUTO -> R.string.flash_auto
        FlashMode.ON -> R.string.flash_on
    }

@get:StringRes
val CaptureTimer.labelRes: Int
    get() = when (this) {
        CaptureTimer.OFF -> R.string.timer_off
        CaptureTimer.THREE_SECONDS -> R.string.timer_3
        CaptureTimer.TEN_SECONDS -> R.string.timer_10
    }

@get:StringRes
val CaptureMode.labelRes: Int
    get() = when (this) {
        CaptureMode.PHOTO -> R.string.mode_photo
        CaptureMode.VIDEO -> R.string.mode_video
        CaptureMode.DUAL_PHOTO -> R.string.mode_dual_photo
        CaptureMode.DUAL_VIDEO -> R.string.mode_dual_video
    }

@get:StringRes
val PhotoAspectRatio.labelRes: Int
    get() = when (this) {
        PhotoAspectRatio.FOUR_THREE -> R.string.aspect_4_3
        PhotoAspectRatio.SIXTEEN_NINE -> R.string.aspect_16_9
    }

@get:StringRes
val PhotoResolution.labelRes: Int
    get() = when (this) {
        PhotoResolution.DEFAULT -> R.string.resolution_auto
        PhotoResolution.BALANCED -> R.string.resolution_balanced
        PhotoResolution.HIGH -> R.string.resolution_high
    }

@get:StringRes
val StampTemplate.labelRes: Int
    get() = when (this) {
        StampTemplate.MINIMAL -> R.string.template_minimal
        StampTemplate.CLASSIC -> R.string.template_classic
        StampTemplate.MAP_CARD -> R.string.template_map_card
        StampTemplate.PROFESSIONAL -> R.string.template_professional
    }

@get:StringRes
val StampPosition.labelRes: Int
    get() = when (this) {
        StampPosition.BOTTOM -> R.string.position_bottom
        StampPosition.TOP -> R.string.position_top
    }

@get:StringRes
val StampTextColor.labelRes: Int
    get() = when (this) {
        StampTextColor.WHITE -> R.string.color_white
        StampTextColor.WARM -> R.string.color_warm
        StampTextColor.YELLOW -> R.string.color_yellow
        StampTextColor.MINT -> R.string.color_mint
        StampTextColor.SKY -> R.string.color_sky
    }

@get:StringRes
val CoordinateFormat.labelRes: Int
    get() = when (this) {
        CoordinateFormat.DECIMAL -> R.string.coord_decimal
        CoordinateFormat.DMS -> R.string.coord_dms
    }

@get:StringRes
val TemperatureUnit.labelRes: Int
    get() = when (this) {
        TemperatureUnit.CELSIUS -> R.string.celsius
        TemperatureUnit.FAHRENHEIT -> R.string.fahrenheit
    }

@get:StringRes
val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }

@get:StringRes
val MapLinkProvider.labelRes: Int
    get() = when (this) {
        MapLinkProvider.OPEN_STREET_MAP -> R.string.map_osm
        MapLinkProvider.GOOGLE_MAPS -> R.string.map_google
    }

@get:StringRes
val LocationDisplayRefresh.labelRes: Int
    get() = when (this) {
        LocationDisplayRefresh.STABLE -> R.string.location_refresh_stable
        LocationDisplayRefresh.BALANCED -> R.string.location_refresh_balanced
        LocationDisplayRefresh.LIVE -> R.string.location_refresh_live
    }

@get:StringRes
val CompassSmoothing.labelRes: Int
    get() = when (this) {
        CompassSmoothing.SMOOTH -> R.string.compass_smoothing_smooth
        CompassSmoothing.BALANCED -> R.string.compass_smoothing_balanced
        CompassSmoothing.RESPONSIVE -> R.string.compass_smoothing_responsive
    }
