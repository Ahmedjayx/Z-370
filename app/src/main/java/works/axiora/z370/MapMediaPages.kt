package works.axiora.z370

import android.view.KeyEvent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The map at full size, heading-up, with zoom and a one-tap hand-off to the navigation app. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MapPage(
    fixState: State<Fix>,
    car: CarState,
    accent: Color,
    resolved: Map<Role, AppEntry?>,
    onRole: (Role) -> Unit,
    onAssign: (Role) -> Unit,
) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(56.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(56.dp)),
    ) {
        LiveMap(fixState, accent, mapNight(car), Modifier.fillMaxSize())
        CallCard(car, accent, Modifier.align(Alignment.TopCenter).padding(24.dp))
        // Speed, bottom left above the nav bar.
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 164.dp).size(170.dp)
                .glass(RoundedCornerShape(40.dp), 1.4f).background(Color(0x99050506)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            T(Cfg.speed(fixState.value.speedKmh), 76, Z.Bright, Condensed, FontWeight.Light)
            T(Cfg.speedUnit, 16, Soft, spacing = 3f)
        }
        // Zoom.
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).glass(RoundedCornerShape(48.dp), 1.4f).background(Color(0x99050506)),
        ) {
            listOf("M12 5v14 M5 12h14" to 0.75f, "M5 12h14" to -0.75f).forEach { (path, step) ->
                Box(
                    Modifier.size(110.dp).clickable {
                        val z = (Cfg.mapZoom.floatValue + step).coerceIn(11f, 18.5f)
                        Cfg.mapZoom.floatValue = z; Cfg.set("mapZoom", z)
                    },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(path, Z.Bright, 40.dp, 2.2f) }
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(24.dp).height(80.dp)
                .glass(RoundedCornerShape(40.dp), 1.4f).background(Color(0x99050506))
                .combinedClickable(onClick = { onRole(Role.MAPS) }, onLongClick = { onAssign(Role.MAPS) })
                .padding(horizontal = 30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineIcon("M3 11l18-8-8 18-2-8z", accent, 30.dp, 2f)
                T("  NAVIGATE", 20, Z.Bright, spacing = 3f, weight = FontWeight.Medium)
            }
        }
        NavBar(resolved, accent, Modifier.align(Alignment.BottomCenter).padding(24.dp))
    }
}

/** Now playing, full size: big artwork, title, controls and the source you play from. */
@Composable
fun MediaPage(car: CarState, accent: Color, resolved: Map<Role, AppEntry?>, onRole: (Role) -> Unit) {
    val ctx = LocalContext.current
    val media = rememberMedia()
    val np = media.now.value
    val btTitle = car.btTitle.value
    val title = np?.title?.ifBlank { "Unknown track" } ?: btTitle
    val artist = when {
        np != null -> np.artist
        btTitle != null -> "Bluetooth audio"
        else -> "Press play to start your phone's music"
    }
    val playing = np?.playing ?: car.btPlaying.value
    fun key(code: Int) {
        val tc = np?.controller?.transportControls
        if (tc != null) when (code) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> tc.skipToPrevious()
            KeyEvent.KEYCODE_MEDIA_NEXT -> tc.skipToNext()
            else -> if (playing) tc.pause() else tc.play()
        } else if (title == null) BtControl.openBtMusic(ctx, thenPlay = true)
        else BtControl.send(ctx, code)
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().glass(RoundedCornerShape(56.dp)).padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterVertically),
        ) {
            Box(
                Modifier.size(620.dp).clip(RoundedCornerShape(48.dp))
                    .background(Brush.linearGradient(listOf(accent, Color(0xFF2A0D06), Color(0xFF0A0B0D)))),
                contentAlignment = Alignment.Center,
            ) {
                val art = np?.art
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else LineIcon(Role.MUSIC.path, Color.White.copy(alpha = 0.55f), 200.dp, 1f)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(title ?: "Nothing playing", 64, Z.Bright, Condensed, FontWeight.Medium)
                T(artist, 28, Soft)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                GlassButton(120, { key(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }) { LineIcon("M19 5L9 12l10 7z M5 5v14", Z.Bright, 44.dp, 2f) }
                Box(
                    Modifier.size(170.dp).clip(CircleShape).background(accent).clickable { key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (playing) LineIcon("M8 5v14 M16 5v14", Color.White, 64.dp, 2.6f) else LineIcon("M7 4l13 8-13 8z", Color.White, 64.dp, 2.6f)
                }
                GlassButton(120, { key(KeyEvent.KEYCODE_MEDIA_NEXT) }) { LineIcon("M5 5l10 7-10 7z M19 5v14", Z.Bright, 44.dp, 2f) }
            }
        }
        // Sources
        Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            val musicApp = resolved[Role.MUSIC]
            SourceCard("BLUETOOTH", "Phone audio", btTitle != null, accent, Modifier.weight(1f)) { BtControl.openBtMusic(ctx) }
            SourceCard("APP", musicApp?.label ?: "Choose in Settings", np != null, accent, Modifier.weight(1f)) {
                if (musicApp != null) launchApp(ctx, musicApp.pkg) else onRole(Role.MUSIC)
            }
            SourceCard("CARPLAY", resolved[Role.CARPLAY]?.label ?: "zlink", false, accent, Modifier.weight(1f)) { onRole(Role.CARPLAY) }
        }
    }
}

@Composable
private fun SourceCard(label: String, name: String, active: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(44.dp)
    Column(
        modifier.fillMaxHeight().glass(shape, if (active) 1.6f else 1f)
            .then(if (active) Modifier.border(3.dp, accent, shape) else Modifier)
            .clickable(onClick = onClick).padding(30.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(label, 18, Soft, spacing = 4f, modifier = Modifier.weight(1f))
            if (active) Box(Modifier.size(14.dp).clip(CircleShape).background(accent))
        }
        T(name, 36, Z.Bright, Condensed, FontWeight.Medium)
        T(if (active) "PLAYING FROM HERE" else "TAP TO OPEN", 16, if (active) accent else Muted, spacing = 2f)
    }
}
