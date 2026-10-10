package com.geostamp.camera.permissions

/** What the app can usefully offer for a runtime permission right now. */
enum class PermissionAction {
    /** Already granted; nothing to offer. */
    NONE,

    /** Android will still show its dialog. */
    REQUEST,

    /** Android will no longer show its dialog ("don't ask again"); only app settings can change it. */
    OPEN_SETTINGS
}

/** Location is optional, so precision is offered as an upgrade rather than a requirement. */
enum class LocationAccess { DENIED, APPROXIMATE, PRECISE }

object PermissionPolicy {
    /**
     * Android reports `shouldShowRationale = false` both before the first request and after a permanent
     * denial. [everRequested] tells the two apart, so the user is never left with a button that does nothing.
     */
    fun action(granted: Boolean, everRequested: Boolean, shouldShowRationale: Boolean): PermissionAction =
        when {
            granted -> PermissionAction.NONE
            !everRequested || shouldShowRationale -> PermissionAction.REQUEST
            else -> PermissionAction.OPEN_SETTINGS
        }

    fun locationAccess(fineGranted: Boolean, coarseGranted: Boolean): LocationAccess =
        when {
            fineGranted -> LocationAccess.PRECISE
            coarseGranted -> LocationAccess.APPROXIMATE
            else -> LocationAccess.DENIED
        }

    /** The optional location explanation appears once, after the camera works, and never blocks it. */
    fun shouldOfferLocation(cameraGranted: Boolean, access: LocationAccess, promptShown: Boolean): Boolean =
        cameraGranted && access == LocationAccess.DENIED && !promptShown
}
