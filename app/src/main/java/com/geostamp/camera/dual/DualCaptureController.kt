package com.geostamp.camera.dual

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.Preview
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.lifecycle.LifecycleOwner
import com.geostamp.camera.camera.CameraCapabilities
import com.geostamp.camera.capture.CaptureMode
import com.geostamp.camera.capture.PhotoProcessor
import com.geostamp.camera.capture.ProcessedPhoto
import com.geostamp.camera.capture.SaveOptions
import com.geostamp.camera.capture.StampRequest
import com.geostamp.camera.stamps.StampPosition
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

enum class DualBindState { IDLE, STARTING, READY, FAILED }

data class DualUiState(
    val inset: InsetLayout = InsetLayout(),
    val bind: DualBindState = DualBindState.IDLE,
    /** The device advertised concurrent cameras but refused this combination at runtime. */
    val runtimeFailure: String? = null
)

/**
 * Dual capture logic kept out of the camera ViewModel: binding, the picture-in-picture layout, the dual
 * photo pipeline (both shutters -> compose -> stamp -> save) and the dual video recorder.
 */
class DualCaptureController(
    private val session: DualCameraSession,
    private val photoProcessor: PhotoProcessor,
    private val capabilities: () -> CameraCapabilities
) {
    private val _state = MutableStateFlow(DualUiState())
    val state: StateFlow<DualUiState> = _state.asStateFlow()

    fun cycleCorner() = _state.update { it.copy(inset = it.inset.copy(corner = it.inset.corner.next())) }

    fun cycleSize() = _state.update { it.copy(inset = it.inset.copy(size = it.inset.size.next())) }

    suspend fun bind(mode: CaptureMode, owner: LifecycleOwner, main: Preview.SurfaceProvider, front: Preview.SurfaceProvider?, stampPosition: StampPosition): Boolean {
        _state.update { it.copy(bind = DualBindState.STARTING, runtimeFailure = null) }
        return try {
            if (mode == CaptureMode.DUAL_PHOTO) {
                session.bindPhoto(owner, main, checkNotNull(front))
            } else {
                session.bindVideo(owner, main, videoInsetNdc(stampPosition))
            }
            _state.update { it.copy(bind = DualBindState.READY) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Concurrent camera bind failed for $mode", e)
            session.unbind()
            _state.update { it.copy(bind = DualBindState.FAILED, runtimeFailure = e.message ?: e.javaClass.simpleName) }
            false
        }
    }

    fun unbind() {
        session.unbind()
        _state.update { it.copy(bind = DualBindState.IDLE) }
    }

    /** Both cameras fire together; the front view is composed into the inset before the stamp is drawn. */
    suspend fun captureAndSave(executor: Executor, request: StampRequest, options: SaveOptions): ProcessedPhoto {
        val (rear, front) = session.takePictures(executor)
        return withContext(Dispatchers.Default) {
            val inset = _state.value.inset.rect(rear.width.toFloat() / rear.height, request.preferences.position)
            val composite = DualPhotoComposer.compose(rear, front, inset)
            front.recycle()
            val original = if (options.saveOriginal) composite.jpegBytes() else null
            photoProcessor.process(composite, original, 0, request, options)
        }
    }

    fun startRecording(file: File, maxDurationMillis: Long, withAudio: Boolean, listener: (VideoRecordEvent) -> Unit): Recording =
        session.startRecording(file, maxDurationMillis, withAudio, listener)

    /** The inset rectangle for the live preview of either mode, in upright frame coordinates. */
    fun previewInset(mode: CaptureMode, stampPosition: StampPosition): NormalizedRect =
        if (mode == CaptureMode.DUAL_VIDEO) {
            _state.value.inset.rect(VIDEO_FRAME_ASPECT, stampPosition, VIDEO_INSET_ASPECT)
        } else {
            _state.value.inset.rect(PHOTO_FRAME_ASPECT, stampPosition)
        }

    private fun videoInsetNdc(stampPosition: StampPosition): NdcPlacement {
        // Portrait display: the composed buffer is in the rear sensor's orientation.
        val rearOrientation = capabilities().cameras
            .firstOrNull { it.lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK }
            ?.sensorOrientation ?: DEFAULT_SENSOR_ORIENTATION
        return CompositionMapping.toNdc(previewInset(CaptureMode.DUAL_VIDEO, stampPosition), rearOrientation)
    }

    private fun Bitmap.jpegBytes(): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, ORIGINAL_QUALITY, it) }.toByteArray()

    companion object {
        private const val TAG = "DualCapture"
        const val PHOTO_FRAME_ASPECT = 3f / 4f
        const val VIDEO_FRAME_ASPECT = 9f / 16f

        /** The front stream is 16:9; a 9:16 inset shows it without stretching. */
        const val VIDEO_INSET_ASPECT = 9f / 16f
        private const val DEFAULT_SENSOR_ORIENTATION = 90
        private const val ORIGINAL_QUALITY = 95
    }
}
