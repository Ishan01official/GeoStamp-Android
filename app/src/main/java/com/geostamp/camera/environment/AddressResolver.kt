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

    suspend fun resolve(latitude: Double, longitude: Double, locale: Locale = Locale.getDefault()): AddressParts? {
        if (!isAvailable) return null
        val geocoder = Geocoder(context, locale)
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 5, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.toList())
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, 5).orEmpty()
        }
        return select(addresses)
    }

    internal fun select(addresses: List<Address>): AddressParts? {
        val first = addresses.firstOrNull { it.toParts() != null } ?: return null
        val primary = first.toParts() ?: return null
        return addresses.asSequence().filter { candidate ->
            candidate === first || (first.hasLatitude() && first.hasLongitude() &&
                candidate.hasLatitude() && candidate.hasLongitude() &&
                kotlin.math.abs(candidate.latitude - first.latitude) < 0.0000001 &&
                kotlin.math.abs(candidate.longitude - first.longitude) < 0.0000001)
        }.mapNotNull { it.toParts() }.filter { it == primary || AddressFormatter.isEnrichment(primary, it) }
            .maxByOrNull(AddressFormatter::completeness) ?: primary
    }

    internal fun Address.toParts(): AddressParts? =
        AddressParts(
            houseNumber = subThoroughfare,
            street = thoroughfare,
            subLocality = subLocality,
            locality = locality,
            adminArea = adminArea,
            postalCode = postalCode,
            country = countryName,
            building = featureName?.takeUnless { it == subThoroughfare || it == thoroughfare },
            district = subAdminArea,
            addressLines = (0..maxAddressLineIndex).mapNotNull { getAddressLine(it)?.trim()?.takeIf(String::isNotEmpty) }
        ).takeIf { AddressFormatter.format(it) != null }
}
