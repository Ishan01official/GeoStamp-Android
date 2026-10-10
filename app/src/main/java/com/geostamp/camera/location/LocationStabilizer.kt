package com.geostamp.camera.location

import com.geostamp.camera.settings.LocationDisplayRefresh
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class LocationStabilizationPolicy(
    val maxAccuracyMeters: Float,
    val maxAgeMillis: Long,
    val displayRefresh: LocationDisplayRefresh
)

/**
 * Converts noisy raw fixes into a calmer display fix while the raw stream remains available
 * for capture-time freshness decisions.
 */
class LocationStabilizer(private val clock: () -> Long = System::currentTimeMillis) {
    private var displayed: LocationStamp? = null
    private var acceptedAtMillis: Long = 0L

    val current: LocationStamp? get() = displayed

    fun update(raw: LocationStamp, policy: LocationStabilizationPolicy): LocationStamp? {
        val now = clock()
        if (!raw.isFresh(now, policy.maxAgeMillis) || !raw.isAccurateEnough(policy.maxAccuracyMeters)) {
            return displayed
        }

        val current = displayed
        if (current == null) return accept(raw.normalizeForDisplay(previous = null, stationary = false), now)

        val movement = isMeaningfulMovement(current, raw)
        val holdElapsed = now - acceptedAtMillis >= policy.displayRefresh.holdMillis
        val shouldAccept = policy.displayRefresh == LocationDisplayRefresh.LIVE ||
            movement ||
            (holdElapsed && raw.measuredAtMillis >= current.measuredAtMillis)

        if (!shouldAccept) return current

        return accept(raw.normalizeForDisplay(previous = current, stationary = !movement), now)
    }

    fun reset() {
        displayed = null
        acceptedAtMillis = 0L
    }

    private fun accept(location: LocationStamp, now: Long): LocationStamp {
        displayed = location
        acceptedAtMillis = now
        return location
    }

    private fun isMeaningfulMovement(previous: LocationStamp, raw: LocationStamp): Boolean {
        val distance = distanceMeters(previous, raw)
        val previousAccuracy = previous.accuracyMeters.takeIf { !it.isNaN() } ?: DEFAULT_ACCURACY_METERS
        val rawAccuracy = raw.accuracyMeters.takeIf { !it.isNaN() } ?: DEFAULT_ACCURACY_METERS
        val noiseRadius = previousAccuracy + rawAccuracy
        val displacementThreshold = max(MIN_MOVEMENT_METERS, noiseRadius * ACCURACY_MOVEMENT_MULTIPLIER)
        if (distance >= displacementThreshold) return true

        val speed = raw.speedMetersPerSecond ?: return false
        val speedDisplacement = max(MIN_SPEED_MOVEMENT_METERS, noiseRadius * SPEED_ACCURACY_MULTIPLIER)
        return speed >= MOVING_SPEED_METERS_PER_SECOND && distance >= speedDisplacement
    }

    private fun LocationStamp.normalizeForDisplay(previous: LocationStamp?, stationary: Boolean): LocationStamp {
        val filteredSpeed = speedMetersPerSecond?.let { speed ->
            if (stationary && speed < STATIONARY_SPEED_METERS_PER_SECOND) 0f else speed
        }
        val filteredAltitude = altitudeMeters?.let { altitude ->
            val previousAltitude = previous?.altitudeMeters ?: return@let altitude
            val threshold = max(MIN_ALTITUDE_CHANGE_METERS, accuracyMeters.takeIf { !it.isNaN() }?.toDouble()?.times(0.2) ?: 0.0)
            if (abs(altitude - previousAltitude) <= threshold) previousAltitude else altitude
        }
        return copy(altitudeMeters = filteredAltitude, speedMetersPerSecond = filteredSpeed)
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0
        private const val DEFAULT_ACCURACY_METERS = 50f
        private const val MIN_MOVEMENT_METERS = 15f
        private const val MIN_SPEED_MOVEMENT_METERS = 8f
        private const val ACCURACY_MOVEMENT_MULTIPLIER = 1.25f
        private const val SPEED_ACCURACY_MULTIPLIER = 0.75f
        private const val MOVING_SPEED_METERS_PER_SECOND = 1.1f
        private const val STATIONARY_SPEED_METERS_PER_SECOND = 0.5f
        private const val MIN_ALTITUDE_CHANGE_METERS = 3.0

        fun distanceMeters(a: LocationStamp, b: LocationStamp): Float {
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val deltaLat = lat2 - lat1
            val deltaLon = Math.toRadians(b.longitude - a.longitude)
            val sinLat = sin(deltaLat / 2.0)
            val sinLon = sin(deltaLon / 2.0)
            val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
            return (2.0 * EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt(1.0 - h.coerceIn(0.0, 1.0)))).toFloat()
        }
    }
}
