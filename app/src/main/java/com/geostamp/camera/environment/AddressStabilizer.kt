package com.geostamp.camera.environment

import com.geostamp.camera.location.LocationStamp
import kotlin.math.max

data class AddressStabilityPolicy(val holdMillis: Long = 30_000L, val minMovementMeters: Float = 50f)

class AddressStabilizer(private val policy: AddressStabilityPolicy = AddressStabilityPolicy()) {
    private var displayed: AddressParts? = null
    private var anchor: LocationStamp? = null
    private var displayedAtMillis = 0L
    private var pending: String? = null
    val currentLocation: LocationStamp? get() = anchor

    fun onLookup(parts: AddressParts, at: LocationStamp, nowMillis: Long): AddressParts? {
        val candidate = AddressFormatter.format(parts) ?: return displayed
        val current = displayed
        val currentAnchor = anchor
        val moved = currentAnchor == null || movedMeaningfully(currentAnchor, at)
        val accept = when {
            current == null || moved -> true
            candidate == AddressFormatter.format(current) -> false
            AddressFormatter.isEnrichment(current, parts) -> true
            AddressFormatter.isEnrichment(parts, current) -> false
            pending == candidate && nowMillis - displayedAtMillis >= policy.holdMillis -> true
            else -> {
                pending = candidate
                false
            }
        }
        if (accept) {
            displayed = parts
            anchor = at
            displayedAtMillis = nowMillis
            pending = null
        }
        return displayed
    }

    fun reset() {
        displayed = null
        anchor = null
        pending = null
        displayedAtMillis = 0L
    }

    private fun movedMeaningfully(from: LocationStamp, to: LocationStamp): Boolean {
        val noise = (from.accuracyMeters.takeIf { from.hasAccuracy } ?: 0f) + (to.accuracyMeters.takeIf { to.hasAccuracy } ?: 0f)
        return from.distanceMetersTo(to) > max(policy.minMovementMeters, noise)
    }
}
