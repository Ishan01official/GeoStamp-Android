package com.geostamp.camera.sensors

data class CompassReading(
    val magneticDegrees: Float,
    val trueDegrees: Float?,
    val accuracy: CompassAccuracy
) {
    val displayDegrees: Float = trueDegrees ?: magneticDegrees

    fun displayText(): String =
        "${CompassHeading.cardinal(displayDegrees)} ${displayDegrees.toInt()} deg (${accuracy.label})"
}

enum class CompassAccuracy(val label: String) {
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low"),
    UNRELIABLE("calibrate"),
    UNKNOWN("unknown")
}

sealed interface CompassUpdate {
    data object Unavailable : CompassUpdate
    data class Available(val reading: CompassReading) : CompassUpdate

    fun displayText(): String =
        when (this) {
            Unavailable -> "Compass unavailable"
            is Available -> reading.displayText()
        }
}
