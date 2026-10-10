package com.geostamp.camera.dual

import com.geostamp.camera.stamps.StampPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsetLayoutTest {
    @Test
    fun `inset never overlaps a bottom stamp`() {
        InsetCorner.entries.forEach { corner ->
            InsetSize.entries.forEach { size ->
                val rect = InsetLayout(corner, size).rect(frameAspect = 3f / 4f, stampPosition = StampPosition.BOTTOM)
                assertTrue("$corner $size", rect.bottom <= 1f - InsetLayout.STAMP_BAND + 1e-4f)
                assertTrue("$corner $size", rect.top >= 0f && rect.left >= 0f && rect.right <= 1f)
            }
        }
    }

    @Test
    fun `inset never overlaps a top stamp`() {
        InsetCorner.entries.forEach { corner ->
            InsetSize.entries.forEach { size ->
                val rect = InsetLayout(corner, size).rect(frameAspect = 9f / 16f, stampPosition = StampPosition.TOP)
                assertTrue("$corner $size", rect.top >= InsetLayout.STAMP_BAND - 1e-4f)
                assertTrue("$corner $size", rect.bottom <= 1f)
            }
        }
    }

    @Test
    fun `inset keeps a portrait 3 to 4 shape in pixels`() {
        val rect = InsetLayout(InsetCorner.TOP_END, InsetSize.MEDIUM).rect(frameAspect = 3f / 4f, stampPosition = StampPosition.BOTTOM)
        val frameWidth = 3000f
        val frameHeight = 4000f
        assertEquals(0.75f, rect.width * frameWidth / (rect.height * frameHeight), 1e-3f)
    }

    @Test
    fun `corner and size cycle through every option`() {
        assertEquals(InsetCorner.TOP_START, InsetCorner.BOTTOM_END.next())
        assertEquals(InsetSize.SMALL, InsetSize.LARGE.next())
    }
}
