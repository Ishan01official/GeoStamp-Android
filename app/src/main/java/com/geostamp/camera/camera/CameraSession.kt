package com.geostamp.camera.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExposureState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.MeteringPoint
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.geostamp.camera.capture.CaptureMode
import com.geostamp.camera.capture.FlashMode
import com.geostamp.camera.capture.LensFacing
import com.geostamp.camera.capture.MediaStoreWriter
import com.geostamp.camera.capture.PhotoAspectRatio
import com.geostamp.camera.capture.PhotoResolution
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Desired camera configuration; applied incrementally so unchanged values never trigger a rebind. */
data class CameraConfig(
    val mode: CaptureMode,
    val lens: LensFacing,
    val aspectRatio: PhotoAspectRatio,
    val resolution: PhotoResolution,
    val flashMode: FlashMode
)

/**
 * Owns the CameraX controller. CameraX handles lifecycle binding, device rotation for capture
 * (so landscape shots are saved upright) and use-case negotiation.
 */
class CameraSession(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private var applied: CameraConfig? = null

    val controller = LifecycleCameraController(appContext).apply {
        isPinchToZoomEnabled = false
        isTapToFocusEnabled = false
        imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        videoCaptureQualitySelector = QualitySelector.from(
            Quality.FHD,
            FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)
        )
    }

    val zoomState: LiveData<ZoomState> get() = controller.zoomState

    fun bind(owner: LifecycleOwner) = controller.bindToLifecycle(owner)

    fun unbind() = controller.unbind()

    fun onInitialized(listener: () -> Unit) =
        controller.initializationFuture.addListener(listener, mainExecutor)

    fun hasLens(lens: LensFacing): Boolean =
        runCatching { controller.hasCamera(lens.selector()) }.getOrDefault(lens == LensFacing.BACK)

    fun hasFlashUnit(): Boolean = controller.cameraInfo?.hasFlashUnit() ?: false

    fun exposureState(): ExposureState? = controller.cameraInfo?.exposureState

    fun apply(config: CameraConfig) {
        val previous = applied
        if (previous?.lens != config.lens) controller.cameraSelector = config.lens.selector()
        if (previous?.mode != config.mode) {
            controller.setEnabledUseCases(
                if (config.mode == CaptureMode.PHOTO) CameraController.IMAGE_CAPTURE else CameraController.VIDEO_CAPTURE
            )
        }
        val previewRatio = if (config.mode == CaptureMode.VIDEO) PhotoAspectRatio.SIXTEEN_NINE else config.aspectRatio
        val previousPreviewRatio = previous?.let {
            if (it.mode == CaptureMode.VIDEO) PhotoAspectRatio.SIXTEEN_NINE else it.aspectRatio
        }
        if (previousPreviewRatio != previewRatio) {
            controller.previewResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(previewRatio.strategy())
                .build()
        }
        if (previous?.aspectRatio != config.aspectRatio || previous.resolution != config.resolution) {
            controller.imageCaptureResolutionSelector = captureSelector(config.aspectRatio, config.resolution)
        }
        controller.imageCaptureFlashMode = config.flashMode.imageCaptureMode
        controller.enableTorch(config.mode == CaptureMode.VIDEO && config.flashMode == FlashMode.ON)
        applied = config
    }

    fun setZoomRatio(ratio: Float) {
        val state = controller.zoomState.value ?: return
        controller.setZoomRatio(ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio))
    }

    fun setExposureIndex(index: Int) {
        controller.cameraControl?.setExposureCompensationIndex(index)
    }

    fun focus(point: MeteringPoint, onResult: (Boolean) -> Unit) {
        val control = controller.cameraControl ?: return
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(FOCUS_RESET_SECONDS, TimeUnit.SECONDS)
            .build()
        val future = control.startFocusAndMetering(action)
        future.addListener(
            { onResult(runCatching { future.get().isFocusSuccessful }.getOrDefault(false)) },
            mainExecutor
        )
    }

    fun takePicture(executor: Executor, callback: ImageCapture.OnImageCapturedCallback) =
        controller.takePicture(executor, callback)

    /** Caller must have checked RECORD_AUDIO before passing [withAudio] = true. */
    @SuppressLint("MissingPermission")
    fun startRecording(withAudio: Boolean, listener: (VideoRecordEvent) -> Unit): Recording {
        val name = "GeoStamp_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, MediaStoreWriter.VIDEO_RELATIVE_PATH)
        }
        val options = MediaStoreOutputOptions.Builder(
            appContext.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()
        return controller.startRecording(options, AudioConfig.create(withAudio), mainExecutor) { listener(it) }
    }

    private fun LensFacing.selector(): CameraSelector =
        if (this == LensFacing.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    private fun PhotoAspectRatio.strategy(): AspectRatioStrategy =
        when (this) {
            PhotoAspectRatio.FOUR_THREE -> AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            PhotoAspectRatio.SIXTEEN_NINE -> AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        }

    private fun captureSelector(aspectRatio: PhotoAspectRatio, resolution: PhotoResolution): ResolutionSelector {
        val strategy = when (resolution) {
            PhotoResolution.HIGH -> ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
            PhotoResolution.DEFAULT -> bounded(aspectRatio, longEdge = 4096)
            PhotoResolution.BALANCED -> bounded(aspectRatio, longEdge = 2560)
        }
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectRatio.strategy())
            .setResolutionStrategy(strategy)
            .build()
    }

    private fun bounded(aspectRatio: PhotoAspectRatio, longEdge: Int): ResolutionStrategy {
        val shortEdge = when (aspectRatio) {
            PhotoAspectRatio.FOUR_THREE -> longEdge * 3 / 4
            PhotoAspectRatio.SIXTEEN_NINE -> longEdge * 9 / 16
        }
        return ResolutionStrategy(
            Size(longEdge, shortEdge),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
        )
    }

    private companion object {
        const val FOCUS_RESET_SECONDS = 3L
    }
}
