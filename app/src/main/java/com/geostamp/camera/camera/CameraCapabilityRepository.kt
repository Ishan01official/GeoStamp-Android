package com.geostamp.camera.camera

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

/** One physical-facing camera as Camera2 reports it. */
data class CameraDescription(
    val id: String,
    val lensFacing: Int?,
    val hasFlash: Boolean,
    val hardwareLevel: Int?,
    val sensorOrientation: Int?
)

/** Why simultaneous front + rear capture cannot be offered. */
enum class DualUnsupportedReason { NO_FRONT_OR_REAR_CAMERA, NO_CONCURRENT_FEATURE, NO_FRONT_REAR_PAIR, ANDROID_TOO_OLD }

data class CameraCapabilities(
    val cameras: List<CameraDescription>,
    val concurrentFeature: Boolean,
    val concurrentCameraIdSets: Set<Set<String>>,
    val sdkInt: Int
) {
    private val frontIds: Set<String> get() = cameras.filter { it.lensFacing == CameraCharacteristics.LENS_FACING_FRONT }.map { it.id }.toSet()
    private val backIds: Set<String> get() = cameras.filter { it.lensFacing == CameraCharacteristics.LENS_FACING_BACK }.map { it.id }.toSet()

    val hasBackCamera: Boolean get() = backIds.isNotEmpty()
    val hasFrontCamera: Boolean get() = frontIds.isNotEmpty()

    /** Front/rear id pairs Camera2 guarantees can stream at the same time. */
    val frontRearPairs: List<Pair<String, String>>
        get() = concurrentCameraIdSets.flatMap { set ->
            set.filter { it in backIds }.flatMap { back -> set.filter { it in frontIds }.map { front -> back to front } }
        }.distinct()

    val dualUnsupportedReason: DualUnsupportedReason?
        get() = when {
            sdkInt < Build.VERSION_CODES.R -> DualUnsupportedReason.ANDROID_TOO_OLD
            !hasBackCamera || !hasFrontCamera -> DualUnsupportedReason.NO_FRONT_OR_REAR_CAMERA
            !concurrentFeature -> DualUnsupportedReason.NO_CONCURRENT_FEATURE
            frontRearPairs.isEmpty() -> DualUnsupportedReason.NO_FRONT_REAR_PAIR
            else -> null
        }

    /** Both modes need the same hardware guarantee; a runtime bind failure is reported separately. */
    val concurrentFrontBackSupported: Boolean get() = dualUnsupportedReason == null
    val dualPhotoSupported: Boolean get() = concurrentFrontBackSupported
    val dualVideoSupported: Boolean get() = concurrentFrontBackSupported
}

interface CameraCapabilitySource {
    fun cameras(): List<CameraDescription>
    fun concurrentCameraIdSets(): Set<Set<String>>
    fun hasConcurrentFeature(): Boolean
    fun sdkInt(): Int = Build.VERSION.SDK_INT
}

class CameraCapabilityRepository(private val source: CameraCapabilitySource) {
    constructor(context: Context) : this(Camera2CapabilitySource(context.applicationContext))

    private val cached by lazy {
        CameraCapabilities(
            cameras = source.cameras(),
            concurrentFeature = source.hasConcurrentFeature(),
            concurrentCameraIdSets = source.concurrentCameraIdSets(),
            sdkInt = source.sdkInt()
        )
    }

    fun capabilities(): CameraCapabilities = cached

    /** What actually happened the last time each dual mode was started on this phone. */
    private val runtimeResults = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun recordDualResult(mode: String, result: String) {
        runtimeResults[mode] = result
    }

    fun dualResults(): Map<String, String> = runtimeResults.toMap()
}

private class Camera2CapabilitySource(private val context: Context) : CameraCapabilitySource {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    override fun cameras(): List<CameraDescription> =
        runCatching { cameraManager.cameraIdList.toList() }.getOrDefault(emptyList()).map { id ->
            val characteristics = runCatching { cameraManager.getCameraCharacteristics(id) }.getOrNull()
            CameraDescription(
                id = id,
                lensFacing = characteristics?.get(CameraCharacteristics.LENS_FACING),
                hasFlash = characteristics?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                hardwareLevel = characteristics?.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL),
                sensorOrientation = characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION)
            )
        }

    override fun concurrentCameraIdSets(): Set<Set<String>> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { cameraManager.concurrentCameraIds }.getOrDefault(emptySet())
        } else {
            emptySet()
        }

    override fun hasConcurrentFeature(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)
}
