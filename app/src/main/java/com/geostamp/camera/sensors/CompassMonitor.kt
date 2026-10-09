package com.geostamp.camera.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.geostamp.camera.location.LocationStamp

class CompassMonitor(
    context: Context,
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
) : SensorEventListener {
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private var gravityValues: FloatArray? = null
    private var magneticValues: FloatArray? = null
    private var referenceLocation: LocationStamp? = null
    private var accuracy = CompassAccuracy.UNKNOWN
    private var onUpdate: ((CompassUpdate) -> Unit)? = null

    fun setReferenceLocation(location: LocationStamp?) {
        referenceLocation = location
    }

    fun start(onUpdate: (CompassUpdate) -> Unit) {
        stop()
        this.onUpdate = onUpdate

        if (accelerometer == null || magnetometer == null) {
            onUpdate(CompassUpdate.Unavailable)
            return
        }

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        onUpdate = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> gravityValues = event.values.clone()
            Sensor.TYPE_MAGNETIC_FIELD -> magneticValues = event.values.clone()
        }
        publishReadingIfReady()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            this.accuracy = accuracy.toCompassAccuracy()
        }
    }

    private fun publishReadingIfReady() {
        val gravity = gravityValues ?: return
        val magnetic = magneticValues ?: return
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        val hasRotation = SensorManager.getRotationMatrix(
            rotationMatrix,
            inclinationMatrix,
            gravity,
            magnetic
        )
        if (!hasRotation) return

        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val magneticDegrees = normalizeDegrees(Math.toDegrees(orientation[0].toDouble()).toFloat())
        val trueDegrees = trueHeading(magneticDegrees)
        onUpdate?.invoke(
            CompassUpdate.Available(
                CompassReading(
                    magneticDegrees = magneticDegrees,
                    trueDegrees = trueDegrees,
                    accuracy = accuracy
                )
            )
        )
    }

    private fun trueHeading(magneticDegrees: Float): Float? {
        val location = referenceLocation ?: return null
        val altitude = location.altitudeMeters?.toFloat() ?: 0f
        val field = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            altitude,
            location.measuredAtMillis
        )
        return normalizeDegrees(magneticDegrees + field.declination)
    }

    private fun normalizeDegrees(degrees: Float): Float =
        ((degrees % FULL_CIRCLE_DEGREES) + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES

    private fun Int.toCompassAccuracy(): CompassAccuracy =
        when (this) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.LOW
            SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassAccuracy.UNRELIABLE
            else -> CompassAccuracy.UNKNOWN
        }

    private companion object {
        const val FULL_CIRCLE_DEGREES = 360
    }
}
