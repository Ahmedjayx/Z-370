package works.axiora.z370

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Discovers what car data this firmware exposes to ordinary apps:
 *  1. finds the car / MCU / CAN system apps,
 *  2. pulls every intent-action-looking string out of their code and listens for all of them,
 *  3. watches system settings and system properties for anything that changes.
 * Read-only: it never writes settings or sends commands.
 */
class Probe(private val ctx: Context) {
    val events = mutableStateListOf<String>()
    val changes = mutableStateListOf<String>()
    val apps = mutableStateListOf<String>()
    val status = mutableStateOf("Scanning car apps…")

    private val lastByAction = HashMap<String, String>()
    private var receiver: BroadcastReceiver? = null
    private var actionCount = 0
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val pkgKeys = listOf("can", "mcu", "car", "climate", "hvac", "vehicle", "syu", "fyt", "wyd", "bus", "radio", "launcher", "setting")
    private val actionShape = Regex("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+){2,8}")
    private val upperTail = Regex(".*\\.[A-Z][A-Z0-9_]{2,}$")

    private fun now() = clock.format(Date())

    // ------------------------------------------------------------ scan

    suspend fun scan() {
        val found = withContext(Dispatchers.IO) { scanApps() }
        apps.addAll(found.first)
        val actions = found.second
        actionCount = actions.size
        listen(actions)
        status.value = "Listening for ${actions.size} broadcast types · watching settings and properties"
    }

    private fun scanApps(): Pair<List<String>, Set<String>> {
        val pm = ctx.packageManager
        val lines = ArrayList<String>()
        val actions = LinkedHashSet<String>()
        val all = pm.getInstalledApplications(0)
        val matched = all.filter { ai ->
            val p = ai.packageName.lowercase()
            ai.packageName != ctx.packageName && !p.startsWith("com.google.") &&
                pkgKeys.any { p.contains(it) } &&
                (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 || !p.startsWith("com.android."))
        }
        lines += "Installed packages: ${all.size}, car-related candidates: ${matched.size}"
        val flags = (PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS).toLong()
        for (ai in matched) {
            val sys = if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) "system" else "user"
            lines += "■ ${ai.packageName}  [$sys]  ${ai.sourceDir}"
            try {
                val pi = pm.getPackageInfo(ai.packageName, PackageManager.PackageInfoFlags.of(flags))
                pi.services?.filter { it.exported }?.forEach { lines += "   service  ${it.name}" + (it.permission?.let { p -> "  (perm $p)" } ?: "") }
                pi.receivers?.filter { it.exported }?.forEach { lines += "   receiver ${it.name}" }
                pi.providers?.forEach { pr ->
                    lines += "   provider ${pr.authority}  exported=${pr.exported}  read=${pr.readPermission ?: "-"}"
                    if (pr.exported) pr.authority?.split(";")?.firstOrNull()?.let { a -> lines += "      " + tryQuery(a) }
                }
            } catch (e: Exception) {
                lines += "   (details unavailable: ${e.javaClass.simpleName})"
            }
            val before = actions.size
            harvestActions(ai.sourceDir, actions)
            lines += "   action strings found: ${actions.size - before}"
        }
        return lines to actions.take(1500).toSet()
    }

    private fun tryQuery(auth: String): String = try {
        ctx.contentResolver.query(Uri.parse("content://$auth"), null, null, null, null).use { c ->
            if (c == null) "query: null cursor"
            else {
                val cols = c.columnNames.joinToString(",")
                val first = if (c.moveToFirst()) (0 until c.columnCount).joinToString(" | ") { i ->
                    try { c.getString(i) ?: "null" } catch (_: Exception) { "?" }
                } else "no rows"
                "query OK  cols=[$cols]  rows=${c.count}  first=[$first]"
            }
        }
    } catch (e: Exception) {
        "query refused: ${e.javaClass.simpleName}"
    }

    /** Pulls action-like strings out of the app's dex code (apk, or the odex/vdex next to it). */
    private fun harvestActions(sourceDir: String?, out: MutableSet<String>) {
        if (sourceDir == null) return
        val dir = File(sourceDir).parentFile ?: return
        val files = dir.walkTopDown().maxDepth(3).filter {
            it.isFile && it.canRead() && it.length() < 80L * 1024 * 1024 &&
                (it.name.endsWith(".apk") || it.name.endsWith(".vdex") || it.name.endsWith(".odex") || it.name.endsWith(".dex"))
        }.toList()
        for (f in files) {
            try {
                if (f.name.endsWith(".apk")) {
                    ZipFile(f).use { z ->
                        z.entries().asSequence().filter { it.name.endsWith(".dex") }.forEach { e ->
                            z.getInputStream(e).use { extract(it.readBytes(), out) }
                        }
                    }
                } else extract(f.readBytes(), out)
            } catch (_: Exception) {
            }
        }
    }

    private fun extract(bytes: ByteArray, out: MutableSet<String>) {
        val text = String(bytes, Charsets.ISO_8859_1)
        for (m in actionShape.findAll(text)) {
            val s = m.value
            if (s.length > 120) continue
            if (s.startsWith("android.") || s.startsWith("com.android.") || s.startsWith("com.google.") ||
                s.startsWith("java.") || s.startsWith("kotlin.") || s.startsWith("androidx.")) continue
            if (upperTail.matches(s) || s.contains("action", ignoreCase = true)) out += s
        }
    }

    // ------------------------------------------------------------ broadcasts

    private fun listen(actions: Set<String>) {
        val filter = IntentFilter()
        actions.forEach { filter.addAction(it) }
        // A few standard ones head units commonly reuse for ignition and screen state.
        listOf(Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF, Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED)
            .forEach { filter.addAction(it) }
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i == null) return
                val action = i.action ?: "?"
                val body = describe(i.extras)
                val key = "$action|$body"
                if (lastByAction[action] == key) return
                lastByAction[action] = key
                events.add(0, "${now()}  $action  $body")
                if (events.size > 400) events.removeAt(events.lastIndex)
            }
        }
        ContextCompat.registerReceiver(ctx, rx, filter, ContextCompat.RECEIVER_EXPORTED)
        receiver = rx
    }

    private fun describe(b: Bundle?): String {
        if (b == null || b.isEmpty) return ""
        return b.keySet().sorted().joinToString("  ") { k ->
            @Suppress("DEPRECATION")
            val v = b.get(k)
            val s = when (v) {
                is IntArray -> v.contentToString()
                is FloatArray -> v.contentToString()
                is LongArray -> v.contentToString()
                is ByteArray -> v.joinToString(" ") { x -> "%02X".format(x) }
                is Array<*> -> v.contentToString()
                else -> v.toString()
            }
            "$k=$s"
        }
    }

    fun stop() {
        receiver?.let { try { ctx.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
    }

    // ------------------------------------------------------------ settings + properties

    private var lastSnap: Map<String, String>? = null

    suspend fun pollLoop(alive: () -> Boolean) {
        while (alive()) {
            val snap = withContext(Dispatchers.IO) { snapshot() }
            val prev = lastSnap
            if (prev != null) {
                val diffs = ArrayList<String>()
                for ((k, v) in snap) {
                    val old = prev[k]
                    if (old != v) diffs += "${now()}  $k: ${old ?: "(new)"}  →  $v"
                }
                for (k in prev.keys) if (!snap.containsKey(k)) diffs += "${now()}  $k: removed"
                diffs.forEach { changes.add(0, it) }
                while (changes.size > 400) changes.removeAt(changes.lastIndex)
            }
            lastSnap = snap
            delay(1200)
        }
    }

    private val noisyProps = listOf("time", "uptime", "boottime", "init.svc_debug", "ro.", "persist.sys.timezone")

    private fun snapshot(): Map<String, String> {
        val m = HashMap<String, String>()
        for ((tag, uri) in listOf("system" to Settings.System.CONTENT_URI, "secure" to Settings.Secure.CONTENT_URI, "global" to Settings.Global.CONTENT_URI)) {
            try {
                ctx.contentResolver.query(uri, arrayOf("name", "value"), null, null, null)?.use { c ->
                    while (c.moveToNext()) m["settings/$tag/${c.getString(0)}"] = c.getString(1) ?: "null"
                }
            } catch (_: Exception) {
            }
        }
        try {
            val p = Runtime.getRuntime().exec("getprop")
            p.inputStream.bufferedReader().useLines { seq ->
                seq.forEach { line ->
                    val i = line.indexOf("]: [")
                    if (line.startsWith("[") && i > 0) {
                        val k = line.substring(1, i)
                        if (noisyProps.none { k.contains(it) }) m["prop/$k"] = line.substring(i + 4).trimEnd(']')
                    }
                }
            }
            p.waitFor()
        } catch (_: Exception) {
        }
        return m
    }

    // ------------------------------------------------------------ report

    fun saveReport(): String = try {
        val name = "z370-probe-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val text = buildString {
            appendLine("Z370 Launcher CAN probe")
            appendLine("Device ${Build.MANUFACTURER} ${Build.MODEL} · ${Build.DISPLAY} · Android ${Build.VERSION.RELEASE}")
            appendLine("Broadcast types listened for: $actionCount")
            appendLine(); appendLine("== EVENTS (newest first) =="); events.forEach { appendLine(it) }
            appendLine(); appendLine("== CHANGED VALUES (newest first) =="); changes.forEach { appendLine(it) }
            appendLine(); appendLine("== CAR APPS =="); apps.forEach { appendLine(it) }
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("could not create file")
        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
        "Saved to Download/$name"
    } catch (e: Exception) {
        "Save failed: ${e.message}"
    }
}

// ---------------------------------------------------------------- screen

@Composable
fun ProbeScreen(accent: Color, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val probe = remember { Probe(ctx.applicationContext) }
    val saved = remember { mutableStateOf("") }
    val tab = remember { mutableStateOf(0) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false; probe.stop() }
    }
    LaunchedEffect(Unit) { probe.scan() }
    LaunchedEffect(Unit) { probe.pollLoop { isActive } }

    val mono = TextStyle(color = Z.Silver, fontSize = 19.sp, fontFamily = FontFamily.Monospace, lineHeight = 25.sp)

    Column(
        Modifier.fillMaxSize().background(Z.Ground).clickable(enabled = false) {}.padding(36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                T("DIAGNOSTICS", 18, Z.Dim, spacing = 3f)
                T("CAN probe", 44, Z.Bright, Condensed, FontWeight.Medium)
            }
            Pill("SAVE REPORT") { saved.value = probe.saveReport() }
            Spacer(Modifier.width(14.dp))
            RoundButton(80, Z.Bezel, onClose) { LineIcon("M6 6l12 12 M18 6L6 18", Z.Silver, 34.dp, 2f) }
        }
        BasicText(probe.status.value + if (saved.value.isNotEmpty()) "\n" + saved.value else "", style = mono.copy(color = accent))
        BasicText(
            "Leave this open, then one at a time: open and close each door and the boot, shift into reverse and out, " +
                "lights on and off, change fan speed and temperature, press steering-wheel buttons. Wait 3 seconds between each.",
            style = TextStyle(color = Z.Dim, fontSize = 22.sp, fontFamily = Barlow, lineHeight = 30.sp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("EVENTS ${probe.events.size}", "CHANGES ${probe.changes.size}", "CAR APPS").forEachIndexed { i, label ->
                Pill(label, selected = tab.value == i, accent = accent) { tab.value = i }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Z.Stroke))
        val rows = when (tab.value) { 0 -> probe.events; 1 -> probe.changes; else -> probe.apps }
        if (rows.isEmpty()) {
            BasicText(
                when (tab.value) {
                    0 -> "No broadcasts yet. Try the actions above."
                    1 -> "No changes yet. Values are compared every 1.2 seconds."
                    else -> "Scanning…"
                },
                style = mono.copy(color = Z.Faint),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows) { line -> BasicText(line, style = mono) }
        }
    }
}

@Composable
fun Pill(label: String, selected: Boolean = false, accent: Color = Z.Silver, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).border(if (selected) 2.dp else 1.dp, if (selected) accent else Z.Stroke, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
    ) { T(label, 20, if (selected) Z.Bright else Z.Silver, spacing = 2f) }
}

private fun Modifier.clip(RoundedCornerShape(16.dp)) = this.then(androidx.compose.ui.draw.clip(RoundedCornerShape(16.dp)))
