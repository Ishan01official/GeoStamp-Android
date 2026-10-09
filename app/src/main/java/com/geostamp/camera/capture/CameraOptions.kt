package com.geostamp.camera.capture

import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture

enum class FlashMode(val label: String, val imageCaptureMode: Int) {
    OFF("Flash off", ImageCapture.FLASH_MODE_OFF),
    AUTO("Flash auto", ImageCapture.FLASH_MODE_AUTO),
    ON("Flash on", ImageCapture.FLASH_MODE_ON);

    fun next(): FlashMode = entries[(ordinal + 1) % entries.size]
}

enum class CaptureTimer(val label: String, val seconds: Int) {
    OFF("Timer off", 0),
    THREE_SECONDS("Timer 3s", 3),
    TEN_SECONDS("Timer 10s", 10);

    fun next(): CaptureTimer = entries[(ordinal + 1) % entries.size]
}

enum class PhotoAspectRatio(val label: String, val cameraXRatio: Int) {
    FOUR_THREE("Aspect 4:3", AspectRatio.RATIO_4_3),
    SIXTEEN_NINE("Aspect 16:9", AspectRatio.RATIO_16_9);

    fun next(): PhotoAspectRatio = entries[(ordinal + 1) % entries.size]
}

enum class PhotoResolution(val label: String) {
    DEFAULT("Resolution auto"),
    BALANCED("Resolution balanced"),
    HIGH("Resolution high");

    fun next(): PhotoResolution = entries[(ordinal + 1) % entries.size]

    fun targetSize(aspectRatio: PhotoAspectRatio): Size? =
        when (this) {
            DEFAULT -> null
            BALANCED -> when (aspectRatio) {
                PhotoAspectRatio.FOUR_THREE -> Size(2560, 1920)
                PhotoAspectRatio.SIXTEEN_NINE -> Size(2560, 1440)
            }
            HIGH -> when (aspectRatio) {
                PhotoAspectRatio.FOUR_THREE -> Size(4000, 3000)
                PhotoAspectRatio.SIXTEEN_NINE -> Size(3840, 2160)
            }
        }
}
