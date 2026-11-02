package works.axiora.z370

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The car, rendered: a pearl-white 370Z with its live door and boot state. Pre-rendered images
 * (8 open/closed combinations and a 36-step turntable), so the unit only shows pictures.
 * On start-up and every screen wake it turns once on the spot, then settles.
 *
 * 3D model: "2020 Nissan 370Z Sport Touring" by Ddiaz Design (sketchfab.com/ddiaz-design),
 * CC BY-NC-SA 4.0. The renders are a derived work under the same licence. Personal, non-commercial use.
 */
@Composable
fun CarHero(car: CarState, modifier: Modifier) {
    val ctx = LocalContext.current
    val res = ctx.resources
    val pkg = ctx.packageName

    fun load(name: String): ImageBitmap? {
        val id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return BitmapFactory.decodeResource(res, id)?.asImageBitmap()
    }

    // Turntable on wake.
    val wake = remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val rx = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) { wake.intValue++ }
        }
        ctx.registerReceiver(rx, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON))
        onDispose { try { ctx.unregisterReceiver(rx) } catch (_: Exception) {} }
    }
    var spinFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(wake.intValue) {
        delay(300)
        for (i in 1 until 36) {
            val t0 = System.currentTimeMillis()
            spinFrame = withContext(Dispatchers.IO) { load("spin_%03d".format(i)) } ?: break
            delay((66 - (System.currentTimeMillis() - t0)).coerceAtLeast(0))
        }
        spinFrame = null
    }

    val key = "car_%d%d%d".format(
        if (car.driverDoor.value) 1 else 0,
        if (car.passengerDoor.value) 1 else 0,
        if (car.boot.value) 1 else 0,
    )
    val states = remember { HashMap<String, ImageBitmap?>() }

    Box(modifier, contentAlignment = Alignment.Center) {
        val spin = spinFrame
        if (spin != null) {
            Image(spin, null, Modifier.fillMaxSize().padding(horizontal = 12.dp), contentScale = ContentScale.Fit)
        } else {
            Crossfade(targetState = key, animationSpec = tween(350), label = "car") { k ->
                // Keep at most the two images a crossfade needs in memory.
                val bmp = states.getOrPut(k) { load(k) }
                if (states.size > 2) states.keys.filter { it != k && it != key }.forEach { states.remove(it) }
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize().padding(horizontal = 12.dp), contentScale = ContentScale.Fit)
            }
        }
    }
}
