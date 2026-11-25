package app.apkstore

import app.apkstore.data.AppDetails
import app.apkstore.data.PlayApi
import app.apkstore.data.ReviewSort
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * בדיקות מול פליי החי (דורש אינטרנט), על בסיס המקרים שבבדיקות של google-play-scraper:
 * משחק, אפליקציה בתשלום, "משתנה בהתאם למכשיר", פרטי מפתח, אפליקציה שלא קיימת,
 * חיפוש בעברית, חיפוש בלי תוצאות, עמודים נוספים וביקורות בכל סוגי המיון.
 * דוח מלא נכתב ל-app/build/outputs/play-report.txt
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayApiTest {

    companion object {
        private val report = StringBuilder()
        private fun log(s: String) { synchronized(report) { report.appendLine(s) } }

        @JvmStatic @AfterClass fun writeReport() {
            File("build/outputs").mkdirs()
            File("build/outputs/play-report.txt").writeText(report.toString())
        }
    }

    private fun details(pkg: String): AppDetails = runBlocking { PlayApi.details(pkg) }.also { a ->
        log("""
            |== $pkg
            |  title=${a.title} | dev=${a.developer} | score=${a.score} | installs=${a.installs} (${a.installsExact})
            |  price=${a.price ?: "free"} | iap=${a.iapRange} | ads=${a.containsAds} | preregister=${a.preregister}
            |  version=${a.version} | minAndroid=${a.minAndroid} | updated=${a.updated} | released=${a.released}
            |  rating=${a.contentRating} / ${a.contentRatingNote} | categories=${a.categories}
            |  histogram=${a.histogram} | ratings=${a.ratingsCount} | reviewsCount=${a.reviewsCount}
            |  reviews=${a.reviews.size} (replies=${a.reviews.count { it.reply != null }}) | similar=${a.similar.size}
            |  dataSafety=${a.dataSafety.size} | permissions=${a.permissions.sumOf { it.items.size }} | screenshots=${a.screenshots.size} | video=${a.video != null}
            |  web=${a.developerWebsite} | email=${a.developerEmail} | legal=${a.developerLegalName} | privacy=${a.privacyPolicy}
            |  whatsNew=${a.whatsNew?.take(60)?.replace('\n', ' ')}
            |  description=${a.description?.take(60)?.replace('\n', ' ')}
            """.trimMargin())
    }

    /** שדות שחייבים להיות בכל אפליקציה רגילה */
    private fun core(a: AppDetails) {
        assertTrue(a.title.isNotBlank() && a.title != a.pkg)
        assertNotNull("icon", a.icon)
        assertNotNull("developer", a.developer)
        assertNotNull("description", a.description)
        assertTrue("screenshots", a.screenshots.isNotEmpty())
        assertNotNull("installs", a.installs)
        assertNotNull("updated", a.updated)
    }

    @Test fun regularApp() {
        val a = details("com.waze")
        core(a)
        assertTrue(a.free)
        assertEquals(5, a.histogram.size)
        assertTrue("reviews", a.reviews.isNotEmpty())
        assertTrue("similar", a.similar.isNotEmpty())
        assertTrue("permissions", a.permissions.isNotEmpty())
        assertTrue("dataSafety", a.dataSafety.isNotEmpty())
        assertNotNull(a.minAndroid)
    }

    @Test fun game() {
        val a = details("com.supercell.clashofclans")
        core(a)
        assertTrue(a.inAppPurchases)
        assertNotNull("iap range", a.iapRange)
    }

    /** כמו בספרייה: "Varies with device" */
    @Test fun variesWithDevice() {
        val a = details("com.facebook.katana")
        core(a)
    }

    @Test fun developerLegalInfo() {
        val a = details("com.soundcloud.android")
        core(a)
        assertNotNull("legal name", a.developerLegalName)
    }

    @Test fun privacyPolicy() {
        val a = details("com.snapchat.android")
        core(a)
        assertNotNull(a.privacyPolicy)
    }

    /** אפליקציה בתשלום - חייבים לזהות כדי לא להציע הורדה */
    @Test fun paidApp() {
        val a = details("com.mojang.minecraftpe")
        assertFalse("should be paid", a.free)
        log("  -> paid detected: ${a.price}")
    }

    @Test fun paidApp2() {
        val a = details("com.teslacoilsw.launcher.prime")
        assertFalse("should be paid", a.free)
    }

    @Test fun notFound() {
        try {
            runBlocking { PlayApi.details("com.dxco.pandavszombiesasdadad") }
            fail("should throw")
        } catch (e: java.io.IOException) {
            log("== notFound -> ${e.message}")
        }
    }

    @Test fun searchExactMatchFirst() {
        val p = runBlocking { PlayApi.search("waze") }
        log("== search waze: ${p.items.size} results, first=${p.items.firstOrNull()?.pkg}, next=${p.next != null}")
        assertEquals("com.waze", p.items.first().pkg)
        assertTrue(p.items.size > 5)
        assertEquals(p.items.size, p.items.distinctBy { it.pkg }.size)
    }

    @Test fun searchHebrew() {
        val p = runBlocking { PlayApi.search("מחשבון") }
        log("== search מחשבון: ${p.items.size} results: ${p.items.take(3).map { it.title }}")
        assertTrue(p.items.size > 5)
        assertTrue(p.items.all { it.icon != null })
    }

    @Test fun searchPaidMarked() {
        val p = runBlocking { PlayApi.search("minecraft") }
        val paid = p.items.filter { it.price != null }
        log("== search minecraft: ${p.items.size} results, paid=${paid.map { it.pkg + " " + it.price }}")
        assertTrue("minecraft should be marked paid", paid.any { it.pkg == "com.mojang.minecraftpe" })
    }

    @Test fun searchEmpty() {
        val p = runBlocking { PlayApi.search("zzqqxxvvkkjj123987") }
        log("== search nonsense: ${p.items.size} results")
    }

    @Test fun searchMorePages() {
        // לא לכל חיפוש יש עמוד נוסף (לפעמים פליי מחזיר 30 תוצאות וזהו) - בודקים על כמה
        val first = listOf("waze", "navigation", "calculator").map { q ->
            runBlocking { PlayApi.search(q) }.also { log("   '$q': ${it.items.size} results, next=${it.next != null}") }
        }.firstOrNull { it.next != null }
        assertNotNull("at least one search should have more pages", first)
        first!!
        val second = runBlocking { PlayApi.searchMore(first.next!!) }
        log("== search more: first=${first.items.size}, second=${second.items.size}, next=${second.next != null}")
        assertTrue(second.items.size > 10)
        assertTrue(second.items.all { it.title.isNotBlank() && it.icon != null })
        val overlap = second.items.count { s -> first.items.any { it.pkg == s.pkg } }
        log("   overlap with first page: $overlap")
    }

    @Test fun reviewsAllSorts() {
        for (sort in ReviewSort.entries) {
            val p = runBlocking { PlayApi.reviews("com.waze", sort) }
            log("== reviews $sort: ${p.items.size}, next=${p.next != null}, first=${p.items.firstOrNull()?.let { "${it.score}★ ${it.time}" }}")
            assertTrue(p.items.isNotEmpty())
            assertNotNull(p.next)
        }
        val newest = runBlocking { PlayApi.reviews("com.waze", ReviewSort.Newest) }
        val times = newest.items.mapNotNull { it.time }
        assertEquals("newest should be sorted", times.sortedDescending(), times)
        val page2 = runBlocking { PlayApi.reviews("com.waze", ReviewSort.Newest, newest.next) }
        log("   page2: ${page2.items.size}, overlap=${page2.items.count { r -> newest.items.any { it.id == r.id } }}")
        assertTrue(page2.items.isNotEmpty())
        assertTrue(page2.items.none { r -> newest.items.any { it.id == r.id } })
    }

    @Test fun reviewsWithReplies() {
        // אפליקציות שבדרך כלל המפתח עונה בהן
        val p = runBlocking { PlayApi.reviews("com.supercell.clashofclans", ReviewSort.Newest, count = 100) }
        val replies = p.items.filter { it.reply != null }
        log("== replies: ${replies.size}/${p.items.size}, sample=${replies.firstOrNull()?.reply?.take(50)}")
        replies.forEach { assertNull("reply time sanity", it.replyTime?.takeIf { t -> t < 1_000_000_000_000L }) }
    }
}
