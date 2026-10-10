package com.geostamp.camera.sensors

import com.geostamp.camera.location.LocationStamp
import kotlin.math.roundToInt

/** What a direction value actually measures. These are never presented as one another. */
enum class HeadingKind {
    /** Compass direction referenced to geographic north (magnetic heading corrected by declination). */
    TRUE_HEADING,

    /** Compass direction referenced to magnetic north, when declination is unknown. */
    MAGNETIC_HEADING,

    /** GNSS direction of travel while moving. Says nothing about where the camera points. */
    COURSE
}

/**
 * The one heading value shown in the top bar and burned into the stamp. Whole degrees are fixed once,
 * here, so the two can never disagree through separate rounding or separate sensor reads.
 */
data class HeadingSnapshot(val degrees: Int, val kind: HeadingKind, val accuracy: CompassAccuracy) {
    val cardinal: String get() = CompassHeading.cardinal(degrees.toFloat())

    companion object {
        fun of(degrees: Float, kind: HeadingKind, accuracy: CompassAccuracy) =
            HeadingSnapshot(((degrees.roundToInt() % 360) + 360) % 360, kind, accuracy)
    }
}

/** Localized wording; English defaults keep the formatter usable in JVM tests. */
data class HeadingLabels(
    val trueHeading: String = "%1\$s %2\$d° true",
    val magneticHeading: String = "%1\$s %2\$d° magnetic",
    val course: String = "Course %1\$s %2\$d°"
)

object HeadingFormatter {
    fun format(snapshot: HeadingSnapshot, labels: HeadingLabels = HeadingLabels()): String {
        val pattern = when (snapshot.kind) {
            HeadingKind.TRUE_HEADING -> labels.trueHeading
            HeadingKind.MAGNETIC_HEADING -> labels.magneticHeading
            HeadingKind.COURSE -> labels.course
        }
        return pattern.format(snapshot.cardinal, snapshot.degrees)
    }
}

object HeadingResolver {
    /** GNSS bearing is noise below walking pace. */
    const val MIN_COURSE_SPEED_METERS_PER_SECOND = 1.4f
    private const val MAX_COURSE_BEARING_ERROR_DEGREES = 30f

    /**
     * Compass first. Without a usable compass, the GNSS course is used only while clearly moving with a
     * trustworthy bearing. Otherwise there is no direction at all, never an invented one.
     */
    fun resolve(compass: CompassReading?, location: LocationStamp?): HeadingSnapshot? {
        if (compass != null) {
            val kind = if (compass.trueDegrees != null) HeadingKind.TRUE_HEADING else HeadingKind.MAGNETIC_HEADING
            return HeadingSnapshot.of(compass.displayDegrees, kind, compass.accuracy)
        }
        val bearing = location?.bearingDegrees ?: return null
        val speed = location.speedMetersPerSecond ?: return null
        if (speed < MIN_COURSE_SPEED_METERS_PER_SECOND) return null
        val error = location.bearingAccuracyDegrees
        if (error != null && error > MAX_COURSE_BEARING_ERROR_DEGREES) return null
        return HeadingSnapshot.of(bearing, HeadingKind.COURSE, CompassAccuracy.UNKNOWN)
    }
}
