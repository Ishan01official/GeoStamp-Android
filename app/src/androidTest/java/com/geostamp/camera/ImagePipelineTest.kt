package com.geostamp.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.geostamp.camera.capture.CapturedImageDecoder
import com.geostamp.camera.capture.SaveOptions
import com.geostamp.camera.capture.StampRequest
import com.geostamp.camera.location.LocationStamp
import com.geostamp.camera.stamps.StampData
import com.geostamp.camera.stamps.StampPreferences
import com.geostamp.camera.stamps.StampTemplate
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the capture pipeline keeps the photographed scene: decode -> rotate -> stamp -> JPEG -> MediaStore.
 * Regression guard for "saved photo is black" reports.
 */
@RunWith(AndroidJUnit4::class)
class ImagePipelineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container = context.appContainer
    private val location = LocationStamp(29.007953, 77.767663, 5f, System.currentTimeMillis(), 220.0, null, "gps")

    private fun scene(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width) {
                setPixel(x, y, if (x < width / 2) Color.rgb(220, 60, 40) else Color.rgb(40, 180, 230))
            }
        }

    @Test
    fun rotationPutsLeftEdgeOnTopForQuarterTurn() {
        val source = scene(40, 20)
        val rotated = CapturedImageDecoder.rotateUpright(source, 90)
        assertEquals(20, rotated.width)
        assertEquals(40, rotated.height)
        assertTrue(rotated.isMutable)
        // Clockwise rotation: the source's left (red) half becomes the top half.
        assertEquals(Color.rgb(220, 60, 40), rotated.getPixel(10, 5))
        assertEquals(Color.rgb(40, 180, 230), rotated.getPixel(10, 35))
    }

    @Test
    fun jpegDecodeKeepsBrightness() {
        val bytes = ByteArrayOutputStream().also { scene(400, 300).compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
        val decoded = CapturedImageDecoder.decodeJpeg(bytes)
        assertTrue(decoded.isMutable)
        assertTrue("mean luminance ${meanLuma(decoded)}", meanLuma(decoded) > 100)
    }

    @Test
    fun stampOnlyCoversTheCardAndSceneStaysVisible() {
        val bitmap = scene(3000, 4000)
        val before = bitmap.getPixel(100, 100)
        container.photoProcessor.stamp(
            bitmap,
            StampRequest(StampData(System.currentTimeMillis(), location), StampPreferences(template = StampTemplate.PROFESSIONAL), null, null)
        )
        assertEquals("top of the photo must be untouched", before, bitmap.getPixel(100, 100))
        assertEquals(bitmap.getPixel(2900, 1500), Color.rgb(40, 180, 230))
        // Card is translucent: scene colour still bleeds through near its corner, so it is not opaque black.
        val cardPixel = bitmap.getPixel(200, 3850)
        assertNotEquals(Color.BLACK, cardPixel)
        assertTrue("card should darken the scene", Color.red(cardPixel) < 220)
        assertTrue("card must not be opaque", Color.red(cardPixel) > 40)
    }

    @Test
    fun savedPhotoContainsTheScene() {
        val bitmap = scene(1200, 1600)
        val result = container.photoProcessor.process(
            upright = bitmap,
            originalJpeg = null,
            originalRotationDegrees = 0,
            request = StampRequest(StampData(System.currentTimeMillis(), location), StampPreferences(), null, null),
            options = SaveOptions(jpegQuality = 90, saveOriginal = false)
        )
        try {
            val saved = context.contentResolver.openInputStream(result.uri)!!.use { BitmapFactory.decodeStream(it) }
            assertEquals(1200, saved.width)
            assertEquals(1600, saved.height)
            assertTrue("saved photo mean luminance ${meanLuma(saved)}", meanLuma(saved) > 90)
            val top = saved.getPixel(100, 100)
            assertTrue(Color.red(top) > 180 && Color.green(top) < 100)
        } finally {
            context.contentResolver.delete(result.uri, null, null)
        }
    }

    private fun meanLuma(bitmap: Bitmap): Double {
        var sum = 0.0
        var count = 0
        val step = maxOf(1, bitmap.width / 50)
        for (y in 0 until bitmap.height step step) for (x in 0 until bitmap.width step step) {
            val p = bitmap.getPixel(x, y)
            sum += 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
            count++
        }
        return sum / count
    }
}
