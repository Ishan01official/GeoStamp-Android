package com.geostamp.camera.environment

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
    val addressLines: List<String> = emptyList()
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
            Regex("(?<![\\p{L}\\p{N}])${Regex.escape(component)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(piece)

    private fun unique(pieces: List<String>): String? = pieces.fold(mutableListOf<String>()) { result, piece ->
        if (result.none { it.equals(piece, ignoreCase = true) }) result += piece
        result
    }.joinToString(", ").ifEmpty { null }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
