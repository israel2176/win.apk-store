package app.apkstore

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.apkstore.data.AppSummary
import app.apkstore.data.PlayApi
import app.apkstore.ui.Load
import app.apkstore.ui.StoreViewModel
import app.apkstore.ui.Tab
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * צילומי מסך של כל המסכים בכמה גדלי חלון, עם נתונים אמיתיים מפליי (דורש אינטרנט).
 * הרצה: ./gradlew recordRoborazziDebug  -> app/build/outputs/roborazzi/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensTest {
    @get:Rule val rule = createComposeRule()

    private fun waitFor(what: String, vm: StoreViewModel, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 60_000
        while (!cond()) {
            if (System.currentTimeMillis() > end) error("timeout: $what home=${vm.home::class.simpleName} results=${vm.results}")
            shadowOf(Looper.getMainLooper()).idle()
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(100)
        }
    }

    private fun tour(prefix: String, query: String = "waze") {
        val vm = StoreViewModel(ApplicationProvider.getApplicationContext<Application>())
        rule.setContent { StoreRoot(vm) }

        waitFor("home", vm) { vm.home !is Load.Loading }
        settle(); shot("$prefix-1-home")

        rule.runOnIdle { vm.search(query) }
        waitFor("search", vm) { vm.results !is Load.Loading }
        settle(); shot("$prefix-2-search")

        val first = (vm.results as? Load.Done)?.value?.firstOrNull()?.pkg ?: "com.waze"
        rule.runOnIdle { vm.open(first) }
        waitFor("app", vm) { vm.detailsCache[first].let { it != null && it !is Load.Loading } }
        settle(); shot("$prefix-3-app")

        rule.runOnIdle { vm.selectTab(Tab.Downloads) }
        settle(); shot("$prefix-4-downloads")
    }

    /** זמן לטעינת התמונות */
    private fun settle() {
        repeat(15) { shadowOf(Looper.getMainLooper()).idle(); rule.mainClock.advanceTimeBy(200); Thread.sleep(200) }
    }

    private fun shot(name: String) = rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Test @Config(sdk = [34], qualifiers = "w400dp-h860dp-port-xxhdpi")
    fun phone() = tour("phone")

    @Test @Config(sdk = [34], qualifiers = "w860dp-h400dp-land-xxhdpi")
    fun phoneLandscape() = tour("phone-land")

    @Test @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tablet() = tour("tablet")

    @Test @Config(sdk = [34], qualifiers = "w400dp-h860dp-port-night-xxhdpi")
    fun phoneDark() = tour("phone-dark")

    /** מסך ההורדות עם כל המצבים: ממתין לבדיקת נטפרי, חסום, נכשל, מוכן להתקנה */
    @Test @Config(sdk = [34], qualifiers = "w400dp-h860dp-port-xxhdpi")
    fun downloadStates() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val apps = runBlocking { PlayApi.search("waze") }.items.take(4)
        val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "apks").apply { mkdirs() }
        val apk = File(dir, "${apps[0].pkg}.apk").apply { writeText("x") }
        fun item(a: AppSummary) = JSONObject().put("pkg", a.pkg).put("title", a.title).put("icon", a.icon)
        val list = JSONArray()
            .put(item(apps[1]).put("t", "notchecked").put("page", "https://netfree.link/block/#{}"))
            .put(item(apps[2]).put("t", "blocked").put("page", "https://netfree.link/block/#{}"))
            .put(item(apps[3]).put("t", "failed").put("msg", "לא נמצא קובץ להורדה"))
            .put(item(apps[0]).put("t", "ready").put("path", apk.absolutePath).put("xapk", false))
        app.getSharedPreferences("downloads", Context.MODE_PRIVATE).edit().putString("items", list.toString()).commit()

        val vm = StoreViewModel(app)
        rule.setContent { StoreRoot(vm) }
        rule.runOnIdle { vm.selectTab(Tab.Downloads) }
        settle(); shot("flow-downloads")

        rule.runOnIdle { vm.search("waze") }
        waitFor("search", vm) { vm.results !is Load.Loading }
        settle(); shot("flow-search")

        rule.runOnIdle { vm.open(apps[0].pkg) }
        waitFor("app", vm) { vm.detailsCache[apps[0].pkg].let { it != null && it !is Load.Loading } }
        settle(); shot("flow-app-ready")
    }

    /** דף אפליקציה מלא, על מסך גבוה במיוחד כדי שכל האזורים ייכנסו לצילום אחד */
    @Test @Config(sdk = [34], qualifiers = "w400dp-h3400dp-port-xxhdpi")
    fun appPageFull() = appPage("full-app")

    @Test @Config(sdk = [34], qualifiers = "w1280dp-h1900dp-land-xhdpi")
    fun appPageTablet() = appPage("full-app-tablet")

    /** אפליקציה בתשלום: במקום "הורדה" מוצג המחיר, והכפתור לא פעיל */
    @Test @Config(sdk = [34], qualifiers = "w400dp-h1400dp-port-xxhdpi")
    fun appPagePaid() = appPage("full-app-paid", "com.mojang.minecraftpe")

    /** אפליקציה שקיימת בפליי ולא ב-APKPure */
    @Test @Config(sdk = [34], qualifiers = "w400dp-h1100dp-port-xxhdpi")
    fun appNotOnApkPure() = appPage("apk-missing", "com.payboxapp")

    /** APKPure מעודכן: שורה שקטה עם גרסה, גודל ותאריך */
    @Test @Config(sdk = [34], qualifiers = "w400dp-h1100dp-port-xxhdpi")
    fun appOnApkPure() = appPage("apk-ok", "com.waze")

    /** גרסה ישנה ב-APKPure (מדומה: מציבים גרסה ישנה במטמון לפני שהדף בודק) */
    @Test @Config(sdk = [34], qualifiers = "w400dp-h1100dp-port-xxhdpi")
    fun appOlderOnApkPure() = appPage("apk-older", "com.snapchat.android") { vm ->
        vm.apkCache["com.snapchat.android"] = Load.Done(
            app.apkstore.data.ApkSource(
                "https://x", true, versionName = "13.90.0.0", size = 98_765_432,
                updatedAt = System.currentTimeMillis() - 200L * 24 * 3600_000,
            )
        )
    }

    private fun appPage(name: String, pkg: String = "com.waze", prepare: (StoreViewModel) -> Unit = {}) {
        val vm = StoreViewModel(ApplicationProvider.getApplicationContext<Application>())
        prepare(vm)
        rule.setContent { StoreRoot(vm) }
        rule.runOnIdle { vm.open(pkg) }
        waitFor("app", vm) { vm.detailsCache[pkg].let { it != null && it !is Load.Loading } }
        waitFor("apk", vm) { vm.apkCache[pkg].let { it != null && it !is Load.Loading } || (vm.detailsCache[pkg] as? Load.Done)?.value?.free == false }
        settle(); shot(name)
    }
}
