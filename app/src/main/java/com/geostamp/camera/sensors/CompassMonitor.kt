package com.geostamp.camera.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.geostamp.camera.location.LocationStamp
import kotlin.math.abs

/**
 * Magnetic compass from whichever fused source the phone supports. Phones without a magnetometer report
 * [CompassUpdate.Unavailable] and are never given a heading derived from the gyroscope or game rotation
 * vector, because neither knows where north is.
 */
class CompassMonitor(
    context: Context,
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
) : SensorEventListener {
    val capabilities: SensorCapabilities = SensorCapabilities.from(sensorManager)

    private var gravityValues: FloatArray? = null
    private var magneticValues: FloatArray? = null
    private var referenceLocation: LocationStamp? = null
    private var accuracy = CompassAccuracy.UNKNOWN
    private var onUpdate: ((CompassUpdate) -> Unit)? = null
    private val rotation = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    fun setReferenceLocation(location: LocationStamp?) {
        referenceLocation = location
    }

    fun start(onUpdate: (CompassUpdate) -> Unit) {
        stop()
        this.onUpdate = onUpdate
        when (capabilities.compassSource) {
            CompassSource.ROTATION_VECTOR ->
                register(Sensor.TYPE_ROTATION_VECTOR)
            CompassSource.ACCELEROMETER_MAGNETOMETER -> {
                register(Sensor.TYPE_ACCELEROMETER)
                register(Sensor.TYPE_MAGNETIC_FIELD)
            }
            CompassSource.NONE -> onUpdate(CompassUpdate.Unavailable)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        onUpdate = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                publish()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                gravityValues = event.values.clone()
                publishFromAccelerometerAndMagnetometer()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magneticValues = event.values.clone()
                publishFromAccelerometerAndMagnetometer()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD || sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            this.accuracy = accuracy.toCompassAccuracy()
        }
    }

    private fun publishFromAccelerometerAndMagnetometer() {
        val gravity = gravityValues ?: return
        val magnetic = magneticValues ?: return
        if (SensorManager.getRotationMatrix(rotation, null, gravity, magnetic)) publish()
    }

    private fun publish() {
        // rotation[8] is the cosine of the angle between the screen normal and vertical. When the phone is
        // held up like a camera, report the direction the rear camera faces instead of the top edge.
        val matrix = if (abs(rotation[8]) < UPRIGHT_THRESHOLD) {
            SensorManager.remapCoordinateSystem(rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
            remapped
        } else {
            rotation
        }
        SensorManager.getOrientation(matrix, orientation)
        val magneticDegrees = CompassSmoother.normalize(Math.toDegrees(orientation[0].toDouble()).toFloat())
        onUpdate?.invoke(
            CompassUpdate.Available(
                CompassReading(magneticDegrees = magneticDegrees, trueDegrees = trueHeading(magneticDegrees), accuracy = accuracy)
            )
        )
    }

    private fun trueHeading(magneticDegrees: Float): Float? {
        val location = referenceLocation ?: return null
        val field = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitudeMeters?.toFloat() ?: 0f,
            location.measuredAtMillis
        )
        return CompassSmoother.normalize(magneticDegrees + field.declination)
    }

    private fun register(type: Int) {
        sensorManager.getDefaultSensor(type)?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    private fun Int.toCompassAccuracy(): CompassAccuracy =
        when (this) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.LOW
            SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassAccuracy.UNRELIABLE
            else -> CompassAccuracy.UNKNOWN
        }

    private companion object {
        /** About 45° away from lying flat. */
        const val UPRIGHT_THRESHOLD = 0.7f
    }
}
