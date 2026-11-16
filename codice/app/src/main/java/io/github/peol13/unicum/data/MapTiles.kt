package io.github.peol13.unicum.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * Riquadri della mappa per il percorso degli allenamenti, dai server di OpenStreetMap (gratuiti, senza chiave,
 * con User-Agent dell'app come chiede la loro policy). Nessuna libreria: si scaricano i PNG solo quando si apre
 * un allenamento con percorso, si tengono in memoria e su disco nella cache dell'app e si disegnano in un
 * Canvas, attenuati per stare nei colori dell'app (vedi RouteMap).
 */
object MapTiles {
    /** Lato di un riquadro nelle coordinate "mondo" di Web Mercator. */
    const val TILE = 256.0
    const val ATTRIBUTION = "© OpenStreetMap"

    private val memory = LruCache<String, Bitmap>(48)

    /** Coordinate x/y in pixel-mondo allo zoom [z] (proiezione Web Mercator). */
    fun worldX(lon: Double, z: Int): Double = (lon + 180.0) / 360.0 * TILE * 2.0.pow(z)

    fun worldY(lat: Double, z: Int): Double {
        val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * TILE * 2.0.pow(z)
    }

    /**
     * Zoom più alto (al massimo 17) per cui il percorso, con il riquadro disegnato a [tilePx] pixel,
     * sta in [widthPx] × [heightPx] lasciando un margine.
     */
    fun fitZoom(points: List<RoutePoint>, widthPx: Float, heightPx: Float, tilePx: Float): Int {
        for (z in 17 downTo 2) {
            val xs = points.map { worldX(it.lon, z) }
            val ys = points.map { worldY(it.lat, z) }
            val scale = tilePx / TILE
            val w = (xs.max() - xs.min()) * scale
            val h = (ys.max() - ys.min()) * scale
            if (w <= widthPx * .82 && h <= heightPx * .78) return z
        }
        return 2
    }

    /** Il riquadro z/x/y (bitmap 256 px), dalla memoria, dal disco o dalla rete; null se non si riesce. */
    suspend fun tile(context: Context, z: Int, x: Int, y: Int): Bitmap? = withContext(Dispatchers.IO) {
        val n = 1 shl z
        if (y < 0 || y >= n) return@withContext null
        val wx = ((x % n) + n) % n
        val key = "$z-$wx-$y"
        memory.get(key)?.let { return@withContext it }
        val file = File(context.cacheDir, "map_tiles_osm/$key.png")
        val cached = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        val bmp = cached ?: runCatching {
            val conn = URL("https://tile.openstreetmap.org/$z/$wx/$y.png").openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", AppInfo.USER_AGENT)
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.inputStream.use { it.readBytes() }.also { bytes ->
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
            }.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }.getOrNull()
        bmp?.also { memory.put(key, it) }
    }
}
