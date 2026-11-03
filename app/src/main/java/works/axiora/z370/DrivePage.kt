package works.axiora.z370

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private const val GAUGE_MAX = 260f
private val Cardinal = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

/** The driving page: one big speed face (chosen in Settings) and the live readouts under it. */
@Composable
fun DrivePage(fixState: State<Fix>, car: CarState, accent: Color) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // Face switcher: three option chips right on the page.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("sweep" to "SWEEP", "numbers" to "NUMBERS", "minimal" to "MINIMAL").forEach { (id, label) ->
                GlassPill(label, if (Cfg.driveFace.value == id) accent else null, Modifier.weight(1f)) { Cfg.put(Cfg.driveFace, "driveFace", id) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().glass(RoundedCornerShape(56.dp)), contentAlignment = Alignment.Center) {
            SpeedFace(fixState, accent)
        }
        Row(Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Card(Modifier.weight(1f).fillMaxHeight(), "HEADING") { HeadingBody(fixState, accent) }
            Card(Modifier.weight(1f).fillMaxHeight(), "STEERING") { SteeringBody(car, accent) }
        }
        Card(Modifier.fillMaxWidth(), "TRIP") { TripBody() }
        Card(Modifier.fillMaxWidth(), "PERFORMANCE") { PerfBody(accent) }
    }
}

@Composable
private fun SpeedFace(fixState: State<Fix>, accent: Color) {
    val kmh = fixState.value.speedKmh
    val warn = Cfg.redlineKmh.intValue
    val over = warn > 0 && (kmh ?: 0) >= warn
    val shown by animateFloatAsState(((kmh ?: 0) / GAUGE_MAX).coerceIn(0f, 1f), tween(450), label = "speed")
    val pulse = if (over) {
        val t = rememberInfiniteTransition(label = "warn")
        t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "p").value
    } else 0f
    val warnColor = Color(0xFFFF3B2F)

    when (Cfg.driveFace.value) {
        "numbers" -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T(Cfg.speed(kmh), 300, if (over) warnColor else Z.Bright, Condensed, FontWeight.Light)
            T(Cfg.speedUnit, 30, Soft, spacing = 10f)
            Box(Modifier.padding(top = 20.dp).size(760.dp, 14.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
                    drawLine(if (over) warnColor else accent, Offset(0f, size.height / 2), Offset(size.width * shown.coerceAtLeast(0.01f), size.height / 2), size.height, StrokeCap.Round)
                    if (warn > 0) {
                        val x = size.width * (warn / GAUGE_MAX)
                        drawLine(warnColor, Offset(x, -12f), Offset(x, size.height + 12f), 4f)
                    }
                }
            }
        }
        "minimal" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(Cfg.speed(kmh), 300, if (over) warnColor.copy(alpha = 0.6f + 0.4f * pulse) else Color(0xFFB8BDC5), Condensed, FontWeight.Light)
            T(Cfg.speedUnit, 24, Muted, spacing = 10f)
        }
        else -> Box(Modifier.fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = 24f
                val r = size.minDimension / 2 - inset
                val tl = Offset(center.x - r, center.y - r); val sz = Size(r * 2, r * 2)
                // Ticks every 20 km/h, labels every 40.
                for (v in 0..GAUGE_MAX.toInt() step 10) {
                    val a = Math.toRadians((135.0 + 270.0 * v / GAUGE_MAX))
                    val major = v % 20 == 0
                    val r1 = r - 34f; val r2 = r1 - if (major) 30f else 14f
                    val col = if (warn in 1..v) warnColor.copy(alpha = 0.8f) else Color.White.copy(alpha = if (major) 0.7f else 0.3f)
                    drawLine(col, Offset(center.x + r1 * cos(a).toFloat(), center.y + r1 * sin(a).toFloat()),
                        Offset(center.x + r2 * cos(a).toFloat(), center.y + r2 * sin(a).toFloat()), if (major) 4f else 2.5f, StrokeCap.Round)
                }
                drawArc(Color.White.copy(alpha = 0.10f), 135f, 270f, false, tl, sz, style = Stroke(18f, cap = StrokeCap.Round))
                // Numbers every 40 (in the chosen unit's scale).
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true; textSize = r * 0.105f; textAlign = android.graphics.Paint.Align.CENTER
                    color = android.graphics.Color.argb(200, 230, 233, 238)
                }
                for (v in 0..GAUGE_MAX.toInt() step 40) {
                    val a = Math.toRadians((135.0 + 270.0 * v / GAUGE_MAX))
                    val rt = r * 0.70f
                    val label = if (Cfg.mph.value) (v / 1.609f).toInt().toString() else v.toString()
                    drawContext.canvas.nativeCanvas.drawText(label, center.x + rt * cos(a).toFloat(), center.y + rt * sin(a).toFloat() + 12f, paint)
                }
                if (shown > 0.002f) {
                    drawArc(accent.copy(alpha = 0.25f), 135f, 270f * shown, false, tl, sz, style = Stroke(44f, cap = StrokeCap.Round))
                    drawArc(if (over) warnColor else accent, 135f, 270f * shown, false, tl, sz, style = Stroke(18f, cap = StrokeCap.Round))
                }
                if (over) drawCircle(warnColor.copy(alpha = 0.10f * pulse), r - 60f, center)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T(Cfg.speed(kmh), 200, if (over) warnColor else Z.Bright, Condensed, FontWeight.Light)
                T(Cfg.speedUnit, 28, Soft, spacing = 10f)
            }
        }
    }
}

@Composable
private fun HeadingBody(fixState: State<Fix>, accent: Color) {
    val b = fixState.value.bearing
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        Canvas(Modifier.size(140.dp)) {
            val c = center; val r = size.minDimension / 2 - 6f
            drawCircle(Color.White.copy(alpha = 0.35f), r, c, style = Stroke(3f))
            for (i in 0 until 8) rotate(i * 45f, c) {
                drawLine(Color.White.copy(alpha = if (i % 2 == 0) 0.8f else 0.35f), Offset(c.x, c.y - r), Offset(c.x, c.y - r + if (i % 2 == 0) 20f else 10f), 3f)
            }
            if (b != null) rotate(b, c) {
                val p = Path().apply { moveTo(c.x, c.y - r + 26f); lineTo(c.x + 18f, c.y + 18f); lineTo(c.x, c.y + 6f); lineTo(c.x - 18f, c.y + 18f); close() }
                drawPath(p, accent)
            }
        }
        Column {
            T(if (b == null) "--" else Cardinal[(((b + 22.5f) % 360f) / 45f).toInt() % 8], 84, Z.Bright, Condensed, FontWeight.Light)
            T(if (b == null) "NO GPS HEADING" else "%03d°".format(b.toInt()), 22, Soft, spacing = 3f)
        }
    }
}

@Composable
private fun SteeringBody(car: CarState, accent: Color) {
    val a = car.steeringDeg.value
    val shown by animateFloatAsState(a ?: 0f, tween(120), label = "steer")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        Canvas(Modifier.size(140.dp)) {
            val c = center; val r = size.minDimension / 2 - 8f
            rotate(-shown, c) {
                drawCircle(Color.White.copy(alpha = 0.85f), r, c, style = Stroke(10f))
                drawLine(Color.White.copy(alpha = 0.85f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 8f)
                drawLine(Color.White.copy(alpha = 0.85f), c, Offset(c.x, c.y + r), 8f)
                drawLine(accent, Offset(c.x, c.y - r - 4f), Offset(c.x, c.y - r + 16f), 8f, StrokeCap.Round)
            }
        }
        Column {
            T(a?.let { "%d°".format(kotlin.math.abs(it.toInt())) } ?: "--", 84, Z.Bright, Condensed, FontWeight.Light)
            T(if (a == null) "WAITING FOR CAN" else if (kotlin.math.abs(a) < 3f) "CENTRED" else if (a > 0) "LEFT" else "RIGHT", 22, Soft, spacing = 3f)
        }
    }
}

@Composable
private fun TripBody() {
    val km = Trip.km.floatValue
    val min = Trip.minutes.intValue
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val d = Cfg.dist(km).split(" ")
        Stat("DISTANCE", d[0], d.getOrNull(1))
        Stat("TIME", "%d:%02d".format(min / 60, min % 60))
        Stat("AVERAGE", Cfg.speed(Trip.avgKmh.intValue.takeIf { it > 0 }), Cfg.speedUnit)
        Stat("TOP", Cfg.speed(Trip.maxKmh.intValue.takeIf { it > 0 }), Cfg.speedUnit)
    }
    Spacer(Modifier.height(2.dp))
    T("A new trip starts after 30 minutes parked.", 18, Muted)
}
