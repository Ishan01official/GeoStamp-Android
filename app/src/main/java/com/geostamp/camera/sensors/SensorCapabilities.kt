package com.geostamp.camera.sensors

import android.hardware.Sensor
import android.hardware.SensorManager

/** Which motion sensors this phone has. Many budget phones have no magnetometer. */
data class SensorCapabilities(
    val accelerometer: Boolean,
    val magnetometer: Boolean,
    val rotationVector: Boolean,
    val gameRotationVector: Boolean
) {
    /** A compass needs magnetic north. A game rotation vector or gyroscope alone cannot provide it. */
    val compassSource: CompassSource
        get() = when {
            rotationVector && magnetometer -> CompassSource.ROTATION_VECTOR
            accelerometer && magnetometer -> CompassSource.ACCELEROMETER_MAGNETOMETER
            else -> CompassSource.NONE
        }

    val hasCompass: Boolean get() = compassSource != CompassSource.NONE

    companion object {
        fun from(sensorManager: SensorManager) = SensorCapabilities(
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null,
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null,
            rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null,
            gameRotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null
        )
    }
}

enum class CompassSource { ROTATION_VECTOR, ACCELEROMETER_MAGNETOMETER, NONE }
