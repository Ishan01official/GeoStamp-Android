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
import com.geostamp.camera.address.AddressChoice
import com.geostamp.camera.address.AddressEditScope
import com.geostamp.camera.address.AddressOverride
import com.geostamp.camera.address.StampAddress
import com.geostamp.camera.appContainer
import com.geostamp.camera.capture.ProcessedPhoto
import com.geostamp.camera.dual.DualCameraSession
import com.geostamp.camera.dual.DualCaptureController
import com.geostamp.camera.stamps.StampPosition
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.geostamp.camera.capture.CaptureMode
import com.geostamp.camera.capture.CapturedImageDecoder
import com.geostamp.camera.capture.LensFacing
import com.geostamp.camera.capture.SaveOptions
import com.geostamp.camera.capture.StampRequest
import com.geostamp.camera.video.VideoJobState
import com.geostamp.camera.video.VideoLimits
import com.geostamp.camera.video.VideoOutcome
import com.geostamp.camera.video.VideoStampRequest
import com.geostamp.camera.video.VideoStampSample
import java.io.File
import com.geostamp.camera.location.LocationStamp
import com.geostamp.camera.location.LocationStabilizationPolicy
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
    val supportedModes: Set<CaptureMode> = setOf(CaptureMode.PHOTO, CaptureMode.VIDEO),
    val countdown: Int? = null,
    val isProcessing: Boolean = false,
    val recordingSeconds: Long? = null,
    val recordingHasAudio: Boolean = true,
    val lastCapture: LastCapture? = null,
    val lastCaptureInfo: String? = null,
    val cameraReady: Boolean = false
)

data class LastCapture(val uri: Uri, val thumbnail: Bitmap?, val isVideo: Boolean)

sealed interface CameraEvent {
    data class Saved(val uri: Uri, val isVideo: Boolean) : CameraEvent
    data class Failed(val messageRes: Int, val detail: String? = null) : CameraEvent

    /** The recording is in the gallery but without a stamp; [messageRes] says why. Never reported as success. */
    data class VideoSavedWithoutStamp(val uri: Uri, val messageRes: Int) : CameraEvent
    data object ShutterFeedback : CameraEvent

    /** Non-blocking notice: the photo was saved, but without coordinates. Coordinates are never invented. */
    data object SavedWithoutGps : CameraEvent

    /** The user picked a dual mode this phone cannot run; explain instead of faking it. */
    data object DualUnsupported : CameraEvent

    /** The phone advertised concurrent cameras but refused to start them. */
    data class DualFailed(val detail: String?) : CameraEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val settingsRepository = container.settingsRepository
    private val locationRepository = container.locationRepository
    private val compassRepository = container.compassRepository
    private val environment = container.environmentRepository
    private val addressOverrides = container.addressOverrides
    private val captureExecutor = Executors.newSingleThreadExecutor()

    val session = CameraSession(application)

    /** Simultaneous front + rear capture, used only where the hardware supports it. */
    val dual = DualCaptureController(DualCameraSession(application), container.photoProcessor) {
        container.cameraCapabilityRepository.capabilities()
    }

    /** Post-recording stamping progress, shared with the app-wide video coordinator. */
    val videoJob: StateFlow<VideoJobState> = container.videoCapture.state

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val location: StateFlow<LocationUpdate?> = locationRepository.updates
    /** The single heading snapshot used by both the top bar and the stamp. */
    val heading = compassRepository.heading
    val hasCompass: Boolean get() = compassRepository.capabilities.hasCompass
    val addressOverride: StateFlow<AddressOverride?> = addressOverrides.override

    /** The automatically detected address for the current stable location, shown in the edit dialog. */
    val detectedAddress: StateFlow<String?> = combine(location, environment.snapshot) { update, _ ->
        environment.forCapture((update as? LocationUpdate.Available)?.location).address?.value
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    /**
     * True when location access exists but no fix is usable for stamping yet. Drives the small
     * "GPS not ready" hint near the shutter; capture is still allowed.
     */
    val gpsNotReady: StateFlow<Boolean> = combine(location, settings.filterNotNull(), clockTicks()) { update, appSettings, now ->
        when (update) {
            null, LocationUpdate.PermissionDenied -> false
            else -> locationRepository.latestLocation?.let {
                it.isFresh(now, appSettings.location.maxAgeSeconds * 1000L) &&
                    it.isAccurateEnough(appSettings.location.maxAccuracyMeters.toFloat())
            } != true
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), false)

    fun setAddressOverride(text: String, scope: AddressEditScope) = addressOverrides.set(text, scope)

    fun restoreDetectedAddress() = addressOverrides.restoreDetected()

    private val _capture = MutableStateFlow(CaptureUiState())
    val capture: StateFlow<CaptureUiState> = _capture.asStateFlow()

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events: Flow<CameraEvent> = _events.receiveAsFlow()

    private val logo = MutableStateFlow<Bitmap?>(null)
    private val overlaySize = MutableStateFlow<Pair<Int, Int>?>(null)
    private var countdownJob: Job? = null
    private var recording: Recording? = null
    private var recordingStartedAtMillis: Long = 0L
    private var videoMetadataJob: Job? = null
    private val videoStampSamples = mutableListOf<VideoStampSample>()

    /** The stamp exactly as it would be burned in right now, rendered at preview scale. */
    val liveStamp: StateFlow<Bitmap?> = combine(
        overlaySize, settings.filterNotNull(), location, heading, environment.snapshot, logo, clockTicks(), addressOverrides.override
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
            settings.filterNotNull().map { it.location }.distinctUntilChanged().collect { locationSettings ->
                locationRepository.setPolicy(
                    LocationStabilizationPolicy(
                        maxAccuracyMeters = locationSettings.maxAccuracyMeters.toFloat(),
                        maxAgeMillis = locationSettings.maxAgeSeconds * 1000L,
                        displayRefresh = locationSettings.displayRefresh
                    )
                )
                compassRepository.setSmoothing(locationSettings.compassSmoothing)
            }
        }
        viewModelScope.launch {
            combine(location, settings.filterNotNull()) { update, appSettings -> update to appSettings }
                .collect { (update, appSettings) ->
                    val fix = (update as? LocationUpdate.Available)?.location
                    compassRepository.setReferenceLocation(fix)
                    if (fix != null && appSettings.services.anyEnabled) {
                        environment.onLocation(fix, appSettings.services, appSettings.location.showHouseNumbers)
                    }
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
            val cameraCapabilities = container.cameraCapabilityRepository.capabilities()
            val supportedModes = buildSet {
                add(CaptureMode.PHOTO)
                add(CaptureMode.VIDEO)
                if (cameraCapabilities.dualPhotoSupported) add(CaptureMode.DUAL_PHOTO)
                if (cameraCapabilities.dualVideoSupported) add(CaptureMode.DUAL_VIDEO)
            }
            _capture.update {
                it.copy(hasFrontCamera = session.hasLens(LensFacing.FRONT), supportedModes = supportedModes)
            }
        }
        viewModelScope.launch {
            combine(session.boundCamera, _capture.map { it.lens }.distinctUntilChanged()) { bound, lens -> bound to lens }
                .collect { (bound, lens) ->
                    _capture.update {
                        it.copy(
                            hasFlashUnit = FlashAvailability.resolve(lens, bound),
                            cameraReady = bound != null && bound.lens == lens
                        )
                    }
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
        if (mode !in _capture.value.supportedModes) {
            _events.trySend(CameraEvent.DualUnsupported)
            return
        }
        cancelCountdown()
        _capture.update { it.copy(mode = mode) }
        applyCameraConfig()
    }

    fun switchLens() {
        val state = _capture.value
        if (state.recordingSeconds != null || !state.hasFrontCamera || state.mode.isDual()) return
        val next = if (state.lens == LensFacing.BACK) LensFacing.FRONT else LensFacing.BACK
        _capture.update { it.copy(lens = next) }
        applyCameraConfig()
    }

    fun setZoom(ratio: Float) = session.setZoomRatio(ratio)

    fun focus(point: MeteringPoint, onResult: (Boolean) -> Unit) = session.focus(point, onResult)

    fun setExposure(index: Int) = session.setExposureIndex(index)

    fun onShutter() {
        val state = _capture.value
        when {
            state.mode == CaptureMode.DUAL_PHOTO -> if (!state.isProcessing) captureDualPhoto()
            state.mode.isVideoMode() -> if (state.recordingSeconds == null) startRecording() else stopRecording()
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
        val (request, options) = photoRequest() ?: return
        _capture.update { it.copy(isProcessing = true) }
        _events.trySend(CameraEvent.ShutterFeedback)
        if (request.data.location == null && location.value !is LocationUpdate.PermissionDenied) _events.trySend(CameraEvent.SavedWithoutGps)

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

    /** Reads the stamp data for "now" and consumes a one-capture address edit. */
    private fun photoRequest(): Pair<StampRequest, SaveOptions>? {
        val appSettings = settings.value ?: return null
        val capturedAt = System.currentTimeMillis()
        val fix = usableLocation(appSettings, capturedAt)
        val nearby = environment.forCapture(fix)
        val address = stampAddress(nearby.address?.value)
        addressOverrides.onCaptureCompleted()
        val request = StampRequest(
            data = StampData(
                capturedAtMillis = capturedAt,
                location = fix,
                heading = compassRepository.heading.value,
                address = address.text,
                weather = nearby.weather?.value,
                addressSource = address.source
            ),
            preferences = appSettings.stamp,
            map = nearby.map?.value,
            logo = logo.value
        )
        return request to SaveOptions(appSettings.storage.jpegQuality, appSettings.storage.saveOriginal)
    }

    private fun captureDualPhoto() {
        val (request, options) = photoRequest() ?: return
        _capture.update { it.copy(isProcessing = true) }
        _events.trySend(CameraEvent.ShutterFeedback)
        if (request.data.location == null && location.value !is LocationUpdate.PermissionDenied) _events.trySend(CameraEvent.SavedWithoutGps)
        container.applicationScope.launch {
            runCatching { dual.captureAndSave(captureExecutor, request, options) }
                .onSuccess { photo -> onPhotoSaved(photo) }
                .onFailure { fail(R.string.error_capture_failed, it) }
        }
    }

    val dualUnsupportedReason: DualUnsupportedReason?
        get() = container.cameraCapabilityRepository.capabilities().dualUnsupportedReason

    /** Binds both cameras for a dual mode. On a runtime refusal the app returns to normal photo mode. */
    suspend fun bindDual(owner: LifecycleOwner, main: Preview.SurfaceProvider, front: Preview.SurfaceProvider?): Boolean {
        val mode = _capture.value.mode
        val stampPosition = settings.value?.stamp?.position ?: StampPosition.BOTTOM
        session.unbind()
        val ok = dual.bind(mode, owner, main, front, stampPosition)
        container.cameraCapabilityRepository.recordDualResult(mode.name, if (ok) "started" else "failed: ${dual.state.value.runtimeFailure}")
        if (!ok) {
            _events.trySend(CameraEvent.DualFailed(dual.state.value.runtimeFailure))
            _capture.update { it.copy(mode = CaptureMode.PHOTO) }
        }
        return ok
    }

    /** Leaves concurrent mode and gives the cameras back to the single-camera controller. */
    fun leaveDual(owner: LifecycleOwner) {
        dual.unbind()
        session.bind(owner)
        applyCameraConfig()
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
                .onSuccess(::onPhotoSaved)
                .onFailure { fail(R.string.error_save_failed, it) }
        }
    }

    private fun onPhotoSaved(photo: ProcessedPhoto) {
        _capture.update {
            it.copy(
                isProcessing = false,
                lastCapture = LastCapture(photo.uri, photo.thumbnail, isVideo = false),
                lastCaptureInfo = "${photo.width}×${photo.height}"
            )
        }
        _events.trySend(CameraEvent.Saved(photo.uri, isVideo = false))
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

    private fun usableDisplayLocation(appSettings: AppSettings, nowMillis: Long): LocationStamp? =
        locationRepository.latestDisplayLocation?.takeIf {
            it.isFresh(nowMillis, appSettings.location.maxAgeSeconds * 1000L) &&
                it.isAccurateEnough(appSettings.location.maxAccuracyMeters.toFloat())
        }

    private fun startRecording() {
        val videos = container.videoCapture
        if (videos.state.value != VideoJobState.Idle) {
            _events.trySend(CameraEvent.Failed(R.string.error_video_busy))
            return
        }
        if (!videos.hasSpaceToRecord()) {
            _events.trySend(CameraEvent.Failed(R.string.error_video_low_storage))
            return
        }
        val withAudio = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val startedAt = System.currentTimeMillis()
        val file = videos.newRecordingFile(startedAt)
        recording = runCatching {
            val listener: (VideoRecordEvent) -> Unit = { event -> onRecordEvent(event, file) }
            if (_capture.value.mode == CaptureMode.DUAL_VIDEO) {
                dual.startRecording(file, VideoLimits.MAX_DURATION_MILLIS, withAudio, listener)
            } else {
                session.startRecording(file, VideoLimits.MAX_DURATION_MILLIS, withAudio, listener)
            }
        }.onFailure { fail(R.string.error_video_failed, it) }.getOrNull()
        if (recording != null) {
            recordingStartedAtMillis = startedAt
            startVideoMetadataSampling(startedAt)
            _capture.update { it.copy(recordingSeconds = 0, recordingHasAudio = withAudio) }
        }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
    }

    private fun onRecordEvent(event: VideoRecordEvent, file: File) {
        when (event) {
            // CameraX starts a few hundred ms after the button press; time the stamp from the real start.
            is VideoRecordEvent.Start -> {
                recordingStartedAtMillis = System.currentTimeMillis()
                startVideoMetadataSampling(recordingStartedAtMillis)
            }
            is VideoRecordEvent.Status -> _capture.update {
                it.copy(recordingSeconds = (event.recordingStats.recordedDurationNanos / 1_000_000_000L).coerceAtMost(VideoLimits.MAX_DURATION_SECONDS))
            }
            is VideoRecordEvent.Finalize -> {
                val samples = stopVideoMetadataSampling()
                addressOverrides.onCaptureCompleted()
                _capture.update { it.copy(recordingSeconds = null) }
                recording = null
                if (event.error != VideoRecordEvent.Finalize.ERROR_NONE) Log.w(TAG, "Recording finalized with code ${event.error}", event.cause)
                // A limit-reached or source-inactive finalize still produces a complete file; the coordinator checks it.
                processRecordedVideo(file, samples)
            }
            else -> Unit
        }
    }

    private fun startVideoMetadataSampling(startedAtMillis: Long) {
        videoMetadataJob?.cancel()
        videoStampSamples.clear()
        videoMetadataJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                videoStampSamples += videoStampSample(recordingStartedAtMillis, now)
                delay(ONE_SECOND - (now - recordingStartedAtMillis).mod(ONE_SECOND))
            }
        }
    }

    private fun stopVideoMetadataSampling(): List<VideoStampSample> {
        videoMetadataJob?.cancel()
        videoMetadataJob = null
        return videoStampSamples.toList()
    }

    private fun videoStampSample(startedAtMillis: Long, nowMillis: Long): VideoStampSample {
        val appSettings = settings.value
        val fix = appSettings?.let { usableDisplayLocation(it, nowMillis) }
        val nearby = environment.forCapture(fix)
        val address = stampAddress(nearby.address?.value)
        return VideoStampSample(
            elapsedMillis = (nowMillis - startedAtMillis).coerceAtLeast(0L),
            data = StampData(
                capturedAtMillis = nowMillis,
                location = fix,
                heading = compassRepository.heading.value,
                address = address.text,
                weather = nearby.weather?.value,
                addressSource = address.source
            ),
            map = nearby.map?.value,
            logo = logo.value
        )
    }

    private fun processRecordedVideo(file: File, samples: List<VideoStampSample>) {
        val appSettings = settings.value
        val startedAt = recordingStartedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val request = appSettings?.stamp?.takeIf { it.enabled }?.let { preferences ->
            VideoStampRequest(
                startedAtMillis = startedAt,
                preferences = preferences,
                samples = samples.ifEmpty { listOf(videoStampSample(startedAt, startedAt)) }
            )
        }
        container.videoCapture.process(file, request, keepOriginal = appSettings?.storage?.saveOriginal == true) { outcome ->
            onVideoOutcome(outcome)
        }
    }

    fun cancelVideoStamping() = container.videoCapture.cancel()

    private fun onVideoOutcome(outcome: VideoOutcome) {
        when (outcome) {
            is VideoOutcome.Stamped -> {
                viewModelScope.launch {
                    val thumbnail = container.galleryRepository.thumbnail(outcome.uri)
                    _capture.update {
                        it.copy(
                            lastCapture = LastCapture(outcome.uri, thumbnail, isVideo = true),
                            lastCaptureInfo = "${outcome.info.width}×${outcome.info.height} · ${outcome.info.durationMillis / 1000}s"
                        )
                    }
                }
                _events.trySend(CameraEvent.Saved(outcome.uri, isVideo = true))
            }
            is VideoOutcome.SavedWithoutStamp -> {
                viewModelScope.launch {
                    val thumbnail = container.galleryRepository.thumbnail(outcome.uri)
                    _capture.update { it.copy(lastCapture = LastCapture(outcome.uri, thumbnail, isVideo = true)) }
                }
                _events.trySend(CameraEvent.VideoSavedWithoutStamp(outcome.uri, outcome.reason.messageRes()))
            }
            is VideoOutcome.Failed -> _events.trySend(CameraEvent.Failed(outcome.reason.messageRes(), outcome.detail))
        }
    }

    private fun VideoOutcome.Reason.messageRes(): Int = when (this) {
        VideoOutcome.Reason.CANCELLED -> R.string.video_unstamped_cancelled
        VideoOutcome.Reason.LOW_STORAGE -> R.string.video_unstamped_low_storage
        VideoOutcome.Reason.STAMPING_FAILED, VideoOutcome.Reason.VERIFICATION_FAILED -> R.string.video_unstamped_failed
        VideoOutcome.Reason.SAVE_FAILED -> R.string.error_save_failed
        VideoOutcome.Reason.RECORDING_INVALID -> R.string.error_video_failed
    }

    private fun stampAddress(detected: String?): StampAddress = AddressChoice.effective(detected, addressOverrides.override.value)

    private fun applyCameraConfig() {
        val camera = settings.value?.camera ?: return
        val state = _capture.value
        if (state.mode.isDual()) return
        session.apply(CameraConfig(state.mode, state.lens, camera.aspectRatio, camera.resolution, camera.flashMode))
    }

    private fun renderLiveStamp(input: LiveStampInput): Bitmap? {
        val (width, height) = input.size ?: return null
        if (!input.settings.camera.liveStampPreview || !input.settings.stamp.enabled) return null
        val fix = usableDisplayLocation(input.settings, input.nowMillis)
        val nearby = environment.forCapture(fix)
        val scale = (LIVE_STAMP_MAX_EDGE.toFloat() / maxOf(width, height)).coerceAtMost(1f)
        val bitmap = Bitmap.createBitmap((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val address = stampAddress(nearby.address?.value)
        val data = StampData(input.nowMillis, fix, compassRepository.heading.value, address.text, nearby.weather?.value, address.source)
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
