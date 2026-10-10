package com.geostamp.camera.environment

/**
 * A normalized address that works for any country. Every field is optional, and nothing assumes a
 * particular national layout:
 *
 * - [houseNumber] and [street] are the house number and road.
 * - [building] is a named building or premises.
 * - [neighbourhood], [colony] and [subLocality] are a neighbourhood, residential area or suburb.
 * - [locality] is the city, town or village.
 * - [district] is a county or district, the platform's sub-admin area.
 * - [adminArea] is the state, province or region.
 * - [postalCode], [country] and [countryCode] are the postcode, country name and ISO 3166 code.
 * - [addressLines] is the provider's own formatted address, which already follows local conventions.
 */
data class AddressParts(
    val houseNumber: String? = null,
    val street: String? = null,
    val subLocality: String? = null,
    val locality: String? = null,
    val adminArea: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val building: String? = null,
    val colony: String? = null,
    val neighbourhood: String? = null,
    val district: String? = null,
    val addressLines: List<String> = emptyList(),
    val countryCode: String? = null
)

enum class AddressDetail { DETAILED, STANDARD, SHORT }

object AddressFormatter {
    fun format(parts: AddressParts, detail: AddressDetail = AddressDetail.DETAILED): String? {
        val region = listOfNotNull(parts.adminArea.clean(), parts.postalCode.clean().takeUnless { detail == AddressDetail.SHORT })
            .joinToString(" ").ifEmpty { null }
        val components = listOfNotNull(
            parts.houseNumber.clean().takeIf { detail == AddressDetail.DETAILED },
            parts.building.clean().takeIf { detail == AddressDetail.DETAILED },
            parts.street.clean().takeIf { detail == AddressDetail.DETAILED },
            parts.colony.clean().takeUnless { detail == AddressDetail.SHORT },
            parts.neighbourhood.clean().takeUnless { detail == AddressDetail.SHORT },
            parts.subLocality.clean(), parts.locality.clean(),
            parts.district.clean().takeIf { detail == AddressDetail.DETAILED }, region, parts.country.clean()
        )
        val supplied = parts.addressLines.flatMap { it.split(',') }.mapNotNull { it.clean() }
        if (detail == AddressDetail.SHORT) return unique(components)
        val excluded = listOfNotNull(parts.houseNumber.clean(), parts.building.clean(), parts.street.clean(), parts.district.clean())
        val pieces = supplied.filter { piece ->
            detail == AddressDetail.DETAILED ||
                (piece.any(Char::isLetter) && excluded.none { containsComponent(piece, it) })
        }.toMutableList()
        components.forEachIndexed { index, component ->
            val regionPresent = component == region && listOfNotNull(parts.adminArea.clean(), parts.postalCode.clean())
                .all { value -> pieces.any { containsComponent(it, value) } }
            if (!regionPresent && pieces.none { containsComponent(it, component) }) {
                val next = pieces.indexOfFirst { piece -> components.drop(index + 1).any { containsComponent(piece, it) } }
                if (next < 0) pieces += component else pieces.add(next, component)
            }
        }
        return unique(pieces)
    }

    fun completeness(parts: AddressParts): Int = format(parts)?.split(Regex("[,\\s]+"))?.count(String::isNotEmpty) ?: 0

    fun isEnrichment(current: AddressParts, candidate: AddressParts): Boolean {
        if (!compatible(current, candidate) || completeness(candidate) <= completeness(current)) return false
        val next = format(candidate)?.split(',')?.map(String::trim).orEmpty()
        return format(current)?.split(',')?.map(String::trim).orEmpty().all { component ->
            next.any { containsComponent(it, component) }
        }
    }

    fun compatible(first: AddressParts, second: AddressParts): Boolean =
        listOf(first.houseNumber to second.houseNumber, first.street to second.street,
            first.building to second.building, first.colony to second.colony,
            first.neighbourhood to second.neighbourhood, first.subLocality to second.subLocality,
            first.locality to second.locality, first.district to second.district,
            first.adminArea to second.adminArea, first.postalCode to second.postalCode,
            first.country to second.country).all { (left, right) ->
            left.clean() == null || right.clean() == null || left?.trim().equals(right?.trim(), ignoreCase = true)
        }

    private fun containsComponent(piece: String, component: String): Boolean =
        piece.equals(component, ignoreCase = true) ||
            // Chinese, Japanese, Thai and similar scripts have no spaces between words, so word boundaries never match.
            (component.any(::isUnspacedScript) && piece.contains(component, ignoreCase = true)) ||
            Regex("(?<![\\p{L}\\p{N}])${Regex.escape(component)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(piece)

    private fun isUnspacedScript(char: Char): Boolean =
        Character.UnicodeScript.of(char.code) in UNSPACED_SCRIPTS

    private val UNSPACED_SCRIPTS = setOf(
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.THAI, Character.UnicodeScript.LAO, Character.UnicodeScript.KHMER, Character.UnicodeScript.MYANMAR
    )

    private fun unique(pieces: List<String>): String? = pieces.fold(mutableListOf<String>()) { result, piece ->
        if (result.none { it.equals(piece, ignoreCase = true) }) result += piece
        result
    }.joinToString(", ").ifEmpty { null }

    private fun String?.clean(): String? =
        this?.trim()?.trim(',', ';', ' ')?.takeIf { it.isNotEmpty() && it.lowercase() !in PLACEHOLDERS && it.any(Char::isLetterOrDigit) }

    /** Values some geocoders return instead of leaving a field empty. They are never shown. */
    private val PLACEHOLDERS = setOf("null", "unknown", "unnamed road", "unnamed", "n/a", "none", "undefined")
}
