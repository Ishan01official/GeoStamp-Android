package com.geostamp.camera.environment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import java.io.File
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Builds a real map thumbnail centered on a coordinate from OpenStreetMap tiles.
 * Tiles are cached on disk to respect the OSM tile usage policy.
 */
class MapTileRenderer(
    private val cacheDir: File,
    private val fetch: (String) -> ByteArray = HttpClient::get
) {
    fun render(latitude: Double, longitude: Double, zoom: Int = DEFAULT_ZOOM): Bitmap {
        val center = worldPixel(latitude, longitude, zoom)
        val left = center.first - TILE_SIZE / 2.0
        val top = center.second - TILE_SIZE / 2.0
        val output = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val firstX = floor(left / TILE_SIZE).toInt()
        val firstY = floor(top / TILE_SIZE).toInt()
        val maxIndex = (1 shl zoom) - 1
        for (tileX in firstX..firstX + 1) {
            for (tileY in firstY..firstY + 1) {
                if (tileY !in 0..maxIndex) continue
                val wrappedX = ((tileX % (maxIndex + 1)) + maxIndex + 1) % (maxIndex + 1)
                val tile = loadTile(zoom, wrappedX, tileY)
                canvas.drawBitmap(tile, (tileX * TILE_SIZE - left).toFloat(), (tileY * TILE_SIZE - top).toFloat(), null)
                tile.recycle()
            }
        }
        return output
    }

    fun clearCache() {
        File(cacheDir, CACHE_FOLDER).deleteRecursively()
    }

    private fun loadTile(zoom: Int, x: Int, y: Int): Bitmap {
        val file = File(cacheDir, "$CACHE_FOLDER/$zoom/$x/$y.png")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < CACHE_MAX_AGE_MILLIS
        if (!fresh) {
            val bytes = fetch("https://tile.openstreetmap.org/$zoom/$x/$y.png")
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
        return BitmapFactory.decodeFile(file.absolutePath) ?: error("Unreadable map tile")
    }

    companion object {
        const val TILE_SIZE = 256
        const val DEFAULT_ZOOM = 16
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
