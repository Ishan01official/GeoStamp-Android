package com.geostamp.camera.environment

import android.graphics.Bitmap
import android.util.Log
import com.geostamp.camera.location.LocationStamp
import com.geostamp.camera.settings.OnlineServices
import com.geostamp.camera.stamps.WeatherReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A value that is only valid near the place and time it was measured. */
data class Placed<T>(val value: T, val near: LocationStamp, val fetchedAtMillis: Long)

data class EnvironmentSnapshot(
    val address: Placed<String>? = null,
    val weather: Placed<WeatherReading>? = null,
    val map: Placed<Bitmap>? = null
)

/**
 * Prefetches opt-in online data in the background so the shutter never waits for the network.
 * Values are discarded at capture time if they were fetched for a different place.
 */
class EnvironmentRepository(
    private val scope: CoroutineScope,
    private val addressResolver: AddressResolver,
    private val weatherClient: WeatherClient,
    private val mapTileRenderer: MapTileRenderer,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val _snapshot = MutableStateFlow(EnvironmentSnapshot())
    val snapshot: StateFlow<EnvironmentSnapshot> = _snapshot.asStateFlow()
    private var addressJob: Job? = null
    private var weatherJob: Job? = null
    private var mapJob: Job? = null

    fun onLocation(location: LocationStamp, services: OnlineServices) {
        val current = _snapshot.value
        if (!services.addressLookup) _snapshot.update { it.copy(address = null) }
        if (!services.weather) _snapshot.update { it.copy(weather = null) }
        if (!services.mapTiles) _snapshot.update { it.copy(map = null) }

        if (services.addressLookup && needsRefresh(current.address, location, ADDRESS_TTL) && addressJob?.isActive != true) {
            addressJob = scope.launch(Dispatchers.IO) {
                runCatching { addressResolver.resolve(location.latitude, location.longitude) }
                    .onFailure { Log.w(TAG, "Address lookup failed", it) }
                    .getOrNull()
                    ?.let { address -> _snapshot.update { it.copy(address = Placed(address, location, clock())) } }
            }
        }
        if (services.weather && needsRefresh(current.weather, location, WEATHER_TTL) && weatherJob?.isActive != true) {
            weatherJob = scope.launch(Dispatchers.IO) {
                runCatching { weatherClient.current(location.latitude, location.longitude) }
                    .onFailure { Log.w(TAG, "Weather lookup failed", it) }
                    .getOrNull()
                    ?.let { weather -> _snapshot.update { it.copy(weather = Placed(weather, location, clock())) } }
            }
        }
        if (services.mapTiles && needsRefresh(current.map, location, MAP_TTL, MAP_MAX_DISTANCE) && mapJob?.isActive != true) {
            mapJob = scope.launch(Dispatchers.IO) {
                runCatching { mapTileRenderer.render(location.latitude, location.longitude) }
                    .onFailure { Log.w(TAG, "Map tiles failed", it) }
                    .getOrNull()
                    ?.let { map -> _snapshot.update { it.copy(map = Placed(map, location, clock())) } }
            }
        }
    }

    /** Values valid for a photo taken at [location]; anything else is dropped rather than reused. */
    fun forCapture(location: LocationStamp?): EnvironmentSnapshot {
        if (location == null) return EnvironmentSnapshot()
        val now = clock()
        val current = _snapshot.value
        return EnvironmentSnapshot(
            address = current.address?.takeIf { it.isValidFor(location, now, ADDRESS_TTL * 4, MAX_DISTANCE) },
            weather = current.weather?.takeIf { it.isValidFor(location, now, WEATHER_TTL * 2, WEATHER_MAX_DISTANCE) },
            map = current.map?.takeIf { it.isValidFor(location, now, MAP_TTL * 4, MAP_MAX_DISTANCE) }
        )
    }

    fun clearMapCache() {
        _snapshot.update { it.copy(map = null) }
        scope.launch(Dispatchers.IO) { mapTileRenderer.clearCache() }
    }

    private fun needsRefresh(placed: Placed<*>?, location: LocationStamp, ttl: Long, distance: Float = MAX_DISTANCE): Boolean =
        placed == null || !placed.isValidFor(location, clock(), ttl, distance)

    private fun Placed<*>.isValidFor(location: LocationStamp, now: Long, ttl: Long, distance: Float): Boolean =
        now - fetchedAtMillis <= ttl && near.distanceMetersTo(location) <= distance

    private companion object {
        const val TAG = "EnvironmentRepository"
        const val MAX_DISTANCE = 40f
        const val MAP_MAX_DISTANCE = 15f
        const val WEATHER_MAX_DISTANCE = 3_000f
        const val ADDRESS_TTL = 5 * 60_000L
        const val WEATHER_TTL = 15 * 60_000L
        const val MAP_TTL = 10 * 60_000L
    }
}
