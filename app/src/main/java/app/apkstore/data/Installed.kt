package app.apkstore.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** גרסה מותקנת של אפליקציה */
data class InstalledApp(val versionName: String?, val versionCode: Long)

/**
 * אילו אפליקציות מותקנות בטלפון. נבדק לפי דרישה (רק אפליקציות שמוצגות),
 * ומתעדכן לבד כשאפליקציה מותקנת, מתעדכנת או נמחקת.
 * דורש QUERY_ALL_PACKAGES, כדי לראות אפליקציות אחרות באנדרואיד 11 ומעלה.
 */
class InstalledApps(context: Context) {
    private val app = context.applicationContext
    private val pm = app.packageManager

    /** מה שנבדק עד עכשיו: null = לא מותקנת */
    private val _state = MutableStateFlow<Map<String, InstalledApp?>>(emptyMap())
    val state: StateFlow<Map<String, InstalledApp?>> = _state

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                i.data?.schemeSpecificPart?.let(::refresh)
            }
        }, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    /** בודק אפליקציה אם עוד לא נבדקה */
    fun track(pkg: String) {
        if (!_state.value.containsKey(pkg)) refresh(pkg)
    }

    fun refresh(pkg: String) {
        val info = query(pkg)
        _state.update { it + (pkg to info) }
    }

    /** כשחוזרים לאפליקציה - בודקים שוב את כל מה שמוצג (אולי הותקן או נמחק בינתיים) */
    fun refreshAll() {
        _state.update { cur -> cur.keys.associateWith(::query) }
    }

    private fun query(pkg: String): InstalledApp? = try {
        val p = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getPackageInfo(pkg, 0)
        InstalledApp(p.versionName, if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong())
    } catch (_: PackageManager.NameNotFoundException) { null }

    /** פתיחת אפליקציה מותקנת; false אם אין לה מסך פתיחה */
    fun open(pkg: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
