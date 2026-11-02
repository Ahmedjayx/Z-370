package works.axiora.z370

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The car, rendered: a pearl-white 370Z with its live door and boot state.
 * All 8 open/closed pictures are decoded once, off the UI thread, then only swapped, so a door
 * opening never stalls a frame. On every screen wake the car eases in (fade, rise, settle):
 * a GPU-only effect, smooth at 60 fps, unlike a frame-by-frame turntable.
 *
 * 3D model: "2020 Nissan 370Z Sport Touring" by Ddiaz Design (sketchfab.com/ddiaz-design),
 * CC BY-NC-SA 4.0. The renders are a derived work under the same licence. Personal, non-commercial use.
 */
private val STATES = listOf("car_000", "car_001", "car_010", "car_011", "car_100", "car_101", "car_110", "car_111")

@Composable
fun CarHero(car: CarState, modifier: Modifier) {
    val ctx = LocalContext.current

    val images by produceState<Map<String, ImageBitmap>>(emptyMap()) {
        value = withContext(Dispatchers.IO) {
            STATES.mapNotNull { name ->
                val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
                if (id == 0) return@mapNotNull null
                val bmp = BitmapFactory.decodeResource(ctx.resources, id) ?: return@mapNotNull null
                bmp.prepareToDraw() // upload to the GPU ahead of first use
                name to bmp.asImageBitmap()
            }.toMap()
        }
    }

    // Ease in on start-up and every screen wake.
    val wake = remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val rx = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) { wake.intValue++ }
        }
        ctx.registerReceiver(rx, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON))
        onDispose { try { ctx.unregisterReceiver(rx) } catch (_: Exception) {} }
    }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(wake.intValue, images.isNotEmpty()) {
        if (images.isEmpty()) return@LaunchedEffect
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }

    val key = "car_%d%d%d".format(
        if (car.driverDoor.value) 1 else 0,
        if (car.passengerDoor.value) 1 else 0,
        if (car.boot.value) 1 else 0,
    )

    Box(
        modifier.graphicsLayer {
            val r = reveal.value
            alpha = r
            scaleX = 0.92f + 0.08f * r
            scaleY = 0.92f + 0.08f * r
            translationY = 40f * (1f - r)
        },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = key, animationSpec = tween(300), label = "car") { k ->
            val bmp = images[k] ?: images["car_000"]
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize().padding(horizontal = 12.dp), contentScale = ContentScale.Fit)
        }
    }
}
