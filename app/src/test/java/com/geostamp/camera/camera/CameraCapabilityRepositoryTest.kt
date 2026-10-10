package com.geostamp.camera.camera

import android.hardware.camera2.CameraCharacteristics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCapabilityRepositoryTest {
    @Test
    fun detectsConcurrentFrontBackCameraSets() {
        val repository = CameraCapabilityRepository(
            FakeCapabilitySource(
                lenses = mapOf(
                    "0" to CameraCharacteristics.LENS_FACING_BACK,
                    "1" to CameraCharacteristics.LENS_FACING_FRONT
                ),
                concurrentSets = setOf(setOf("0", "1"))
            )
        )

        val capabilities = repository.capabilities()

        assertTrue(capabilities.hasBackCamera)
        assertTrue(capabilities.hasFrontCamera)
        assertTrue(capabilities.concurrentFrontBackSupported)
        assertFalse(capabilities.dualPhotoSupported)
        assertFalse(capabilities.dualVideoSupported)
    }

    @Test
    fun reportsUnsupportedWhenNoConcurrentPairExists() {
        val repository = CameraCapabilityRepository(
            FakeCapabilitySource(
                lenses = mapOf(
                    "0" to CameraCharacteristics.LENS_FACING_BACK,
                    "1" to CameraCharacteristics.LENS_FACING_FRONT
                ),
                concurrentSets = emptySet()
            )
        )

        val capabilities = repository.capabilities()

        assertFalse(capabilities.concurrentFrontBackSupported)
        assertFalse(capabilities.dualPhotoSupported)
        assertFalse(capabilities.dualVideoSupported)
    }

    private class FakeCapabilitySource(
        private val lenses: Map<String, Int>,
        private val concurrentSets: Set<Set<String>>
    ) : CameraCapabilitySource {
        override fun cameraIds(): List<String> = lenses.keys.toList()

        override fun lensFacing(cameraId: String): Int? = lenses[cameraId]

        override fun concurrentCameraIdSets(): Set<Set<String>> = concurrentSets
    }
}
