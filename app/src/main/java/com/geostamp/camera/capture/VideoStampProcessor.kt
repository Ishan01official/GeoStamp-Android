package com.geostamp.camera.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import com.geostamp.camera.stamps.StampContentBuilder
import com.geostamp.camera.stamps.StampData
import com.geostamp.camera.stamps.StampPreferences
import com.geostamp.camera.stamps.StampRenderer
import com.geostamp.camera.stamps.StampStyle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class VideoStampSample(
    val elapsedMillis: Long,
    val data: StampData,
    val map: Bitmap?,
    val logo: Bitmap?
)

data class VideoStampRequest(
    val startedAtMillis: Long,
    val preferences: StampPreferences,
    val samples: List<VideoStampSample>
) {
    fun sampleAtElapsed(elapsedMillis: Long): VideoStampSample? =
        samples.lastOrNull { it.elapsedMillis <= elapsedMillis } ?: samples.firstOrNull()
}

data class ProcessedVideo(val uri: Uri, val width: Int, val height: Int, val durationMillis: Long)

/**
 * Fallback stamped-video pipeline. It decodes representative frames, burns the same stamp card
 * used for photos into each encoded frame, then copies the original audio samples unchanged.
 */
class VideoStampProcessor(
    private val context: Context,
    private val writer: MediaStoreWriter,
    private val contentBuilder: () -> StampContentBuilder,
    private val renderer: () -> StampRenderer
) {
    fun process(inputUri: Uri, request: VideoStampRequest, saveOriginal: Boolean): ProcessedVideo {
        val retriever = MediaMetadataRetriever()
        var outputUri: Uri? = null
        try {
            retriever.setDataSource(context, inputUri)
            val metadata = VideoMetadata.from(retriever)
            val firstFrame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: error("Could not decode first video frame")
            val orientation = Orientation.from(metadata.rotationDegrees, firstFrame, metadata)
            val outputName = MediaStoreWriter.videoDisplayName(
                PhotoProcessor.NAME_PREFIX,
                request.startedAtMillis,
                STAMPED_SUFFIX
            )
            val targetUri = writer.createPendingVideo(outputName, request.startedAtMillis)
            outputUri = targetUri

            writer.openFileDescriptor(targetUri, "rw").use { descriptor ->
                encodeStampedVideo(
                    inputUri = inputUri,
                    retriever = retriever,
                    firstFrame = firstFrame,
                    metadata = metadata,
                    orientation = orientation,
                    request = request,
                    muxer = MediaMuxer(descriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                )
            }
            writer.publishVideo(targetUri)
            if (!saveOriginal) writer.delete(inputUri)
            return ProcessedVideo(targetUri, orientation.width, orientation.height, metadata.durationUs / 1_000L)
        } catch (error: Throwable) {
            outputUri?.let { writer.delete(it) }
            throw error
        } finally {
            retriever.release()
        }
    }

    private fun encodeStampedVideo(
        inputUri: Uri,
        retriever: MediaMetadataRetriever,
        firstFrame: Bitmap,
        metadata: VideoMetadata,
        orientation: Orientation,
        request: VideoStampRequest,
        muxer: MediaMuxer
    ) {
        val audio = audioFormat(inputUri)
        val audioTrackIndex = audio?.let { muxer.addTrack(it.format) } ?: NO_TRACK
        val encoder = MediaCodec.createEncoderByType(VIDEO_MIME)
        val renderBitmap = Bitmap.createBitmap(orientation.width, orientation.height, Bitmap.Config.ARGB_8888)
        val renderCanvas = Canvas(renderBitmap)
        val codecFormat = MediaFormat.createVideoFormat(VIDEO_MIME, orientation.width, orientation.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, metadata.bitRate ?: estimateBitRate(orientation.width, orientation.height, metadata.frameRate))
            setInteger(MediaFormat.KEY_FRAME_RATE, metadata.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
        }

        var encoderSurface: Surface? = null
        var eglSurface: EncoderSurface? = null
        try {
            encoder.configure(codecFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderSurface = encoder.createInputSurface()
            eglSurface = EncoderSurface(encoderSurface, orientation.width, orientation.height)
            encoder.start()
            val state = MuxerState(muxer = muxer, audioTrackIndex = audioTrackIndex)
            val frameIntervalUs = 1_000_000L / metadata.frameRate
            val frameCount = max(1, ceil(metadata.durationUs / frameIntervalUs.toDouble()).toInt())

            for (index in 0 until frameCount) {
                val presentationTimeUs = min(index * frameIntervalUs, max(0L, metadata.durationUs - 1L))
                val source = if (index == 0) {
                    firstFrame
                } else {
                    retriever.getFrameAtTime(presentationTimeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                }
                drawSourceFrame(source, renderCanvas, orientation)
                drawStamp(renderCanvas, renderBitmap.width, renderBitmap.height, request, presentationTimeUs)
                eglSurface.draw(renderBitmap, presentationTimeUs)
                drainEncoder(encoder, state, endOfStream = false)
                if (index != 0) source.recycle()
            }

            encoder.signalEndOfInputStream()
            drainEncoder(encoder, state, endOfStream = true)
            if (audio != null) copyAudio(inputUri, audio.trackIndex, muxer, audioTrackIndex)
            muxer.stop()
        } finally {
            firstFrame.recycle()
            renderBitmap.recycle()
            runCatching { encoder.stop() }
            encoder.release()
            eglSurface?.release()
            encoderSurface?.release()
            muxer.release()
        }
    }

    private fun drawSourceFrame(source: Bitmap, canvas: Canvas, orientation: Orientation) {
        canvas.drawColor(Color.BLACK)
        if (!orientation.applyRotation || source.width == orientation.width && source.height == orientation.height) {
            canvas.drawBitmap(source, null, RectF(0f, 0f, orientation.width.toFloat(), orientation.height.toFloat()), null)
            return
        }

        canvas.save()
        when (orientation.rotationDegrees) {
            90 -> {
                canvas.translate(orientation.width.toFloat(), 0f)
                canvas.rotate(90f)
                canvas.drawBitmap(source, null, RectF(0f, 0f, orientation.height.toFloat(), orientation.width.toFloat()), null)
            }
            180 -> {
                canvas.translate(orientation.width.toFloat(), orientation.height.toFloat())
                canvas.rotate(180f)
                canvas.drawBitmap(source, null, RectF(0f, 0f, orientation.width.toFloat(), orientation.height.toFloat()), null)
            }
            270 -> {
                canvas.translate(0f, orientation.height.toFloat())
                canvas.rotate(270f)
                canvas.drawBitmap(source, null, RectF(0f, 0f, orientation.height.toFloat(), orientation.width.toFloat()), null)
            }
            else -> canvas.drawBitmap(source, null, RectF(0f, 0f, orientation.width.toFloat(), orientation.height.toFloat()), null)
        }
        canvas.restore()
    }

    private fun drawStamp(
        canvas: Canvas,
        width: Int,
        height: Int,
        request: VideoStampRequest,
        presentationTimeUs: Long
    ) {
        val sample = request.sampleAtElapsed(presentationTimeUs / 1_000L)
        val data = sample?.data ?: StampData(capturedAtMillis = request.startedAtMillis + presentationTimeUs / 1_000L)
        val content = contentBuilder().build(
            data = data,
            preferences = request.preferences,
            mapAvailable = sample?.map != null,
            logoAvailable = sample?.logo != null
        )
        renderer().render(canvas, width, height, content, StampStyle.from(request.preferences), sample?.map, sample?.logo)
    }

    private fun drainEncoder(encoder: MediaCodec, state: MuxerState, endOfStream: Boolean) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            when (val status = encoder.dequeueOutputBuffer(info, ENCODER_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!state.started) { "Video format changed twice" }
                    state.videoTrackIndex = state.muxer.addTrack(encoder.outputFormat)
                    state.muxer.start()
                    state.started = true
                }
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> if (status >= 0) {
                    val buffer = encoder.getOutputBuffer(status) ?: error("Encoder output buffer $status was null")
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0) {
                        check(state.started) { "Muxer has not started" }
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        state.muxer.writeSampleData(state.videoTrackIndex, buffer, info)
                    }
                    val reachedEnd = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(status, false)
                    if (reachedEnd) return
                }
            }
        }
    }

    private fun copyAudio(inputUri: Uri, sourceTrack: Int, muxer: MediaMuxer, targetTrack: Int) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, inputUri, null)
            extractor.selectTrack(sourceTrack)
            val maxInputSize = extractor.getTrackFormat(sourceTrack)
                .getIntegerOrDefault(MediaFormat.KEY_MAX_INPUT_SIZE, DEFAULT_AUDIO_BUFFER_SIZE)
            val buffer = ByteBuffer.allocateDirect(maxInputSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, extractor.sampleFlags.toMuxerFlags())
                muxer.writeSampleData(targetTrack, buffer, info)
                extractor.advance()
            }
        } finally {
            extractor.release()
        }
    }

    private fun audioFormat(inputUri: Uri): TrackFormat? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, inputUri, null)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) return TrackFormat(index, format)
            }
            return null
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int =
        if (containsKey(key)) getInteger(key) else default

    private fun estimateBitRate(width: Int, height: Int, frameRate: Int): Int =
        (width * height * frameRate * BITS_PER_PIXEL).toInt().coerceIn(MIN_VIDEO_BIT_RATE, MAX_VIDEO_BIT_RATE)

    private fun Int.toMuxerFlags(): Int {
        check(this and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Encrypted audio samples are not supported" }
        var flags = 0
        if (this and MediaExtractor.SAMPLE_FLAG_SYNC != 0) flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        if (this and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        return flags
    }

    private data class TrackFormat(val trackIndex: Int, val format: MediaFormat)

    private data class MuxerState(
        val muxer: MediaMuxer,
        val audioTrackIndex: Int,
        var videoTrackIndex: Int = NO_TRACK,
        var started: Boolean = false
    )

    private data class VideoMetadata(
        val width: Int,
        val height: Int,
        val rotationDegrees: Int,
        val durationUs: Long,
        val frameRate: Int,
        val bitRate: Int?
    ) {
        companion object {
            fun from(retriever: MediaMetadataRetriever): VideoMetadata {
                val width = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).coerceAtLeast(1)
                val height = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).coerceAtLeast(1)
                val durationMs = retriever.longMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).coerceAtLeast(1L)
                return VideoMetadata(
                    width = width,
                    height = height,
                    rotationDegrees = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
                    durationUs = durationMs * 1_000L,
                    frameRate = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE).takeIf { it > 0 }
                        ?: DEFAULT_FRAME_RATE,
                    bitRate = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE).takeIf { it > 0 }
                )
            }
        }
    }

    private data class Orientation(
        val width: Int,
        val height: Int,
        val rotationDegrees: Int,
        val applyRotation: Boolean
    ) {
        companion object {
            fun from(rotationDegrees: Int, frame: Bitmap, metadata: VideoMetadata): Orientation {
                val normalized = ((rotationDegrees % 360) + 360) % 360
                val rotatedDisplay = normalized == 90 || normalized == 270
                val displayWidth = if (rotatedDisplay) metadata.height else metadata.width
                val displayHeight = if (rotatedDisplay) metadata.width else metadata.height
                val retrieverAlreadyRotated = frame.width == displayWidth && frame.height == displayHeight
                return Orientation(
                    width = if (retrieverAlreadyRotated) frame.width else displayWidth,
                    height = if (retrieverAlreadyRotated) frame.height else displayHeight,
                    rotationDegrees = normalized,
                    applyRotation = !retrieverAlreadyRotated && normalized != 0
                )
            }
        }
    }

    private class EncoderSurface(surface: Surface, private val width: Int, private val height: Int) {
        private val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private val context: android.opengl.EGLContext
        private val eglSurface: android.opengl.EGLSurface
        private val renderer: TextureRenderer

        init {
            check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL init failed" }
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val configCount = IntArray(1)
            val attributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
            check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, configs.size, configCount, 0)) {
                "EGL config selection failed"
            }
            val config = configs[0] ?: error("No EGL config")
            context = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0
            )
            eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
            makeCurrent()
            renderer = TextureRenderer()
        }

        fun draw(bitmap: Bitmap, presentationTimeUs: Long) {
            makeCurrent()
            GLES20.glViewport(0, 0, width, height)
            renderer.draw(bitmap)
            EGLExt.eglPresentationTimeANDROID(display, eglSurface, presentationTimeUs * 1_000L)
            check(EGL14.eglSwapBuffers(display, eglSurface)) { "EGL swap failed" }
        }

        fun release() {
            EGL14.eglDestroySurface(display, eglSurface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }

        private fun makeCurrent() {
            check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "EGL make current failed" }
        }
    }

    private class TextureRenderer {
        private val vertexBuffer = floatBuffer(
            floatArrayOf(
                -1f, -1f, 0f, 1f,
                1f, -1f, 1f, 1f,
                -1f, 1f, 0f, 0f,
                1f, 1f, 1f, 0f
            )
        )
        private val program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        private val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        private val texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        private val textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        private val textureId = IntArray(1).also {
            GLES20.glGenTextures(1, it, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, it[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }[0]

        fun draw(bitmap: Bitmap) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glUniform1i(textureHandle, 0)
            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, FLOAT_STRIDE_BYTES, vertexBuffer)
            vertexBuffer.position(2)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, FLOAT_STRIDE_BYTES, vertexBuffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)
        }
    }

    companion object {
        private const val VIDEO_MIME = "video/avc"
        private const val STAMPED_SUFFIX = "_stamped"
        private const val DEFAULT_FRAME_RATE = 30
        private const val I_FRAME_INTERVAL_SECONDS = 1
        private const val ENCODER_TIMEOUT_US = 10_000L
        private const val NO_TRACK = -1
        private const val DEFAULT_AUDIO_BUFFER_SIZE = 256 * 1024
        private const val BITS_PER_PIXEL = 0.18f
        private const val MIN_VIDEO_BIT_RATE = 2_000_000
        private const val MAX_VIDEO_BIT_RATE = 20_000_000
        private const val EGL_RECORDABLE_ANDROID = 0x3142
        private const val FLOAT_STRIDE_BYTES = 4 * 4

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """

        private fun MediaMetadataRetriever.intMetadata(key: Int): Int =
            extractMetadata(key)?.toFloatOrNull()?.toInt() ?: 0

        private fun MediaMetadataRetriever.longMetadata(key: Int): Long =
            extractMetadata(key)?.toLongOrNull() ?: 0L

        private fun floatBuffer(values: FloatArray) =
            ByteBuffer.allocateDirect(values.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(values)
                    position(0)
                }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { "GL program link failed: ${GLES20.glGetProgramInfoLog(program)}" }
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            return program
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { "GL shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}" }
            return shader
        }
    }
}
