package com.geostamp.camera.sensors

/** Formatting only; real headings require a calibrated sensor reading. */
object CompassHeading {
    private val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    fun cardinal(degrees: Float): String {
        val normalized = ((degrees % 360f) + 360f) % 360f
        return directions[((normalized + 22.5f) / 45f).toInt() % 8]
    }
}
