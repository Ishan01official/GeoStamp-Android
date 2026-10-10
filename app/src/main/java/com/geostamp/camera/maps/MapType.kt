package com.geostamp.camera.maps

/**
 * The look of map thumbnails on stamps. Every type is served by a keyless provider; see [MapStyle] for
 * the sources, their licences and how each type falls back when its tiles cannot be loaded.
 */
enum class MapType {
    NORMAL,
    SATELLITE,
    TERRAIN,
    HYBRID;

    /** The tile layers for this type. */
    val style: MapStyle
        get() = when (this) {
            NORMAL -> MapStyle(base = TileSource.OSM_STANDARD, zoom = 16)
            TERRAIN -> MapStyle(base = TileSource.OPEN_TOPO_MAP, zoom = 15)
            // Sentinel-2 imagery has about 10 m per pixel, so zoom 15 is the sharpest honest level.
            SATELLITE -> MapStyle(base = TileSource.EOX_CLOUDLESS, zoom = 15)
            // No keyless label-only layer exists, so roads and names come from a translucent OSM layer.
            HYBRID -> MapStyle(base = TileSource.EOX_CLOUDLESS, zoom = 15, overlay = TileSource.OSM_STANDARD, overlayAlpha = HYBRID_OVERLAY_ALPHA)
        }

    /** The type to try next when this one's tiles fail, so photo stamping never breaks. */
    val fallback: MapType? get() = if (this == NORMAL) null else NORMAL

    companion object {
        val DEFAULT = NORMAL
        const val HYBRID_OVERLAY_ALPHA = 0.45f
    }
}

data class MapStyle(
    val base: TileSource,
    val zoom: Int,
    val overlay: TileSource? = null,
    val overlayAlpha: Float = 1f
) {
    /** Short credit drawn on the thumbnail itself. Full credits are in the in-app Privacy Policy and README. */
    val attribution: String
        get() = listOfNotNull(base.attribution, overlay?.attribution?.takeIf { it != base.attribution }).joinToString(" · ")
}

/**
 * Keyless raster tile providers. All URLs for map tiles live here.
 *
 * - OpenStreetMap standard tiles: ODbL data, used under the OSM tile usage policy with a cache.
 * - OpenTopoMap: CC-BY-SA, map data from OpenStreetMap and SRTM.
 * - EOxCloudless (Sentinel-2): CC BY-NC-SA 4.0 for non-commercial use. GeoStamp is free and ad-free;
 *   commercial users who need satellite thumbnails must obtain their own licence from EOX.
 */
enum class TileSource(
    private val template: String,
    val folder: String,
    val attribution: String,
    val extension: String
) {
    OSM_STANDARD("https://tile.openstreetmap.org/{z}/{x}/{y}.png", "osm", "© OpenStreetMap", "png"),
    OPEN_TOPO_MAP("https://tile.opentopomap.org/{z}/{x}/{y}.png", "opentopomap", "© OpenStreetMap, OpenTopoMap", "png"),
    EOX_CLOUDLESS(
        "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2023_3857/default/g/{z}/{y}/{x}.jpg",
        "eox_s2cloudless_2023",
        "EOxCloudless 2023, Copernicus",
        "jpg"
    );

    fun url(zoom: Int, x: Int, y: Int): String =
        template.replace("{z}", zoom.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
}
