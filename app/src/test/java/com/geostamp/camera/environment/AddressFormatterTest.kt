package com.geostamp.camera.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Address rendering must work for any country's structure, not one national layout. */
class AddressFormatterTest {
    private val united = AddressParts(
        houseNumber = "1600", street = "Amphitheatre Parkway", locality = "Mountain View",
        district = "Santa Clara County", adminArea = "California", postalCode = "94043",
        country = "United States", countryCode = "US"
    )
    private val germany = AddressParts(
        houseNumber = "1", street = "Platz der Republik", subLocality = "Tiergarten", locality = "Berlin",
        adminArea = "Berlin", postalCode = "11011", country = "Deutschland", countryCode = "DE",
        addressLines = listOf("Platz der Republik 1, 11011 Berlin, Deutschland")
    )
    private val japan = AddressParts(
        locality = "渋谷区", adminArea = "東京都", postalCode = "150-0002", country = "日本", countryCode = "JP",
        addressLines = listOf("日本、〒150-0002 東京都渋谷区渋谷２丁目２１−１")
    )
    private val brazilVillage = AddressParts(locality = "Alter do Chão", adminArea = "Pará", country = "Brasil", countryCode = "BR")

    @Test
    fun everyDetailLevelProducesCleanText() {
        for (parts in listOf(united, germany, japan, brazilVillage)) {
            for (detail in AddressDetail.entries) {
                val text = AddressFormatter.format(parts, detail)
                assertClean(text)
            }
        }
    }

    @Test
    fun usAddressKeepsStreetCityStateAndCountry() {
        val text = AddressFormatter.format(united)!!
        listOf("1600", "Amphitheatre Parkway", "Mountain View", "California 94043", "United States").forEach {
            assertTrue("$it missing from $text", text.contains(it))
        }
        assertEquals("Mountain View, California, United States", AddressFormatter.format(united, AddressDetail.SHORT))
    }

    @Test
    fun cityStatesAreNotRepeated() {
        val singapore = AddressParts(locality = "Singapore", adminArea = "Singapore", country = "Singapore")
        assertEquals("Singapore", AddressFormatter.format(singapore, AddressDetail.SHORT))
        val berlin = AddressFormatter.format(germany, AddressDetail.SHORT)!!
        assertEquals("Tiergarten, Berlin, Deutschland", berlin)
    }

    @Test
    fun providerLineInAScriptWithoutSpacesIsNotDuplicated() {
        assertEquals("日本、〒150-0002 東京都渋谷区渋谷２丁目２１−１", AddressFormatter.format(japan))
    }

    @Test
    fun placeholdersFromGeocodersAreNeverShown() {
        val parts = AddressParts(
            street = "Unnamed Road", subLocality = "null", locality = "Unknown", district = " , ",
            adminArea = "Bavaria", country = "Germany", addressLines = listOf("Unnamed Road, , Bavaria, Germany")
        )
        for (detail in AddressDetail.entries) {
            val text = AddressFormatter.format(parts, detail)!!
            assertClean(text)
            assertEquals("Bavaria, Germany", text)
        }
    }

    @Test
    fun missingFieldsGiveNullInsteadOfEmptyText() {
        assertNull(AddressFormatter.format(AddressParts()))
        assertNull(AddressFormatter.format(AddressParts(locality = "null", country = "Unknown")))
    }

    @Test
    fun villageOnlyAddressStillFormats() {
        assertEquals("Alter do Chão, Pará, Brasil", AddressFormatter.format(brazilVillage))
    }

    private fun assertClean(text: String?) {
        if (text == null) return
        assertFalse(text, text.contains("null", ignoreCase = true))
        assertFalse(text, text.contains("Unknown", ignoreCase = true))
        assertFalse(text, text.contains(", ,"))
        assertFalse(text, text.startsWith(",") || text.endsWith(","))
        val pieces = text.split(", ")
        assertEquals("duplicate component in $text", pieces.size, pieces.map { it.lowercase() }.toSet().size)
    }
}
