package com.geostamp.camera

import android.location.Address
import android.location.Geocoder
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.geostamp.camera.environment.AddressDetail
import com.geostamp.camera.environment.AddressFormatter
import com.geostamp.camera.environment.AddressResolver
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AddressResolverTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun preservesProviderLinesAndStructuredComponents() {
        val raw = Address(Locale.ENGLISH).apply {
            setAddressLine(0, "12, Cedar Society, North Quarter, Sample City, Sample State 123456, Country")
            subThoroughfare = "12"
            featureName = "Cedar Society"
            subLocality = "North Quarter"
            locality = "Sample City"
            subAdminArea = "Sample District"
            adminArea = "Sample State"
            postalCode = "123456"
            countryName = "Country"
        }
        val parts = AddressResolver(context).run { raw.toParts() }!!
        assertEquals("12", parts.houseNumber)
        assertEquals("Cedar Society", parts.building)
        assertEquals("Sample District", parts.district)
        assertTrue(AddressFormatter.format(parts)!!.contains("Cedar Society"))
        assertEquals("North Quarter, Sample City, Sample State, Country", AddressFormatter.format(parts, AddressDetail.SHORT))
    }

    @Test
    fun choosesRicherCompatibleResultOnlyForSameProviderLocation() {
        val broad = Address(Locale.ENGLISH).apply {
            locality = "Sample City"
            latitude = 29.007923
            longitude = 77.767677
        }
        fun detailed(latitude: Double, city: String = "Sample City") = Address(Locale.ENGLISH).apply {
            locality = city
            subThoroughfare = "12"
            thoroughfare = "Park Road"
            this.latitude = latitude
            longitude = 77.767677
        }
        val resolver = AddressResolver(context)
        assertEquals("12", resolver.select(listOf(broad, detailed(broad.latitude)))?.houseNumber)
        assertEquals(null, resolver.select(listOf(broad, detailed(29.007960)))?.houseNumber)
        assertEquals(null, resolver.select(listOf(broad, detailed(broad.latitude, "Other City")))?.houseNumber)
    }

    @Test
    fun compareLiveResponsesAtRequestedCoordinates() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveGeocoding") == "true")
        assumeTrue(Geocoder.isPresent())
        for ((latitude, longitude) in listOf(29.007923 to 77.767677, 29.007960 to 77.767680)) {
            @Suppress("DEPRECATION")
            val responses = Geocoder(context, Locale.ENGLISH).getFromLocation(latitude, longitude, 5).orEmpty()
            Log.i("GeoStampGeocodeTest", "$latitude,$longitude raw=$responses")
            assertTrue("No provider response at $latitude,$longitude", responses.isNotEmpty())
            val parts = AddressResolver(context).select(responses)!!
            Log.i("GeoStampGeocodeTest", "components=$parts")
            for (detail in AddressDetail.entries) {
                Log.i("GeoStampGeocodeTest", "$latitude,$longitude $detail=${AddressFormatter.format(parts, detail)}")
            }
        }
    }
}
