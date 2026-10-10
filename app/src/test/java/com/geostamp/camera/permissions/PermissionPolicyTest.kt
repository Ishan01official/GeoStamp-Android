package com.geostamp.camera.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {
    @Test
    fun `never requested permission can be requested`() {
        assertEquals(PermissionAction.REQUEST, PermissionPolicy.action(granted = false, everRequested = false, shouldShowRationale = false))
    }

    @Test
    fun `denied once can be requested again`() {
        assertEquals(PermissionAction.REQUEST, PermissionPolicy.action(granted = false, everRequested = true, shouldShowRationale = true))
    }

    @Test
    fun `permanently denied permission sends the user to app settings`() {
        assertEquals(PermissionAction.OPEN_SETTINGS, PermissionPolicy.action(granted = false, everRequested = true, shouldShowRationale = false))
    }

    @Test
    fun `granted permission needs no action`() {
        assertEquals(PermissionAction.NONE, PermissionPolicy.action(granted = true, everRequested = true, shouldShowRationale = false))
    }

    @Test
    fun `approximate location is recognised separately from precise`() {
        assertEquals(LocationAccess.APPROXIMATE, PermissionPolicy.locationAccess(fineGranted = false, coarseGranted = true))
        assertEquals(LocationAccess.PRECISE, PermissionPolicy.locationAccess(fineGranted = true, coarseGranted = true))
        assertEquals(LocationAccess.DENIED, PermissionPolicy.locationAccess(fineGranted = false, coarseGranted = false))
    }

    @Test
    fun `location is offered only after the camera works and only once`() {
        assertFalse(PermissionPolicy.shouldOfferLocation(cameraGranted = false, access = LocationAccess.DENIED, promptShown = false))
        assertTrue(PermissionPolicy.shouldOfferLocation(cameraGranted = true, access = LocationAccess.DENIED, promptShown = false))
        assertFalse(PermissionPolicy.shouldOfferLocation(cameraGranted = true, access = LocationAccess.DENIED, promptShown = true))
        assertFalse(PermissionPolicy.shouldOfferLocation(cameraGranted = true, access = LocationAccess.APPROXIMATE, promptShown = false))
    }
}
