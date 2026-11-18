package com.example.map

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import java.io.File
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

object OsmMapManager {
    const val CACHE_LIMIT_BYTES: Long = 500L * 1024L * 1024L // 500 MB
    const val CACHE_TRIM_BYTES: Long = 450L * 1024L * 1024L
    const val PERMANENT_TILE_EXPIRATION_MS: Long = 365L * 24L * 60L * 60L * 1000L
    const val OFFLINE_MIN_ZOOM = 12
    const val OFFLINE_MAX_ZOOM = 17

    /**
     * Hue-preserving luminance inversion tuned to Pacely's Obsidian Slate palette (#0E1219).
     * Maps bright OSM land/streets to deep charcoal/obsidian while preserving water (deep navy),
     * parks (dark pine), and road casings, and lifting dark map labels to crisp off-white.
     */
    fun createDarkModeColorMatrix(): ColorMatrix {
        val contrastScale = 0.88f
        val chromaRetention = 0.72f
        val diag = contrastScale * chromaRetention + (contrastScale * (-(1f + chromaRetention) / 3f))
        val off = contrastScale * (-(1f + chromaRetention) / 3f)
        val baseOffset = 255f * contrastScale

        return ColorMatrix(
            floatArrayOf(
                diag, off, off, 0f, baseOffset + 14f,
                off, diag, off, 0f, baseOffset + 18f,
                off, off, diag, 0f, baseOffset + 25f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    fun createDarkModeColorFilter(): ColorMatrixColorFilter =
        ColorMatrixColorFilter(createDarkModeColorMatrix())

    @Volatile
    private var isInitialized = false

    fun ensureInitialized(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            val appContext = context.applicationContext
            val basePath = File(appContext.filesDir, "osmdroid").apply { mkdirs() }
            val tileCache = File(basePath, "tiles").apply { mkdirs() }

            val config = Configuration.getInstance()
            config.load(
                appContext,
                appContext.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
            )
            config.userAgentValue = appContext.packageName
            config.osmdroidBasePath = basePath
            config.osmdroidTileCache = tileCache
            config.tileFileSystemCacheMaxBytes = CACHE_LIMIT_BYTES
            config.tileFileSystemCacheTrimBytes = CACHE_TRIM_BYTES
            config.expirationOverrideDuration = PERMANENT_TILE_EXPIRATION_MS
            config.isMapViewHardwareAccelerated = true
            isInitialized = true
        }
    }

    fun computeBoundingBoxForRadiusKm(
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): BoundingBox {
        val clampedRadiusKm = radiusKm.coerceIn(0.5, 50.0)
        val radiusMeters = clampedRadiusKm * 1000.0
        val latDelta = radiusMeters / 111_320.0
        val cosLat = cos(Math.toRadians(latitude)).coerceAtLeast(0.15)
        val lonDelta = radiusMeters / (111_320.0 * cosLat)

        val north = (latitude + latDelta).coerceIn(-85.05, 85.05)
        val south = (latitude - latDelta).coerceIn(-85.05, 85.05)
        val east = (longitude + lonDelta).coerceIn(-180.0, 180.0)
        val west = (longitude - lonDelta).coerceIn(-180.0, 180.0)
        return BoundingBox(north, east, south, west)
    }

    fun latLonToTileIndex(latitude: Double, longitude: Double, zoom: Int): Long {
        val z = zoom.coerceIn(0, 22)
        val n = 1 shl z
        val clampedLat = latitude.coerceIn(-85.0511, 85.0511)
        val latRad = Math.toRadians(clampedLat)
        val x = (((longitude + 180.0) / 360.0) * n).toInt().coerceIn(0, n - 1)
        val y = (((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0) * n)
            .toInt()
            .coerceIn(0, n - 1)
        return MapTileIndex.getTileIndex(z, x, y)
    }

    fun metersToPixelsAt(latitude: Double, zoomLevel: Double, meters: Double, density: Float): Float {
        val latRad = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
        val metersPerPixel256 = (156_543.03392 * cos(latRad)) / 2.0.pow(zoomLevel)
        if (metersPerPixel256 <= 0.0) return 40f
        return ((meters / metersPerPixel256) * density.coerceAtLeast(1f)).toFloat()
    }

    fun computeDirectorySizeBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                total += file.length()
            }
        }
        return total
    }

    suspend fun getCachedStorageMb(context: Context): Double = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        val tileCacheDir = Configuration.getInstance().osmdroidTileCache
        val bytes = computeDirectorySizeBytes(tileCacheDir)
        bytes / (1024.0 * 1024.0)
    }

    suspend fun hasCachedTilesForArea(
        context: Context,
        latitude: Double,
        longitude: Double,
        zoomLevel: Int
    ): Boolean = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        var writer: SqlTileWriter? = null
        try {
            writer = SqlTileWriter()
            val source = TileSourceFactory.MAPNIK
            val candidateZooms = buildList {
                add(zoomLevel.coerceIn(1, 19))
                for (z in OFFLINE_MAX_ZOOM downTo OFFLINE_MIN_ZOOM) {
                    if (z != zoomLevel) add(z)
                }
            }
            for (z in candidateZooms) {
                val centerTile = latLonToTileIndex(latitude, longitude, z)
                if (writer.exists(source, centerTile)) {
                    return@withContext true
                }
            }
            false
        } catch (_: Exception) {
            false
        } finally {
            try {
                writer?.onDetach()
            } catch (_: Exception) {
            }
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun observeNetworkConnectivity(context: Context): Flow<Boolean> = callbackFlow {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        trySend(isNetworkAvailable(appContext))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isNetworkAvailable(appContext))
            }

            override fun onLost(network: Network) {
                trySend(isNetworkAvailable(appContext))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            trySend(isNetworkAvailable(appContext))
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()
}
