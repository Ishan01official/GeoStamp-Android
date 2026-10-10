package com.geostamp.camera.environment

/**
 * Structured reverse-geocoding result. A street address is an interpretation of the measured coordinates,
 * so each part is kept separately and the least reliable one, the house number, can be withheld.
 */
data class AddressParts(
    val houseNumber: String? = null,
    val street: String? = null,
    val subLocality: String? = null,
    val locality: String? = null,
    val adminArea: String? = null,
    val postalCode: String? = null,
    val country: String? = null
)

object AddressFormatter {
    /** Street, area, city, state and country; the house number only when [includeHouseNumber]. */
    fun format(parts: AddressParts, includeHouseNumber: Boolean): String? {
        val street = listOfNotNull(
            parts.houseNumber?.trim()?.takeIf { includeHouseNumber && it.isNotEmpty() },
            parts.street?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString(" ").ifEmpty { null }
        val region = listOfNotNull(parts.adminArea.clean(), parts.postalCode.clean()).joinToString(" ").ifEmpty { null }
        val pieces = listOfNotNull(street, parts.subLocality.clean(), parts.locality.clean(), region, parts.country.clean())
        // Geocoders often repeat the same name at two levels, e.g. "Meerut, Meerut".
        return pieces.fold(mutableListOf<String>()) { acc, piece ->
            if (acc.none { it.equals(piece, ignoreCase = true) }) acc += piece
            acc
        }.joinToString(", ").ifEmpty { null }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
