package com.geostamp.camera.gallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemTest {
    @Test
    fun originalsAndVideosAreUnstamped() {
        assertTrue(MediaItem.isStampedName("GeoStamp_20261010_010537_123.jpg", isVideo = false))
        assertTrue(MediaItem.isStampedName("GeoStamp_20261010_010537_123_batch.jpg", isVideo = false))
        assertFalse(MediaItem.isStampedName("GeoStamp_20261010_010537_123_original.jpg", isVideo = false))
        assertFalse(MediaItem.isStampedName("GeoStamp_20261010_010537_123.mp4", isVideo = true))
    }
}
