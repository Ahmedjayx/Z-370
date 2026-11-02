package works.axiora.z370

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt

/**
 * Notification access gives two things: the active media sessions, and Google Maps' (or Waze's)
 * ongoing navigation notification, which carries the next turn while it routes.
 */
class MediaListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification) {
        if (sbn.packageName !in Nav.APPS || !sbn.isOngoing) return
        val ex = sbn.notification.extras
        val title = ex.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        val text = ex.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        val sub = ex.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()
        val icon = try {
            sbn.notification.getLargeIcon()?.loadDrawable(this)?.toBitmap(160, 160)?.asImageBitmap()
        } catch (_: Exception) { null }
        if (title.isNullOrBlank() && text.isNullOrBlank()) return
        Nav.step.value = NavStep(title.orEmpty(), text.orEmpty(), sub, icon, sbn.packageName)
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification) {
        if (sbn.packageName == Nav.step.value?.app && sbn.isOngoing) Nav.step.value = null
    }
}

data class NavStep(val title: String, val text: String, val sub: String?, val arrow: ImageBitmap?, val app: String)

object Nav {
    val APPS = setOf("com.google.android.apps.maps", "com.waze")
    val step = mutableStateOf<NavStep?>(null)
}

// ---------------------------------------------------------------- GPS

/** Trip computer from GPS: distance and time since the drive began. A new trip starts after 30 min parked. */
object Trip {
    val km = androidx.compose.runtime.mutableFloatStateOf(0f)
    val minutes = androidx.compose.runtime.mutableIntStateOf(0)
    private var last: android.location.Location? = null
    private var startMs = 0L
    private var lastMoveMs = 0L

    fun onFix(loc: android.location.Location) {
        val now = System.currentTimeMillis()
        val moving = loc.hasSpeed() && loc.speed > 1.5f
        if (moving) {
            if (startMs == 0L || now - lastMoveMs > 30 * 60_000L) { km.floatValue = 0f; startMs = now; last = null }
            lastMoveMs = now
            last?.let { p -> val d = p.distanceTo(loc); if (d < 500f) km.floatValue += d / 1000f }
            last = loc
        }
        if (startMs != 0L) minutes.intValue = ((now - startMs) / 60_000L).toInt()
    }
}

data class Fix(val speedKmh: Int?, val bearing: Float?, val lat: Double? = null, val lon: Double? = null)

@Composable
fun rememberGpsFix(granted: Boolean): State<Fix> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(Fix(null, null)) }
    DisposableEffect(granted) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val listener = LocationListener { loc ->
            val speed = if (loc.hasSpeed()) (loc.speed * 3.6f).roundToInt() else null
            // Bearing is noise when stationary; hold the last good one.
            val bearing = if (loc.hasBearing() && loc.speed > 1.5f) loc.bearing else state.value.bearing
            state.value = Fix(speed, bearing, loc.latitude, loc.longitude)
            Trip.onFix(loc)
        }
        var registered = false
        if (granted && lm != null) {
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, listener, Looper.getMainLooper())
                registered = true
                (lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))
                    ?.let { if (state.value.lat == null) state.value = Fix(null, null, it.latitude, it.longitude) }
            } catch (_: Exception) {
            }
        }
        onDispose { if (registered) lm?.removeUpdates(listener) }
    }
    return state
}

// ---------------------------------------------------------------- Media

data class NowPlaying(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val art: ImageBitmap?,
    val controller: MediaController,
)

fun hasMediaAccess(ctx: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

class MediaState(val access: State<Boolean>, val now: State<NowPlaying?>)

@Composable
fun rememberMedia(): MediaState {
    val ctx = LocalContext.current
    val access = remember { mutableStateOf(hasMediaAccess(ctx)) }
    val now = remember { mutableStateOf<NowPlaying?>(null) }

    // Re-check access whenever we come back to the home screen.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) access.value = hasMediaAccess(ctx)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    DisposableEffect(access.value) {
        val msm = ctx.getSystemService(MediaSessionManager::class.java)
        val comp = ComponentName(ctx, MediaListener::class.java)
        var current: MediaController? = null

        val publish: () -> Unit = {
            val c = current
            now.value = if (c == null) null else {
                val md = c.metadata
                val bmp: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                NowPlaying(
                    title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                    artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                    playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
                    art = bmp?.asImageBitmap(),
                    controller = c,
                )
            }
        }
        val cb = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
            override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
            override fun onSessionDestroyed() {
                current = null
                publish()
            }
        }
        val pick: (List<MediaController>?) -> Unit = { list ->
            current?.unregisterCallback(cb)
            current = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: list?.firstOrNull()
            current?.registerCallback(cb)
            publish()
        }
        val sessions = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

        var registered = false
        if (access.value && msm != null) {
            try {
                msm.addOnActiveSessionsChangedListener(sessions, comp)
                registered = true
                pick(msm.getActiveSessions(comp))
            } catch (_: SecurityException) {
                access.value = false
            }
        }
        onDispose {
            if (registered) msm?.removeOnActiveSessionsChangedListener(sessions)
            current?.unregisterCallback(cb)
        }
    }
    return MediaState(access, now)
}

// ---------------------------------------------------------------- Apps

data class AppEntry(val label: String, val pkg: String, val icon: ImageBitmap)

fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map {
            AppEntry(
                label = it.loadLabel(pm).toString(),
                pkg = it.activityInfo.packageName,
                icon = it.loadIcon(pm).toBitmap(128, 128).asImageBitmap(),
            )
        }
        .sortedBy { it.label.lowercase() }
}

fun launchApp(ctx: Context, pkg: String): Boolean = try {
    val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
    if (i != null) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } else false
} catch (_: Exception) {
    false
}

/** Bumps whenever an app is installed, removed or updated. */
@Composable
fun rememberPackageTick(): State<Int> {
    val ctx = LocalContext.current
    val tick = remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                tick.intValue++
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ctx.registerReceiver(rx, f)
        onDispose { ctx.unregisterReceiver(rx) }
    }
    return tick
}

// ---------------------------------------------------------------- Dock roles

enum class Role(val label: String, val keywords: List<String>, val path: String) {
    CARPLAY(
        "CARPLAY", listOf("zlink", "carplay", "carlink", "autolink", "easyconnect"),
        "M4 6h16v10H4z M8 20h8 M12 16v4",
    ),
    MAPS(
        "MAPS", listOf("com.google.android.apps.maps", "waze", "maps", "navi"),
        "M9 4L3 6v14l6-2 6 2 6-2V4l-6 2z M9 4v14 M15 6v14",
    ),
    PHONE(
        "PHONE", listOf("bluetooth", "dialer", "phone", "blink"),
        "M5 3h4l2 5-2.5 1.5a11 11 0 0 0 6 6L16 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 5a2 2 0 0 1 2-2z",
    ),
    MUSIC(
        "MUSIC", listOf("spotify", "anghami", "music", "player", "media"),
        "M9 18V5l12-2v13 M3 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0 M15 16a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
    ),
    CAMERA(
        "CAMERA", listOf("avm", "camera", "360", "dvr", "reverse"),
        "M3 7h4l2-3h6l2 3h4v13H3z M8 13a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
    ),
}

fun resolveRole(ctx: Context, role: Role, apps: List<AppEntry>): AppEntry? {
    val saved = Prefs.rolePkg(ctx, role)
    apps.firstOrNull { it.pkg == saved }?.let { return it }
    for (k in role.keywords) {
        apps.firstOrNull { it.pkg.lowercase().contains(k) || it.label.lowercase().contains(k) }?.let { return it }
    }
    return null
}

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("z370", Context.MODE_PRIVATE)
    fun rolePkg(ctx: Context, role: Role): String? = sp(ctx).getString("role_${role.name}", null)
    fun setRolePkg(ctx: Context, role: Role, pkg: String?) =
        sp(ctx).edit().apply { if (pkg == null) remove("role_${role.name}") else putString("role_${role.name}", pkg) }.apply()
    fun probeOpen(ctx: Context): Boolean = sp(ctx).getBoolean("probe_open", false)
    fun setProbeOpen(ctx: Context, open: Boolean) = sp(ctx).edit().putBoolean("probe_open", open).apply()
    fun accent(ctx: Context): Int = sp(ctx).getInt("accent", 0xFFFF5A1F.toInt())
    fun setAccent(ctx: Context, argb: Int) = sp(ctx).edit().putInt("accent", argb).apply()
}
