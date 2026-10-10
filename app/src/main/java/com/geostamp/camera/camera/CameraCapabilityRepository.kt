package com.geostamp.camera.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

data class CameraCapabilities(
    val hasBackCamera: Boolean,
    val hasFrontCamera: Boolean,
    val concurrentFrontBackSupported: Boolean
) {
    val dualPhotoSupported: Boolean get() = false
    val dualVideoSupported: Boolean get() = false
}

interface CameraCapabilitySource {
    fun cameraIds(): List<String>
    fun lensFacing(cameraId: String): Int?
    fun concurrentCameraIdSets(): Set<Set<String>>
}

class CameraCapabilityRepository(private val source: CameraCapabilitySource) {
    constructor(context: Context) : this(Camera2CapabilitySource(context.applicationContext))

    fun capabilities(): CameraCapabilities {
        val ids = source.cameraIds()
        val frontIds = ids.filter { source.lensFacing(it) == CameraCharacteristics.LENS_FACING_FRONT }.toSet()
        val backIds = ids.filter { source.lensFacing(it) == CameraCharacteristics.LENS_FACING_BACK }.toSet()
        val concurrentFrontBack = source.concurrentCameraIdSets().any { set ->
            set.any { it in frontIds } && set.any { it in backIds }
        }
        return CameraCapabilities(
            hasBackCamera = backIds.isNotEmpty(),
            hasFrontCamera = frontIds.isNotEmpty(),
            concurrentFrontBackSupported = concurrentFrontBack
        )
    }
}

private class Camera2CapabilitySource(context: Context) : CameraCapabilitySource {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    override fun cameraIds(): List<String> = runCatching { cameraManager.cameraIdList.toList() }.getOrDefault(emptyList())

    override fun lensFacing(cameraId: String): Int? =
        runCatching { cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) }.getOrNull()

    override fun concurrentCameraIdSets(): Set<Set<String>> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { cameraManager.concurrentCameraIds }.getOrDefault(emptySet())
        } else {
            emptySet()
        }
}
