package com.geostamp.camera.video

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File
import kotlin.math.abs

/** What a finished MP4 actually contains, read back from the file rather than assumed. */
data class VideoFileInfo(
    val durationMillis: Long,
    /** Upright (display) size, after applying rotation metadata. */
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val sizeBytes: Long
) {
    companion object {
        fun read(file: File): VideoFileInfo? {
            if (!file.isFile || file.length() == 0L) return null
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                val rotation = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val rawWidth = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val rawHeight = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val sideways = rotation % 180 != 0
                val tracks = trackTypes(file)
                VideoFileInfo(
                    durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                    width = if (sideways) rawHeight else rawWidth,
                    height = if (sideways) rawWidth else rawHeight,
                    rotationDegrees = rotation,
                    hasVideo = "video" in tracks,
                    hasAudio = "audio" in tracks,
                    sizeBytes = file.length()
                )
            } catch (_: RuntimeException) {
                null
            } finally {
                retriever.release()
            }
        }

        private fun trackTypes(file: File): Set<String> {
            val extractor = MediaExtractor()
            return try {
                extractor.setDataSource(file.absolutePath)
                (0 until extractor.trackCount).mapNotNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.substringBefore('/')
                }.toSet()
            } catch (_: Exception) {
                emptySet()
            } finally {
                extractor.release()
            }
        }

        private fun MediaMetadataRetriever.int(key: Int): Int = extractMetadata(key)?.toIntOrNull() ?: 0
    }
}

object VideoVerification {
    private const val DURATION_TOLERANCE_MILLIS = 1_500L

    /** The stamped copy may replace the recording only if it is a complete, playable equivalent. */
    fun isFaithfulCopy(input: VideoFileInfo, output: VideoFileInfo?): Boolean {
        output ?: return false
        return output.hasVideo &&
            output.sizeBytes > 0 &&
            (!input.hasAudio || output.hasAudio) &&
            abs(output.durationMillis - input.durationMillis) <= DURATION_TOLERANCE_MILLIS &&
            output.width == input.width &&
            output.height == input.height
    }

    /** CameraX finalizes some "errors" with a complete file, e.g. when the 60 s limit stops recording. */
    fun isUsableRecording(info: VideoFileInfo?): Boolean =
        info != null && info.hasVideo && info.durationMillis > 0
}
