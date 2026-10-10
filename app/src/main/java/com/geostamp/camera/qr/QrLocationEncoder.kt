package com.geostamp.camera.qr

import com.geostamp.camera.maps.LocationUriBuilder
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** A QR symbol as dark/light modules, without the quiet zone. */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    init {
        require(dark.size == size * size)
    }

    fun isDark(x: Int, y: Int): Boolean = dark[y * size + x]

    /** Modules across including the mandatory light border on both sides. */
    val sizeWithQuietZone: Int get() = size + 2 * QrLocationEncoder.QUIET_ZONE_MODULES
}

/**
 * Turns a coordinate into a QR code that opens that exact spot. Encoding happens on the device; nothing
 * is sent anywhere. The payload is a plain Google Maps search link, which Android Camera, Google Lens,
 * the iPhone Camera and ordinary QR readers all offer to open.
 */
object QrLocationEncoder {
    /** The ISO 18004 minimum light border, in modules. */
    const val QUIET_ZONE_MODULES = 4

    /**
     * Level Q restores up to about 25% damage, enough for JPEG and messaging-app recompression,
     * while keeping the symbol small enough (about 41 modules) to stay legible on a stamp.
     */
    val ERROR_CORRECTION: ErrorCorrectionLevel = ErrorCorrectionLevel.Q

    private var cached: Pair<String, QrMatrix>? = null

    fun payload(latitude: Double, longitude: Double): String? =
        if (LocationUriBuilder.isValid(latitude, longitude)) LocationUriBuilder.googleMaps(latitude, longitude) else null

    fun encode(latitude: Double, longitude: Double): QrMatrix? = payload(latitude, longitude)?.let(::encode)

    @Synchronized
    fun encode(payload: String): QrMatrix {
        cached?.let { (text, matrix) -> if (text == payload) return matrix }
        val code = Encoder.encode(payload, ERROR_CORRECTION, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
        val bytes = code.matrix
        val size = bytes.width
        val dark = BooleanArray(size * size) { index -> bytes.get(index % size, index / size).toInt() == 1 }
        return QrMatrix(size, dark).also { cached = payload to it }
    }
}
