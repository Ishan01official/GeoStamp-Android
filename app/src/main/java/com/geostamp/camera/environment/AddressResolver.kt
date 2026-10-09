package com.geostamp.camera.environment

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Reverse geocoding through the platform Geocoder. Returns null instead of guessing. */
class AddressResolver(private val context: Context) {
    val isAvailable: Boolean get() = Geocoder.isPresent()

    suspend fun resolve(latitude: Double, longitude: Double, locale: Locale = Locale.getDefault()): String? {
        if (!isAvailable) return null
        val geocoder = Geocoder(context, locale)
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resume(null)
                    }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
        }
        return address?.format()
    }

    private fun Address.format(): String? {
        val line = getAddressLine(0)?.trim()
        if (!line.isNullOrEmpty()) return line
        return listOfNotNull(locality, adminArea, countryName).joinToString(", ").ifEmpty { null }
    }
}
