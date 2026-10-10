package com.geostamp.camera.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.geostamp.camera.capture.CaptureTimer
import com.geostamp.camera.capture.FlashMode
import com.geostamp.camera.capture.PhotoAspectRatio
import com.geostamp.camera.capture.PhotoResolution
import com.geostamp.camera.stamps.CoordinateFormat
import com.geostamp.camera.stamps.StampDateFormat
import com.geostamp.camera.stamps.StampFields
import com.geostamp.camera.stamps.StampPosition
import com.geostamp.camera.stamps.StampPreferences
import com.geostamp.camera.stamps.StampTemplate
import com.geostamp.camera.stamps.StampTextColor
import com.geostamp.camera.stamps.TemperatureUnit
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "geostamp_settings")

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toAppSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toAppSettings())
            preferences.write(updated)
        }
    }
}

private object Keys {
    val themeMode = stringPreferencesKey("appearance.theme")
    val dynamicColor = booleanPreferencesKey("appearance.dynamic_color")

    val flash = stringPreferencesKey("camera.flash")
    val timer = stringPreferencesKey("camera.timer")
    val aspect = stringPreferencesKey("camera.aspect")
    val resolution = stringPreferencesKey("camera.resolution")
    val grid = booleanPreferencesKey("camera.grid")
    val liveStamp = booleanPreferencesKey("camera.live_stamp")
    val tapToFocus = booleanPreferencesKey("camera.tap_to_focus")
    val simpleMode = booleanPreferencesKey("camera.simple_mode")

    val maxAccuracy = intPreferencesKey("location.max_accuracy")
    val maxAge = intPreferencesKey("location.max_age")
    val locationDisplayRefresh = stringPreferencesKey("location.display_refresh")
    val compassSmoothing = stringPreferencesKey("location.compass_smoothing")

    val stampEnabled = booleanPreferencesKey("stamp.enabled")
    val template = stringPreferencesKey("stamp.template")
    val position = stringPreferencesKey("stamp.position")
    val fontScale = floatPreferencesKey("stamp.font_scale")
    val textColor = stringPreferencesKey("stamp.text_color")
    val opacity = floatPreferencesKey("stamp.opacity")
    val customText = stringPreferencesKey("stamp.custom_text")
    val logoPath = stringPreferencesKey("stamp.logo_path")
    val dateFormat = stringPreferencesKey("stamp.date_format")
    val coordinateFormat = stringPreferencesKey("stamp.coordinate_format")
    val temperatureUnit = stringPreferencesKey("stamp.temperature_unit")
    val exifLocation = booleanPreferencesKey("privacy.exif_location")

    val address = booleanPreferencesKey("services.address")
    val mapTiles = booleanPreferencesKey("services.map_tiles")
    val weather = booleanPreferencesKey("services.weather")
    val mapLink = stringPreferencesKey("services.map_link")

    val saveOriginal = booleanPreferencesKey("storage.save_original")
    val jpegQuality = intPreferencesKey("storage.jpeg_quality")

    val cameraRequested = booleanPreferencesKey("onboarding.camera_requested")
    val locationPromptShown = booleanPreferencesKey("onboarding.location_prompt_shown")
    val locationRequested = booleanPreferencesKey("onboarding.location_requested")

    fun field(template: StampTemplate, name: String) =
        booleanPreferencesKey("stamp.fields.${template.name}.$name")
}

private inline fun <reified T : Enum<T>> Preferences.enumOf(key: Preferences.Key<String>, default: T): T =
    this[key]?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

private fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        appearance = AppearanceSettings(
            themeMode = enumOf(Keys.themeMode, defaults.appearance.themeMode),
            dynamicColor = this[Keys.dynamicColor] ?: defaults.appearance.dynamicColor
        ),
        camera = CameraSettings(
            flashMode = enumOf(Keys.flash, FlashMode.OFF),
            timer = enumOf(Keys.timer, CaptureTimer.OFF),
            aspectRatio = enumOf(Keys.aspect, PhotoAspectRatio.FOUR_THREE),
            resolution = enumOf(Keys.resolution, PhotoResolution.DEFAULT),
            gridEnabled = this[Keys.grid] ?: defaults.camera.gridEnabled,
            liveStampPreview = this[Keys.liveStamp] ?: defaults.camera.liveStampPreview,
            tapToFocus = this[Keys.tapToFocus] ?: defaults.camera.tapToFocus,
            simpleMode = this[Keys.simpleMode] ?: defaults.camera.simpleMode
        ),
        location = LocationSettings(
            maxAccuracyMeters = this[Keys.maxAccuracy] ?: defaults.location.maxAccuracyMeters,
            maxAgeSeconds = this[Keys.maxAge] ?: defaults.location.maxAgeSeconds,
            displayRefresh = enumOf(Keys.locationDisplayRefresh, defaults.location.displayRefresh),
            compassSmoothing = enumOf(Keys.compassSmoothing, defaults.location.compassSmoothing)
        ),
        stamp = readStamp(defaults.stamp),
        services = OnlineServices(
            addressLookup = this[Keys.address] ?: false,
            mapTiles = this[Keys.mapTiles] ?: false,
            weather = this[Keys.weather] ?: false,
            mapLinkProvider = enumOf(Keys.mapLink, MapLinkProvider.OPEN_STREET_MAP)
        ),
        storage = StorageSettings(
            saveOriginal = this[Keys.saveOriginal] ?: defaults.storage.saveOriginal,
            jpegQuality = (this[Keys.jpegQuality] ?: defaults.storage.jpegQuality)
                .coerceIn(StorageSettings.QUALITY_RANGE)
        ),
        onboarding = OnboardingState(
            cameraRequested = this[Keys.cameraRequested] ?: false,
            locationPromptShown = this[Keys.locationPromptShown] ?: false,
            locationRequested = this[Keys.locationRequested] ?: false
        )
    )
}

private fun Preferences.readStamp(defaults: StampPreferences): StampPreferences =
    StampPreferences(
        enabled = this[Keys.stampEnabled] ?: defaults.enabled,
        template = enumOf(Keys.template, defaults.template),
        fieldsByTemplate = StampTemplate.entries.associateWith { readFields(it) },
        position = enumOf(Keys.position, defaults.position),
        fontScale = (this[Keys.fontScale] ?: defaults.fontScale)
            .coerceIn(StampPreferences.MIN_FONT_SCALE, StampPreferences.MAX_FONT_SCALE),
        textColor = enumOf(Keys.textColor, defaults.textColor),
        backgroundOpacity = (this[Keys.opacity] ?: defaults.backgroundOpacity).coerceIn(0f, 1f),
        customText = this[Keys.customText] ?: defaults.customText,
        logoPath = this[Keys.logoPath]?.takeIf { it.isNotBlank() },
        dateFormat = enumOf(Keys.dateFormat, StampDateFormat.LONG),
        coordinateFormat = enumOf(Keys.coordinateFormat, CoordinateFormat.DECIMAL),
        temperatureUnit = enumOf(Keys.temperatureUnit, TemperatureUnit.CELSIUS),
        writeExifLocation = this[Keys.exifLocation] ?: defaults.writeExifLocation
    )

private fun Preferences.readFields(template: StampTemplate): StampFields {
    val d = template.defaultFields
    fun flag(name: String, default: Boolean) = this[Keys.field(template, name)] ?: default
    return StampFields(
        dateTime = flag("date_time", d.dateTime),
        address = flag("address", d.address),
        coordinates = flag("coordinates", d.coordinates),
        accuracy = flag("accuracy", d.accuracy),
        heading = flag("heading", d.heading),
        altitude = flag("altitude", d.altitude),
        speed = flag("speed", d.speed),
        weather = flag("weather", d.weather),
        map = flag("map", d.map),
        customText = flag("custom_text", d.customText),
        logo = flag("logo", d.logo)
    )
}

private fun MutablePreferences.write(settings: AppSettings) {
    this[Keys.themeMode] = settings.appearance.themeMode.name
    this[Keys.dynamicColor] = settings.appearance.dynamicColor

    with(settings.camera) {
        this@write[Keys.flash] = flashMode.name
        this@write[Keys.timer] = timer.name
        this@write[Keys.aspect] = aspectRatio.name
        this@write[Keys.resolution] = resolution.name
        this@write[Keys.grid] = gridEnabled
        this@write[Keys.liveStamp] = liveStampPreview
        this@write[Keys.tapToFocus] = tapToFocus
        this@write[Keys.simpleMode] = simpleMode
    }

    this[Keys.maxAccuracy] = settings.location.maxAccuracyMeters
    this[Keys.maxAge] = settings.location.maxAgeSeconds
    this[Keys.locationDisplayRefresh] = settings.location.displayRefresh.name
    this[Keys.compassSmoothing] = settings.location.compassSmoothing.name

    with(settings.stamp) {
        this@write[Keys.stampEnabled] = enabled
        this@write[Keys.template] = template.name
        this@write[Keys.position] = position.name
        this@write[Keys.fontScale] = fontScale
        this@write[Keys.textColor] = textColor.name
        this@write[Keys.opacity] = backgroundOpacity
        this@write[Keys.customText] = customText
        if (logoPath == null) this@write.remove(Keys.logoPath) else this@write[Keys.logoPath] = logoPath
        this@write[Keys.dateFormat] = dateFormat.name
        this@write[Keys.coordinateFormat] = coordinateFormat.name
        this@write[Keys.temperatureUnit] = temperatureUnit.name
        this@write[Keys.exifLocation] = writeExifLocation
        fieldsByTemplate.forEach { (template, fields) -> this@write.writeFields(template, fields) }
    }

    with(settings.services) {
        this@write[Keys.address] = addressLookup
        this@write[Keys.mapTiles] = mapTiles
        this@write[Keys.weather] = weather
        this@write[Keys.mapLink] = mapLinkProvider.name
    }

    this[Keys.saveOriginal] = settings.storage.saveOriginal
    this[Keys.jpegQuality] = settings.storage.jpegQuality

    this[Keys.cameraRequested] = settings.onboarding.cameraRequested
    this[Keys.locationPromptShown] = settings.onboarding.locationPromptShown
    this[Keys.locationRequested] = settings.onboarding.locationRequested
}

private fun MutablePreferences.writeFields(template: StampTemplate, fields: StampFields) {
    this[Keys.field(template, "date_time")] = fields.dateTime
    this[Keys.field(template, "address")] = fields.address
    this[Keys.field(template, "coordinates")] = fields.coordinates
    this[Keys.field(template, "accuracy")] = fields.accuracy
    this[Keys.field(template, "heading")] = fields.heading
    this[Keys.field(template, "altitude")] = fields.altitude
    this[Keys.field(template, "speed")] = fields.speed
    this[Keys.field(template, "weather")] = fields.weather
    this[Keys.field(template, "map")] = fields.map
    this[Keys.field(template, "custom_text")] = fields.customText
    this[Keys.field(template, "logo")] = fields.logo
}
