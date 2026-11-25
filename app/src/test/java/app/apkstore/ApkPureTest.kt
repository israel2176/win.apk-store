package app.apkstore

import app.apkstore.data.ApkPure
import app.apkstore.data.ApkSource
import app.apkstore.data.Freshness
import app.apkstore.data.NotOnApkPureException
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
