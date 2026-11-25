package app.apkstore.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
    const val EXTRA_TITLE = "app.apkstore.TITLE"

    /**
     * מצב מתקין חבילות (מאחורי הקלעים, בלי שום שינוי בממשק):
     * כשהאפליקציה קיבלה את ההרשאה INSTALL_PACKAGES - בסביבה שמאפשרת לתת אותה - ההתקנה שקטה
     * (בלי חלון אישור ובלי "מקורות לא ידועים"), והמתקין הרשום של האפליקציה הוא Google Play.
     * בטלפון רגיל ההרשאה לא ניתנת, וההתקנה רגילה.
     */
    private const val INSTALLER_OF_RECORD = "com.android.vending"

    fun isPackageInstaller(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.INSTALL_PACKAGES) == PackageManager.PERMISSION_GRANTED

    /**
     * מתקין APK רגיל, או XAPK (ZIP עם base + split APKs) בסשן אחד.
     * השם והאייקון נמסרים למערכת, כדי שבמסך ההתקנה ובהתראות יופיע שם האפליקציה ולא רק שם החבילה.
     */
    suspend fun install(context: Context, path: String, xapk: Boolean, pkg: String, title: String, icon: Bitmap?) {
        val pm = context.packageManager
        val privileged = isPackageInstaller(context)
        if (!privileged && !pm.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(app.apkstore.R.string.install_allow), Toast.LENGTH_LONG).show()
            }
            return
        }

        withContext(Dispatchers.IO) {
            val installer = pm.packageInstaller
            fun params(asPlay: Boolean) = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(pkg)
                setAppLabel(title)
                icon?.let(::setAppIcon)
                setSize(File(path).length())
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                if (asPlay) {
                    if (Build.VERSION.SDK_INT >= 30) setInstallerPackageName(INSTALLER_OF_RECORD)
                    if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
                }
            }
            // אם המערכת לא מקבלת את Google Play כמתקין - מתקינים בכל זאת, רגיל
            val id = if (privileged) {
                try { installer.createSession(params(asPlay = true)) }
                catch (_: SecurityException) { installer.createSession(params(asPlay = false)) }
            } else installer.createSession(params(asPlay = false))
            installer.openSession(id).use { session ->
                if (xapk) {
                    ZipFile(path).use { zip ->
                        val apks = zip.entries().asSequence()
                            .filter { !it.isDirectory && it.name.endsWith(".apk", true) }
                            .toList()
                        if (apks.isEmpty()) throw app.apkstore.data.AppException(app.apkstore.R.string.err_xapk_empty)
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
                    context, id, Intent(context, InstallReceiver::class.java).putExtra(EXTRA_TITLE, title), flags
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
            PackageInstaller.STATUS_SUCCESS -> {
                val title = intent.getStringExtra(Installer.EXTRA_TITLE)
                Toast.makeText(context, if (title != null) context.getString(app.apkstore.R.string.installed_named, title)
                    else context.getString(app.apkstore.R.string.installed), Toast.LENGTH_SHORT).show()
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: context.getString(app.apkstore.R.string.err_install)
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}
