package app.apkstore

import app.apkstore.data.ApkPure
import app.apkstore.data.ApkSource
import app.apkstore.data.Freshness
import app.apkstore.data.NoAbiException
import app.apkstore.data.NotOnApkPureException
import app.apkstore.data.RemoteZip
import app.apkstore.data.Versions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** APKPure: מה יש, מה אין, ואיך משווים לגרסה שבפליי */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApkPureTest {

    // ---- מול APKPure החי (דורש אינטרנט) ----

    @Test fun existingApp() {
        val s = runBlocking { ApkPure.resolve("com.waze") }
        println("waze: $s")
        assertTrue(s.url.startsWith("https://"))
        assertNotNull("version", s.versionName)
        assertTrue("version looks numeric", s.versionName!!.first().isDigit())
        assertNotNull("size", s.size)
        assertTrue(s.size!! > 1_000_000)
        assertNotNull("updatedAt", s.updatedAt)
        assertTrue("updated within ~2 years", s.updatedAt!! > System.currentTimeMillis() - 730L * 24 * 3600_000)
    }

    /** הקובץ שנבחר למעבד של המכשיר (או שאין בו קוד מקומי) */
    @Test fun existingAppMatchesDevice() {
        val s = runBlocking { ApkPure.resolve("com.waze") }
        val abis = RemoteZip.abis(s.url)
        println("waze abis: $abis")
        assertNotNull("abis readable", abis)
        assertTrue("device ABI or no native code: $abis", abis!!.isEmpty() || ApkPure.deviceAbis.any { it in abis })
    }

    /**
     * כמו ב-Win.apk (x86_64 ואחריו arm64-v8a). ל-Waze אין ב-APKPure גרסת x86_64, ובגרסה
     * העדכנית יש וריאנטים של armeabi-v7a ושל arm64-v8a: נבחר arm64-v8a, כמו בפליי.
     */
    @Test fun winApkGetsDeviceAbi() {
        val saved = android.os.Build.SUPPORTED_ABIS
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build::class.java, "SUPPORTED_ABIS", arrayOf("x86_64", "arm64-v8a"))
        try {
            val s = runBlocking { ApkPure.resolve("com.waze") }
            val abis = RemoteZip.abis(s.url)
            println("waze on Win.apk: $abis ${s.versionName}")
            assertNotNull("abis readable", abis)
            assertTrue("a Win.apk ABI or no native code: $abis", abis!!.isEmpty() || "x86_64" in abis || "arm64-v8a" in abis)
        } finally {
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build::class.java, "SUPPORTED_ABIS", saved)
        }
    }

    /** קיימת בפליי, לא קיימת ב-APKPure */
    @Test fun missingApp() {
        try {
            runBlocking { ApkPure.resolve("com.payboxapp") }
            fail("should throw NotOnApkPureException")
        } catch (e: NotOnApkPureException) {
            println("paybox: ${e.message}")
        }
    }

    @Test fun nonexistentPackage() {
        try {
            runBlocking { ApkPure.resolve("com.dxco.pandavszombiesasdadad") }
            fail("should throw")
        } catch (_: NotOnApkPureException) { }
    }

    // ---- בחירת וריאנט (בלי רשת) ----

    /** כמו התשובה של APKPure: בית אורך, שם, sha1, ואחר כך הקישור */
    private fun entry(version: String, url: String, xapk: Boolean = false): String {
        val type = if (xapk) "XAPKJ" else "APKJ"
        return "\u0012" + version.length.toChar() + version + ":(" + "a".repeat(40) + "\u0000" + type + "\u0001\u0002" + url + "\u0000"
    }

    @Test fun listsVersionsAndVariants() {
        val text = "xx" + entry("2.0", "https://d.pure.com/a") + entry("2.0", "https://d.pure.com/b") +
            entry("26.43.30 (040700-1006944126)", "https://d.pure.com/c", xapk = true) +
            entry("54.3.08-24 [0] [PR] 1022323148", "https://d.pure.com/d")
        val listed = ApkPure.list(text)
        assertEquals(listOf("2.0", "26.43.30 (040700-1006944126)", "54.3.08-24 [0] [PR] 1022323148"), listed.map { it.version })
        assertEquals(listOf("https://d.pure.com/a", "https://d.pure.com/b"), listed[0].variants.map { it.url })
        assertTrue(listed[1].variants[0].isXapk)
    }

    private fun listed() = ApkPure.list(
        entry("3.0", "https://d.pure.com/arm") + entry("3.0", "https://d.pure.com/x86") +
            entry("2.0", "https://d.pure.com/old-x64") + entry("2.0", "https://d.pure.com/old-arm")
    )

    /** Build.SUPPORTED_ABIS ב-Win.apk */
    private val win = listOf("x86_64", "arm64-v8a")

    @Test fun choosesX86_64Variant() {
        val abis = mapOf(
            "https://d.pure.com/arm" to setOf("arm64-v8a"), "https://d.pure.com/x86" to setOf("x86", "x86_64"),
        )
        assertEquals("https://d.pure.com/x86", runBlocking { ApkPure.choose("p", listed(), win) { abis[it] } }.url)
    }

    @Test fun noNativeCodeIsFine() {
        assertEquals("https://d.pure.com/arm", runBlocking { ApkPure.choose("p", listed(), win) { emptySet() } }.url)
    }

    /** בגרסה העדכנית אין שום ABI של המכשיר - הגרסה הקודמת שיש בה */
    @Test fun fallsBackToOlderVersion() {
        val abis = mapOf("https://d.pure.com/old-x64" to setOf("x86_64"))
        assertEquals("https://d.pure.com/old-x64",
            runBlocking { ApkPure.choose("p", listed(), win) { abis[it] ?: setOf("armeabi-v7a", "x86") } }.url)
    }

    /** בלי x86_64 בגרסה העדכנית, אבל עם arm64-v8a - כמו פליי, עדיין מה שהמכשיר מריץ */
    @Test fun takesSecondAbiOfLatestVersion() {
        val abis = mapOf("https://d.pure.com/arm" to setOf("arm64-v8a"), "https://d.pure.com/x86" to setOf("x86"))
        assertEquals("https://d.pure.com/arm", runBlocking { ApkPure.choose("p", listed(), win) { abis[it] } }.url)
    }

    @Test fun refusesWithoutDeviceAbi() {
        try {
            runBlocking { ApkPure.choose("p", listed(), win) { setOf("armeabi-v7a", "x86") } }
            fail("should throw NoAbiException")
        } catch (_: NoAbiException) { }
    }

    /** אי אפשר לדעת מה יש בקובץ (שרת בלי Range) - הקובץ הראשון, כמו apkeep */
    @Test fun unknownTakesFirst() {
        assertEquals("https://d.pure.com/arm", runBlocking { ApkPure.choose("p", listed(), win) { null } }.url)
    }

    // ---- השוואת גרסאות (בלי רשת) ----

    @Test fun compareVersions() {
        assertEquals(-1, Versions.compare("5.27.5.0", "5.28.0.0"))
        assertEquals(1, Versions.compare("5.28.0.0", "5.27.5.0"))
        assertEquals(0, Versions.compare("5.28", "5.28.0.0"))
        assertEquals(1, Versions.compare("10.0", "9.9.9"))
        assertEquals(-1, Versions.compare("1.26.66.1", "1.26.70"))
        assertNull("not numeric", Versions.compare("Varies with device", "5.0"))
        assertEquals(0, Versions.compare("14.33.1.0 beta", "14.33.1.0"))
    }

    private val day = 24 * 3600_000L
    private val now = System.currentTimeMillis()
    private fun src(v: String?, at: Long?) = ApkSource("https://x", false, versionName = v, updatedAt = at)

    @Test fun freshnessByVersion() {
        assertEquals(Freshness.Older, Versions.freshness(src("5.26.0", now), "5.27.0", now))
        assertEquals(Freshness.Same, Versions.freshness(src("5.27.0", now), "5.27.0", now))
        assertEquals(Freshness.Newer, Versions.freshness(src("5.28.0", now), "5.27.0", now))
    }

    /** פליי לא מציג גרסה ("משתנה בהתאם למכשיר") - משווים לפי תאריך */
    @Test fun freshnessByDate() {
        assertEquals(Freshness.Older, Versions.freshness(src("5.26.0", now - 10 * day), null, now))
        assertEquals(Freshness.Same, Versions.freshness(src("5.26.0", now - 1 * day), null, now))
        assertEquals(Freshness.Same, Versions.freshness(src("5.26.0", now), null, now - 5 * day))
        assertEquals(Freshness.Unknown, Versions.freshness(src("5.26.0", null), null, now))
    }
}
