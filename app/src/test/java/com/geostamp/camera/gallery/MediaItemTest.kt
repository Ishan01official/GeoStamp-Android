package com.geostamp.camera.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemTest {
    @Test
    fun originalsAndVideosAreUnstamped() {
        assertTrue(MediaItem.isStampedName("GeoStamp_20261010_010537_123.jpg", isVideo = false))
        assertTrue(MediaItem.isStampedName("GeoStamp_20261010_010537_123_batch.jpg", isVideo = false))
        assertFalse(MediaItem.isStampedName("GeoStamp_20261010_010537_123_original.jpg", isVideo = false))
    }

    @Test
    fun addressEditUsesTheSavedOriginalOfAStampedPhoto() {
        assertEquals(
            "GeoStamp_20261010_010537_123_original.jpg",
            MediaItem.unstampedSourceName("GeoStamp_20261010_010537_123.jpg", isVideo = false)
        )
    }

    @Test
    fun addressEditOfAnOriginalUsesItself() {
        assertEquals(
            "GeoStamp_20261010_010537_123_original.jpg",
            MediaItem.unstampedSourceName("GeoStamp_20261010_010537_123_original.jpg", isVideo = false)
        )
    }

    @Test
    fun videosAreNotReStampedFromGallery() {
        assertNull(MediaItem.unstampedSourceName("GeoStamp_20261010_010537_123.mp4", isVideo = true))
    }
}
