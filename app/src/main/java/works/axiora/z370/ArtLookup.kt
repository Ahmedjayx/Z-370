package works.axiora.z370

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cover art for Bluetooth music. The phone only sends the track title over Bluetooth, so the
 * cover is looked up by title on the iTunes Search API (free, no key) and cached on the unit, so a
 * song seen once shows its art again with no signal. Idea after Celica Dash's ArtLookup (MIT).
 */
object ArtLookup {
    private val memory = object : LinkedHashMap<String, ImageBitmap?>(32, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, ImageBitmap?>) = size > 40
    }

    private fun key(q: String) = q.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(80)

    suspend fun find(ctx: Context, title: String, artist: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        val q = listOfNotNull(title, artist?.takeIf { it.isNotBlank() && it != "Bluetooth audio" }).joinToString(" ")
        val k = key(q)
        synchronized(memory) { if (memory.containsKey(k)) return@withContext memory[k] }
        val dir = File(ctx.cacheDir, "art").apply { mkdirs() }
        val file = File(dir, "$k.jpg")
        val miss = File(dir, "$k.none")
        val bmp: ImageBitmap? = when {
            file.exists() -> BitmapFactory.decodeFile(file.path)?.asImageBitmap()
            miss.exists() && System.currentTimeMillis() - miss.lastModified() < 86_400_000L -> null
            else -> try {
                val url = URL("https://itunes.apple.com/search?media=music&entity=song&limit=1&term=" + URLEncoder.encode(q, "UTF-8"))
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 6000; c.readTimeout = 6000
                val res = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("results")
                val art = res?.optJSONObject(0)?.optString("artworkUrl100")?.takeIf { it.isNotBlank() }
                if (art == null) { miss.writeText(""); null } else {
                    val big = art.replace("100x100bb", "600x600bb")
                    val bytes = (URL(big).openConnection() as HttpURLConnection).apply { connectTimeout = 6000; readTimeout = 8000 }.inputStream.readBytes()
                    file.writeBytes(bytes)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            } catch (_: Exception) { null } // offline: try again next time
        }
        synchronized(memory) { if (bmp != null || miss.exists()) memory[k] = bmp }
        bmp
    }
}

/** Art for the given Bluetooth track, or null while looking / if none found. */
@Composable
fun rememberBtArt(title: String?, artist: String?): State<ImageBitmap?> {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(null, title, artist) {
        value = if (title.isNullOrBlank()) null else ArtLookup.find(ctx, title, artist)
    }
}
