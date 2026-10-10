package com.geostamp.camera.dual

import org.junit.Assert.assertEquals
import org.junit.Test

class CompositionMappingTest {
    private val topRight = NormalizedRect(left = 0.6f, top = 0.05f, right = 0.95f, bottom = 0.35f)

    @Test
    fun `unrotated buffer maps directly`() {
        val ndc = CompositionMapping.toNdc(topRight, 0)
        assertEquals(0.35f, ndc.scaleX, 1e-4f)
        assertEquals(0.30f, ndc.scaleY, 1e-4f)
        assertEquals(0.55f, ndc.offsetX, 1e-4f) // centre x 0.775 -> 0.55
        assertEquals(0.60f, ndc.offsetY, 1e-4f) // centre y 0.2 -> +0.6 (up)
    }

    @Test
    fun `90 degree sensor swaps axes`() {
        val ndc = CompositionMapping.toNdc(topRight, 90)
        // Upright top-right corner is the buffer's top-left after undoing a clockwise quarter turn.
        assertEquals(0.30f, ndc.scaleX, 1e-4f)
        assertEquals(0.35f, ndc.scaleY, 1e-4f)
        assertEquals(-0.60f, ndc.offsetX, 1e-4f)
        assertEquals(0.55f, ndc.offsetY, 1e-4f)
    }

    @Test
    fun `centred inset stays centred for every rotation`() {
        val centred = NormalizedRect(0.4f, 0.4f, 0.6f, 0.6f)
        listOf(0, 90, 180, 270).forEach { rotation ->
            val ndc = CompositionMapping.toNdc(centred, rotation)
            assertEquals(0f, ndc.offsetX, 1e-4f)
            assertEquals(0f, ndc.offsetY, 1e-4f)
        }
    }
}
