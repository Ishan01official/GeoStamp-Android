package com.geostamp.camera.diagnostics

import android.hardware.camera2.CameraCharacteristics
import com.geostamp.camera.camera.CameraCapabilities
import com.geostamp.camera.sensors.SensorCapabilities

/** Plain-text device report: what the phone says it can do, and what GeoStamp observed at runtime. */
data class DiagnosticSection(val title: String, val rows: List<Pair<String, String>>)

object DiagnosticReport {
    fun build(
        device: String,
        android: String,
        cameras: CameraCapabilities,
        dualResults: Map<String, String>,
        sensors: SensorCapabilities
    ): List<DiagnosticSection> = listOf(
        DiagnosticSection("Device", listOf("Model" to device, "Android" to android)),
        DiagnosticSection(
            "Cameras",
            cameras.cameras.map { camera ->
                "Camera ${camera.id}" to listOf(
                    facing(camera.lensFacing),
                    if (camera.hasFlash) "flash" else "no flash",
                    "level ${level(camera.hardwareLevel)}",
                    "sensor ${camera.sensorOrientation ?: "?"}°"
                ).joinToString(" · ")
            }
        ),
        DiagnosticSection(
            "Dual Capture",
            listOf(
                "Concurrent camera feature" to yesNo(cameras.concurrentFeature),
                "Concurrent camera sets" to cameras.concurrentCameraIdSets.joinToString(" ") { set -> set.sorted().joinToString("+", "[", "]") }.ifEmpty { "none" },
                "Front + rear pairs" to cameras.frontRearPairs.joinToString(" ") { (back, front) -> "rear $back + front $front" }.ifEmpty { "none" },
                "Dual Photo / Dual Video" to (cameras.dualUnsupportedReason?.let { "not supported ($it)" } ?: "supported"),
            ) + dualResults.map { (mode, result) -> "Last start $mode" to result }
        ),
        DiagnosticSection(
            "Motion sensors",
            listOf(
                "Accelerometer" to yesNo(sensors.accelerometer),
                "Magnetometer" to yesNo(sensors.magnetometer),
                "Rotation vector" to yesNo(sensors.rotationVector),
                "Game rotation vector" to yesNo(sensors.gameRotationVector),
                "Compass source" to sensors.compassSource.name
            )
        )
    )

    fun asText(sections: List<DiagnosticSection>): String =
        sections.joinToString("\n\n") { section ->
            section.title + "\n" + section.rows.joinToString("\n") { (label, value) -> "  $label: $value" }
        }

    private fun facing(value: Int?) = when (value) {
        CameraCharacteristics.LENS_FACING_BACK -> "rear"
        CameraCharacteristics.LENS_FACING_FRONT -> "front"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
        else -> "unknown"
    }

    private fun level(value: Int?) = when (value) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "?"
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}
