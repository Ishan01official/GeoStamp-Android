package com.geostamp.camera.camera

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPoint
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geostamp.camera.R
import com.geostamp.camera.appContainer
import com.geostamp.camera.capture.CaptureMode
import com.geostamp.camera.capture.CapturedImageDecoder
import com.geostamp.camera.capture.LensFacing
import com.geostamp.camera.capture.SaveOptions
import com.geostamp.camera.capture.StampRequest
import com.geostamp.camera.location.LocationStamp
import com.geostamp.camera.location.LocationUpdate
import com.geostamp.camera.settings.AppSettings
import com.geostamp.camera.settings.CameraSettings
import com.geostamp.camera.stamps.StampData
import com.geostamp.camera.stamps.StampStyle
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Transient capture state, separate from persisted settings and fast-changing sensor flows. */
data class CaptureUiState(
    val mode: CaptureMode = CaptureMode.PHOTO,
    val lens: LensFacing = LensFacing.BACK,
    val hasFrontCamera: Boolean = false,
    val hasFlashUnit: Boolean = false,
    val countdown: Int? = null,
    val isProcessing: Boolean = false,
    val recordingSeconds: Long? = null,
    val lastCapture: LastCapture? = null,
    val lastCaptureInfo: String? = null,
    val cameraReady: Boolean = false
)

data class LastCapture(val uri: Uri, val thumbnail: Bitmap?, val isVideo: Boolean)

sealed interface CameraEvent {
    data class Saved(val uri: Uri, val isVideo: Boolean) : CameraEvent
    data class Failed(val messageRes: Int, val detail: String? = null) : CameraEvent
    data object ShutterFeedback : CameraEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val settingsRepository = container.settingsRepository
    private val locationRepository = container.locationRepository
    private val compassRepository = container.compassRepository
    private val environment = container.environmentRepository
    private val captureExecutor = Executors.newSingleThreadExecutor()

    val session = CameraSession(application)

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val location: StateFlow<LocationUpdate?> = locationRepository.updates
    val compass = compassRepository.updates

    private val _capture = MutableStateFlow(CaptureUiState())
    val capture: StateFlow<CaptureUiState> = _capture.asStateFlow()

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events: Flow<CameraEvent> = _events.receiveAsFlow()

    private val logo = MutableStateFlow<Bitmap?>(null)
    private val overlaySize = MutableStateFlow<Pair<Int, Int>?>(null)
    private var countdownJob: Job? = null
    private var recording: Recording? = null

    /** The stamp exactly as it would be burned in right now, rendered at preview scale. */
    val liveStamp: StateFlow<Bitmap?> = combine(
        overlaySize, settings.filterNotNull(), location, compass, environment.snapshot, logo, clockTicks()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        LiveStampInput(
            size = values[0] as Pair<Int, Int>?,
            settings = values[1] as AppSettings,
            logo = values[5] as Bitmap?,
            nowMillis = values[6] as Long
        )
    }
        .conflate()
        .mapLatest { input -> renderLiveStamp(input) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    private data class LiveStampInput(val size: Pair<Int, Int>?, val settings: AppSettings, val logo: Bitmap?, val nowMillis: Long)

    init {
        viewModelScope.launch {
            settings.filterNotNull().map { it.stamp.logoPath }.distinctUntilChanged().collect { path ->
                logo.value = withContext(Dispatchers.IO) { container.stampResources.loadLogo(path) }
            }
        }
        viewModelScope.launch {
            settings.filterNotNull().map { it.camera }.distinctUntilChanged().collect { applyCameraConfig() }
        }
        viewModelScope.launch {
            combine(location, settings.filterNotNull()) { update, appSettings -> update to appSettings.services }
                .collect { (update, services) ->
                    val fix = (update as? LocationUpdate.Available)?.location
                    compassRepository.setReferenceLocation(fix)
                    if (fix != null && services.anyEnabled) environment.onLocation(fix, services)
                }
        }
        viewModelScope.launch {
            container.galleryRepository.latestCapture()?.let { latest ->
                if (_capture.value.lastCapture == null) {
                    _capture.update { it.copy(lastCapture = LastCapture(latest.uri, latest.thumbnail, latest.isVideo)) }
                }
            }
        }
        session.onInitialized {
            _capture.update {
                it.copy(
                    hasFrontCamera = session.hasLens(LensFacing.FRONT),
                    hasFlashUnit = session.hasFlashUnit(),
                    cameraReady = true
                )
            }
        }
    }

    fun onScreenStarted() {
        locationRepository.start()
        compassRepository.start()
    }

    fun onScreenStopped() {
        locationRepository.stop()
        compassRepository.stop()
        cancelCountdown()
        stopRecording()
    }

    fun onPermissionsChanged() {
        locationRepository.start()
    }

    fun onOverlaySize(width: Int, height: Int) {
        overlaySize.value = if (width > 0 && height > 0) width to height else null
    }

    fun updateCameraSettings(transform: (CameraSettings) -> CameraSettings) {
        viewModelScope.launch { settingsRepository.update { it.copy(camera = transform(it.camera)) } }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun setMode(mode: CaptureMode) {
        if (_capture.value.recordingSeconds != null || mode == _capture.value.mode) return
        cancelCountdown()
        _capture.update { it.copy(mode = mode) }
        applyCameraConfig()
    }

    fun switchLens() {
        val state = _capture.value
        if (state.recordingSeconds != null || !state.hasFrontCamera) return
        val next = if (state.lens == LensFacing.BACK) LensFacing.FRONT else LensFacing.BACK
        _capture.update { it.copy(lens = next) }
        applyCameraConfig()
        session.onInitialized { _capture.update { it.copy(hasFlashUnit = session.hasFlashUnit()) } }
    }

    fun setZoom(ratio: Float) = session.setZoomRatio(ratio)

    fun focus(point: MeteringPoint, onResult: (Boolean) -> Unit) = session.focus(point, onResult)

    fun setExposure(index: Int) = session.setExposureIndex(index)

    fun onShutter() {
        val state = _capture.value
        when {
            state.mode == CaptureMode.VIDEO -> if (state.recordingSeconds == null) startRecording() else stopRecording()
            state.countdown != null -> cancelCountdown()
            state.isProcessing -> Unit
            else -> startPhotoCapture()
        }
    }

    private fun startPhotoCapture() {
        val seconds = settings.value?.camera?.timer?.seconds ?: 0
        if (seconds == 0) {
            capturePhoto()
            return
        }
        countdownJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                _capture.update { it.copy(countdown = remaining) }
                delay(ONE_SECOND)
            }
            _capture.update { it.copy(countdown = null) }
            capturePhoto()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _capture.update { it.copy(countdown = null) }
    }

    private fun capturePhoto() {
        val appSettings = settings.value ?: return
        val capturedAt = System.currentTimeMillis()
        val fix = usableLocation(appSettings, capturedAt)
        val nearby = environment.forCapture(fix)
        val request = StampRequest(
            data = StampData(
                capturedAtMillis = capturedAt,
                location = fix,
                heading = compassRepository.latestReading,
                address = nearby.address?.value,
                weather = nearby.weather?.value
            ),
            preferences = appSettings.stamp,
            map = nearby.map?.value,
            logo = logo.value
        )
        val options = SaveOptions(appSettings.storage.jpegQuality, appSettings.storage.saveOriginal)
        _capture.update { it.copy(isProcessing = true) }
        _events.trySend(CameraEvent.ShutterFeedback)

        session.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val decoded = runCatching {
                    image.use {
                        val original = if (options.saveOriginal) CapturedImageDecoder.jpegBytes(it) else null
                        Triple(CapturedImageDecoder.decodeUpright(it), original, it.imageInfo.rotationDegrees)
                    }
                }
                decoded.onSuccess { (upright, original, rotation) -> processInBackground(upright, original, rotation, request, options) }
                    .onFailure { fail(R.string.error_capture_failed, it) }
            }

            override fun onError(exception: ImageCaptureException) = fail(R.string.error_capture_failed, exception)
        })
    }

    /** Runs in the application scope so a photo is still saved if the user leaves the screen. */
    private fun processInBackground(
        upright: Bitmap,
        original: ByteArray?,
        originalRotation: Int,
        request: StampRequest,
        options: SaveOptions
    ) {
        container.applicationScope.launch(Dispatchers.Default) {
            runCatching { container.photoProcessor.process(upright, original, originalRotation, request, options) }
                .onSuccess { photo ->
                    _capture.update {
                        it.copy(
                            isProcessing = false,
                            lastCapture = LastCapture(photo.uri, photo.thumbnail, isVideo = false),
                            lastCaptureInfo = "${photo.width}×${photo.height}"
                        )
                    }
                    _events.trySend(CameraEvent.Saved(photo.uri, isVideo = false))
                }
                .onFailure { fail(R.string.error_save_failed, it) }
        }
    }

    private fun fail(messageRes: Int, error: Throwable) {
        Log.e(TAG, "Capture failed", error)
        _capture.update { it.copy(isProcessing = false) }
        _events.trySend(CameraEvent.Failed(messageRes, error.message))
    }

    /** Never stamps stale or imprecise coordinates as if they were current. */
    private fun usableLocation(appSettings: AppSettings, nowMillis: Long): LocationStamp? =
        locationRepository.latestLocation?.takeIf {
            it.isFresh(nowMillis, appSettings.location.maxAgeSeconds * 1000L) &&
                it.isAccurateEnough(appSettings.location.maxAccuracyMeters.toFloat())
        }

    private fun startRecording() {
        val withAudio = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        recording = runCatching {
            session.startRecording(withAudio) { event -> onRecordEvent(event) }
        }.onFailure { fail(R.string.error_video_failed, it) }.getOrNull()
        if (recording != null) _capture.update { it.copy(recordingSeconds = 0) }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
    }

    private fun onRecordEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Status -> _capture.update {
                it.copy(recordingSeconds = event.recordingStats.recordedDurationNanos / 1_000_000_000L)
            }
            is VideoRecordEvent.Finalize -> {
                _capture.update { it.copy(recordingSeconds = null) }
                recording = null
                if (event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE) {
                    _events.trySend(CameraEvent.Failed(R.string.error_video_failed, event.cause?.message))
                } else {
                    val uri = event.outputResults.outputUri
                    viewModelScope.launch {
                        val thumbnail = container.galleryRepository.thumbnail(uri)
                        _capture.update { it.copy(lastCapture = LastCapture(uri, thumbnail, isVideo = true)) }
                    }
                    _events.trySend(CameraEvent.Saved(uri, isVideo = true))
                }
            }
            else -> Unit
        }
    }

    private fun applyCameraConfig() {
        val camera = settings.value?.camera ?: return
        val state = _capture.value
        session.apply(CameraConfig(state.mode, state.lens, camera.aspectRatio, camera.resolution, camera.flashMode))
    }

    private fun renderLiveStamp(input: LiveStampInput): Bitmap? {
        val (width, height) = input.size ?: return null
        if (!input.settings.camera.liveStampPreview || !input.settings.stamp.enabled) return null
        val fix = usableLocation(input.settings, input.nowMillis)
        val nearby = environment.forCapture(fix)
        val scale = (LIVE_STAMP_MAX_EDGE.toFloat() / maxOf(width, height)).coerceAtMost(1f)
        val bitmap = Bitmap.createBitmap((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val data = StampData(input.nowMillis, fix, compassRepository.latestReading, nearby.address?.value, nearby.weather?.value)
        val resources = container.stampResources
        val content = resources.contentBuilder().build(data, input.settings.stamp, nearby.map != null, input.logo != null)
        resources.renderer().render(
            Canvas(bitmap), bitmap.width, bitmap.height, content,
            StampStyle.from(input.settings.stamp), nearby.map?.value, input.logo
        )
        return bitmap
    }

    private fun clockTicks(): Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(ONE_SECOND - now % ONE_SECOND)
        }
    }

    override fun onCleared() {
        stopRecording()
        session.unbind()
        captureExecutor.shutdown()
        super.onCleared()
    }

    private companion object {
        const val TAG = "CameraViewModel"
        const val ONE_SECOND = 1_000L
        const val STOP_TIMEOUT = 2_000L
        const val LIVE_STAMP_MAX_EDGE = 1280
    }
}
