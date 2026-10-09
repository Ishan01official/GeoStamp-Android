package com.geostamp.camera.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoomPresetsTest {
    @Test
    fun includesUltraWideAndOnlySupportedLevels() {
        assertEquals(listOf(0.6f, 1f, 2f, 3f), ZoomPresets.forRange(0.6f, 10f))
        assertEquals(listOf(1f, 2f, 3f, 5f), ZoomPresets.forRange(1f, 8f))
        assertEquals(listOf(1f, 2f), ZoomPresets.forRange(1f, 2.5f))
        assertEquals(listOf(1f), ZoomPresets.forRange(1f, 1f))
    }

    @Test
    fun selectsNearestPresetOnlyWhenClose() {
        val presets = listOf(1f, 2f, 3f)
        assertEquals(2f, ZoomPresets.selected(presets, 2.02f))
        assertNull(ZoomPresets.selected(presets, 1.5f))
    }

    @Test
    fun labels() {
        assertEquals("1×", ZoomPresets.label(1f))
        assertEquals("0.6×", ZoomPresets.label(0.6f))
        assertEquals("2.5×", ZoomPresets.label(2.5f))
    }
}
