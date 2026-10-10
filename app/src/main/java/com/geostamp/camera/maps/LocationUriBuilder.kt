package com.geostamp.camera.maps

import com.geostamp.camera.settings.MapLinkProvider
import java.util.Locale

/**
 * The only place that builds location links. Coordinates are always written with a '.' decimal separator
 * and six decimals (about 0.1 m), whatever the device language, so every map app and QR reader parses them.
 */
object LocationUriBuilder {
    /** Opens in Google Maps on Android and iOS, or in any browser. Also the payload of location QR codes. */
    fun googleMaps(latitude: Double, longitude: Double): String =
        "https://www.google.com/maps/search/?api=1&query=${pair(latitude, longitude)}"

    /** Handled by any installed map app on Android. The pin label repeats the coordinates. */
    fun geo(latitude: Double, longitude: Double): String {
        val coordinates = pair(latitude, longitude)
        return "geo:$coordinates?q=$coordinates"
    }

    fun openStreetMap(latitude: Double, longitude: Double): String {
        val lat = coordinate(latitude)
        val lon = coordinate(longitude)
        return "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=17/$lat/$lon"
    }

    /** The web link for [provider]; also the last-resort fallback when no map app is installed. */
    fun web(provider: MapLinkProvider, latitude: Double, longitude: Double): String =
        when (provider) {
            MapLinkProvider.GOOGLE_MAPS -> googleMaps(latitude, longitude)
            MapLinkProvider.OPEN_STREET_MAP -> openStreetMap(latitude, longitude)
        }

    fun isValid(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    private fun pair(latitude: Double, longitude: Double) = "${coordinate(latitude)},${coordinate(longitude)}"

    private fun coordinate(value: Double): String = String.format(Locale.US, "%.6f", value)
}
