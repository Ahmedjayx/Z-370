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
                val intent = Intent(ACTION_STATUS).setPackage(ctx.packageName)
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
