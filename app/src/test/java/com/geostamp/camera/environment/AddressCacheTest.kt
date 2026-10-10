package com.geostamp.camera.environment

import com.geostamp.camera.location.LocationStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressCacheTest {
    @Test
    fun returnsNearbyCachedAddressForGpsNoise() {
        val cache = AddressCache(ttlMillis = 60_000L, maxDistanceMeters = 60f)
        cache.put(AddressParts(building = "Site A"), location(), fetchedAtMillis = 1_000L)

        val cached = cache.get(location(latitude = 29.00002, longitude = 77.00002), nowMillis = 2_000L)

        assertEquals("Site A", cached?.parts?.building)
    }

    @Test
    fun rejectsExpiredOrDistantAddress() {
        val cache = AddressCache(ttlMillis = 5_000L, maxDistanceMeters = 30f)
        cache.put(AddressParts(building = "Site A"), location(), fetchedAtMillis = 1_000L)

        assertNull(cache.get(location(), nowMillis = 7_001L))
        cache.put(AddressParts(building = "Site A"), location(), fetchedAtMillis = 8_000L)
        assertNull(cache.get(location(latitude = 29.01), nowMillis = 9_000L))
    }

    private fun location(latitude: Double = 29.0, longitude: Double = 77.0) = LocationStamp(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 5f,
        measuredAtMillis = 1_000L,
        altitudeMeters = null,
        speedMetersPerSecond = null
    )
}
