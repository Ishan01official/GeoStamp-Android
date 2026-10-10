package com.geostamp.camera.location

import com.geostamp.camera.settings.LocationDisplayRefresh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Exposes foreground location updates as state. Only active while a screen calls [start]. */
class LocationRepository(
    private val tracker: ForegroundLocationTracker,
    private val stabilizer: LocationStabilizer = LocationStabilizer()
) {
    private val _updates = MutableStateFlow<LocationUpdate?>(null)
    val updates: StateFlow<LocationUpdate?> = _updates.asStateFlow()

    private val _rawUpdates = MutableStateFlow<LocationUpdate?>(null)
    val rawUpdates: StateFlow<LocationUpdate?> = _rawUpdates.asStateFlow()

    private var policy = LocationStabilizationPolicy(
        maxAccuracyMeters = 100f,
        maxAgeMillis = 120_000L,
        displayRefresh = LocationDisplayRefresh.STABLE
    )

    /** The latest raw fix, if any. Freshness is checked by the caller at capture time. */
    val latestLocation: LocationStamp?
        get() = (_rawUpdates.value as? LocationUpdate.Available)?.location

    /** The stabilized fix shown in the UI. */
    val latestDisplayLocation: LocationStamp?
        get() = (_updates.value as? LocationUpdate.Available)?.location

    fun setPolicy(policy: LocationStabilizationPolicy) {
        this.policy = policy
    }

    fun start() = tracker.start { update ->
        _rawUpdates.value = update
        _updates.value = when (update) {
            is LocationUpdate.Available -> {
                val displayLocation = stabilizer.update(update.location, policy)
                if (displayLocation != null) update.copy(location = displayLocation) else update
            }
            else -> update
        }
    }

    fun stop() = tracker.stop()
}
