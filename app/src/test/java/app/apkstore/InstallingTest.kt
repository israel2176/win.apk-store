package app.apkstore

import android.app.Application
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Looper
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import app.apkstore.data.DlState
import app.apkstore.ui.DownloadsScreen
import app.apkstore.ui.StoreViewModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowPackageInstaller
import java.io.File

/**
 * כפתור ההתקנה ננעל מהלחיצה ועד סוף ההתקנה:
 * שתי לחיצות מהירות פותחות סשן אחד בלבד, והכפתור חוזר לפעול כשהמערכת מודיעה שהסשן נגמר.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallingTest {
    @get:Rule val rule = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val pkg = "com.example.t"
    private val installer get() = app.packageManager.packageInstaller

    /** פריט הורדה מוכן להתקנה (כמו במסך ההורדות) */
    private fun readyItem(exists: Boolean = true): StoreViewModel {
        val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "apks").apply { mkdirs() }
        val apk = File(dir, "$pkg.apk").apply { writeBytes(ByteArray(64 * 1024)) }
        val list = JSONArray().put(
            JSONObject().put("pkg", pkg).put("title", "Example").put("t", "ready").put("path", apk.absolutePath).put("xapk", false)
        )
        app.getSharedPreferences("downloads", Context.MODE_PRIVATE).edit().putString("items", list.toString()).commit()
        val vm = StoreViewModel(app)
        if (!exists) apk.delete()
        return vm
    }

    private fun ready(vm: StoreViewModel) = vm.downloads.items.value.first { it.pkg == pkg }.state as DlState.Ready

    /** מריץ את ה-Main looper (ואת ה-IO שמחזיר אליו) עד שהתנאי מתקיים */
    private fun waitUntil(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!cond()) {
            if (System.currentTimeMillis() > end) error("timeout: $what")
            shadowOf(Looper.getMainLooper()).idle()
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(20)
        }
    }

    /** עוד כמה סבבים, כדי שעבודה שעוד רצה (אם בטעות נפתח סשן שני) תספיק להיראות */
    private fun settle() = repeat(25) { shadowOf(Looper.getMainLooper()).idle(); rule.mainClock.advanceTimeByFrame(); Thread.sleep(20) }

    @Test @Config(shadows = [WaitingSession::class])
    fun doubleClickOneSessionAndLockedUntilFinished() {
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        val vm = readyItem()
        val installing = app.getString(R.string.installing)
        val install = app.getString(R.string.install)
        rule.setContent { DownloadsScreen(vm) }
        rule.onNodeWithContentDescription(install).assertIsEnabled()

        // שתי לחיצות מהירות ברצף - השנייה לפני שהראשונה בכלל התחילה לעבוד
        rule.runOnIdle {
            vm.install(ready(vm))
            vm.install(ready(vm))
            assertTrue("locked right away", pkg in vm.installing)
        }
        waitUntil("session") { installer.allSessions.isNotEmpty() }
        settle()
        assertEquals("one session only", 1, installer.allSessions.size)
        val id = installer.allSessions.single().sessionId
        assertEquals(pkg, installer.allSessions.single().appPackageName)

        // בזמן שהסשן פתוח: "מתקין…" ולא לחיץ; לחיצה נוספת לא פותחת סשן
        rule.onNodeWithContentDescription(installing).assertIsNotEnabled()
        rule.runOnIdle { vm.install(ready(vm)) }
        settle()
        assertEquals(1, installer.allSessions.size)

        // התקדמות מהמערכת מגיעה לכפתור
        shadowOf(installer).setSessionProgress(id, 0.5f)
        waitUntil("progress") { vm.installing[pkg] == 0.5f }

        // המערכת מודיעה שההתקנה נכשלה (או בוטלה) - הכפתור חוזר לפעול
        shadowOf(installer).setSessionFails(id)
        waitUntil("released") { pkg !in vm.installing }
        rule.onNodeWithContentDescription(install).assertIsEnabled()
    }

    /** "מקורות לא ידועים" לא אושר: לא נפתח סשן, והכפתור לא נשאר נעול */
    @Test fun noSessionReleases() {
        shadowOf(app.packageManager).setCanRequestPackageInstalls(false)
        val vm = readyItem()
        rule.setContent { DownloadsScreen(vm) }
        rule.runOnIdle { vm.install(ready(vm)) }
        waitUntil("released") { pkg !in vm.installing }
        assertTrue(installer.allSessions.isEmpty())
    }

    /** הקובץ נמחק בינתיים: ההתקנה נכשלת, הסשן מבוטל והכפתור משתחרר */
    @Test fun failureReleases() {
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        val vm = readyItem(exists = false)
        rule.setContent { DownloadsScreen(vm) }
        rule.runOnIdle { vm.install(ready(vm)) }
        waitUntil("released") { pkg !in vm.installing }
        assertFalse("session abandoned", installer.allSessions.any { it.appPackageName == pkg })
    }
}

/**
 * ב-Robolectric ה-commit מצליח מיד. כאן הוא מחכה, כמו טלפון שמציג למשתמש את חלון האישור,
 * עד שהבדיקה מודיעה על הסוף (setSessionFails / setSessionSucceeds).
 */
@Implements(PackageInstaller.Session::class)
class WaitingSession : ShadowPackageInstaller.ShadowSession() {
    @Implementation override fun commit(statusReceiver: IntentSender) {}
}
