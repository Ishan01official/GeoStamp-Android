package com.geostamp.camera.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geostamp.camera.appContainer
import com.geostamp.camera.location.LocationStamp
import com.geostamp.camera.sensors.CompassAccuracy
import com.geostamp.camera.sensors.CompassReading
import com.geostamp.camera.stamps.StampData
import com.geostamp.camera.stamps.StampStyle
import com.geostamp.camera.stamps.WeatherCondition
import com.geostamp.camera.stamps.WeatherReading
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val repository = container.settingsRepository

    val settings: StateFlow<AppSettings?> = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val logo = settings.filterNotNull().map { it.stamp.logoPath }.distinctUntilChanged()
        .map { path -> container.stampResources.loadLogo(path) }
        .flowOn(Dispatchers.IO)

    /** Rendered by the same engine as saved photos, so the preview matches the output. */
    val stampPreview: StateFlow<Bitmap?> = combine(settings.filterNotNull(), logo, container.environmentRepository.snapshot) { s, l, _ -> s to l }
        .mapLatest { (appSettings, logoBitmap) -> renderPreview(appSettings, logoBitmap) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun clearMapCache() = container.environmentRepository.clearMapCache()

    /** Copies the picked image into private storage so the stamp never depends on external files. */
    fun importLogo(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
                    val scale = (LOGO_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
                    val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                    val file = File(getApplication<Application>().filesDir, "stamp_logo.png")
                    file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    file.absolutePath
                }.getOrNull()
            }
            if (path != null) update { it.copy(stamp = it.stamp.copy(logoPath = path)) }
        }
    }

    fun removeLogo() {
        val path = settings.value?.stamp?.logoPath
        update { it.copy(stamp = it.stamp.copy(logoPath = null)) }
        if (path != null) viewModelScope.launch(Dispatchers.IO) { File(path).delete() }
    }

    private fun renderPreview(appSettings: AppSettings, logoBitmap: Bitmap?): Bitmap {
        val bitmap = sampleScene()
        val live = container.locationRepository.latestLocation
        val nearby = container.environmentRepository.forCapture(live)
        val data = StampData(
            capturedAtMillis = System.currentTimeMillis(),
            location = live ?: SAMPLE_LOCATION,
            heading = container.compassRepository.latestReading ?: SAMPLE_HEADING,
            address = nearby.address?.value ?: SAMPLE_ADDRESS,
            weather = nearby.weather?.value ?: SAMPLE_WEATHER.copy(observedAtMillis = System.currentTimeMillis())
        )
        val map = nearby.map?.value ?: sampleMap()
        val resources = container.stampResources
        val content = resources.contentBuilder().build(data, appSettings.stamp, mapAvailable = true, logoAvailable = logoBitmap != null)
        resources.renderer().render(Canvas(bitmap), bitmap.width, bitmap.height, content, StampStyle.from(appSettings.stamp), map, logoBitmap)
        return bitmap
    }

    /** A neutral sky-and-ground gradient so the preview shows contrast without implying a real place. */
    private fun sampleScene(): Bitmap {
        val bitmap = Bitmap.createBitmap(PREVIEW_WIDTH, PREVIEW_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, PREVIEW_HEIGHT.toFloat(),
                intArrayOf(Color.rgb(116, 150, 186), Color.rgb(196, 210, 222), Color.rgb(120, 128, 104), Color.rgb(74, 82, 64)),
                floatArrayOf(0f, 0.55f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, PREVIEW_WIDTH.toFloat(), PREVIEW_HEIGHT.toFloat(), paint)
        return bitmap
    }

    /** Schematic map placeholder used only in this preview; real stamps use downloaded tiles or nothing. */
    private fun sampleMap(): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(236, 232, 224))
        val road = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 14f }
        canvas.drawLine(0f, 90f, 256f, 150f, road)
        canvas.drawLine(150f, 0f, 110f, 256f, road)
        val water = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(170, 211, 223) }
        canvas.drawCircle(220f, 220f, 60f, water)
        return bitmap
    }

    private companion object {
        const val LOGO_EDGE = 512
        const val PREVIEW_WIDTH = 900
        const val PREVIEW_HEIGHT = 1200
        val SAMPLE_LOCATION = LocationStamp(28.613900, 77.209000, 5f, 0L, 216.0, 0f, provider = "sample")
        val SAMPLE_HEADING = CompassReading(25f, 25f, CompassAccuracy.HIGH)
        const val SAMPLE_ADDRESS = "Janpath, New Delhi, Delhi 110001, India"
        val SAMPLE_WEATHER = WeatherReading(24.0, WeatherCondition.CLEAR, 0L)
    }
}
