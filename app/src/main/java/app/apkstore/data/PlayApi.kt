package app.apkstore.data

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** לקוח HTTP אחד לכל האפליקציה */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

data class AppSummary(
    val pkg: String,
    val title: String,
    val icon: String?,
    val developer: String?,
    val score: Double?,
    val genre: String?,
    val installs: String?,
    val screenshots: List<String> = emptyList(),
)

data class AppDetails(
    val pkg: String,
    val title: String,
    val icon: String?,
    val developer: String?,
    val score: Double?,
    val installs: String?,
    val genre: String?,
    val summary: String?,
    val description: String?,
    val version: String?,
    val containsAds: Boolean,
    val screenshots: List<String>,
    // ---- מידע נוסף מדף האפליקציה ----
    val ratingsCount: Long? = null,
    val reviewsCount: Long? = null,
    /** כמות דירוגים לכל מספר כוכבים: אינדקס 0 = כוכב אחד ... 4 = חמישה כוכבים */
    val histogram: List<Long> = emptyList(),
    val updated: String? = null,
    val whatsNew: String? = null,
    val minAndroid: String? = null,
    val contentRating: String? = null,
    val contentRatingNote: String? = null,
    val installsExact: Long? = null,
    val inAppPurchases: Boolean = false,
    val developerEmail: String? = null,
    val developerWebsite: String? = null,
    val developerAddress: String? = null,
    val privacyPolicy: String? = null,
    val dataSafety: List<String> = emptyList(),
    val permissions: List<PermissionGroup> = emptyList(),
    val reviews: List<Review> = emptyList(),
    val similar: List<AppSummary> = emptyList(),
)

data class PermissionGroup(val name: String, val items: List<String>)

data class Review(
    val author: String,
    val avatar: String?,
    val score: Int,
    val text: String,
    val time: Long?,
    val likes: Int,
    val appVersion: String?,
    val reply: String?,
)

/**
 * חיפוש ודף אפליקציה ישירות מ-play.google.com.
 * הנתונים יושבים בבלוקים AF_initDataCallback בתוך ה-HTML (אותה שיטה כמו google-play-scraper).
 * כשגוגל משנה את המבנה - כאן מעדכנים את האינדקסים.
 */
object PlayApi {
    private const val HL = "he"
    private const val GL = "il"

    private val SCRIPT = Regex("""AF_initDataCallback[\s\S]*?</script""")
    private val KEY = Regex("""(ds:.*?)'""")
    private val VALUE = Regex("""data:([\s\S]*?), sideChannel: \{\}\}\);</""")

    suspend fun search(query: String): List<AppSummary> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val ds = datasets(get("https://play.google.com/store/search?q=$q&c=apps&hl=$HL&gl=$GL"))
        val root = ds["ds:4"] ?: return@withContext emptyList()
        val out = mutableListOf<AppSummary>()

        (root.at(0, 1, 0, 23, 16) as? JSONArray)?.let { top -> parseTop(top)?.let(out::add) }

        val sections = root.at(0, 1) as? JSONArray ?: return@withContext out
        for (i in 0 until sections.length()) {
            val list = sections.at(i, 22, 0) as? JSONArray ?: continue
            for (j in 0 until list.length()) {
                val item = parseItem(list.opt(j)) ?: continue
                if (out.none { it.pkg == item.pkg }) out += item
            }
            break
        }
        out
    }

    suspend fun details(pkg: String): AppDetails = withContext(Dispatchers.IO) {
        val ds = datasets(get("https://play.google.com/store/apps/details?id=$pkg&hl=$HL&gl=$GL"))
        val a = ds["ds:5"]?.at(1, 2) as? JSONArray ?: throw IOException("האפליקציה לא נמצאה בפליי")
        val descHtml = a.str(72, 0, 1) ?: a.str(12, 0, 0, 1)
        AppDetails(
            pkg = pkg,
            title = a.str(0, 0) ?: pkg,
            icon = a.str(95, 0, 3, 2),
            developer = a.str(68, 0),
            score = a.num(51, 0, 1),
            installs = a.str(13, 0),
            genre = a.str(79, 0, 0, 0),
            summary = a.str(73, 0, 1)?.let(::html),
            description = descHtml?.let(::html),
            version = a.str(140, 0, 0, 0),
            containsAds = a.at(48) != null,
            screenshots = shots(a.at(78, 0)),
            ratingsCount = a.num(51, 2, 1)?.toLong(),
            reviewsCount = a.num(51, 3, 1)?.toLong(),
            histogram = (1..5).map { a.num(51, 1, it, 1)?.toLong() ?: 0L }.takeIf { h -> h.any { it > 0 } } ?: emptyList(),
            updated = a.str(145, 0, 0),
            whatsNew = a.str(144, 1, 1)?.let(::html),
            minAndroid = a.str(140, 1, 1, 0, 0, 1),
            contentRating = a.str(9, 0),
            contentRatingNote = a.str(9, 3, 1),
            installsExact = a.num(13, 2)?.toLong(),
            inAppPurchases = a.at(19) != null,
            developerEmail = a.str(69, 1, 0),
            developerWebsite = a.str(69, 0, 5, 2),
            developerAddress = a.str(69, 2, 0),
            privacyPolicy = a.str(99, 0, 5, 2),
            dataSafety = list(a.at(136, 1)) { it.str(1) },
            permissions = permissions(a.at(74, 2)),
            reviews = ds.values.firstNotNullOfOrNull(::reviews).orEmpty(),
            similar = ds.values.firstNotNullOfOrNull(::similar).orEmpty().filter { it.pkg != pkg },
        )
    }

    private fun <T> list(raw: Any?, f: (Any?) -> T?): List<T> {
        val arr = raw as? JSONArray ?: return emptyList()
        return (0 until arr.length()).mapNotNull { f(arr.opt(it)) }
    }

    /** הרשאות: קבוצות עם שם (מיקום, אנשי קשר...) ובסוף "אחר" */
    private fun permissions(raw: Any?): List<PermissionGroup> {
        val groups = mutableListOf<PermissionGroup>()
        for (i in 0..1) {
            groups += list(raw.at(i)) { g ->
                val name = g.str(0) ?: return@list null
                PermissionGroup(name, list(g.at(2)) { it.str(1) }).takeIf { it.items.isNotEmpty() }
            }
        }
        val other = list(raw.at(2)) { it.str(1) }
        if (other.isNotEmpty()) {
            val i = groups.indexOfFirst { it.name == "אחר" || it.name == "Other" }
            if (i >= 0) groups[i] = groups[i].copy(items = groups[i].items + other)
            else groups += PermissionGroup("אחר", other)
        }
        return groups
    }

    /** הביקורות נמצאות בבלוק שבו כל פריט מתחיל במזהה, שם כותב, ציון וטקסט */
    private fun reviews(d: JSONArray): List<Review>? {
        val arr = d.at(0) as? JSONArray ?: return null
        val first = arr.at(0) as? JSONArray ?: return null
        if (first.str(0) == null || first.str(1, 0) == null || first.num(2) == null) return null
        return list(arr) { r ->
            Review(
                author = r.str(1, 0) ?: return@list null,
                avatar = r.str(1, 1, 3, 2),
                score = r.num(2)?.toInt() ?: return@list null,
                text = r.str(4) ?: return@list null,
                time = r.num(5, 0)?.toLong()?.times(1000),
                likes = r.num(6)?.toInt() ?: 0,
                appVersion = r.str(10),
                reply = r.str(7, 1),
            )
        }.takeIf { it.isNotEmpty() }
    }

    /** "אפליקציות דומות" - הבלוק שמכיל similar_apps */
    private fun similar(d: JSONArray): List<AppSummary>? {
        if (!d.toString().contains("similar_apps")) return null
        val items = d.at(1, 1, 0, 21, 0) as? JSONArray ?: return null
        return list(items) { parseItem(JSONArray().put(it)) }.takeIf { it.isNotEmpty() }
    }

    // ---- פענוח ----

    private fun parseItem(raw: Any?): AppSummary? {
        val it = raw as? JSONArray ?: return null
        val pkg = it.str(0, 0, 0) ?: return null
        return AppSummary(
            pkg = pkg,
            title = it.str(0, 3) ?: pkg,
            icon = it.str(0, 1, 3, 2),
            developer = it.str(0, 14),
            score = it.num(0, 4, 1),
            genre = it.str(0, 5),
            installs = it.str(0, 15),
        )
    }

    private fun parseTop(t: JSONArray): AppSummary? {
        // המיקום השתנה בעבר: פעם במערך 11, היום באובייקט שבאינדקס 3 תחת המפתח "12"
        val pkg = t.str(11, 0, 0)
            ?: ((t.at(3) as? JSONObject)?.optJSONArray("12")).str(0, 0)
            ?: return null
        return AppSummary(
            pkg = pkg,
            title = t.str(2, 0, 0) ?: pkg,
            icon = t.str(2, 95, 0, 3, 2),
            developer = t.str(2, 68, 0),
            score = t.num(2, 51, 0, 1),
            genre = t.str(2, 79, 0, 0, 0),
            installs = t.str(2, 13, 0),
            screenshots = shots(t.at(2, 78, 0)),
        )
    }

    private fun shots(raw: Any?): List<String> {
        val arr = raw as? JSONArray ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.str(it, 3, 2) }
    }

    private fun html(s: String) =
        HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()

    private fun datasets(dom: String): Map<String, JSONArray> {
        val map = HashMap<String, JSONArray>()
        for (m in SCRIPT.findAll(dom)) {
            val block = m.value
            val key = KEY.find(block)?.groupValues?.get(1) ?: continue
            val value = VALUE.find(block)?.groupValues?.get(1) ?: continue
            (runCatching { JSONTokener(value).nextValue() }.getOrNull() as? JSONArray)?.let { map[key] = it }
        }
        return map
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("Accept-Language", "he-IL,he;q=0.9").build()
        Http.client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            NetFree.detect(resp.header("Content-Type"), body)?.let { throw NetFreeBlockException(it) }
            if (resp.code == 404) throw IOException("לא נמצא")
            if (!resp.isSuccessful) throw IOException("שגיאה בגישה לפליי (${resp.code})")
            return body
        }
    }
}

/** כתובת תמונה מ-play-lh בגודל מבוקש (חוסך הורדת תמונות ענק) */
fun sizedImage(url: String?, px: Int): String? = url?.substringBefore('=')?.plus("=s$px")
fun screenshotImage(url: String?, h: Int): String? = url?.substringBefore('=')?.plus("=h$h")

// ---- עזרים לניווט במערכים המקוננים ----

fun Any?.at(vararg path: Int): Any? {
    var cur: Any? = this
    for (i in path) {
        val arr = cur as? JSONArray ?: return null
        cur = arr.opt(i)
        if (cur == null || cur == JSONObject.NULL) return null
    }
    return cur
}

fun Any?.str(vararg path: Int): String? = (at(*path) as? String)?.takeIf { it.isNotBlank() }
fun Any?.num(vararg path: Int): Double? = (at(*path) as? Number)?.toDouble()
