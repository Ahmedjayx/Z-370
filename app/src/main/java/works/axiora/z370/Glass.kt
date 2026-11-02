package works.axiora.z370

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- Liquid glass look (design B)

/**
 * Glass surface: a light-to-clear tint, a bright top edge fading down the sides, like light caught in thick glass.
 * Painted, not blurred: behind it sit only smooth glows, which a real blur would leave unchanged,
 * so this costs the GPU almost nothing and never stutters.
 */
fun Modifier.glass(shape: Shape, strength: Float = 1f) = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.15f * strength), Color.White.copy(alpha = 0.03f * strength)),
            start = Offset.Zero, end = Offset(700f, 1000f),
        ),
    )
    .border(
        1.5.dp,
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.18f)),
        ),
        shape,
    )

@Composable
fun AmbientBackdrop(accent: Color) {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF050506)).drawBehind {
            val w = size.width; val h = size.height
            fun glow(c: Color, alpha: Float, cx: Float, cy: Float, r: Float) =
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
            glow(accent, 0.55f, w * 0.08f, h * 0.08f, w * 0.80f)
            glow(Color(0xFF8A1A10), 0.60f, w * 1.0f, h * 0.62f, w * 0.85f)
            glow(Color(0xFF2B3440), 0.80f, w * 0.38f, h * 0.80f, w * 0.48f)
        },
    )
}

// ---------------------------------------------------------------- Home

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassHome(
    car: CarState,
    accent: Color,
    locationGranted: Boolean,
    resolved: Map<Role, AppEntry?>,
    onRole: (Role) -> Unit,
    onAssign: (Role) -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
) {
    // Each live value is read inside its own small composable, so a clock tick, a GPS fix or
    // a steering frame redraws only that piece, never the whole screen with the map in it.
    val fixState = rememberGpsFix(locationGranted)
    val media = rememberMedia()
    val sweep = rememberStartupSweep()

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(accent)
        Column(
            Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 36.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // Header: big clock, date, settings.
            Row(Modifier.fillMaxWidth().height(110.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderClock()
                Spacer(Modifier.weight(1f))
                OutsideTemp(car)
                Box(Modifier.size(80.dp).glass(CircleShape).clickable(onClick = onSettings), contentAlignment = Alignment.Center) {
                    LineIcon("M4 6h16 M4 12h16 M4 18h16 M8 4v4 M16 10v4 M10 16v4", Z.Bright, 34.dp)
                }
            }

            // Tiles: cabin, speed, doors.
            Row(Modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Tile("CABIN", Modifier.weight(1f)) { CabinTileBody(car, accent, sweep) }
                Tile("SPEED", Modifier.weight(1f)) { SpeedTileBody(fixState, accent, sweep) }
                Tile("DOORS", Modifier.weight(1f)) { DoorsTileBody(car, accent) }
            }

            // Live map with a glass navigation bar.
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(56.dp))
                    .border(1.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(56.dp))
                    .combinedClickable(onClick = { onRole(Role.MAPS) }, onLongClick = { onAssign(Role.MAPS) }),
            ) {
                LiveMap(fixState, accent, Modifier.fillMaxSize())
                CallCard(car, accent, Modifier.align(Alignment.TopCenter).padding(24.dp))
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth().height(116.dp)
                        .glass(RoundedCornerShape(36.dp), 1.4f).background(Color(0x55050506)).padding(horizontal = 30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val step = Nav.step.value
                    if (step != null) {
                        // Live next turn from Google Maps / Waze while they navigate.
                        val arrow = step.arrow
                        if (arrow != null) Image(arrow, null, Modifier.size(64.dp)) else LineIcon("M9 6l6 6-6 6", Z.Bright, 48.dp, 2.2f)
                        Column(Modifier.weight(1f)) {
                            T(step.title, 38, Z.Bright, Condensed, FontWeight.Medium)
                            T(listOfNotNull(step.text.ifBlank { null }, step.sub).joinToString(" · "), 20, Color(0xFFC3C8CF))
                        }
                    } else {
                        LineIcon("M3 11l18-8-8 18-2-8z", accent, 40.dp, 2f)
                        Column(Modifier.weight(1f)) {
                            T("NAVIGATION", 18, Color(0xFFC3C8CF), spacing = 3f)
                            T(resolved[Role.MAPS]?.label ?: "Choose a maps app", 38, Z.Bright, Condensed, FontWeight.Medium)
                        }
                        T("TAP TO OPEN", 18, Color(0xFFC3C8CF), spacing = 2f)
                    }
                }
            }

            GlassMedia(media, car, accent, resolved[Role.MUSIC]) { onRole(Role.MUSIC) }

            // Dock: one glass pill, icons only.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 70.dp).height(130.dp).glass(RoundedCornerShape(65.dp), 1.2f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(Role.CARPLAY, Role.MAPS, Role.PHONE, Role.MUSIC, Role.CAMERA).forEach { role ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().combinedClickable(onClick = { onRole(role) }, onLongClick = { onAssign(role) }),
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(role.path, Z.Bright, 46.dp, 1.6f) }
                }
                Box(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onApps), contentAlignment = Alignment.Center) {
                    LineIcon("M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z", Z.Bright, 46.dp, 1.6f)
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxHeight().glass(RoundedCornerShape(48.dp)).padding(32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        T(label, 18, Color(0xFFC3C8CF), spacing = 4f)
        content()
    }
}

@Composable
private fun Bar(fraction: Float, accent: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.14f))) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(accent))
    }
}

@Composable
fun DoorGlyph(car: CarState, accent: Color, modifier: Modifier) {
    val spec = androidx.compose.animation.core.tween<Float>(450)
    val dl by androidx.compose.animation.core.animateFloatAsState(if (car.driverDoor.value) 1f else 0f, spec, label = "dl")
    val dr by androidx.compose.animation.core.animateFloatAsState(if (car.passengerDoor.value) 1f else 0f, spec, label = "dr")
    val bt by androidx.compose.animation.core.animateFloatAsState(if (car.boot.value) 1f else 0f, spec, label = "bt")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val body = Color.White.copy(alpha = 0.60f)
        val glassLine = Color.White.copy(alpha = 0.30f)
        // Long bonnet, cabin set back, wide rear haunches: a short-overhang coupe from above, nose up.
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.50f, 0f)
            cubicTo(w * 0.80f, 0f, w * 0.86f, h * 0.06f, w * 0.86f, h * 0.18f)
            lineTo(w * 0.86f, h * 0.48f)
            cubicTo(w * 0.86f, h * 0.56f, w * 0.94f, h * 0.62f, w * 0.94f, h * 0.72f)
            lineTo(w * 0.94f, h * 0.88f)
            cubicTo(w * 0.94f, h * 0.97f, w * 0.80f, h, w * 0.50f, h)
            cubicTo(w * 0.20f, h, w * 0.06f, h * 0.97f, w * 0.06f, h * 0.88f)
            lineTo(w * 0.06f, h * 0.72f)
            cubicTo(w * 0.06f, h * 0.62f, w * 0.14f, h * 0.56f, w * 0.14f, h * 0.48f)
            lineTo(w * 0.14f, h * 0.18f)
            cubicTo(w * 0.14f, h * 0.06f, w * 0.20f, 0f, w * 0.50f, 0f)
            close()
        }
        drawPath(p, body, style = Stroke(3f))
        // Glasshouse: windscreen to the rear hatch glass.
        drawRoundRect(glassLine, Offset(w * 0.26f, h * 0.38f), Size(w * 0.48f, h * 0.36f), CornerRadius(w * 0.18f), style = Stroke(2f))
        // Doors: hinge at the front edge, swing out up to 40°.
        val hingeY = h * 0.42f; val doorLen = h * 0.24f
        fun door(x: Float, open: Float, dir: Float) {
            val a = Math.toRadians((40.0 * open)).toFloat()
            val end = Offset(x + dir * kotlin.math.sin(a) * doorLen, hingeY + kotlin.math.cos(a) * doorLen)
            drawLine(if (open > 0.05f) accent else body, Offset(x, hingeY), end, if (open > 0.05f) 7f else 4f, StrokeCap.Round)
        }
        door(w * 0.14f, dl, -1f)
        door(w * 0.86f, dr, 1f)
        // Boot: hatch lifts as a lit bar behind the car.
        if (bt > 0.05f) drawLine(accent, Offset(w * 0.24f, h + 4f + 10f * bt), Offset(w * 0.76f, h + 4f + 10f * bt), 7f, StrokeCap.Round)
    }
}

@Composable
private fun GlassMedia(media: MediaState, car: CarState, accent: Color, musicApp: AppEntry?, onOpenMusic: () -> Unit) {
    val ctx = LocalContext.current
    val np = media.now.value
    val btTitle = car.btTitle.value
    val title = np?.title?.ifBlank { "Unknown track" } ?: btTitle
    val subtitle = when {
        np != null -> np.artist
        btTitle != null -> "Bluetooth audio"
        else -> musicApp?.label ?: "Nothing playing"
    }
    val playing = np?.playing ?: car.btPlaying.value
    fun key(code: Int) {
        val tc = np?.controller?.transportControls
        if (tc != null) when (code) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> tc.skipToPrevious()
            KeyEvent.KEYCODE_MEDIA_NEXT -> tc.skipToNext()
            else -> if (playing) tc.pause() else tc.play()
        } else if (car.btTitle.value != null) BtControl.send(ctx, code) else sendMediaKey(ctx, code)
    }
    Row(
        Modifier.fillMaxWidth().height(210.dp).glass(RoundedCornerShape(52.dp)).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Box(
            Modifier.size(146.dp).clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(accent, Color(0xFF2A0D06))))
                .clickable { if (np == null) BtControl.openBtMusic(ctx) else onOpenMusic() },
            contentAlignment = Alignment.Center,
        ) {
            val art = np?.art
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else LineIcon(Role.MUSIC.path, Color.White.copy(alpha = 0.7f), 56.dp, 1.4f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T(title ?: "Nothing playing", 46, Z.Bright, Condensed, FontWeight.Medium)
            T(subtitle, 22, Color(0xFFC3C8CF))
        }
        // Phone music only plays once the unit's Bluetooth app is the audio source, so with
        // nothing playing, play first switches to it (opens its music page), then starts playback.
        if (title == null) {
            GlassButton(100, { BtControl.openBtMusic(ctx, thenPlay = true) }, 1.6f) { LineIcon("M7 4l13 8-13 8z", Z.Bright, 38.dp, 2.2f) }
        } else {
            GlassButton(76, { key(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }) { LineIcon("M19 5L9 12l10 7z M5 5v14", Z.Bright, 30.dp, 2f) }
            GlassButton(100, { key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, 1.6f) {
                if (playing) LineIcon("M8 5v14 M16 5v14", Z.Bright, 38.dp, 2.2f) else LineIcon("M7 4l13 8-13 8z", Z.Bright, 38.dp, 2.2f)
            }
            GlassButton(76, { key(KeyEvent.KEYCODE_MEDIA_NEXT) }) { LineIcon("M5 5l10 7-10 7z M19 5v14", Z.Bright, 30.dp, 2f) }
        }
    }
}

@Composable
private fun GlassButton(size: Int, onClick: () -> Unit, strength: Float = 1f, content: @Composable () -> Unit) {
    Box(Modifier.size(size.dp).glass(CircleShape, strength).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}


@Composable
private fun HeaderClock() {
    val ctx = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000 - value % 1000)
        }
    }
    val d = Date(now)
    val fmt = if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
    Row(verticalAlignment = Alignment.CenterVertically) {
        T(SimpleDateFormat(fmt, Locale.getDefault()).format(d), 100, Z.Bright, Condensed, FontWeight.Light)
        Spacer(Modifier.width(22.dp))
        T(SimpleDateFormat("EEEE · dd MMM", Locale.ENGLISH).format(d).uppercase(), 24, Color(0xFFB8BDC5), spacing = 3f)
    }
}

@Composable
private fun OutsideTemp(car: CarState) {
    val t = car.outsideTemp.value ?: return
    T(t.replace("℃", "°").replace("C", "°").trim() + " OUTSIDE", 24, Color(0xFFB8BDC5), spacing = 2f)
    Spacer(Modifier.width(24.dp))
}

@Suppress("unused")
@Composable
private fun SteeringReadout(car: CarState) {
    val a = car.steeringDeg.value ?: return
    // Whole degrees only, so tiny wobble while driving does not redraw anything.
    val deg = a.toInt()
    if (kotlin.math.abs(deg) >= 3) {
        T("%+d°".format(deg), 24, Color(0xFFB8BDC5))
        Spacer(Modifier.width(24.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CabinTileBody(car: CarState, accent: Color, sweep: androidx.compose.runtime.State<Float>) {
    val c = car.cabinC.value
    T(c?.let { "%.1f°".format(it) } ?: "--", 96, Z.Bright, Condensed, FontWeight.Light)
    val set = car.setTempL.value
    val fan = car.fan.value
    if (set != null) T("SET %.1f°".format(set) + (fan?.let { " · FAN $it" } ?: ""), 20, Color(0xFFC3C8CF), spacing = 1f)
    val s = sweep.value
    Bar(if (s >= 0f) s else c?.let { ((it - 10f) / 40f).coerceIn(0f, 1f) } ?: 0f, accent)
}

@Composable
private fun SpeedTileBody(fixState: androidx.compose.runtime.State<Fix>, accent: Color, sweep: androidx.compose.runtime.State<Float>) {
    val s = sweep.value
    val kmh = if (s >= 0f) (s * 260f).toInt() else fixState.value.speedKmh
    Row(verticalAlignment = Alignment.Bottom) {
        T(kmh?.toString() ?: "--", 110, Z.Bright, Condensed, FontWeight.Light)
        Spacer(Modifier.width(10.dp))
        T("KM/H", 20, Color(0xFFC3C8CF), spacing = 4f, modifier = Modifier.padding(bottom = 18.dp))
    }
    val km = Trip.km.floatValue
    val min = Trip.minutes.intValue
    T(if (km < 0.05f && min == 0) "TRIP —" else "TRIP %.1f KM · %d:%02d".format(km, min / 60, min % 60), 20, Color(0xFFC3C8CF), spacing = 1f)
    Bar(if (s >= 0f) s else ((kmh ?: 0) / 260f).coerceIn(0f, 1f), accent)
}

/**
 * Factory-style needle sweep: up to full scale and back once when the launcher starts and every time
 * the screen wakes (ignition on). Returns -1 when idle, otherwise the sweep position 0..1.
 */
@Composable
fun rememberStartupSweep(): androidx.compose.runtime.State<Float> {
    val ctx = LocalContext.current
    val value = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }
    val trigger = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val rx = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) { trigger.intValue++ }
        }
        ctx.registerReceiver(rx, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON))
        onDispose { try { ctx.unregisterReceiver(rx) } catch (_: Exception) {} }
    }
    androidx.compose.runtime.LaunchedEffect(trigger.intValue) {
        val a = androidx.compose.animation.core.Animatable(0f)
        value.floatValue = 0f
        delay(350)
        a.animateTo(1f, androidx.compose.animation.core.tween(750, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { value.floatValue = this.value }
        delay(150)
        a.animateTo(0f, androidx.compose.animation.core.tween(850, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { value.floatValue = this.value }
        value.floatValue = -1f
    }
    return value
}

@Composable
private fun CallCard(car: CarState, accent: Color, modifier: Modifier) {
    val st = car.callState.value
    if (st < 4) return
    val ctx = LocalContext.current
    val who = car.callName.value ?: car.callNumber.value ?: "Unknown"
    Row(
        modifier.fillMaxWidth().height(150.dp).glass(RoundedCornerShape(40.dp), 1.4f).background(Color(0x99050506)).padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(Modifier.weight(1f)) {
            T(when (st) { 5 -> "INCOMING CALL"; 4 -> "CALLING"; else -> "ON CALL" }, 18, accent, spacing = 3f, weight = FontWeight.Medium)
            T(who, 46, Z.Bright, Condensed, FontWeight.Medium)
            if (car.callName.value != null && car.callNumber.value != null) T(car.callNumber.value!!, 20, Color(0xFFC3C8CF))
        }
        if (st == 5) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Color(0xFF1F9D55)).clickable { BtControl.call(ctx, true) },
                contentAlignment = Alignment.Center,
            ) { LineIcon("M5 3h4l2 5-2.5 1.5a11 11 0 0 0 6 6L16 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 5a2 2 0 0 1 2-2z", Color.White, 40.dp, 2f) }
        }
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(Color(0xFFD9332B)).clickable { BtControl.call(ctx, false) },
            contentAlignment = Alignment.Center,
        ) { LineIcon("M6 6l12 12 M18 6L6 18", Color.White, 36.dp, 2.4f) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DoorsTileBody(car: CarState, accent: Color) {
    DoorGlyph(car, accent, Modifier.align(Alignment.CenterHorizontally).width(90.dp).height(140.dp))
    val open = listOfNotNull(
        if (car.driverDoor.value) "Driver" else null,
        if (car.passengerDoor.value) "Passenger" else null,
        if (car.boot.value) "Boot" else null,
    )
    T(if (open.isEmpty()) "All closed" else open.joinToString(" · ") + " open", 20, if (open.isEmpty()) Color(0xFFC3C8CF) else accent)
}
