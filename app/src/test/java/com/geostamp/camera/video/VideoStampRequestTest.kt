package com.geostamp.camera.video

import com.geostamp.camera.stamps.StampData
import com.geostamp.camera.stamps.StampPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoStampRequestTest {
    @Test
    fun sampleSelectionUsesLatestMetadataAtOrBeforeFrameTime() {
        val request = VideoStampRequest(
            startedAtMillis = 10_000L,
            preferences = StampPreferences(),
            samples = listOf(
                sample(elapsedMillis = 0L, capturedAt = 10_000L),
                sample(elapsedMillis = 1_000L, capturedAt = 11_000L),
                sample(elapsedMillis = 2_000L, capturedAt = 12_000L)
            )
        )

        assertEquals(10_000L, request.sampleAtElapsed(999L)?.data?.capturedAtMillis)
        assertEquals(11_000L, request.sampleAtElapsed(1_500L)?.data?.capturedAtMillis)
        assertEquals(12_000L, request.sampleAtElapsed(5_000L)?.data?.capturedAtMillis)
    }

    @Test
    fun stampClockAdvancesOncePerSecondOfVideo() {
        val request = VideoStampRequest(startedAtMillis = 10_000L, preferences = StampPreferences(), samples = emptyList())

        assertEquals(10_000L, request.stampTimeAt(0L))
        assertEquals(10_000L, request.stampTimeAt(999_999L))
        assertEquals(11_000L, request.stampTimeAt(1_000_000L))
        assertEquals(69_000L, request.stampTimeAt(59_966_000L))
    }

    @Test
    fun videoLimitIsOneMinute() {
        assertEquals(60_000L, VideoLimits.MAX_DURATION_MILLIS)
        assertEquals(60L, VideoLimits.MAX_DURATION_SECONDS)
    }

    private fun sample(elapsedMillis: Long, capturedAt: Long) = VideoStampSample(
        elapsedMillis = elapsedMillis,
        data = StampData(capturedAtMillis = capturedAt),
        map = null,
        logo = null
    )
}
