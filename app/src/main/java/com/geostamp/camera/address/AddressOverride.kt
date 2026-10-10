package com.geostamp.camera.address

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Where the stamped address text came from. Manual text is never presented as GPS-derived. */
enum class AddressSource { DETECTED, MANUAL }

enum class AddressEditScope {
    /** Used for the next photo or video, then the detected address returns. */
    NEXT_CAPTURE,

    /** Used until the user restores the detected address or the app is closed. */
    SESSION
}

data class AddressOverride(val text: String, val scope: AddressEditScope)

data class StampAddress(val text: String?, val source: AddressSource)

object AddressChoice {
    /** The manual address wins while active. Coordinates are never touched by an edit. */
    fun effective(detected: String?, override: AddressOverride?): StampAddress {
        val manual = override?.text?.trim()?.takeIf { it.isNotEmpty() }
        return if (manual != null) StampAddress(manual, AddressSource.MANUAL) else StampAddress(detected, AddressSource.DETECTED)
    }
}

/** Holds the user's address correction for the current app session only; nothing is written to disk. */
class AddressOverrideRepository {
    private val _override = MutableStateFlow<AddressOverride?>(null)
    val override: StateFlow<AddressOverride?> = _override.asStateFlow()

    fun set(text: String, scope: AddressEditScope) {
        _override.value = text.trim().takeIf { it.isNotEmpty() }?.let { AddressOverride(it, scope) }
    }

    fun restoreDetected() {
        _override.value = null
    }

    /** Called after a photo or video is saved; one-capture edits expire, session edits stay. */
    fun onCaptureCompleted() {
        _override.update { current -> current?.takeIf { it.scope == AddressEditScope.SESSION } }
    }
}
