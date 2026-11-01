package works.axiora.z370

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- Look

object Z {
    val Ground = Color(0xFF07080A)
    val Panel = Color(0xFF0E1013)
    val Stroke = Color(0xFF23262A)
    val Bezel = Color(0xFF3A3E44)
    val BezelHi = Color(0xFF44484F)
    val Track = Color(0xFF24272B)
    val Text = Color(0xFFE6E8EB)
    val Bright = Color(0xFFF4F5F7)
    val Dim = Color(0xFF8D929A)
    val Faint = Color(0xFF6F747C)
    val Silver = Color(0xFFC9CCD1)
    val Accents = listOf(Color(0xFFFF5A1F), Color(0xFFFF8A1F), Color(0xFFE8ECF2), Color(0xFFD9332B))
}

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
)
val Condensed = FontFamily(
    Font(R.font.barlow_condensed_light, FontWeight.Light),
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
)

@Composable
fun T(
    text: String,
    size: Int,
    color: Color = Z.Text,
    font: FontFamily = Barlow,
    weight: FontWeight = FontWeight.Normal,
    spacing: Float = 0f,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontFamily = font,
            fontWeight = weight,
            letterSpacing = spacing.sp,
            lineHeight = (size * 1.05f).sp,
        ),
    )
}

@Composable
fun LineIcon(path: String, color: Color, size: Dp, width: Float = 1.5f) {
    val vec = remember(path, color, width) {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(color),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ).build()
    }
    Image(painter = rememberVectorPainter(vec), contentDescription = null, modifier = Modifier.size(size))
}

// ---------------------------------------------------------------- Root

@Composable
fun Root(homeSignal: Int, locationGranted: Boolean) {
    val ctx = LocalContext.current
    var accent by remember { mutableStateOf(Color(Prefs.accent(ctx))) }
    var drawer by remember { mutableStateOf(false) }
    var assignRole by remember { mutableStateOf<Role?>(null) }
    var settings by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf(Prefs.probeOpen(ctx)) }
    var roleTick by remember { mutableStateOf(0) }

    val pkgTick by rememberPackageTick()
    val apps by produceState(initialValue = emptyList<AppEntry>(), pkgTick) {
        value = withContext(Dispatchers.IO) { loadApps(ctx) }
    }

    LaunchedEffect(homeSignal) {
        drawer = false; assignRole = null; settings = false
    }
    // Back never closes the probe: only its X does, so a stray press can't end a recording.
    BackHandler { drawer = false; assignRole = null; settings = false }

    val car = rememberCarState()
    BoxWithConstraints(Modifier.fillMaxSize().background(Z.Ground)) {
        // 1 dp == 1 px of the 1080-wide design, whatever the unit reports.
        val scale = constraints.maxWidth / 1080f
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            val resolved = remember(apps, roleTick) { Role.entries.associateWith { resolveRole(ctx, it, apps) } }
            val open: (Role) -> Unit = { role ->
                val app = resolved[role]
                if (app == null || !launchApp(ctx, app.pkg)) {
                    assignRole = role; drawer = true
                }
            }
            GlassHome(
                car = car,
                accent = accent,
                locationGranted = locationGranted,
                resolved = resolved,
                onRole = open,
                onAssign = { assignRole = it; drawer = true },
                onApps = { assignRole = null; drawer = true },
                onSettings = { settings = true },
            )
            if (drawer) {
                Drawer(
                    apps = apps,
                    assignRole = assignRole,
                    accent = accent,
                    onPick = { app ->
                        val r = assignRole
                        if (r != null) {
                            Prefs.setRolePkg(ctx, r, app.pkg); roleTick++
                        } else launchApp(ctx, app.pkg)
                        drawer = false; assignRole = null
                    },
                    onReset = {
                        assignRole?.let { Prefs.setRolePkg(ctx, it, null) }
                        roleTick++; drawer = false; assignRole = null
                    },
                    onClose = { drawer = false; assignRole = null },
                )
            }
            if (settings) {
                SettingsSheet(
                    accent = accent,
                    onAccent = { accent = it; Prefs.setAccent(ctx, it.toArgb()) },
                    onClose = { settings = false },
                    onProbe = { settings = false; probe = true; Prefs.setProbeOpen(ctx, true) },
                )
            }
            if (probe) ProbeScreen(accent = accent, onClose = { probe = false; Prefs.setProbeOpen(ctx, false) })
            // Headlights on: dim everything to sit with the gauges at night. Draw-only, touches pass through.
            if (car.night.value) Box(Modifier.fillMaxSize().background(Color(0x66000000)))
        }
    }
}

// ---------------------------------------------------------------- Home

@Composable
fun Home(
    car: CarState,
    accent: Color,
    locationGranted: Boolean,
    resolved: Map<Role, AppEntry?>,
    onRole: (Role) -> Unit,
    onAssign: (Role) -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000 - value % 1000)
        }
    }
    val fix by rememberGpsFix(locationGranted)
    val media = rememberMedia()

    Column(
        Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        StatusRow(now, car, onSettings)
        Row(
            Modifier.fillMaxWidth().height(440.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            PodColumn("CABIN", Modifier.weight(1f)) { CabinPod(car.cabinC.value, accent) }
            PodColumn("GPS SPEED", Modifier.weight(1.35f)) { SpeedPod(fix.speedKmh, accent) }
            PodColumn("CLOCK", Modifier.weight(1f)) { ClockPod(now, accent) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NavCard(resolved[Role.MAPS], accent, Modifier.fillMaxSize(), { onRole(Role.MAPS) }, { onAssign(Role.MAPS) })
            if (car.driverDoor.value || car.passengerDoor.value || car.boot.value) DoorAlert(car, accent, Modifier.fillMaxSize())
        }
        MediaCard(media, car, accent, resolved[Role.MUSIC], { onRole(Role.MUSIC) })
        Dock(accent, onRole, onAssign, onApps)
    }
}

@Composable
fun StatusRow(now: Long, car: CarState, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val timeFmt = if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
    val d = Date(now)
    Row(
        Modifier.fillMaxWidth().height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(SimpleDateFormat(timeFmt, Locale.getDefault()).format(d), 46, Z.Bright, Condensed, FontWeight.Medium, 1f)
        Spacer(Modifier.width(22.dp))
        T(SimpleDateFormat("EEE dd MMM", Locale.ENGLISH).format(d).uppercase(), 22, Z.Dim, spacing = 2f)
        Spacer(Modifier.weight(1f))
        car.steeringDeg.value?.let { a ->
            Canvas(Modifier.size(40.dp)) {
                val c = center; val r = size.minDimension / 2f - 3f
                drawCircle(Z.Dim, r, c, style = Stroke(3f))
                rotate(a, c) {
                    drawLine(Z.Dim, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 3f)
                    drawLine(Z.Dim, c, Offset(c.x, c.y + r), 3f)
                }
            }
            Spacer(Modifier.width(10.dp))
            T("%+d°".format(a.toInt()), 22, Z.Dim)
            Spacer(Modifier.width(16.dp))
        }
        Box(
            Modifier.size(72.dp).clip(CircleShape).clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon("M4 6h16 M4 12h16 M4 18h16 M8 4v4 M16 10v4 M10 16v4", Z.Dim, 34.dp)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Z.Stroke))
}

@Composable
fun PodColumn(label: String, modifier: Modifier, pod: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        pod()
        T(label, 18, Z.Faint, spacing = 4f)
    }
}

@Composable
fun Bezel(size: Int, ring: Color = Z.Bezel, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF17191D), Color(0xFF0A0B0D))))
            .border(3.dp, ring, CircleShape)
            .padding(10.dp)
            .border(1.dp, Color(0xFF2A2D32), CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val Cardinals = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

@Composable
fun CompassPod(bearing: Float?, accent: Color) {
    Bezel(270) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f - 14f
            for (i in 0 until 8) {
                rotate(i * 45f, c) {
                    drawLine(Z.Bezel, Offset(c.x, c.y - r), Offset(c.x, c.y - r + (if (i % 2 == 0) 16f else 9f)), 3f, StrokeCap.Round)
                }
            }
            if (bearing != null) {
                rotate(bearing, c) {
                    val p = Path().apply {
                        moveTo(c.x, c.y - r - 2f); lineTo(c.x - 12f, c.y - r + 20f); lineTo(c.x + 12f, c.y - r + 20f); close()
                    }
                    drawPath(p, accent)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val b = bearing
            T(if (b == null) "--" else Cardinals[(((b + 22.5f) % 360f) / 45f).toInt() % 8], 56, Z.Bright, Condensed, FontWeight.Medium)
            T(if (b == null) "NO FIX" else "%03d°".format(b.toInt()), 20, Z.Dim, spacing = 2f)
        }
    }
}

@Composable
fun SpeedPod(speed: Int?, accent: Color) {
    Bezel(380, Z.BezelHi) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 22f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(Z.Track, 135f, 270f, false, tl, arcSize, style = Stroke(10f, cap = StrokeCap.Round))
            val frac = ((speed ?: 0) / 260f).coerceIn(0f, 1f)
            if (frac > 0f) drawArc(accent, 135f, 270f * frac, false, tl, arcSize, style = Stroke(10f, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(speed?.toString() ?: "--", 150, Z.Bright, Condensed, FontWeight.Light)
            T("KM/H", 22, Z.Dim, spacing = 6f)
        }
    }
}

@Composable
fun ClockPod(now: Long, accent: Color) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h = cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f
    val m = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
    Bezel(270) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f - 14f
            for (i in 0 until 12) {
                rotate(i * 30f, c) {
                    drawLine(Z.Bezel, Offset(c.x, c.y - r), Offset(c.x, c.y - r + (if (i % 3 == 0) 18f else 9f)), 3f, StrokeCap.Round)
                }
            }
            rotate(h * 30f, c) { drawLine(Z.Text, c, Offset(c.x, c.y - r * 0.55f), 6f, StrokeCap.Round) }
            rotate(m * 6f, c) { drawLine(accent, c, Offset(c.x, c.y - r * 0.82f), 4f, StrokeCap.Round) }
            drawCircle(Z.Ground, 8f, c)
            drawCircle(Z.Dim, 8f, c, style = Stroke(2f))
        }
    }
}

// ---------------------------------------------------------------- Cards

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NavCard(app: AppEntry?, accent: Color, modifier: Modifier, onOpen: () -> Unit, onLong: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(22.dp)).background(Z.Panel)
            .border(1.dp, Z.Stroke, RoundedCornerShape(22.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLong),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val hh = size.height
            val road = Color(0xFF121417); val lane = Color(0xFF0E1012)
            drawLine(road, Offset(-20f, hh * 0.22f), Offset(w + 20f, hh * 0.32f), 14f)
            drawLine(road, Offset(-20f, hh * 0.66f), Offset(w + 20f, hh * 0.55f), 14f)
            drawLine(road, Offset(w * 0.18f, -20f), Offset(w * 0.24f, hh + 20f), 14f)
            drawLine(road, Offset(w * 0.62f, -20f), Offset(w * 0.56f, hh + 20f), 14f)
            drawLine(road, Offset(w * 0.82f, -20f), Offset(w * 0.90f, hh + 20f), 14f)
            drawLine(lane, Offset(-20f, hh * 0.85f), Offset(w + 20f, hh * 0.82f), 6f)
            drawLine(lane, Offset(w * 0.40f, -20f), Offset(w * 0.38f, hh + 20f), 6f)
            drawCircle(accent.copy(alpha = 0.18f), 60f, Offset(w * 0.5f, hh * 0.5f))
            drawCircle(accent, 14f, Offset(w * 0.5f, hh * 0.5f))
            drawCircle(Z.Bright, 14f, Offset(w * 0.5f, hh * 0.5f), style = Stroke(4f))
        }
        Column(
            Modifier.padding(28.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xDD07080A))
                .border(1.dp, Color(0xFF2A2D32), RoundedCornerShape(16.dp)).padding(horizontal = 26.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            T("NAVIGATION", 18, Z.Dim, spacing = 3f)
            T(app?.label ?: "Choose a maps app", 40, Z.Bright, Condensed, FontWeight.Medium)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(28.dp).clip(RoundedCornerShape(16.dp))
                .background(Color(0xDD07080A)).border(1.dp, Color(0xFF2A2D32), RoundedCornerShape(16.dp))
                .padding(horizontal = 26.dp, vertical = 16.dp),
        ) { T("TAP TO OPEN", 20, Z.Silver, spacing = 2f) }
    }
}

@Composable
fun MediaCard(media: MediaState, car: CarState, accent: Color, musicApp: AppEntry?, onOpenMusic: () -> Unit) {
    val ctx = LocalContext.current
    val np = media.now.value
    // Without media-session access (blocked on this unit), fall back to the unit's own Bluetooth audio broadcast.
    val btTitle = car.btTitle.value
    val title: String?
    val subtitle: String
    val playing: Boolean
    when {
        np != null -> { title = np.title.ifBlank { "Unknown track" }; subtitle = np.artist; playing = np.playing }
        btTitle != null -> { title = btTitle; subtitle = "Bluetooth audio"; playing = car.btPlaying.value }
        else -> { title = null; subtitle = musicApp?.label ?: "Tap the cover to open music"; playing = false }
    }
    fun key(code: Int) {
        val tc = np?.controller?.transportControls
        if (tc != null) {
            when (code) {
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> tc.skipToPrevious()
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> tc.skipToNext()
                else -> if (playing) tc.pause() else tc.play()
            }
        } else sendMediaKey(ctx, code)
    }
    Row(
        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF121417), Color(0xFF0D0F11))))
            .border(1.dp, Z.Stroke, RoundedCornerShape(22.dp))
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Box(
            Modifier.size(156.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1E22))
                .border(1.dp, Color(0xFF2A2D32), RoundedCornerShape(14.dp)).clickable(onClick = onOpenMusic),
            contentAlignment = Alignment.Center,
        ) {
            val art = np?.art
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else LineIcon(Role.MUSIC.path, Color(0xFF4A4E55), 56.dp, 1.4f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T(if (title == null) "MEDIA" else "NOW PLAYING", 18, Z.Dim, spacing = 3f)
            T(title ?: "Nothing playing", 42, Z.Bright, Condensed, FontWeight.Medium)
            T(subtitle, 24, Color(0xFFA4A9B0))
        }
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RoundButton(72, Color(0xFF2F3338), { key(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) }) { LineIcon("M19 5L9 12l10 7z M5 5v14", Z.Silver, 30.dp, 1.8f) }
                RoundButton(96, accent, { key(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, fill = Color(0xFF1B1E22)) {
                    if (playing) LineIcon("M8 5v14 M16 5v14", Z.Bright, 36.dp, 2f)
                    else LineIcon("M7 4l13 8-13 8z", Z.Bright, 36.dp, 2f)
                }
                RoundButton(72, Color(0xFF2F3338), { key(android.view.KeyEvent.KEYCODE_MEDIA_NEXT) }) { LineIcon("M5 5l10 7-10 7z M19 5v14", Z.Silver, 30.dp, 1.8f) }
            }
        }
    }
}

fun sendMediaKey(ctx: android.content.Context, code: Int) {
    try {
        val am = ctx.getSystemService(android.media.AudioManager::class.java)
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
    } catch (_: Exception) {
    }
}

@Composable
fun RoundButton(size: Int, ring: Color, onClick: () -> Unit, fill: Color = Color.Transparent, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(fill).border(2.dp, ring, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Dock(accent: Color, onRole: (Role) -> Unit, onAssign: (Role) -> Unit, onApps: () -> Unit) {
    val tiles = listOf(Role.CARPLAY, Role.MAPS, Role.PHONE, Role.MUSIC, Role.CAMERA)
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        tiles.forEach { role ->
            DockTile(role.label, role.path, Modifier.weight(1f), { onRole(role) }, { onAssign(role) })
        }
        DockTile("APPS", "M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z", Modifier.weight(1f), onApps, onApps)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockTile(label: String, path: String, modifier: Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Z.Panel)
            .border(1.dp, Z.Stroke, RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        LineIcon(path, Z.Silver, 46.dp)
        T(label, 18, Color(0xFFA4A9B0), spacing = 2f)
    }
}

// ---------------------------------------------------------------- Overlays

@Composable
fun Drawer(
    apps: List<AppEntry>,
    assignRole: Role?,
    accent: Color,
    onPick: (AppEntry) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Color(0xF707080A)).clickable(enabled = false) {}.padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                T(if (assignRole == null) "ALL APPS" else "ASSIGN TILE", 18, Z.Dim, spacing = 3f)
                T(if (assignRole == null) "Applications" else "Choose app for ${assignRole.label}", 44, Z.Bright, Condensed, FontWeight.Medium)
            }
            if (assignRole != null) {
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, Z.Stroke, RoundedCornerShape(16.dp))
                        .clickable(onClick = onReset).padding(horizontal = 24.dp, vertical = 18.dp),
                ) { T("AUTOMATIC", 20, Z.Silver, spacing = 2f) }
                Spacer(Modifier.width(16.dp))
            }
            RoundButton(80, Z.Bezel, onClose) { LineIcon("M6 6l12 12 M18 6L6 18", Z.Silver, 34.dp, 2f) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Z.Stroke))
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(apps, key = { it.pkg }) { app ->
                Column(
                    Modifier.clip(RoundedCornerShape(18.dp)).clickable { onPick(app) }.padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(app.icon, null, Modifier.size(104.dp))
                    T(app.label, 20, Z.Silver, modifier = Modifier.padding(horizontal = 6.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsSheet(accent: Color, onAccent: (Color) -> Unit, onClose: () -> Unit, onProbe: () -> Unit) {
    val ctx = LocalContext.current
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(820.dp).clip(RoundedCornerShape(26.dp)).background(Z.Panel)
                .border(1.dp, Z.Stroke, RoundedCornerShape(26.dp)).clickable(enabled = false) {}.padding(44.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            T("SETTINGS", 18, Z.Dim, spacing = 3f)
            T("Illumination", 44, Z.Bright, Condensed, FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Z.Accents.forEach { c ->
                    Box(
                        Modifier.size(110.dp).clip(CircleShape).background(Z.Ground)
                            .border(if (c == accent) 4.dp else 1.dp, if (c == accent) c else Z.Stroke, CircleShape)
                            .clickable { onAccent(c) },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(52.dp).clip(CircleShape).background(c)) }
                }
            }
            val pickApk = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
            ) { uri -> if (uri != null) Updater.install(ctx, uri) }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Pill("INSTALL UPDATE FROM FILE") {
                    if (!ctx.packageManager.canRequestPackageInstalls()) {
                        try {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + ctx.packageName))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (_: Exception) {}
                    } else pickApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                }
                Pill("MEDIA ACCESS") {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {}
                }
            }
            Pill(if (OfflineMap.busy.value) "DOWNLOADING UAE MAP…" else "DOWNLOAD UAE OFFLINE MAP") { OfflineMap.start(ctx) }
            if (OfflineMap.status.value.isNotEmpty()) T(OfflineMap.status.value, 20, Z.Dim)
            Pill("OPEN CAN PROBE", onClick = onProbe)
            T("Long-press any dock tile, or the navigation card, to choose its app.", 22, Z.Dim)
            val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { "" } }
            T("Version $ver", 20, Z.Faint)
        }
    }
}

@Composable
fun DoorAlert(car: CarState, accent: Color, modifier: Modifier) {
    val driver = car.driverDoor.value
    val passenger = car.passengerDoor.value
    val boot = car.boot.value
    Row(
        modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xF20A0B0D))
            .border(2.dp, accent, RoundedCornerShape(22.dp)).padding(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        // Top view, nose up. Left-hand drive: driver door on the left.
        Canvas(Modifier.width(220.dp).fillMaxHeight()) {
            val w = size.width; val h = size.height
            val bw = w * 0.62f; val bh = minOf(h * 0.92f, bw * 2.2f)
            val left = (w - bw) / 2f; val top = (h - bh) / 2f
            val line = Stroke(4f)
            drawRoundRect(Z.Bezel, Offset(left, top), Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(bw * 0.38f), style = line)
            // glasshouse
            drawRoundRect(Z.Dim, Offset(left + bw * 0.16f, top + bh * 0.30f), Size(bw * 0.68f, bh * 0.40f), androidx.compose.ui.geometry.CornerRadius(bw * 0.2f), style = Stroke(3f))
            val dTop = top + bh * 0.34f; val dBot = top + bh * 0.58f
            fun door(x: Float, open: Boolean, dir: Float) {
                if (open) {
                    drawLine(accent, Offset(x, dTop), Offset(x + dir * bw * 0.55f, dTop + (dBot - dTop) * 0.55f), 10f, StrokeCap.Round)
                    drawCircle(accent, 8f, Offset(x, dTop))
                } else drawLine(Z.Dim, Offset(x, dTop), Offset(x, dBot), 6f, StrokeCap.Round)
            }
            door(left, driver, -1f)
            door(left + bw, passenger, 1f)
            val rear = top + bh
            if (boot) drawLine(accent, Offset(left + bw * 0.2f, rear + 6f), Offset(left + bw * 0.8f, rear + 6f), 10f, StrokeCap.Round)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("OPEN", 18, accent, spacing = 3f, weight = FontWeight.Medium)
            listOfNotNull(
                if (driver) "Driver door" else null,
                if (passenger) "Passenger door" else null,
                if (boot) "Boot" else null,
            ).forEach { T(it, 48, Z.Bright, Condensed, FontWeight.Medium) }
        }
    }
}

@Composable
fun CabinPod(tempC: Float?, accent: Color) {
    // Styled after the factory oil-temp pod: 10–50 °C over a 270° sweep.
    val lo = 10f; val hi = 50f
    Bezel(270) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f - 14f
            for (i in 0..8) {
                val a = 135f + 270f * i / 8f
                rotate(a + 90f, c) {
                    drawLine(Z.Bezel, Offset(c.x, c.y - r), Offset(c.x, c.y - r + (if (i % 2 == 0) 18f else 9f)), 3f, StrokeCap.Round)
                }
            }
            if (tempC != null) {
                val f = ((tempC - lo) / (hi - lo)).coerceIn(0f, 1f)
                rotate(135f + 270f * f + 90f, c) {
                    drawLine(accent, c, Offset(c.x, c.y - r * 0.80f), 5f, StrokeCap.Round)
                }
            }
            drawCircle(Z.Ground, 8f, c)
            drawCircle(Z.Dim, 8f, c, style = Stroke(2f))
        }
        Column(Modifier.fillMaxSize().padding(bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
            T(tempC?.let { "%.1f°".format(it) } ?: "--", 40, Z.Bright, Condensed, FontWeight.Medium)
        }
    }
}
