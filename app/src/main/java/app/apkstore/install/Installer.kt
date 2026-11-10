package app.apkstore.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

object Installer {

    /** מתקין APK רגיל, או XAPK (ZIP עם base + split APKs) בסשן אחד */
    suspend fun install(context: Context, path: String, xapk: Boolean) {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "יש לאשר התקנת אפליקציות ואז ללחוץ שוב על התקנה", Toast.LENGTH_LONG).show()
            }
            return
        }

        withContext(Dispatchers.IO) {
            val installer = pm.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                if (xapk) {
                    ZipFile(path).use { zip ->
                        val apks = zip.entries().asSequence()
                            .filter { !it.isDirectory && it.name.endsWith(".apk", true) }
                            .toList()
                        require(apks.isNotEmpty()) { "לא נמצאו קבצי APK בתוך ה-XAPK" }
                        for (e in apks) {
                            zip.getInputStream(e).use { write(session, e.name.substringAfterLast('/'), e.size, it) }
                        }
                    }
                } else {
                    val f = File(path)
                    f.inputStream().use { write(session, "base.apk", f.length(), it) }
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(
                    context, id, Intent(context, InstallReceiver::class.java), flags
                )
                session.commit(pi.intentSender)
            }
        }
    }

    private fun write(session: PackageInstaller.Session, name: String, size: Long, input: InputStream) {
        session.openWrite(name, 0, if (size > 0) size else -1).use { out ->
            input.copyTo(out, 64 * 1024)
            session.fsync(out)
        }
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(context, "האפליקציה הותקנה", Toast.LENGTH_SHORT).show()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "ההתקנה נכשלה"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}
