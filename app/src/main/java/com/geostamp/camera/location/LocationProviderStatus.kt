package com.geostamp.camera.location

data class LocationProviderStatus(
    val gpsEnabled: Boolean,
    val networkEnabled: Boolean,
    val selectedProvider: String?
) {
    fun hasAnyProvider(): Boolean = gpsEnabled || networkEnabled

    fun selectedProviderLabel(): String =
        selectedProvider?.takeIf { it.isNotBlank() } ?: "none"
}
