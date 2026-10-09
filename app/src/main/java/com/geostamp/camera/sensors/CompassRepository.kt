package com.geostamp.camera.sensors

import com.geostamp.camera.location.LocationStamp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Compass readings as state, de-duplicated to whole degrees to avoid needless UI work. */
class CompassRepository(private val monitor: CompassMonitor) {
    private val _updates = MutableStateFlow<CompassUpdate?>(null)
    val updates: StateFlow<CompassUpdate?> = _updates.asStateFlow()

    val latestReading: CompassReading?
        get() = (_updates.value as? CompassUpdate.Available)?.reading

    fun start() = monitor.start { update ->
        val previous = _updates.value
        if (update is CompassUpdate.Available && previous is CompassUpdate.Available &&
            update.reading.displayDegrees.roundToInt() == previous.reading.displayDegrees.roundToInt() &&
            update.reading.accuracy == previous.reading.accuracy
        ) {
            return@start
        }
        _updates.value = update
    }

    fun stop() = monitor.stop()

    fun setReferenceLocation(location: LocationStamp?) = monitor.setReferenceLocation(location)
}
