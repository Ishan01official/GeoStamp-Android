package com.geostamp.camera.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat

class ForegroundLocationTracker(
    private val context: Context,
    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
) {
    private var listener: LocationListener? = null

    fun start(onUpdate: (LocationUpdate) -> Unit) {
        stop()

        val fineGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseGranted = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fineGranted && !coarseGranted) {
            onUpdate(LocationUpdate.PermissionDenied)
            return
        }

        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val provider = selectProvider(fineGranted, gpsEnabled, networkEnabled)
        val providerStatus = LocationProviderStatus(gpsEnabled, networkEnabled, provider)

        if (provider == null) {
            onUpdate(LocationUpdate.ProvidersDisabled(providerStatus))
            return
        }

        val approximate = !fineGranted && coarseGranted
        onUpdate(LocationUpdate.Waiting(providerStatus))

        try {
            emitLastKnown(provider, approximate, providerStatus, onUpdate)
            val activeListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    onUpdate(
                        LocationUpdate.Available(
                            location = LocationStamp.from(location, approximate = approximate),
                            providerStatus = providerStatus
                        )
                    )
                }

                override fun onProviderDisabled(provider: String) {
                    onUpdate(LocationUpdate.ProvidersDisabled(providerStatus.copy(selectedProvider = provider)))
                }
            }
            listener = activeListener
            locationManager.requestLocationUpdates(
                provider,
                MIN_UPDATE_INTERVAL_MILLIS,
                MIN_UPDATE_DISTANCE_METERS,
                activeListener,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            onUpdate(LocationUpdate.PermissionDenied)
        } catch (e: IllegalArgumentException) {
            onUpdate(LocationUpdate.ProviderUnavailable(e.message ?: "provider error"))
        }
    }

    fun stop() {
        val activeListener = listener ?: return
        try {
            locationManager.removeUpdates(activeListener)
        } catch (_: SecurityException) {
            // The next start will report permission state explicitly.
        } finally {
            listener = null
        }
    }

    private fun emitLastKnown(
        provider: String,
        approximate: Boolean,
        providerStatus: LocationProviderStatus,
        onUpdate: (LocationUpdate) -> Unit
    ) {
        val location = try {
            locationManager.getLastKnownLocation(provider)
        } catch (_: SecurityException) {
            null
        } ?: return

        val stamp = LocationStamp.from(location, approximate = approximate)
        if (stamp.isFresh(System.currentTimeMillis(), LAST_KNOWN_FRESHNESS_MILLIS)) {
            onUpdate(LocationUpdate.Available(stamp, providerStatus))
        } else {
            onUpdate(LocationUpdate.StaleLastKnown(stamp, providerStatus))
        }
    }

    private fun selectProvider(
        fineGranted: Boolean,
        gpsEnabled: Boolean,
        networkEnabled: Boolean
    ): String? =
        when {
            fineGranted && gpsEnabled -> LocationManager.GPS_PROVIDER
            networkEnabled -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val MIN_UPDATE_INTERVAL_MILLIS = 2_000L
        const val MIN_UPDATE_DISTANCE_METERS = 0f
        const val LAST_KNOWN_FRESHNESS_MILLIS = 30_000L
    }
}
