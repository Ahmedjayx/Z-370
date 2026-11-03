package works.axiora.z370

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/** OpenFreeMap: free OpenStreetMap vector tiles, no account or key. Dark style to sit under the glass. */
const val MAP_STYLE = "https://tiles.openfreemap.org/styles/dark"

/**
 * A live map that follows the car, heading-up. The position puck is drawn by us at the centre,
 * since the camera always keeps the car there. Tapping the card opens the real navigation app.
 */
@Composable
fun LiveMap(fixState: androidx.compose.runtime.State<Fix>, accent: Color, modifier: Modifier) {
    val ctx = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(ctx.applicationContext)
        // Render at 1x pixel density (the unit's screen is far denser than a map card needs) and
        // cap at 24 fps: together several times less GPU work, and steadier on this Snapdragon.
        val opts = org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(ctx).pixelRatio(1.0f)
        MapView(ctx, opts).apply {
            onCreate(null)
            setMaximumFps(24)
        }
    }
    val holder = remember { arrayOfNulls<MapLibreMap>(1) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        mapView.getMapAsync { map ->
            holder[0] = map
            map.setStyle(MAP_STYLE) { style ->
                // Drop what a glanceable driving map doesn't need: buildings, points of interest,
                // house numbers, land cover and most labels. Roads, water and road names stay.
                style.layers.map { it.id }.filter { id ->
                    val l = id.lowercase()
                    listOf("building", "poi", "housenumber", "landcover", "landuse", "aeroway", "park", "place_", "mountain", "boundary").any { l.contains(it) }
                }.forEach { style.removeLayer(it) }
            }
            map.setPrefetchesTiles(false)
            map.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = true
                isCompassEnabled = false
                setAllGesturesEnabled(false)
            }
            map.cameraPosition = CameraPosition.Builder().target(LatLng(25.2048, 55.2708)).zoom(14.5).tilt(0.0).build()
        }
        onDispose {
            owner.lifecycle.removeObserver(obs)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Follow the car, heading-up, flat (no 3D tilt: much lighter on this GPU). Moves the camera at most
    // every 2 s and only when the car moved ~15 m or turned ~8°, instead of on every GPS fix.
    LaunchedEffect(Unit) {
        var lastLat = 0.0; var lastLon = 0.0; var lastBearing = 0f
        androidx.compose.runtime.snapshotFlow { fixState.value }.collect { fix ->
            val map = holder[0] ?: return@collect
            val lat = fix.lat ?: return@collect
            val lon = fix.lon ?: return@collect
            val bearing = fix.bearing ?: lastBearing
            val moved = kotlin.math.abs(lat - lastLat) + kotlin.math.abs(lon - lastLon) > 0.00014
            val turned = kotlin.math.abs(bearing - lastBearing) > 8f
            if (!moved && !turned) return@collect
            lastLat = lat; lastLon = lon; lastBearing = bearing
            val pos = CameraPosition.Builder()
                .target(LatLng(lat, lon))
                .zoom(if ((fix.speedKmh ?: 0) > 70) 14.0 else 15.5)
                .bearing(bearing.toDouble())
                .tilt(0.0)
                .build()
            // Linear glide that lasts until the next update: continuous, even motion, no stop-start.
            map.easeCamera(CameraUpdateFactory.newCameraPosition(pos), 2000, false)
            kotlinx.coroutines.delay(2000)
        }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.align(Alignment.Center).size(60.dp)) {
            val c = center
            drawCircle(accent.copy(alpha = 0.20f), size.minDimension / 2f, c)
            val p = Path().apply {
                moveTo(c.x, c.y - 20f); lineTo(c.x + 14f, c.y + 16f); lineTo(c.x, c.y + 8f); lineTo(c.x - 14f, c.y + 16f); close()
            }
            drawPath(p, accent)
            drawPath(p, Color.White, style = Stroke(3f))
        }
    }
}
