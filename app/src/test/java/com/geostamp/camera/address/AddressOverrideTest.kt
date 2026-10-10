package com.geostamp.camera.address

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressOverrideTest {
    @Test
    fun `detected address is used when there is no edit`() {
        assertEquals(StampAddress("Garh Road, Meerut", AddressSource.DETECTED), AddressChoice.effective("Garh Road, Meerut", null))
    }

    @Test
    fun `manual address wins and is marked manual`() {
        val override = AddressOverride("98 Nav Shakti Dham, Meerut", AddressEditScope.SESSION)
        assertEquals(StampAddress("98 Nav Shakti Dham, Meerut", AddressSource.MANUAL), AddressChoice.effective("Garh Road", override))
    }

    @Test
    fun `manual address works without any detected address`() {
        val override = AddressOverride("Site office", AddressEditScope.NEXT_CAPTURE)
        assertEquals(AddressSource.MANUAL, AddressChoice.effective(null, override).source)
    }

    @Test
    fun `one capture edit expires after the capture`() {
        val repository = AddressOverrideRepository()
        repository.set("Site office", AddressEditScope.NEXT_CAPTURE)
        repository.onCaptureCompleted()
        assertNull(repository.override.value)
    }

    @Test
    fun `session edit stays until restored`() {
        val repository = AddressOverrideRepository()
        repository.set("Site office", AddressEditScope.SESSION)
        repository.onCaptureCompleted()
        repository.onCaptureCompleted()
        assertEquals("Site office", repository.override.value?.text)
        repository.restoreDetected()
        assertNull(repository.override.value)
    }

    @Test
    fun `blank edit restores the detected address`() {
        val repository = AddressOverrideRepository()
        repository.set("   ", AddressEditScope.SESSION)
        assertNull(repository.override.value)
    }
}
