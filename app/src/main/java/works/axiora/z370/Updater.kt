package works.axiora.z370

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.widget.Toast

/**
 * Installs an APK through Android's session installer instead of the browser's file handler.
 * Android 13 restricts special access (like notification access) only for apps that arrived as a
 * downloaded or local file; an app installed through a session marked as a store install is not
 * restricted. Updating the launcher through here should lift that block, and gives one-tap updates.
 */
object Updater {
    private const val ACTION_STATUS = "works.axiora.z370.INSTALL_STATUS"

    fun install(ctx: Context, apk: Uri) {
        try {
            val pi = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = pi.createSession(params)
            pi.openSession(id).use { session ->
                ctx.contentResolver.openInputStream(apk)!!.use { input ->
                    session.openWrite("z370.apk", 0, -1).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val intent = Intent(ctx, StatusReceiver::class.java).setAction(ACTION_STATUS)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                session.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
            }
            Toast.makeText(ctx, "Installing update…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Receives the installer's result; asks the user to confirm when Android requires it. */
    class StatusReceiver : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { if (confirm != null) c.startActivity(confirm) } catch (_: Exception) {}
                }
                PackageInstaller.STATUS_SUCCESS -> Toast.makeText(c, "Updated", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(c, "Install: " + (i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "failed"), Toast.LENGTH_LONG).show()
            }
        }
    }
}

/**
 * One-tap update straight from GitHub Releases: reads the latest release, downloads its
 * Z370-Launcher-latest.apk and hands it to the session installer above.
 * GitHub only serves release files without a login when the repository is public.
 */
object GithubUpdate {
    private const val LATEST = "https://api.github.com/repos/Ahmedjayx/Z-370/releases/latest"
    val status = androidx.compose.runtime.mutableStateOf("")
    val busy = androidx.compose.runtime.mutableStateOf(false)

    suspend fun run(ctx: Context) {
        if (busy.value) return
        busy.value = true
        try {
            status.value = "Checking GitHub…"
            val (tag, url) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val conn = java.net.URL(LATEST).openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 15000; conn.readTimeout = 15000
                if (conn.responseCode == 404) error("can't see the repository. It needs to be public for updates without a login.")
                if (conn.responseCode != 200) error("GitHub answered ${conn.responseCode}")
                val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name") == "Z370-Launcher-latest.apk") apkUrl = a.getString("browser_download_url")
                }
                json.getString("tag_name") to (apkUrl ?: error("no APK in the latest release"))
            }
            val current = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { null }
            if (current != null && "v$current" == tag) {
                status.value = "Up to date ($tag)"
                return
            }
            status.value = "Downloading $tag…"
            val file = java.io.File(ctx.cacheDir, "update.apk")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000; conn.readTimeout = 60000
                conn.inputStream.use { inp -> file.outputStream().use { inp.copyTo(it) } }
            }
            status.value = "Installing $tag…"
            Updater.install(ctx, android.net.Uri.fromFile(file))
        } catch (e: Exception) {
            status.value = "Update failed: ${e.message}"
        } finally {
            busy.value = false
        }
    }
}

/**
 * Bluetooth music control on this firmware. The unit's own Bluetooth app (btsuite) ignores Android
 * media keys; it listens for the MCU key broadcast, and also exposes a binder whose sendData() passes
 * raw module commands (MA play/pause, MD next, ME prev). Both are tried.
 */
object BtControl {
    /** Opens the unit's Bluetooth music page, which makes phone audio the active source. */
    fun openBtMusic(ctx: Context, thenPlay: Boolean = false) {
        try {
            ctx.startActivity(
                Intent().setClassName("com.szchoiceway.btsuite", "com.szchoiceway.btsuite.BTMainActivity")
                    .putExtra("GotoPageNum", "BTMusic")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            if (thenPlay) android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed({ send(ctx, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, 1500)
        } catch (_: Exception) {
            android.widget.Toast.makeText(ctx, "Couldn't open Bluetooth music", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** Answer (23) or end (22) a call through the unit's Bluetooth app. Effect not yet confirmed on this unit. */
    fun call(ctx: Context, answer: Boolean) {
        try {
            ctx.sendBroadcast(
                Intent("com.szchoiceway.eventcenter.EventUtils.MCU_KEY_INFOR")
                    .setPackage("com.szchoiceway.btsuite")
                    .putExtra("EventUtils.MCU_KEY_VALUE", if (answer) 23 else 22),
            )
            if (!answer) ctx.sendBroadcast(Intent("com.szchoiceway.btsuite.HBCP_HANGUP_EVENT").setPackage("com.szchoiceway.btsuite"))
        } catch (_: Exception) {}
        sendRaw(ctx, if (answer) "DE" else "DG")
    }

    fun send(ctx: Context, keyCode: Int) {
        val (mcuKey, raw) = when (keyCode) {
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> 2 to "MD"
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> 3 to "ME"
            else -> 6 to "MA"
        }
        try {
            ctx.sendBroadcast(
                Intent("com.szchoiceway.eventcenter.EventUtils.MCU_KEY_INFOR")
                    .setPackage("com.szchoiceway.btsuite")
                    .putExtra("EventUtils.MCU_KEY_VALUE", mcuKey),
            )
        } catch (_: Exception) {}
        sendRaw(ctx, raw)
    }

    private fun sendRaw(ctx: Context, cmd: String) {
        val conn = object : android.content.ServiceConnection {
            override fun onServiceConnected(n: android.content.ComponentName?, b: android.os.IBinder?) {
                try {
                    val data = android.os.Parcel.obtain(); val reply = android.os.Parcel.obtain()
                    data.writeInterfaceToken("com.szchoiceway.btsuite.IBTService")
                    data.writeString(cmd)
                    b?.transact(android.os.IBinder.FIRST_CALL_TRANSACTION + 2, data, reply, 0)
                    data.recycle(); reply.recycle()
                } catch (_: Exception) {}
                try { ctx.unbindService(this) } catch (_: Exception) {}
            }
            override fun onServiceDisconnected(n: android.content.ComponentName?) {}
        }
        try {
            ctx.bindService(
                Intent("com.szchoiceway.btsuite.BTService").setPackage("com.szchoiceway.btsuite"),
                conn, Context.BIND_AUTO_CREATE,
            )
        } catch (_: Exception) {}
    }
}
