package com.geostamp.camera.camera

import android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
import android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCapabilityRepositoryTest {
    private val rearAndFront = listOf(camera("0", LENS_FACING_BACK), camera("1", LENS_FACING_FRONT), camera("2", LENS_FACING_BACK))

    @Test
    fun `concurrent front and rear pair enables both dual modes`() {
        val capabilities = repository(rearAndFront, setOf(setOf("0", "1")), feature = true).capabilities()

        assertNull(capabilities.dualUnsupportedReason)
        assertTrue(capabilities.dualPhotoSupported)
        assertTrue(capabilities.dualVideoSupported)
        assertEquals(listOf("0" to "1"), capabilities.frontRearPairs)
    }

    @Test
    fun `no concurrent sets means unsupported`() {
        val capabilities = repository(rearAndFront, emptySet(), feature = true).capabilities()

        assertEquals(DualUnsupportedReason.NO_FRONT_REAR_PAIR, capabilities.dualUnsupportedReason)
        assertFalse(capabilities.dualPhotoSupported)
        assertFalse(capabilities.dualVideoSupported)
    }

    @Test
    fun `two rear cameras running together do not count as front plus rear`() {
        val capabilities = repository(rearAndFront, setOf(setOf("0", "2")), feature = true).capabilities()

        assertEquals(DualUnsupportedReason.NO_FRONT_REAR_PAIR, capabilities.dualUnsupportedReason)
    }

    @Test
    fun `missing system feature means unsupported even if ids are listed`() {
        val capabilities = repository(rearAndFront, setOf(setOf("0", "1")), feature = false).capabilities()

        assertEquals(DualUnsupportedReason.NO_CONCURRENT_FEATURE, capabilities.dualUnsupportedReason)
    }

    @Test
    fun `phone without front camera cannot do dual capture`() {
        val capabilities = repository(listOf(camera("0", LENS_FACING_BACK)), emptySet(), feature = true).capabilities()

        assertEquals(DualUnsupportedReason.NO_FRONT_OR_REAR_CAMERA, capabilities.dualUnsupportedReason)
    }

    @Test
    fun `android 10 cannot query concurrent cameras`() {
        val capabilities = repository(rearAndFront, setOf(setOf("0", "1")), feature = true, sdk = 29).capabilities()

        assertEquals(DualUnsupportedReason.ANDROID_TOO_OLD, capabilities.dualUnsupportedReason)
    }

    private fun camera(id: String, facing: Int) = CameraDescription(id, facing, hasFlash = facing == LENS_FACING_BACK, hardwareLevel = 1, sensorOrientation = 90)

    private fun repository(cameras: List<CameraDescription>, sets: Set<Set<String>>, feature: Boolean, sdk: Int = 34) =
        CameraCapabilityRepository(object : CameraCapabilitySource {
            override fun cameras() = cameras
            override fun concurrentCameraIdSets() = sets
            override fun hasConcurrentFeature() = feature
            override fun sdkInt() = sdk
        })
}
