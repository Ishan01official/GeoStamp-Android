package com.geostamp.camera.stamps

/** Settings model for future UI integration. Online-only fields remain opt-in. */
data class StampPreferences(
    val enabled: Boolean = true,
    val position: Position = Position.BOTTOM,
    val fontScale: Float = 1f,
    val customText: String = "",
    val includeCoordinates: Boolean = true,
    val includeAccuracy: Boolean = true,
    val includeAltitude: Boolean = false,
    val includeSpeed: Boolean = false,
    val includeAddress: Boolean = false,
    val includeMap: Boolean = false,
    val includeWeather: Boolean = false,
    val writeExifLocation: Boolean = false
) {
    enum class Position { TOP, BOTTOM }
    init { require(fontScale in 0.5f..2.5f) }
}
