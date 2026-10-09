package com.geostamp.camera.location

sealed interface LocationUpdate {
    data object PermissionDenied : LocationUpdate

    data class ProvidersDisabled(
        val providerStatus: LocationProviderStatus
    ) : LocationUpdate

    data class Waiting(
        val providerStatus: LocationProviderStatus
    ) : LocationUpdate

    data class Available(
        val location: LocationStamp,
        val providerStatus: LocationProviderStatus
    ) : LocationUpdate

    data class StaleLastKnown(
        val location: LocationStamp,
        val providerStatus: LocationProviderStatus
    ) : LocationUpdate

    data class ProviderUnavailable(
        val message: String
    ) : LocationUpdate

    fun displayText(nowMillis: Long = System.currentTimeMillis()): String =
        when (this) {
            PermissionDenied -> "Location denied; camera works without GPS"
            is ProvidersDisabled -> "Location providers disabled"
            is Waiting -> "Waiting for ${providerStatus.selectedProviderLabel()} location"
            is Available -> location.displayText(nowMillis)
            is StaleLastKnown -> "Stale ${location.providerLabel()} location (${location.ageMillis(nowMillis) / 1000}s old)"
            is ProviderUnavailable -> "Location unavailable: $message"
        }
}

fun LocationStamp.displayText(nowMillis: Long = System.currentTimeMillis()): String {
    val ageSeconds = (ageMillis(nowMillis) / 1000L).coerceAtLeast(0L)
    val precision = if (approximate) "approx" else "precise"
    return "${providerLabel()} $precision GPS ${accuracyMeters.toInt()}m, ${ageSeconds}s old"
}
