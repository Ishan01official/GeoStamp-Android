package com.geostamp.camera.capture

import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture

enum class FlashMode(val imageCaptureMode: Int) {
    OFF(ImageCapture.FLASH_MODE_OFF),
    AUTO(ImageCapture.FLASH_MODE_AUTO),
    ON(ImageCapture.FLASH_MODE_ON);

    fun next(): FlashMode = entries[(ordinal + 1) % entries.size]
}

enum class CaptureTimer(val seconds: Int) {
    OFF(0),
    THREE_SECONDS(3),
    TEN_SECONDS(10);

    fun next(): CaptureTimer = entries[(ordinal + 1) % entries.size]
}

enum class PhotoAspectRatio(val cameraXRatio: Int, val portraitWidthOverHeight: Float) {
    FOUR_THREE(AspectRatio.RATIO_4_3, 3f / 4f),
    SIXTEEN_NINE(AspectRatio.RATIO_16_9, 9f / 16f);

    fun next(): PhotoAspectRatio = entries[(ordinal + 1) % entries.size]
}

/** Output size policy. Exact sizes depend on what the active camera supports. */
enum class PhotoResolution {
    /** Up to about 12 MP: sharp and fast on most phones. */
    DEFAULT,

    /** About 5 MP for smaller files. */
    BALANCED,

    /** The largest size the camera offers for the aspect ratio. */
    HIGH;

    fun next(): PhotoResolution = entries[(ordinal + 1) % entries.size]
}

enum class CaptureMode { PHOTO, VIDEO, DUAL_PHOTO, DUAL_VIDEO }

enum class LensFacing { BACK, FRONT }
