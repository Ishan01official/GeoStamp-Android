package com.geostamp.camera.sensors

import com.geostamp.camera.settings.CompassSmoothing

/** Circular low-pass filter for compass headings, including 359 to 0 degree wraparound. */
class CompassSmoother {
    private var magneticDegrees: Float? = null
    private var trueDegrees: Float? = null

    fun smooth(reading: CompassReading, mode: CompassSmoothing): CompassReading {
        val alpha = if (reading.accuracy == CompassAccuracy.UNRELIABLE) {
            mode.alpha * UNRELIABLE_ACCURACY_MULTIPLIER
        } else {
            mode.alpha
        }
        val smoothedMagnetic = smoothDegrees(magneticDegrees, reading.magneticDegrees, alpha)
        magneticDegrees = smoothedMagnetic

        val smoothedTrue = reading.trueDegrees?.let { value ->
            smoothDegrees(trueDegrees, value, alpha).also { trueDegrees = it }
        } ?: run {
            trueDegrees = null
            null
        }

        return reading.copy(magneticDegrees = smoothedMagnetic, trueDegrees = smoothedTrue)
    }

    fun reset() {
        magneticDegrees = null
        trueDegrees = null
    }

    companion object {
        private const val FULL_CIRCLE = 360f
        private const val HALF_CIRCLE = 180f
        private const val UNRELIABLE_ACCURACY_MULTIPLIER = 0.5f

        fun smoothDegrees(previous: Float?, next: Float, alpha: Float): Float {
            val normalizedNext = normalize(next)
            val current = previous ?: return normalizedNext
            val delta = shortestDelta(current, normalizedNext)
            return normalize(current + delta * alpha.coerceIn(0f, 1f))
        }

        fun shortestDelta(from: Float, to: Float): Float {
            val delta = normalize(to) - normalize(from)
            return when {
                delta > HALF_CIRCLE -> delta - FULL_CIRCLE
                delta < -HALF_CIRCLE -> delta + FULL_CIRCLE
                else -> delta
            }
        }

        fun normalize(degrees: Float): Float =
            ((degrees % FULL_CIRCLE) + FULL_CIRCLE) % FULL_CIRCLE
    }
}
