package works.axiora.z370

import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Silent CAN recorder for finding speed and RPM. Every time a CAN frame type changes its bytes,
 * one line goes to a file with the GPS speed at that moment:  ms | gps km/h | type | bytes.
 * Matching the bytes against the GPS speed afterwards shows which frame carries road speed and
 * which one moves with the revs. Throttled per type and capped in size, so it costs nothing.
 */
object DriveLog {
    private val io = Executors.newSingleThreadExecutor()
    private var file: File? = null
    private val last = HashMap<Int, String>()
    private val lastAt = HashMap<Int, Long>()
    private val buf = StringBuilder()
    private var flushedAt = 0L
    val status = mutableStateOf("")

    fun init(ctx: Context) { if (file == null) file = File(ctx.filesDir, "can-drive.log") }

    fun onFrame(type: Int, hex: String) {
        val now = SystemClock.elapsedRealtime()
        if (last[type] == hex) return
        if (now - (lastAt[type] ?: 0L) < 150L) return
        last[type] = hex; lastAt[type] = now
        synchronized(buf) {
            buf.append(System.currentTimeMillis()).append('|').append(Trip.gpsKmh).append('|')
                .append("%02X".format(type)).append('|').append(hex).append('\n')
            if (now - flushedAt > 5000L || buf.length > 32_000) { flushedAt = now; flush() }
        }
    }

    private fun flush() {
        val f = file ?: return
        val chunk = buf.toString(); buf.setLength(0)
        io.execute {
            try {
                if (f.length() > 6_000_000L) f.renameTo(File(f.parentFile, "can-drive.old.log"))
                f.appendText(chunk)
            } catch (_: Exception) {}
        }
    }

    /** Copies the log (previous and current) to Download/ for sending. */
    fun save(ctx: Context) {
        synchronized(buf) { flush() }
        io.execute {
            status.value = try {
                val dir = ctx.filesDir
                val parts = listOf(File(dir, "can-drive.old.log"), File(dir, "can-drive.log")).filter { it.exists() }
                if (parts.isEmpty()) error("nothing recorded yet. Drive with the launcher running first")
                val name = "z370-drive-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("could not create file")
                ctx.contentResolver.openOutputStream(uri)!!.use { out ->
                    out.write("Z370 drive log: ms|gps_kmh|type|bytes\n".toByteArray())
                    parts.forEach { p -> p.inputStream().use { it.copyTo(out) } }
                }
                "Saved to Download/$name"
            } catch (e: Exception) {
                "Save failed: ${e.message}"
            }
        }
    }
}
