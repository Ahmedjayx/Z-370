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
import kotlinx.coroutines.launch
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
    var roleTick by remember { mutableStateOf(0) }

    val pkgTick by rememberPackageTick()
    val apps by produceState(initialValue = emptyList<AppEntry>(), pkgTick) {
        value = withContext(Dispatchers.IO) { loadApps(ctx) }
    }

    LaunchedEffect(homeSignal) {
        drawer = false; assignRole = null; settings = false
        Cfg.page.value = "home"
    }
    BackHandler { drawer = false; assignRole = null; settings = false; if (Cfg.page.value == "settings") Cfg.page.value = "home" }

    val car = rememberCarState()
    BoxWithConstraints(Modifier.fillMaxSize().background(Z.Ground)) {
        // 1 dp == 1 px of the 1080-wide design, whatever the unit reports.
        val scale = constraints.maxWidth / 1080f
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            val resolved = remember(apps, roleTick) { Role.entries.associateWith { resolveRole(ctx, it, apps) } }
            val open: (Role) -> Unit = open@{ role ->
                // Default Music tile: the unit's Bluetooth music page (phone audio), unless you picked an app.
                if (role == Role.MUSIC && Prefs.rolePkg(ctx, Role.MUSIC) == null) {
                    BtControl.openBtMusic(ctx); return@open
                }
                val app = resolved[role]
                if (app == null || !launchApp(ctx, app.pkg)) {
                    assignRole = role; drawer = true
                }
            }
            AppShell(
                car = car,
                dayAccent = accent,
                locationGranted = locationGranted,
                resolved = resolved,
                onRole = open,
                onAssign = { assignRole = it; drawer = true },
                onApps = { assignRole = null; drawer = true },
                onAccent = { accent = it; Prefs.setAccent(ctx, it.toArgb()) },
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
            // Headlights on: dim everything to sit with the gauges at night. Draw-only, touches pass through.
            // No extra dimming here: the unit already lowers its backlight with the headlights.
        }
    }
}

// ---------------------------------------------------------------- Home

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
