package com.geostamp.camera.environment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.util.Log
import com.geostamp.camera.maps.MapStyle
import com.geostamp.camera.maps.MapType
import com.geostamp.camera.maps.TileSource
import java.io.File
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Builds a real map thumbnail centered on a coordinate from keyless tile providers (see [TileSource]).
 * Tiles are cached on disk per provider to respect the providers' usage policies. When the selected
 * [MapType] cannot be loaded, the renderer falls back to [MapType.fallback]; if that fails too it throws,
 * and the stamp shows its coordinate panel instead of a map.
 */
class MapTileRenderer(
    private val cacheDir: File,
    private val fetch: (String) -> ByteArray = HttpClient::get
) {
    fun render(latitude: Double, longitude: Double, type: MapType = MapType.DEFAULT): Bitmap {
        var attempt: MapType? = type
        var lastError: Throwable? = null
        while (attempt != null) {
            try {
                return render(latitude, longitude, attempt.style)
            } catch (error: Exception) {
                Log.w(TAG, "Map type $attempt unavailable", error)
                lastError = error
                attempt = attempt.fallback
            }
        }
        throw lastError ?: IllegalStateException("No map type available")
    }

    private fun render(latitude: Double, longitude: Double, style: MapStyle): Bitmap {
        val output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.rgb(236, 232, 224))
        try {
            drawLayer(canvas, latitude, longitude, style.zoom, style.base, alpha = 1f)
            style.overlay?.let { drawLayer(canvas, latitude, longitude, style.zoom, it, style.overlayAlpha) }
        } catch (error: Exception) {
            output.recycle()
            throw error
        }
        drawAttribution(canvas, style.attribution)
        return output
    }

    private fun drawLayer(canvas: Canvas, latitude: Double, longitude: Double, zoom: Int, source: TileSource, alpha: Float) {
        val center = worldPixel(latitude, longitude, zoom)
        val left = center.first - TILE_SIZE / 2.0
        val top = center.second - TILE_SIZE / 2.0
        val firstX = floor(left / TILE_SIZE).toInt()
        val firstY = floor(top / TILE_SIZE).toInt()
        val maxIndex = (1 shl zoom) - 1
        val scale = OUTPUT_SIZE.toFloat() / TILE_SIZE
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = (alpha * 255).roundToInt() }
        for (tileX in firstX..firstX + 1) {
            for (tileY in firstY..firstY + 1) {
                if (tileY !in 0..maxIndex) continue
                val wrappedX = ((tileX % (maxIndex + 1)) + maxIndex + 1) % (maxIndex + 1)
                val tile = loadTile(source, zoom, wrappedX, tileY)
                val dx = ((tileX * TILE_SIZE - left) * scale).toFloat()
                val dy = ((tileY * TILE_SIZE - top) * scale).toFloat()
                canvas.drawBitmap(tile, Rect(0, 0, tile.width, tile.height), RectF(dx, dy, dx + TILE_SIZE * scale, dy + TILE_SIZE * scale), paint)
                tile.recycle()
            }
        }
    }

    /** Providers require visible credit; it is part of the thumbnail so every stamp that shows a map carries it. */
    private fun drawAttribution(canvas: Canvas, text: String) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = OUTPUT_SIZE * 0.06f
            color = Color.argb(225, 40, 40, 40)
        }
        val pad = OUTPUT_SIZE * 0.02f
        val textWidth = paint.measureText(text).coerceAtMost(OUTPUT_SIZE - 2 * pad)
        canvas.drawRect(
            OUTPUT_SIZE - textWidth - 2 * pad,
            OUTPUT_SIZE - paint.textSize - 2 * pad,
            OUTPUT_SIZE.toFloat(),
            OUTPUT_SIZE.toFloat(),
            Paint().apply { color = Color.argb(170, 255, 255, 255) }
        )
        canvas.drawText(text, OUTPUT_SIZE - textWidth - pad, OUTPUT_SIZE - pad * 1.6f, paint)
    }

    fun clearCache() {
        File(cacheDir, CACHE_FOLDER).deleteRecursively()
    }

    private fun loadTile(source: TileSource, zoom: Int, x: Int, y: Int): Bitmap {
        val file = File(cacheDir, "$CACHE_FOLDER/${source.folder}/$zoom/$x/$y.${source.extension}")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < CACHE_MAX_AGE_MILLIS
        if (!fresh) {
            val bytes = fetch(source.url(zoom, x, y))
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
        return BitmapFactory.decodeFile(file.absolutePath) ?: run {
            file.delete()
            error("Unreadable map tile")
        }
    }

    companion object {
        private const val TAG = "MapTileRenderer"
        const val TILE_SIZE = 256

        /** Thumbnails are drawn at twice the tile size so the attribution text stays sharp on large photos. */
        const val OUTPUT_SIZE = 512
        private const val CACHE_FOLDER = "map_tiles"
        private const val CACHE_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** Web Mercator pixel coordinates at [zoom]. */
        fun worldPixel(latitude: Double, longitude: Double, zoom: Int): Pair<Double, Double> {
            val scale = TILE_SIZE * (1 shl zoom).toDouble()
            val x = (longitude + 180.0) / 360.0 * scale
            val latRad = Math.toRadians(latitude.coerceIn(-85.0511, 85.0511))
            val y = (1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * scale
            return x to y
        }
    }
}
