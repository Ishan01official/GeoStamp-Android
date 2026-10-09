package com.geostamp.camera.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CapturedImageDecoderTest {
    @Test
    fun quarterTurnsSwapDimensions() {
        assertEquals(3072 to 4080, CapturedImageDecoder.uprightSize(4080, 3072, 90))
        assertEquals(3072 to 4080, CapturedImageDecoder.uprightSize(4080, 3072, 270))
        assertEquals(4080 to 3072, CapturedImageDecoder.uprightSize(4080, 3072, 180))
        assertEquals(4080 to 3072, CapturedImageDecoder.uprightSize(4080, 3072, 0))
    }
}
