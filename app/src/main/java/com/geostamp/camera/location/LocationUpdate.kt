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
}

