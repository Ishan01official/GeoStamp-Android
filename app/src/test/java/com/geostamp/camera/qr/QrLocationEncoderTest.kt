package com.geostamp.camera.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrLocationEncoderTest {
    @Test
    fun payloadIsAPlainGoogleMapsLinkWithTheExactCoordinates() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=29.007953,77.767663",
            QrLocationEncoder.payload(29.007953, 77.767663)
        )
    }

    @Test
    fun noPayloadForInvalidCoordinates() {
        assertNull(QrLocationEncoder.payload(Double.NaN, 10.0))
        assertNull(QrLocationEncoder.encode(100.0, 10.0))
    }

    @Test
    fun usesQuartileErrorCorrectionAndStandardQuietZone() {
        assertEquals(ErrorCorrectionLevel.Q, QrLocationEncoder.ERROR_CORRECTION)
        assertEquals(4, QrLocationEncoder.QUIET_ZONE_MODULES)
        val matrix = QrLocationEncoder.encode(-33.856784, -151.215297)!!
        assertEquals(matrix.size + 8, matrix.sizeWithQuietZone)
        // Small enough to stay legible on a stamp after resizing.
        assertTrue("version too large: ${matrix.size}", matrix.size <= 45)
    }

    @Test
    fun encodedCodeScansBackToTheSameLink() {
        val coordinates = listOf(29.007953 to 77.767663, -33.856784 to -151.215297, 0.0 to 0.0, 89.999999 to -179.999999)
        for ((lat, lon) in coordinates) {
            val matrix = QrLocationEncoder.encode(lat, lon)!!
            assertEquals(QrLocationEncoder.payload(lat, lon), decode(matrix, modulePx = 4))
        }
    }

    @Test
    fun stillScansWhenSmallBlurredAndNoisyLikeARecompressedPhoto() {
        val matrix = QrLocationEncoder.encode(48.85837, 2.294481)!!
        val expected = QrLocationEncoder.payload(48.85837, 2.294481)
        // A stamp QR is at least 18% of the short side: about 4 px per module after a messaging app shrinks a photo to 1600 px.
        for (modulePx in 3..6) assertEquals(expected, decode(matrix, modulePx, noise = 12))
        // ZXing's reference reader is stricter about soft edges than phone scanners; these sizes are deterministic with it.
        for (modulePx in listOf(3, 6)) assertEquals(expected, decode(matrix, modulePx, noise = 12, blur = true))
    }

    /** Rasterizes like [QrDrawing] (white quiet zone, black modules) and decodes with ZXing's reader. */
    private fun decode(matrix: QrMatrix, modulePx: Int, noise: Int = 0, blur: Boolean = false): String {
        val side = matrix.sizeWithQuietZone * modulePx
        val random = Random(42)
        val pixels = IntArray(side * side) { index ->
            val mx = index % side / modulePx - QrLocationEncoder.QUIET_ZONE_MODULES
            val my = index / side / modulePx - QrLocationEncoder.QUIET_ZONE_MODULES
            val dark = mx in 0 until matrix.size && my in 0 until matrix.size && matrix.isDark(mx, my)
            val base = if (dark) 0 else 255
            val v = (base + if (noise > 0) random.nextInt(-noise, noise + 1) else 0).coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val source = if (blur) boxBlur(pixels, side) else pixels
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, source)))
        return QRCodeReader().decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to false)).text
    }

    /** 3x3 box blur: the softening a downscale and JPEG re-encode apply to module edges. */
    private fun boxBlur(pixels: IntArray, side: Int): IntArray = IntArray(pixels.size) { index ->
        val x = index % side
        val y = index / side
        var sum = 0
        var count = 0
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until side && ny in 0 until side) {
                sum += pixels[ny * side + nx] and 0xFF
                count++
            }
        }
        val v = sum / count
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
}
