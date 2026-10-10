package com.geostamp.camera

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.geostamp.camera.address.AddressSource
import com.geostamp.camera.location.LocationStamp
import com.geostamp.camera.sensors.CompassAccuracy
import com.geostamp.camera.sensors.HeadingKind
import com.geostamp.camera.sensors.HeadingSnapshot
import com.geostamp.camera.stamps.StampData
import com.geostamp.camera.stamps.StampPreferences
import com.geostamp.camera.video.VideoFileInfo
import com.geostamp.camera.video.VideoStampProcessor
import com.geostamp.camera.video.VideoStampRequest
import com.geostamp.camera.video.VideoStampSample
import com.geostamp.camera.video.VideoVerification
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the production video stamper on real CameraX recordings on the device's own codecs.
 * Inputs: Android/data/com.geostamp.camera/files/verify/input_*.mp4 (pushed by the developer).
 * Outputs: output_*.mp4 next to them, for frame-by-frame inspection on the host.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class VideoStampPipelineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resources = context.appContainer.stampResources

    @Test
    fun stampsRealRecordingsAndKeepsAudioDurationAndOrientation() = runBlocking {
        val dir = (InstrumentationRegistry.getArguments().getString("videoFixtureDirectory")?.let(::File)
            ?: File(context.getExternalFilesDir(null), "verify")).apply { mkdirs() }
        val inputs = dir.listFiles { file -> file.name.startsWith("input_") && file.name.endsWith(".mp4") }.orEmpty().sortedBy { it.name }
        assumeTrue("No input videos pushed to $dir", inputs.isNotEmpty())

        val processor = VideoStampProcessor(context, { resources.contentBuilder() }, { resources.renderer() })
        val startedAt = 1_791_000_000_000L // 2026-10-03 ~13:20 UTC: a fixed, recognisable time on every frame.
        val location = LocationStamp(29.007890, 77.767665, 4f, startedAt, 220.0, 0f, "gps")
        val samples = (0..60).map { second ->
            VideoStampSample(
                elapsedMillis = second * 1_000L,
                data = StampData(
                    capturedAtMillis = startedAt + second * 1_000L,
                    location = location,
                    heading = HeadingSnapshot(47, HeadingKind.TRUE_HEADING, CompassAccuracy.HIGH),
                    address = "Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India",
                    addressSource = AddressSource.DETECTED
                ),
                map = null,
                logo = null
            )
        }
        val request = VideoStampRequest(startedAt, StampPreferences(), samples)

        inputs.forEach { input ->
            val output = File(dir, input.name.replace("input_", "output_"))
            output.delete()
            val inputInfo = checkNotNull(VideoFileInfo.read(input)) { "Unreadable ${input.name}" }
            val began = System.nanoTime()
            processor.stamp(input, output, inputInfo, request) { }
            val seconds = (System.nanoTime() - began) / 1e9
            val outputInfo = VideoFileInfo.read(output)
            Log.i(TAG, "${input.name}: in=$inputInfo out=$outputInfo took=${"%.1f".format(seconds)}s")
            assertTrue("${input.name} output is not a faithful copy: in=$inputInfo out=$outputInfo", VideoVerification.isFaithfulCopy(inputInfo, outputInfo))
        }
    }

    private companion object {
        const val TAG = "VideoStampPipelineTest"
    }
}
