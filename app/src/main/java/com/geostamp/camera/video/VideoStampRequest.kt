package com.geostamp.camera.video

import android.graphics.Bitmap
import com.geostamp.camera.dual.NormalizedRect
import com.geostamp.camera.stamps.StampData
import com.geostamp.camera.stamps.StampPreferences

/** Stamp data captured once per second while recording. */
data class VideoStampSample(
    val elapsedMillis: Long,
    val data: StampData,
    val map: Bitmap?,
    val logo: Bitmap?
)

data class VideoStampRequest(
    val startedAtMillis: Long,
    val preferences: StampPreferences,
    val samples: List<VideoStampSample>,
    /** Dual Video only: where CameraX composed the front camera, framed like the Dual Photo window. */
    val pipFrame: NormalizedRect? = null
) {
    /** The most recent sample at or before [elapsedMillis]; location values follow the stable display fix. */
    fun sampleAtElapsed(elapsedMillis: Long): VideoStampSample? =
        samples.lastOrNull { it.elapsedMillis <= elapsedMillis } ?: samples.firstOrNull()

    /** The clock on the stamp advances once per second of video, from the moment recording started. */
    fun stampTimeAt(presentationTimeUs: Long): Long =
        startedAtMillis + (presentationTimeUs / 1_000_000L) * 1_000L
}

object VideoLimits {
    const val MAX_DURATION_MILLIS = 60_000L
    const val MAX_DURATION_SECONDS = MAX_DURATION_MILLIS / 1_000L

    /** Raw recording + stamped copy + gallery copy of a one-minute FHD clip, with headroom. */
    const val MIN_FREE_BYTES_TO_RECORD = 400L * 1024 * 1024
}
