package com.geostamp.camera.camera

import com.geostamp.camera.capture.LensFacing
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashAvailabilityTest {
    private val rearWithFlash = BoundCamera(LensFacing.BACK, hasFlashUnit = true)
    private val frontWithoutFlash = BoundCamera(LensFacing.FRONT, hasFlashUnit = false)

    @Test
    fun `flash is unavailable while no camera is bound yet`() {
        assertFalse(FlashAvailability.resolve(LensFacing.BACK, bound = null))
    }

    @Test
    fun `first launch after permission grant enables flash once the rear camera binds`() {
        // Permission granted -> provider initialized (nothing bound) -> rear camera bound.
        assertFalse(FlashAvailability.resolve(LensFacing.BACK, bound = null))
        assertTrue(FlashAvailability.resolve(LensFacing.BACK, rearWithFlash))
    }

    @Test
    fun `switching to a front camera without flash disables flash`() {
        assertFalse(FlashAvailability.resolve(LensFacing.FRONT, frontWithoutFlash))
    }

    @Test
    fun `stale rear binding does not report flash for a requested front camera`() {
        assertFalse(FlashAvailability.resolve(LensFacing.FRONT, rearWithFlash))
    }

    @Test
    fun `switching back to the rear camera re-enables flash`() {
        assertFalse(FlashAvailability.resolve(LensFacing.BACK, frontWithoutFlash))
        assertTrue(FlashAvailability.resolve(LensFacing.BACK, rearWithFlash))
    }

    @Test
    fun `rear camera without a flash unit stays disabled`() {
        assertFalse(FlashAvailability.resolve(LensFacing.BACK, BoundCamera(LensFacing.BACK, hasFlashUnit = false)))
    }
}
