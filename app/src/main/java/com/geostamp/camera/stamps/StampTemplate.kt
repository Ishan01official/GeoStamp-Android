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
    val logo: Boolean = true,
    /** A scannable code that opens the captured coordinates in a map app. Needs a location fix. */
    val qrCode: Boolean = false
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
    ),

    /** Place, date, coordinates and a QR code that opens the exact spot on another phone. */
    QR_LOCATION(
        defaultFields = StampFields(dateTime = true, address = true, coordinates = true, map = true, logo = false, qrCode = true),
        compact = false
    );

    companion object {
        fun defaultFieldMap(): Map<StampTemplate, StampFields> = entries.associateWith { it.defaultFields }
    }
}
