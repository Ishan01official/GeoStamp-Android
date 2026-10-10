package com.geostamp.camera.environment

import com.geostamp.camera.location.LocationStabilizer
import com.geostamp.camera.location.LocationStamp

data class CachedAddress(
    val address: String,
    val near: LocationStamp,
    val fetchedAtMillis: Long
)

/** Small in-memory reverse-geocode cache keyed by proximity instead of exact GPS noise. */
class AddressCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxDistanceMeters: Float = DEFAULT_MAX_DISTANCE_METERS
) {
    private val entries = ArrayDeque<CachedAddress>()

    fun get(location: LocationStamp, nowMillis: Long): CachedAddress? {
        val match = entries
            .asSequence()
            .filter { nowMillis - it.fetchedAtMillis <= ttlMillis }
            .filter { LocationStabilizer.distanceMeters(it.near, location) <= maxDistanceMeters }
            .minByOrNull { LocationStabilizer.distanceMeters(it.near, location) }
            ?: return null
        entries.remove(match)
        entries.addFirst(match)
        trim(nowMillis)
        return match
    }

    fun put(address: String, location: LocationStamp, fetchedAtMillis: Long): CachedAddress {
        val cached = CachedAddress(address, location, fetchedAtMillis)
        entries.removeAll { LocationStabilizer.distanceMeters(it.near, location) <= maxDistanceMeters }
        entries.addFirst(cached)
        trim(fetchedAtMillis)
        return cached
    }

    private fun trim(nowMillis: Long) {
        entries.removeAll { nowMillis - it.fetchedAtMillis > ttlMillis }
        while (entries.size > maxEntries) entries.removeLast()
    }

    companion object {
        private const val DEFAULT_MAX_ENTRIES = 24
        private const val DEFAULT_TTL_MILLIS = 30 * 60_000L
        private const val DEFAULT_MAX_DISTANCE_METERS = 60f
    }
}
