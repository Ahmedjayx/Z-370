package works.axiora.z370

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val Palette = listOf(
    0xFFFF5A1F to "EMBER", 0xFFFF8A1F to "AMBER", 0xFFD9332B to "RACE RED", 0xFFE8ECF2 to "PEARL",
    0xFF4FB3FF to "ICE", 0xFF3DDC97 to "MINT", 0xFF9B7BFF to "VIOLET", 0xFFFFD23F to "GOLD",
)

@Composable
fun SettingsPage(
    accent: Color,
    resolved: Map<Role, AppEntry?>,
    onAccent: (Color) -> Unit,
    onAssign: (Role) -> Unit,
    onProbe: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        T("Settings", 72, Z.Bright, Condensed, FontWeight.Light, modifier = Modifier.padding(start = 8.dp))

        // ---------------------------------------------------------------- Look
        Section("LOOK", "Changes as you tap.")
        Card(Modifier.fillMaxWidth(), "ACCENT") {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Palette.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        row.forEach { (argb, name) ->
                            val c = Color(argb)
                            val on = c == accent
                            Row(
                                Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(30.dp))
                                    .background(Color.White.copy(alpha = if (on) 0.16f else 0.06f))
                                    .border(if (on) 3.dp else 1.dp, if (on) c else Color.White.copy(alpha = 0.18f), RoundedCornerShape(30.dp))
                                    .clickable { onAccent(c) }.padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.radialGradient(listOf(c, c.copy(alpha = 0.55f)))))
                                T(name, 16, if (on) Z.Bright else Soft, spacing = 2f)
                            }
                        }
                    }
                }
            }
        }
        T("BACKGROUND", 16, Muted, spacing = 3f, modifier = Modifier.padding(start = 8.dp, top = 6.dp))
        Options(
            listOf(Triple("ink", "Ink", "Soft black"), Triple("black", "Black", "True black"), Triple("navy", "Navy", "Cool"), Triple("warm", "Warm", "Brown-black")),
            Cfg.bg.value, accent, height = 190,
            preview = { v ->
                val base = when (v) { "black" -> Color.Black; "navy" -> Color(0xFF060A12); "warm" -> Color(0xFF0B0806); else -> Color(0xFF050506) }
                Box(
                    Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(18.dp)).background(base)
                        .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent), Offset(30f, 20f), 160f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp)),
                )
            },
        ) { Cfg.put(Cfg.bg, "bg", it) }
        T("GLASS", 16, Muted, spacing = 3f, modifier = Modifier.padding(start = 8.dp, top = 6.dp))
        Options(
            listOf(Triple(0.6f, "Subtle", "Clearer panels"), Triple(1f, "Normal", null), Triple(1.5f, "Frosted", "More contrast")),
            Cfg.glass.floatValue, accent,
        ) { Cfg.glass.floatValue = it; Cfg.set("glass", it) }
        T("AMBIENT GLOW", 16, Muted, spacing = 3f, modifier = Modifier.padding(start = 8.dp, top = 6.dp))
        Options(
            listOf(Triple(0f, "Off", "Flat"), Triple(0.55f, "Low", null), Triple(1f, "Full", "Accent light")),
            Cfg.glow.floatValue, accent,
        ) { Cfg.glow.floatValue = it; Cfg.set("glow", it) }

        T("NIGHT (HEADLIGHTS ON)", 16, Muted, spacing = 3f, modifier = Modifier.padding(start = 8.dp, top = 6.dp))
        Options(
            listOf(Triple("off", "Same as day", null), Triple("red", "Cluster red", "Red accent, softer glow"), Triple("dim", "Red + dim", "Darker screen")),
            Cfg.nightTheme.value, accent,
        ) { Cfg.put(Cfg.nightTheme, "nightTheme", it) }

        // ---------------------------------------------------------------- Home
        Section("HOME", "What the home screen shows.")
        T("LEFT TILE", 16, Muted, spacing = 3f, modifier = Modifier.padding(start = 8.dp))
        Options(
            listOf(Triple("weather", "Weather", "Open-Meteo"), Triple("steering", "Steering", "CAN angle"), Triple("doors", "Doors", "Top view"), Triple("fuel", "Fuel", "Range + tank")),
            Cfg.leftTile.value, accent,
        ) { Cfg.put(Cfg.leftTile, "leftTile", it) }

        // ---------------------------------------------------------------- Drive
        Section("DRIVE", "The Drive page's face and speed warning.")
        Options(
            listOf(Triple("sweep", "Sweep", "Arc gauge"), Triple("numbers", "Numbers", "Big digits"), Triple("minimal", "Minimal", "Night")),
            Cfg.driveFace.value, accent, height = 230,
            preview = { v -> FacePreview(v, accent) },
        ) { Cfg.put(Cfg.driveFace, "driveFace", it) }
        T("SPEED WARNING", 16, Muted, spacing = 3f, modifier = Modifier.padding(start = 8.dp, top = 6.dp))
        Options(
            listOf(Triple(0, "Off", null), Triple(100, "100", Cfg.speedUnit.lowercase()), Triple(120, "120", null), Triple(140, "140", null)),
            Cfg.redlineKmh.intValue, accent,
        ) { Cfg.redlineKmh.intValue = it; Cfg.set("redlineKmh", it) }

        // ---------------------------------------------------------------- Units
        Section("UNITS")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            OptionCard("km/h", !Cfg.mph.value, accent, "Kilometres") { Cfg.put(Cfg.mph, "mph", false) }
            OptionCard("mph", Cfg.mph.value, accent, "Miles") { Cfg.put(Cfg.mph, "mph", true) }
            OptionCard("°C", !Cfg.fahrenheit.value, accent, "Celsius") { Cfg.put(Cfg.fahrenheit, "fahrenheit", false) }
            OptionCard("°F", Cfg.fahrenheit.value, accent, "Fahrenheit") { Cfg.put(Cfg.fahrenheit, "fahrenheit", true) }
        }

        // ---------------------------------------------------------------- Car
        Section("CAR", "The name shown in the Garage.")
        GlassField(Cfg.carName.value, { Cfg.put(Cfg.carName, "carName", it) }, "370Z", modifier = Modifier.fillMaxWidth())

        // ---------------------------------------------------------------- Apps
        Section("APPS", "Which app each shortcut opens. Tap to change.")
        Card(Modifier.fillMaxWidth(), pad = 12) {
            listOf(Role.MAPS, Role.MUSIC, Role.CARPLAY, Role.PHONE, Role.CAMERA).forEach { role ->
                Row(
                    Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(30.dp)).clickable { onAssign(role) }.padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    LineIcon(role.path, accent, 38.dp, 1.7f)
                    T(role.label, 20, Soft, spacing = 3f, modifier = Modifier.width(170.dp))
                    T(
                        if (role == Role.MUSIC && Prefs.rolePkg(ctx, Role.MUSIC) == null) "Bluetooth music"
                        else resolved[role]?.label ?: "Not set", 30, Z.Bright, Condensed, FontWeight.Medium, modifier = Modifier.weight(1f),
                    )
                    LineIcon("M9 6l6 6-6 6", Muted, 30.dp, 2f)
                }
            }
        }

        // ---------------------------------------------------------------- System
        Section("SYSTEM")
        val scope = rememberCoroutineScope()
        val pickApk = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        ) { uri -> if (uri != null) Updater.install(ctx, uri) }
        fun needInstallPermission(): Boolean {
            if (ctx.packageManager.canRequestPackageInstalls()) return false
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Exception) {}
            return true
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassPill(if (GithubUpdate.busy.value) "UPDATING…" else "UPDATE FROM GITHUB") { if (!needInstallPermission()) scope.launch { GithubUpdate.run(ctx) } }
            GlassPill("INSTALL APK FILE") { if (!needInstallPermission()) pickApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }
        }
        if (GithubUpdate.status.value.isNotEmpty()) T(GithubUpdate.status.value, 20, Muted, modifier = Modifier.padding(start = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassPill("MEDIA ACCESS") {
                try { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            }
            GlassPill("CAN PROBE", onClick = onProbe)
            GlassPill("SAVE DRIVE LOG") { DriveLog.save(ctx) }
        }
        if (DriveLog.status.value.isNotEmpty()) T(DriveLog.status.value, 20, Muted, modifier = Modifier.padding(start = 8.dp))
        val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { "" } }
        Column(Modifier.padding(start = 8.dp, top = 10.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("Z370 Launcher $ver", 22, Soft)
            T("Car model: \"2020 Nissan 370Z Sport Touring\" by Ddiaz Design, CC BY-NC-SA 4.0", 17, Muted)
            T("Wireframe technique after Celica Dash by Kade (MIT).", 17, Muted)
            T("Weather by Open-Meteo.com.", 17, Muted)
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Tiny drawing of each Drive face for its option card. */
@Composable
fun FacePreview(face: String, accent: Color) {
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val c = center; val r = size.height * 0.46f
        when (face) {
            "sweep" -> {
                val tl = Offset(c.x - r, c.y - r); val sz = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                drawArc(Color.White.copy(alpha = 0.18f), 135f, 270f, false, tl, sz, style = Stroke(7f, cap = StrokeCap.Round))
                drawArc(accent, 135f, 160f, false, tl, sz, style = Stroke(7f, cap = StrokeCap.Round))
            }
            "numbers" -> {
                drawRoundRect(Color.White.copy(alpha = 0.75f), Offset(c.x - r * 0.9f, c.y - r * 0.6f), androidx.compose.ui.geometry.Size(r * 0.7f, r * 1.1f), androidx.compose.ui.geometry.CornerRadius(8f), style = Stroke(5f))
                drawRoundRect(Color.White.copy(alpha = 0.75f), Offset(c.x + r * 0.1f, c.y - r * 0.6f), androidx.compose.ui.geometry.Size(r * 0.7f, r * 1.1f), androidx.compose.ui.geometry.CornerRadius(8f), style = Stroke(5f))
                drawLine(accent, Offset(c.x - r * 1.2f, c.y + r * 0.8f), Offset(c.x + r * 0.4f, c.y + r * 0.8f), 7f, StrokeCap.Round)
            }
            else -> {
                drawLine(Color.White.copy(alpha = 0.6f), Offset(c.x - r * 0.6f, c.y), Offset(c.x + r * 0.6f, c.y), 6f, StrokeCap.Round)
                drawCircle(accent, 6f, Offset(c.x, c.y + r * 0.6f))
            }
        }
    }
}
