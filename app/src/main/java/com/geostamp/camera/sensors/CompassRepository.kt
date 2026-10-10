package com.geostamp.camera.sensors

import com.geostamp.camera.location.LocationStamp
import com.geostamp.camera.settings.CompassSmoothing
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Compass readings as state, de-duplicated to whole degrees to avoid needless UI work. */
class CompassRepository(
    private val monitor: CompassMonitor,
    private val smoother: CompassSmoother = CompassSmoother()
) {
    private val _updates = MutableStateFlow<CompassUpdate?>(null)
    val updates: StateFlow<CompassUpdate?> = _updates.asStateFlow()
    private var smoothing = CompassSmoothing.SMOOTH

    val latestReading: CompassReading?
        get() = (_updates.value as? CompassUpdate.Available)?.reading

    fun start() = monitor.start { update ->
        val smoothed = when (update) {
            is CompassUpdate.Available -> CompassUpdate.Available(smoother.smooth(update.reading, smoothing))
            else -> update
        }
        val previous = _updates.value
        if (smoothed is CompassUpdate.Available && previous is CompassUpdate.Available &&
            smoothed.reading.displayDegrees.roundToInt() == previous.reading.displayDegrees.roundToInt() &&
            smoothed.reading.accuracy == previous.reading.accuracy
        ) {
            return@start
        }
        _updates.value = smoothed
    }

    fun stop() = monitor.stop()

    fun setReferenceLocation(location: LocationStamp?) = monitor.setReferenceLocation(location)

    fun setSmoothing(mode: CompassSmoothing) {
        if (smoothing == mode) return
        smoothing = mode
        smoother.reset()
    }
}
