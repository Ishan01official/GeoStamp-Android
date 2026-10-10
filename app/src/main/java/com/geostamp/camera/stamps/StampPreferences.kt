package com.geostamp.camera.stamps

/** User-facing stamp configuration. Online-only fields stay opt-in through [com.geostamp.camera.settings.OnlineServices]. */
data class StampPreferences(
    val enabled: Boolean = true,
    val template: StampTemplate = StampTemplate.MAP_CARD,
    val fieldsByTemplate: Map<StampTemplate, StampFields> = StampTemplate.defaultFieldMap(),
    val position: StampPosition = StampPosition.BOTTOM,
    val fontScale: Float = 1f,
    val textColor: StampTextColor = StampTextColor.WHITE,
    val backgroundOpacity: Float = DEFAULT_BACKGROUND_OPACITY,
    val customText: String = "",
    val logoPath: String? = null,
    val dateFormat: StampDateFormat = StampDateFormat.LONG,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val writeExifLocation: Boolean = false
) {
    init {
        require(fontScale in MIN_FONT_SCALE..MAX_FONT_SCALE)
        require(backgroundOpacity in 0f..1f)
    }

    /** Field selection for the active template, including the user's per-template overrides. */
    val fields: StampFields get() = fieldsByTemplate[template] ?: template.defaultFields

    fun withFields(fields: StampFields): StampPreferences =
        copy(fieldsByTemplate = fieldsByTemplate + (template to fields))

    companion object {
        const val MIN_FONT_SCALE = 0.6f
        const val MAX_FONT_SCALE = 1.8f
        const val DEFAULT_BACKGROUND_OPACITY = 0.6f
    }
}

enum class StampPosition { BOTTOM, TOP }

enum class StampTextColor(val argb: Int) {
    WHITE(0xFFFFFFFF.toInt()),
    WARM(0xFFFFF1D6.toInt()),
    YELLOW(0xFFFFD54F.toInt()),
    MINT(0xFFB9F6CA.toInt()),
    SKY(0xFFB3E5FC.toInt())
}

enum class StampDateFormat(val pattern: String) {
    LONG("d MMMM yyyy · HH:mm:ss"),
    SHORT("dd/MM/yyyy HH:mm"),
    US("MM/dd/yyyy h:mm a"),
    ISO("yyyy-MM-dd HH:mm:ss")
}

enum class CoordinateFormat { DECIMAL, DMS }

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }
