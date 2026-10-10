package com.geostamp.camera.environment

import com.geostamp.camera.location.LocationStamp
import org.junit.Assert.assertEquals
import org.junit.Test

class AddressStabilizerTest {
    private val desk = location(29.007890, 77.767665, accuracy = 4f)

    private fun parts(house: String?, street: String = "Nav Shakti Dham", area: String = "Ganga Nagar") =
        AddressParts(houseNumber = house, street = street, subLocality = area, locality = "Meerut", adminArea = "Uttar Pradesh", postalCode = "250001", country = "India")

    @Test
    fun `formatter drops house number and duplicate place names`() {
        val text = AddressFormatter.format(parts("98").copy(subLocality = "Meerut"), includeHouseNumber = false)
        assertEquals("Nav Shakti Dham, Meerut, Uttar Pradesh 250001, India", text)
    }

    @Test
    fun `changing house numbers at the same desk never reach the stamp`() {
        val stabilizer = AddressStabilizer()
        val expected = "Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India"
        listOf("60", "79", "89", "90", "98", "107").forEachIndexed { i, number ->
            val shown = stabilizer.onLookup(parts(number), desk.jitter(i), nowMillis = i * 20_000L, showHouseNumbers = false)
            assertEquals(expected, shown)
        }
    }

    @Test
    fun `opted-in house numbers still need two agreeing lookups`() {
        val stabilizer = AddressStabilizer()
        assertEquals(
            "Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India",
            stabilizer.onLookup(parts("98"), desk, 0L, showHouseNumbers = true)
        )
        assertEquals(
            "98 Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India",
            stabilizer.onLookup(parts("98"), desk.jitter(1), 20_000L, showHouseNumbers = true)
        )
    }

    @Test
    fun `opted-in house numbers that disagree stay hidden`() {
        val stabilizer = AddressStabilizer()
        stabilizer.onLookup(parts("60"), desk, 0L, showHouseNumbers = true)
        val shown = stabilizer.onLookup(parts("79"), desk.jitter(1), 20_000L, showHouseNumbers = true)
        assertEquals("Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India", shown)
    }

    @Test
    fun `small reported accuracy alone does not confirm a house number`() {
        val stabilizer = AddressStabilizer()
        val shown = stabilizer.onLookup(parts("98"), location(29.00789, 77.767665, accuracy = 1f), 0L, showHouseNumbers = true)
        assertEquals("Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India", shown)
    }

    @Test
    fun `poor accuracy never confirms a house number`() {
        val stabilizer = AddressStabilizer()
        val vague = location(29.00789, 77.767665, accuracy = 25f)
        stabilizer.onLookup(parts("98"), vague, 0L, showHouseNumbers = true)
        val shown = stabilizer.onLookup(parts("98"), vague, 20_000L, showHouseNumbers = true)
        assertEquals("Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India", shown)
    }

    @Test
    fun `single different street while stationary is ignored`() {
        val stabilizer = AddressStabilizer()
        stabilizer.onLookup(parts(null), desk, 0L, showHouseNumbers = false)
        val shown = stabilizer.onLookup(parts(null, street = "Garh Road"), desk.jitter(1), 40_000L, showHouseNumbers = false)
        assertEquals("Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India", shown)
    }

    @Test
    fun `repeated different street replaces address only after the hold window`() {
        val stabilizer = AddressStabilizer()
        stabilizer.onLookup(parts(null), desk, 0L, showHouseNumbers = false)
        stabilizer.onLookup(parts(null, street = "Garh Road"), desk, 10_000L, showHouseNumbers = false)
        assertEquals(
            "Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India",
            stabilizer.onLookup(parts(null, street = "Garh Road"), desk, 20_000L, showHouseNumbers = false)
        )
        assertEquals(
            "Garh Road, Ganga Nagar, Meerut, Uttar Pradesh 250001, India",
            stabilizer.onLookup(parts(null, street = "Garh Road"), desk, 31_000L, showHouseNumbers = false)
        )
    }

    @Test
    fun `real movement updates the address immediately`() {
        val stabilizer = AddressStabilizer()
        stabilizer.onLookup(parts(null), desk, 0L, showHouseNumbers = false)
        val moved = location(29.0110, 77.7677, accuracy = 5f) // about 350 m north
        assertEquals(
            "Garh Road, Ganga Nagar, Meerut, Uttar Pradesh 250001, India",
            stabilizer.onLookup(parts(null, street = "Garh Road"), moved, 1_000L, showHouseNumbers = false)
        )
    }

    private fun LocationStamp.jitter(step: Int) = copy(latitude = latitude + 0.00002 * (step % 3), longitude = longitude - 0.00002 * (step % 2))

    private fun location(lat: Double, lon: Double, accuracy: Float) =
        LocationStamp(lat, lon, accuracy, measuredAtMillis = 0L, altitudeMeters = null, speedMetersPerSecond = null)
}
