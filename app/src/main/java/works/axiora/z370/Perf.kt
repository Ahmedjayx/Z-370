package works.axiora.z370

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * GPS performance timers: 0–100 km/h, 80–120 km/h and the quarter mile, from the fixes the
 * launcher already receives. GPS reports about once a second, so each start and finish time is
 * interpolated between the two fixes either side of the threshold: good to roughly ±0.2 s.
 */
object Perf {
    class Run(val id: String, val label: String) {
        val last = mutableStateOf<Float?>(null)
        val best = mutableStateOf<Float?>(null)
        val live = mutableStateOf<Float?>(null)   // elapsed while running
        var startNs = 0L
        var running = false
    }

    val zeroTo100 = Run("p0100", "0–100")
    val eightyTo120 = Run("p80120", "80–120")
    val quarter = Run("pqm", "¼ MILE")
    val trapKmh = mutableStateOf<Int?>(null)
    val all = listOf(zeroTo100, eightyTo120, quarter)

    private var prefs: android.content.SharedPreferences? = null
    private var lastNs = 0L
    private var lastKmh = 0f
    private var quarterM = 0f

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("z370_perf", Context.MODE_PRIVATE); prefs = p
        all.forEach { r -> p.getFloat(r.id, 0f).takeIf { it > 0f }?.let { r.best.value = it } }
    }

    fun resetBest() {
        all.forEach { it.best.value = null; it.last.value = null }
        trapKmh.value = null
        prefs?.edit()?.clear()?.apply()
    }

    private fun finish(r: Run, endNs: Long) {
        r.running = false
        r.live.value = null
        val t = (endNs - r.startNs) / 1e9f
        if (t <= 0.5f || t > 60f) return
        r.last.value = t
        if (r.best.value == null || t < r.best.value!!) {
            r.best.value = t
            prefs?.edit()?.putFloat(r.id, t)?.apply()
        }
    }

    /** Linear time at which speed crossed `v` between the previous fix and this one. */
    private fun cross(v: Float, ns: Long, kmh: Float): Long {
        if (kmh == lastKmh) return ns
        val f = ((v - lastKmh) / (kmh - lastKmh)).coerceIn(0f, 1f)
        return lastNs + ((ns - lastNs) * f).toLong()
    }

    fun onFix(loc: android.location.Location) {
        if (!loc.hasSpeed()) return
        val ns = loc.elapsedRealtimeNanos
        val kmh = loc.speed * 3.6f
        if (lastNs == 0L || ns <= lastNs || ns - lastNs > 3_000_000_000L) { lastNs = ns; lastKmh = kmh; return }

        // From a standstill: 0–100 and the quarter mile start together.
        if (lastKmh < 2f && kmh >= 2f) {
            val t0 = cross(2f, ns, kmh)
            zeroTo100.startNs = t0; zeroTo100.running = true
            quarter.startNs = t0; quarter.running = true; quarterM = 0f
        }
        if (kmh < 2f) { zeroTo100.running = false; quarter.running = false; zeroTo100.live.value = null; quarter.live.value = null }

        if (zeroTo100.running) {
            if (lastKmh < 100f && kmh >= 100f) finish(zeroTo100, cross(100f, ns, kmh))
            else if ((ns - zeroTo100.startNs) > 30_000_000_000L || kmh < lastKmh - 8f) { zeroTo100.running = false; zeroTo100.live.value = null }
            else zeroTo100.live.value = (ns - zeroTo100.startNs) / 1e9f
        }
        if (quarter.running) {
            val dtS = (ns - lastNs) / 1e9f
            val add = (lastKmh + kmh) / 2f / 3.6f * dtS
            if (quarterM + add >= 402.3f) {
                val f = ((402.3f - quarterM) / add).coerceIn(0f, 1f)
                trapKmh.value = kmh.toInt()
                finish(quarter, lastNs + ((ns - lastNs) * f).toLong())
            } else {
                quarterM += add
                if ((ns - quarter.startNs) > 40_000_000_000L) { quarter.running = false; quarter.live.value = null }
                else quarter.live.value = (ns - quarter.startNs) / 1e9f
            }
        }

        // Rolling 80–120.
        if (!eightyTo120.running && lastKmh < 80f && kmh >= 80f) { eightyTo120.startNs = cross(80f, ns, kmh); eightyTo120.running = true }
        if (eightyTo120.running) {
            if (lastKmh < 120f && kmh >= 120f) finish(eightyTo120, cross(120f, ns, kmh))
            else if (kmh < 76f || (ns - eightyTo120.startNs) > 30_000_000_000L) { eightyTo120.running = false; eightyTo120.live.value = null }
            else eightyTo120.live.value = (ns - eightyTo120.startNs) / 1e9f
        }

        lastNs = ns; lastKmh = kmh
    }
}

@Composable
fun ColumnScope.PerfBody(accent: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Perf.all.forEach { r ->
            val live = r.live.value
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(r.label, 16, Muted, spacing = 3f)
                T(
                    when { live != null -> "%.1f".format(live); r.last.value != null -> "%.2f".format(r.last.value); else -> "--" },
                    64, if (live != null) accent else Z.Bright, Condensed, FontWeight.Light,
                )
                T(r.best.value?.let { "BEST %.2f S".format(it) } ?: "NO BEST YET", 16, if (r.best.value != null) Soft else Muted, spacing = 2f)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        T(
            Perf.trapKmh.value?.let { "Trap speed ${Cfg.speed(it)} ${Cfg.speedUnit.lowercase()} · " }.orEmpty() +
                "Starts by itself from a stop or at 80. GPS-timed, about ±0.2 s.",
            18, Muted, modifier = Modifier.weight(1f),
        )
        GlassPill("RESET BEST") { Perf.resetBest() }
    }
}
