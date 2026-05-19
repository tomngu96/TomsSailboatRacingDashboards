package com.sailboatracing.offline

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.util.MapTileIndex
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

object TileDownloader {

    const val MIN_ZOOM = 10
    const val MAX_ZOOM = 16
    private const val AVG_TILE_BYTES = 12_000L

    fun tileCount(north: Double, south: Double, west: Double, east: Double): Int {
        val n = maxOf(north, south)
        val s = minOf(north, south)
        val w = minOf(west, east)
        val e = maxOf(west, east)
        var count = 0
        for (z in MIN_ZOOM..MAX_ZOOM) {
            val xMin = lon2tile(w, z)
            val xMax = lon2tile(e, z)
            val yMin = lat2tile(n, z)
            val yMax = lat2tile(s, z)
            count += (xMax - xMin + 1) * (yMax - yMin + 1)
        }
        return count
    }

    fun estimatedBytes(north: Double, south: Double, west: Double, east: Double): Long =
        tileCount(north, south, west, east) * AVG_TILE_BYTES

    suspend fun download(
        context: Context,
        north: Double,
        south: Double,
        west: Double,
        east: Double,
        onProgress: (done: Int, total: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val n = maxOf(north, south)
        val s = minOf(north, south)
        val w = minOf(west, east)
        val e = maxOf(west, east)
        val tileSource = TileSourceFactory.MAPNIK
        val writer = SqlTileWriter()
        val userAgent = context.packageName
        var done = 0
        val total = tileCount(n, s, w, e)

        for (z in MIN_ZOOM..MAX_ZOOM) {
            if (!isActive) break
            val xMin = lon2tile(w, z)
            val xMax = lon2tile(e, z)
            val yMin = lat2tile(n, z)
            val yMax = lat2tile(s, z)
            for (x in xMin..xMax) {
                if (!isActive) break
                for (y in yMin..yMax) {
                    if (!isActive) break
                    val bytes = fetchTile(z, x, y, userAgent)
                    if (bytes != null) {
                        val tileIndex = MapTileIndex.getTileIndex(z, x, y)
                        writer.saveFile(tileSource, tileIndex, bytes.inputStream(), Long.MAX_VALUE)
                    }
                    done++
                    onProgress(done, total)
                }
            }
        }
        writer.onDetach()
    }

    private fun fetchTile(z: Int, x: Int, y: Int, userAgent: String): ByteArray? {
        return try {
            val url = URL("https://tile.openstreetmap.org/$z/$x/$y.png")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", userAgent)
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.connect()
            if (conn.responseCode == 200) conn.inputStream.readBytes() else null
        } catch (_: Exception) { null }
    }

    private fun lon2tile(lon: Double, zoom: Int): Int =
        floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()

    private fun lat2tile(lat: Double, zoom: Int): Int {
        val r = Math.toRadians(lat)
        return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl zoom)).toInt()
    }
}
