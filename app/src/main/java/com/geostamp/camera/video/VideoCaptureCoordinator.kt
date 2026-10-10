package com.geostamp.camera.video

import android.net.Uri
import android.os.StatFs
import android.util.Log
import com.geostamp.camera.capture.MediaStoreWriter
import com.geostamp.camera.capture.PhotoProcessor
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Progress of the post-recording stamp step, for the UI. */
sealed interface VideoJobState {
    data object Idle : VideoJobState
    data class Stamping(val progressPercent: Int?) : VideoJobState
}

/** How a recording ended up in the gallery. Only [Stamped] means the stamp is inside the video. */
sealed interface VideoOutcome {
    data class Stamped(val uri: Uri, val info: VideoFileInfo, val originalUri: Uri?) : VideoOutcome
    data class SavedWithoutStamp(val uri: Uri, val reason: Reason) : VideoOutcome
    data class Failed(val reason: Reason, val detail: String?) : VideoOutcome

    enum class Reason { CANCELLED, LOW_STORAGE, STAMPING_FAILED, VERIFICATION_FAILED, RECORDING_INVALID, SAVE_FAILED }
}

/**
 * Owns a recording from the moment CameraX finalizes it until it is safely in the gallery:
 * raw file (app-private) -> stamped copy -> verified -> published. The raw recording is only deleted
 * after the stamped copy is verified and published, and is published itself (clearly unstamped) if
 * stamping fails, is cancelled, or there is not enough space. Runs in the application scope so leaving
 * the camera screen does not lose the video.
 */
class VideoCaptureCoordinator(
    private val workDir: File,
    private val writer: MediaStoreWriter,
    private val processor: VideoStampProcessor,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<VideoJobState>(VideoJobState.Idle)
    val state: StateFlow<VideoJobState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        workDir.mkdirs()
    }

    fun newRecordingFile(startedAtMillis: Long): File = File(workDir, "$RAW_PREFIX$startedAtMillis.mp4")

    fun hasSpaceToRecord(): Boolean = freeBytes() >= VideoLimits.MIN_FREE_BYTES_TO_RECORD

    fun cancel() {
        job?.cancel()
    }

    /** Stamps and publishes [raw]; [onDone] runs on the main thread. When [request] is null the stamp is disabled. */
    fun process(raw: File, request: VideoStampRequest?, keepOriginal: Boolean, onDone: (VideoOutcome) -> Unit) {
        job = scope.launch(Dispatchers.Default) {
            val outcome = try {
                handle(raw, request, keepOriginal)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { saveUnstamped(raw, request, VideoOutcome.Reason.CANCELLED) }
            } finally {
                _state.value = VideoJobState.Idle
            }
            withContext(Dispatchers.Main + NonCancellable) { onDone(outcome) }
        }
    }

    private suspend fun handle(raw: File, request: VideoStampRequest?, keepOriginal: Boolean): VideoOutcome {
        val startedAt = request?.startedAtMillis ?: raw.lastModified()
        val inputInfo = VideoFileInfo.read(raw)
        if (!VideoVerification.isUsableRecording(inputInfo)) {
            raw.delete()
            return VideoOutcome.Failed(VideoOutcome.Reason.RECORDING_INVALID, null)
        }
        if (request == null) return saveUnstamped(raw, null, reason = null)
        if (freeBytes() < raw.length() * SPACE_FACTOR + SPACE_MARGIN_BYTES) {
            return saveUnstamped(raw, request, VideoOutcome.Reason.LOW_STORAGE)
        }

        val stamped = File(workDir, "$STAMPED_PREFIX$startedAt.mp4")
        _state.value = VideoJobState.Stamping(null)
        try {
            processor.stamp(raw, stamped, inputInfo!!, request) { percent -> _state.value = VideoJobState.Stamping(percent) }
        } catch (e: CancellationException) {
            stamped.delete()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Video stamping failed", e)
            stamped.delete()
            return saveUnstamped(raw, request, VideoOutcome.Reason.STAMPING_FAILED, e.message)
        }

        val outputInfo = VideoFileInfo.read(stamped)
        if (!VideoVerification.isFaithfulCopy(inputInfo!!, outputInfo)) {
            Log.e(TAG, "Stamped video failed verification: in=$inputInfo out=$outputInfo")
            stamped.delete()
            return saveUnstamped(raw, request, VideoOutcome.Reason.VERIFICATION_FAILED)
        }

        return try {
            val uri = writer.saveVideoFile(stamped, displayName(startedAt, MediaStoreWriter.STAMPED_VIDEO_SUFFIX), startedAt)
            val originalUri = if (keepOriginal) {
                runCatching { writer.saveVideoFile(raw, displayName(startedAt, MediaStoreWriter.ORIGINAL_SUFFIX), startedAt) }.getOrNull()
            } else {
                null
            }
            raw.delete()
            VideoOutcome.Stamped(uri, outputInfo!!, originalUri)
        } catch (e: Exception) {
            Log.e(TAG, "Saving stamped video failed", e)
            saveUnstamped(raw, request, VideoOutcome.Reason.SAVE_FAILED, e.message)
        } finally {
            stamped.delete()
        }
    }

    /** Never lose the user's recording: publish it as an unstamped video and say so. */
    private fun saveUnstamped(raw: File, request: VideoStampRequest?, reason: VideoOutcome.Reason?, detail: String? = null): VideoOutcome {
        val startedAt = request?.startedAtMillis ?: raw.lastModified()
        return try {
            val uri = writer.saveVideoFile(raw, displayName(startedAt, suffix = ""), startedAt)
            raw.delete()
            VideoOutcome.SavedWithoutStamp(uri, reason ?: VideoOutcome.Reason.STAMPING_FAILED)
        } catch (e: Exception) {
            Log.e(TAG, "Could not save recording; keeping it for recovery", e)
            VideoOutcome.Failed(reason ?: VideoOutcome.Reason.SAVE_FAILED, detail ?: e.message)
        }
    }

    /**
     * Called once at app start. Any raw recording still here belongs to a process that died mid-way;
     * publish it unstamped rather than lose it, and clear temporary and half-written files.
     */
    fun recoverInterrupted(): Int {
        var recovered = 0
        workDir.listFiles().orEmpty().forEach { file ->
            when {
                file.name.startsWith(STAMPED_PREFIX) -> file.delete()
                file.name.startsWith(RAW_PREFIX) -> {
                    if (VideoVerification.isUsableRecording(VideoFileInfo.read(file))) {
                        val startedAt = file.name.removePrefix(RAW_PREFIX).removeSuffix(".mp4").toLongOrNull() ?: file.lastModified()
                        runCatching { writer.saveVideoFile(file, displayName(startedAt, ""), startedAt) }
                            .onSuccess { recovered++; file.delete() }
                    } else {
                        file.delete()
                    }
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            runCatching { writer.deleteStalePendingVideos(System.currentTimeMillis() - STALE_PENDING_MILLIS) }
        }
        return recovered
    }

    private fun freeBytes(): Long = runCatching { StatFs(workDir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)

    private fun displayName(startedAt: Long, suffix: String) =
        MediaStoreWriter.videoDisplayName(PhotoProcessor.NAME_PREFIX, startedAt, suffix)

    private companion object {
        const val TAG = "VideoCapture"
        const val RAW_PREFIX = "rec_"
        const val STAMPED_PREFIX = "stamped_"
        const val SPACE_FACTOR = 3L
        const val SPACE_MARGIN_BYTES = 50L * 1024 * 1024
        const val STALE_PENDING_MILLIS = 60 * 60 * 1000L
    }
}
