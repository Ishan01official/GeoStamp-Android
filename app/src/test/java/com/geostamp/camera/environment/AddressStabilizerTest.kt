package com.geostamp.camera.environment

import com.geostamp.camera.location.LocationStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressStabilizerTest {
    private val earlier = LocationStamp(29.007923, 77.767677, 5f, 0L, null, null)
    private val latest = earlier.copy(latitude = 29.007960, longitude = 77.767680)
    private val broad = AddressParts(subLocality = "Ganga Nagar", locality = "Meerut", adminArea = "Uttar Pradesh", postalCode = "250001", country = "India")
    private val detailed = broad.copy(houseNumber = "87", colony = "Nav Shakti Dham",
        addressLines = listOf("87, Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India"))

    @Test
    fun providerLineKeepsDetailsAbsentFromStructuredFields() {
        assertEquals("87, Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India",
            AddressFormatter.format(broad.copy(addressLines = detailed.addressLines)))
    }

    @Test
    fun standardKeepsColonyAvailableOnlyInProviderLine() {
        val parts = broad.copy(houseNumber = "87", addressLines = detailed.addressLines)
        assertEquals("Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India",
            AddressFormatter.format(parts, AddressDetail.STANDARD))
    }

    @Test
    fun preservesEveryDistinctStructuredLevelInGeographicOrder() {
        val parts = detailed.copy(addressLines = emptyList(), building = "Tower A", street = "Park Road",
            neighbourhood = "East Quarter", district = "Meerut District")
        assertEquals("87, Tower A, Park Road, Nav Shakti Dham, East Quarter, Ganga Nagar, Meerut, Meerut District, Uttar Pradesh 250001, India",
            AddressFormatter.format(parts))
        assertEquals("87, Nav Shakti Dham, Ganga Nagar, Meerut, Meerut District, Uttar Pradesh 250001, India",
            AddressFormatter.format(detailed.copy(district = "Meerut District")))
    }

    @Test
    fun conflictingUnstructuredDetailsCannotBypassStabilityAsAnUpgrade() {
        val stabilizer = AddressStabilizer()
        val original = broad.copy(addressLines = listOf("12, Cedar Society, Ganga Nagar, Meerut, Uttar Pradesh 250001, India"))
        val conflict = broad.copy(addressLines = listOf("99, Different Larger Society, Ganga Nagar, Meerut, Uttar Pradesh 250001, India"))
        stabilizer.onLookup(original, earlier, 0L)
        assertEquals(original, stabilizer.onLookup(conflict, latest, 1_000L))
    }

    @Test
    fun formatsThreeLevelsWithoutFabricatingOrDuplicatingComponents() {
        assertEquals("87, Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India", AddressFormatter.format(detailed))
        assertEquals("Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India", AddressFormatter.format(detailed, AddressDetail.STANDARD))
        assertEquals("Ganga Nagar, Meerut, Uttar Pradesh, India", AddressFormatter.format(detailed, AddressDetail.SHORT))
        assertEquals(AddressFormatter.format(broad), AddressFormatter.format(broad.copy(district = "Meerut")))
        assertNull(AddressFormatter.format(AddressParts()))
    }

    @Test
    fun bothCoordinatesAllowImmediateCompatibleUpgradeAndKeepRicherAddress() {
        for ((start, next) in listOf(earlier to latest, latest to earlier)) {
            val stabilizer = AddressStabilizer()
            assertEquals(broad, stabilizer.onLookup(broad, start, 0L))
            assertEquals(detailed, stabilizer.onLookup(detailed, next, 1_000L))
            assertEquals(detailed, stabilizer.onLookup(broad, start, 60_000L))
        }
    }

    @Test
    fun conflictingHouseNeedsRepeatedResponseAfterHold() {
        val stabilizer = AddressStabilizer()
        val other = detailed.copy(houseNumber = "88", addressLines = emptyList())
        stabilizer.onLookup(detailed, earlier, 0L)
        assertEquals(detailed, stabilizer.onLookup(other, latest, 10_000L))
        assertEquals(detailed, stabilizer.onLookup(other, latest, 20_000L))
        assertEquals(other, stabilizer.onLookup(other, latest, 31_000L))
    }

    @Test
    fun realMovementAcceptsNewAddressAndDoesNotMergeDifferentPlaces() {
        val stabilizer = AddressStabilizer()
        stabilizer.onLookup(detailed, earlier, 0L)
        val other = broad.copy(locality = "Delhi")
        assertEquals(other, stabilizer.onLookup(other, earlier.copy(latitude = 29.02), 1_000L))
    }

    @Test
    fun cachePreservesRawComponentsForChangingDetailWithoutLookup() {
        val cache = AddressCache()
        cache.put(detailed, earlier, 0L)
        val cached = cache.get(latest, 1_000L)!!.parts
        assertEquals(AddressFormatter.format(detailed, AddressDetail.SHORT), AddressFormatter.format(cached, AddressDetail.SHORT))
        assertEquals(AddressFormatter.format(detailed), AddressFormatter.format(cached))
        assertNull(cache.get(earlier.copy(latitude = 29.0082), 1_000L))
    }
}
