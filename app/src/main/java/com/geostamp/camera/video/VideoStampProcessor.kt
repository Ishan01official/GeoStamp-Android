package com.geostamp.camera.video

import android.content.Context
import android.graphics.Canvas
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.geostamp.camera.stamps.StampContentBuilder
import com.geostamp.camera.stamps.StampData
import com.geostamp.camera.stamps.StampRenderer
import com.geostamp.camera.stamps.StampStyle
import com.google.common.collect.ImmutableList
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Burns the photo stamp into every frame of a recorded video with AndroidX Media3 Transformer.
 * Decoding, GPU compositing and encoding run on Media3's own threads; audio is passed through unchanged
 * and orientation is preserved (frames are composed upright, rotation is written back as metadata).
 */
@UnstableApi
class VideoStampProcessor(
    private val context: Context,
    private val contentBuilder: () -> StampContentBuilder,
    private val renderer: () -> StampRenderer
) {
    /** Writes the stamped copy to [output]; [input] is never modified. Cancelling the coroutine cancels the export. */
    suspend fun stamp(input: File, output: File, inputInfo: VideoFileInfo, request: VideoStampRequest, onProgress: (Int) -> Unit) {
        val builder = contentBuilder()
        val stampRenderer = renderer()
        val style = StampStyle.from(request.preferences)
        val overlay = VideoStampOverlay(inputInfo.width, inputInfo.height) { canvas, width, height, presentationTimeUs ->
            drawStamp(canvas, width, height, presentationTimeUs, request, builder, stampRenderer, style)
        }
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
            .setEffects(Effects(emptyList(), listOf(OverlayEffect(ImmutableList.of<TextureOverlay>(overlay)))))
            .build()

        // Transformer must be created, started, polled and cancelled on one looper thread.
        withContext(Dispatchers.Main) {
            coroutineScope {
                val done = kotlinx.coroutines.CompletableDeferred<Unit>()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder().setBitrate(targetBitrate(inputInfo)).build()
                            )
                            .setEnableFallback(true)
                            .build()
                    )
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            done.complete(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            done.completeExceptionally(exportException)
                        }
                    })
                    .build()
                val poller = launch {
                    val holder = ProgressHolder()
                    while (true) {
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                        delay(PROGRESS_POLL_MILLIS)
                    }
                }
                try {
                    transformer.start(item, output.absolutePath)
                    suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation { transformer.cancel() }
                        done.invokeOnCompletion { error ->
                            if (error == null) continuation.resume(Unit) else continuation.resumeWithException(error)
                        }
                    }
                    onProgress(100)
                } finally {
                    poller.cancel()
                }
            }
        }
    }

    private fun drawStamp(
        canvas: Canvas,
        width: Int,
        height: Int,
        presentationTimeUs: Long,
        request: VideoStampRequest,
        builder: StampContentBuilder,
        stampRenderer: StampRenderer,
        style: StampStyle
    ) {
        val sample = request.sampleAtElapsed(presentationTimeUs / 1_000L)
        val data = (sample?.data ?: StampData(capturedAtMillis = request.startedAtMillis))
            .copy(capturedAtMillis = request.stampTimeAt(presentationTimeUs))
        val content = builder.build(data, request.preferences, mapAvailable = sample?.map != null, logoAvailable = sample?.logo != null)
        stampRenderer.render(canvas, width, height, content, style, sample?.map, sample?.logo)
    }

    /** Keeps roughly the recording's quality: about 0.15 bits per pixel per frame at 30 fps, within sane bounds. */
    private fun targetBitrate(info: VideoFileInfo): Int =
        (info.width.toLong() * info.height * FRAME_RATE * BITS_PER_PIXEL).toInt().coerceIn(MIN_BITRATE, MAX_BITRATE)

    private companion object {
        const val PROGRESS_POLL_MILLIS = 250L
        const val FRAME_RATE = 30
        const val BITS_PER_PIXEL = 0.25
        const val MIN_BITRATE = 4_000_000
        const val MAX_BITRATE = 20_000_000
    }
}
