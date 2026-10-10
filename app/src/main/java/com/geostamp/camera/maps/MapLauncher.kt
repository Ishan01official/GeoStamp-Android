package com.geostamp.camera.maps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.geostamp.camera.settings.MapLinkProvider

/**
 * Opens a coordinate outside the app. For Google Maps: the Google Maps app if installed, then any app that
 * handles geo: links, then the browser. For OpenStreetMap: any geo: handler, then the OSM website.
 * Returns false instead of crashing when nothing on the phone can open a map.
 */
object MapLauncher {
    private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    fun open(context: Context, latitude: Double, longitude: Double, provider: MapLinkProvider): Boolean {
        if (!LocationUriBuilder.isValid(latitude, longitude)) return false
        val web = Uri.parse(LocationUriBuilder.web(provider, latitude, longitude))
        val geo = Uri.parse(LocationUriBuilder.geo(latitude, longitude))
        val attempts = buildList {
            if (provider == MapLinkProvider.GOOGLE_MAPS) add(view(web).setPackage(GOOGLE_MAPS_PACKAGE))
            add(view(geo))
            add(view(web))
        }
        return attempts.any { intent -> tryStart(context, intent) }
    }

    private fun view(uri: Uri) = Intent(Intent.ACTION_VIEW, uri)

    private fun tryStart(context: Context, intent: Intent): Boolean =
        try {
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
}
