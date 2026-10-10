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

    @Test
    fun `video margins match the Dual Photo margins in pixels`() {
        val photo = InsetLayout(InsetCorner.TOP_START).rect(3f / 4f, StampPosition.BOTTOM)
        val video = InsetLayout(InsetCorner.TOP_START).rect(9f / 16f, StampPosition.BOTTOM, 9f / 16f, 0.8f)
        val width = 1080f
        assertEquals(photo.left * width, video.left * width, 0.5f)
        assertEquals(photo.top * width * 4f / 3f, video.top * width * 16f / 9f, 0.5f)
    }

    @Test
    fun `video inset is narrower so it is about as tall as the photo inset`() {
        val photo = InsetLayout().rect(3f / 4f, StampPosition.BOTTOM)
        val video = InsetLayout().rect(9f / 16f, StampPosition.BOTTOM, 9f / 16f, DualCaptureController.VIDEO_SIZE_SCALE)
        val photoHeightPx = photo.height * 1080f * 4f / 3f
        val videoHeightPx = video.height * 1080f * 16f / 9f
        assertTrue("video ${videoHeightPx}px vs photo ${photoHeightPx}px", videoHeightPx <= photoHeightPx * 1.15f)
        // The front camera's 9:16 shape is kept, so faces are never stretched.
        assertEquals(9f / 16f, video.width * 1080f / videoHeightPx, 1e-3f)
    }

    @Test
    fun `inset stays clear of every control band and of the side rail`() {
        val rail = NormalizedRect(0.86f, 0.05f, 0.98f, 0.38f)
        for (rtlRail in listOf(false, true)) {
            val side = if (rtlRail) NormalizedRect(1f - rail.right, rail.top, 1f - rail.left, rail.bottom) else rail
            val safe = FrameSafeArea(top = 0.08f, bottom = 0.2f, rail = side)
            for (aspect in listOf(9f / 16f to 0.8f, 3f / 4f to 1f)) {
                InsetCorner.entries.forEach { corner ->
                    InsetSize.entries.forEach { size ->
                        val insetAspect = if (aspect.first < 0.7f) 9f / 16f else 3f / 4f
                        val rect = InsetLayout(corner, size).rect(aspect.first, StampPosition.BOTTOM, insetAspect, aspect.second, safe)
                        val label = "$corner $size rtl=$rtlRail aspect=${aspect.first}"
                        assertTrue("$label top", rect.top >= safe.top - 1e-4f)
                        assertTrue("$label bottom", rect.bottom <= 1f - safe.bottom - InsetLayout.STAMP_BAND + 1e-4f)
                        assertTrue("$label rail", !rect.intersects(side))
                        assertTrue("$label inside", rect.left >= 0f && rect.right <= 1f)
                    }
                }
            }
        }
    }

    @Test
    fun `safe area rounds outward to whole percent and is capped`() {
        val area = FrameSafeArea.fromPixels(1000f, 1000f, coveredTop = 61f, coveredBottom = 900f)
        assertEquals(0.07f, area.top, 1e-6f)
        assertEquals(0.45f, area.bottom, 1e-6f)
        assertEquals(FrameSafeArea.fromPixels(1000f, 1000f, 60.2f, 0f), FrameSafeArea.fromPixels(1000f, 1000f, 60.9f, 0f))
        val withRail = FrameSafeArea.fromPixels(1000f, 2000f, 0f, 0f, NormalizedRect(861f, 105f, 975f, 701f))
        assertEquals(NormalizedRect(0.86f, 0.05f, 0.98f, 0.36f), withRail.rail)
    }

    @Test
    fun `rounded frame extends far enough to hide the square video corners`() {
        // A square corner lies inside the outer rounded edge once the outset is at least (1 - 1/sqrt 2) of the radius.
        val needed = 1f - 1f / kotlin.math.sqrt(2f)
        assertTrue(PipFrame.OUTSET_RATIO >= needed)
    }
}
