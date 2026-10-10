package com.geostamp.camera.stamps

/** Which pieces of information a stamp shows. Each field only renders when backed by real data. */
data class StampFields(
    val dateTime: Boolean = true,
    val address: Boolean = false,
    val coordinates: Boolean = true,
    val accuracy: Boolean = false,
    val heading: Boolean = false,
    val altitude: Boolean = false,
    val speed: Boolean = false,
    val weather: Boolean = false,
    val map: Boolean = false,
    val customText: Boolean = true,
    val logo: Boolean = true
)

enum class StampTemplate(val defaultFields: StampFields, val compact: Boolean) {
    MINIMAL(
        defaultFields = StampFields(dateTime = true, coordinates = true),
        compact = true
    ),
    CLASSIC(
        defaultFields = StampFields(dateTime = true, address = true, coordinates = true, accuracy = true),
        compact = false
    ),
    MAP_CARD(
        defaultFields = StampFields(dateTime = true, address = true, coordinates = true, map = true),
        compact = false
    ),
    PROFESSIONAL(
        defaultFields = StampFields(
            dateTime = true,
            address = true,
            coordinates = true,
            accuracy = true,
            heading = true,
            altitude = true,
            speed = true,
            weather = true,
            map = true
        ),
        compact = false
    );

    companion object {
        fun defaultFieldMap(): Map<StampTemplate, StampFields> = entries.associateWith { it.defaultFields }
    }
}
