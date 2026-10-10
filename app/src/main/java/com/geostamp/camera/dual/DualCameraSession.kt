package com.geostamp.camera.dual

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.CompositionSettings
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.geostamp.camera.capture.CapturedImageDecoder
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Simultaneous front + rear capture through CameraX's concurrent camera API. Only used after
 * [com.geostamp.camera.camera.CameraCapabilities] confirmed a concurrent front/rear pair; a bind that the
 * device still rejects is reported to the caller, never replaced by sequential shots.
 */
class DualCameraSession(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private var provider: ProcessCameraProvider? = null
    private var rearCapture: ImageCapture? = null
    private var frontCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    private suspend fun provider(): ProcessCameraProvider =
        provider ?: ProcessCameraProvider.awaitInstance(appContext).also { provider = it }

    /** Each camera gets its own preview and still capture. */
    suspend fun bindPhoto(owner: LifecycleOwner, rearSurface: Preview.SurfaceProvider, frontSurface: Preview.SurfaceProvider) {
        val cameraProvider = provider()
        cameraProvider.unbindAll()
        val ratio = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
        fun preview(surface: Preview.SurfaceProvider) = Preview.Builder().setResolutionSelector(ratio).build().also { it.surfaceProvider = surface }
        fun still() = ImageCapture.Builder()
            .setResolutionSelector(ratio)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(Surface.ROTATION_0)
            .build()
        val rear = still().also { rearCapture = it }
        val front = still().also { frontCapture = it }
        cameraProvider.bindToLifecycle(
            listOf(
                SingleCameraConfig(CameraSelector.DEFAULT_BACK_CAMERA, group(preview(rearSurface), rear), owner),
                SingleCameraConfig(CameraSelector.DEFAULT_FRONT_CAMERA, group(preview(frontSurface), front), owner)
            )
        )
        videoCapture = null
    }

    /** One preview and one recording composed by CameraX: rear full frame, front as picture-in-picture. */
    suspend fun bindVideo(owner: LifecycleOwner, surface: Preview.SurfaceProvider, inset: NdcPlacement) {
        val cameraProvider = provider()
        cameraProvider.unbindAll()
        val ratio = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build()
        val preview = Preview.Builder().setResolutionSelector(ratio).build().also { it.surfaceProvider = surface }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)))
            .build()
        val video = VideoCapture.Builder(recorder).setTargetRotation(Surface.ROTATION_0).build().also { videoCapture = it }
        val shared = group(preview, video)
        cameraProvider.bindToLifecycle(
            listOf(
                SingleCameraConfig(CameraSelector.DEFAULT_BACK_CAMERA, shared, CompositionSettings.DEFAULT, owner),
                SingleCameraConfig(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    shared,
                    CompositionSettings.Builder()
                        .setAlpha(1f)
                        .setScale(inset.scaleX, inset.scaleY)
                        .setOffset(inset.offsetX, inset.offsetY)
                        .build(),
                    owner
                )
            )
        )
        rearCapture = null
        frontCapture = null
    }

    /** Fires both shutters together and returns upright (rear, front) bitmaps. */
    suspend fun takePictures(executor: Executor): Pair<Bitmap, Bitmap> = coroutineScope {
        val rear = checkNotNull(rearCapture) { "Dual photo is not bound" }
        val front = checkNotNull(frontCapture) { "Dual photo is not bound" }
        val rearShot = async { capture(rear, executor) }
        val frontShot = async { capture(front, executor) }
        rearShot.await() to frontShot.await()
    }

    @SuppressLint("MissingPermission")
    fun startRecording(file: File, maxDurationMillis: Long, withAudio: Boolean, listener: (VideoRecordEvent) -> Unit): Recording {
        val video = checkNotNull(videoCapture) { "Dual video is not bound" }
        val options = FileOutputOptions.Builder(file).setDurationLimitMillis(maxDurationMillis).build()
        return video.output.prepareRecording(appContext, options)
            .apply { if (withAudio) withAudioEnabled() }
            .start(mainExecutor) { listener(it) }
    }

    fun unbind() {
        provider?.unbindAll()
        rearCapture = null
        frontCapture = null
        videoCapture = null
    }

    private fun group(vararg useCases: androidx.camera.core.UseCase): UseCaseGroup =
        UseCaseGroup.Builder().apply { useCases.forEach { addUseCase(it) } }.build()

    private suspend fun capture(capture: ImageCapture, executor: Executor): Bitmap =
        suspendCancellableCoroutine { continuation ->
            capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    runCatching { image.use { CapturedImageDecoder.decodeUpright(it) } }
                        .onSuccess { continuation.resume(it) }
                        .onFailure { continuation.resumeWithException(it) }
                }

                override fun onError(exception: ImageCaptureException) = continuation.resumeWithException(exception)
            })
        }
}
