package com.geostamp.camera.environment

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTPS GET used only by enabled online services. Never called for offline capture. */
internal object HttpClient {
    /** Tile providers' usage policies ask for an identifying user agent with a contact URL. */
    private val USER_AGENT = "GeoStamp-Android/${com.geostamp.camera.BuildConfig.VERSION_NAME} (+https://github.com/Ishan01official/GeoStamp-Android)"
    private const val TIMEOUT_MILLIS = 6_000

    fun get(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            check(code in 200..299) { "HTTP $code" }
            return connection.inputStream.use { input ->
                ByteArrayOutputStream().also { input.copyTo(it) }.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
}
