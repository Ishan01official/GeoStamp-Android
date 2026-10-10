package com.geostamp.camera.sensors

data class CompassReading(
    val magneticDegrees: Float,
    val trueDegrees: Float?,
    val accuracy: CompassAccuracy
) {
    val displayDegrees: Float = trueDegrees ?: magneticDegrees
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
}
