package com.geostamp.camera.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Exposes foreground location updates as state. Only active while a screen calls [start]. */
class LocationRepository(private val tracker: ForegroundLocationTracker) {
    private val _updates = MutableStateFlow<LocationUpdate?>(null)
    val updates: StateFlow<LocationUpdate?> = _updates.asStateFlow()

    /** The latest usable fix, if any. Freshness is checked by the caller at capture time. */
    val latestLocation: LocationStamp?
        get() = (_updates.value as? LocationUpdate.Available)?.location

    fun start() = tracker.start { _updates.value = it }

    fun stop() = tracker.stop()
}
