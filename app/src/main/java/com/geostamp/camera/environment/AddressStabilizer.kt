package com.geostamp.camera.environment

import com.geostamp.camera.location.LocationStamp
import kotlin.math.max

data class AddressStabilityPolicy(
    /** A stationary phone keeps its address at least this long before a different one may replace it. */
    val holdMillis: Long = 30_000L,
    val minMovementMeters: Float = 50f,
    /** House numbers need at least this good a fix, and agreement between consecutive lookups. */
    val houseNumberMaxAccuracyMeters: Float = 10f,
    val houseNumberMaxSpreadMeters: Float = 15f
)

/**
 * Keeps the displayed address calm while the phone is stationary. Reverse geocoding of the same desk can
 * return different house numbers on every call, so:
 * - house numbers are hidden unless the user opted in and two consecutive lookups near each other agree;
 * - a different street-level address replaces the current one only after real movement, or after it is
 *   returned twice in a row and the hold window has passed.
 * A small reported GPS accuracy alone never makes a house number trustworthy.
 */
class AddressStabilizer(private val policy: AddressStabilityPolicy = AddressStabilityPolicy()) {
    private var displayed: String? = null
    private var displayedStreetLevel: String? = null
    private var anchor: LocationStamp? = null
    private var displayedAtMillis = 0L
    private var pending: String? = null
    private var previousLookup: Pair<AddressParts, LocationStamp>? = null

    val current: String? get() = displayed

    fun onLookup(parts: AddressParts, at: LocationStamp, nowMillis: Long, showHouseNumbers: Boolean): String? {
        val streetLevel = AddressFormatter.format(parts, includeHouseNumber = false) ?: return displayed
        val houseConfirmed = showHouseNumbers && isHouseNumberConfirmed(parts, at)
        previousLookup = parts to at
        val candidate = if (houseConfirmed) AddressFormatter.format(parts, includeHouseNumber = true) ?: streetLevel else streetLevel

        val currentAnchor = anchor
        val moved = currentAnchor == null || movedMeaningfully(currentAnchor, at)
        val accept = when {
            displayed == null || moved -> true
            streetLevel == displayedStreetLevel -> candidate != displayed && (houseConfirmed || !showHouseNumbers)
            pending == streetLevel && nowMillis - displayedAtMillis >= policy.holdMillis -> true
            else -> {
                pending = streetLevel
                false
            }
        }
        if (accept) {
            displayed = candidate
            displayedStreetLevel = streetLevel
            anchor = at
            displayedAtMillis = nowMillis
            pending = null
        }
        return displayed
    }

    fun reset() {
        displayed = null
        displayedStreetLevel = null
        anchor = null
        pending = null
        previousLookup = null
    }

    private fun isHouseNumberConfirmed(parts: AddressParts, at: LocationStamp): Boolean {
        val number = parts.houseNumber?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (!at.hasAccuracy || at.accuracyMeters > policy.houseNumberMaxAccuracyMeters) return false
        val (previousParts, previousAt) = previousLookup ?: return false
        return previousParts.houseNumber?.trim() == number &&
            previousParts.street == parts.street &&
            previousAt.distanceMetersTo(at) <= policy.houseNumberMaxSpreadMeters
    }

    private fun movedMeaningfully(from: LocationStamp, to: LocationStamp): Boolean {
        val noise = (from.accuracyMeters.takeIf { from.hasAccuracy } ?: 0f) + (to.accuracyMeters.takeIf { to.hasAccuracy } ?: 0f)
        return from.distanceMetersTo(to) > max(policy.minMovementMeters, noise)
    }
}
