package com.geostamp.camera.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoVerificationTest {
    private val recording = VideoFileInfo(
        durationMillis = 30_040L, width = 1080, height = 1920, rotationDegrees = 90,
        hasVideo = true, hasAudio = true, sizeBytes = 60_000_000L
    )

    @Test
    fun `complete stamped copy passes`() {
        assertTrue(VideoVerification.isFaithfulCopy(recording, recording.copy(durationMillis = 30_000L, sizeBytes = 55_000_000L)))
    }

    @Test
    fun `missing output fails`() {
        assertFalse(VideoVerification.isFaithfulCopy(recording, null))
    }

    @Test
    fun `lost audio fails`() {
        assertFalse(VideoVerification.isFaithfulCopy(recording, recording.copy(hasAudio = false)))
    }

    @Test
    fun `silent recording does not require audio`() {
        val silent = recording.copy(hasAudio = false)
        assertTrue(VideoVerification.isFaithfulCopy(silent, silent))
    }

    @Test
    fun `truncated output fails`() {
        assertFalse(VideoVerification.isFaithfulCopy(recording, recording.copy(durationMillis = 20_000L)))
    }

    @Test
    fun `changed orientation or resolution fails`() {
        assertFalse(VideoVerification.isFaithfulCopy(recording, recording.copy(width = 1920, height = 1080)))
    }

    @Test
    fun `rotation may be stored differently as long as the upright frame matches`() {
        assertTrue(VideoVerification.isFaithfulCopy(recording, recording.copy(rotationDegrees = 270)))
    }

    @Test
    fun `empty or video-less recording is unusable`() {
        assertFalse(VideoVerification.isUsableRecording(null))
        assertFalse(VideoVerification.isUsableRecording(recording.copy(hasVideo = false)))
        assertFalse(VideoVerification.isUsableRecording(recording.copy(durationMillis = 0L)))
        assertTrue(VideoVerification.isUsableRecording(recording))
    }
}
