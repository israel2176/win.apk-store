package app.apkstore.data

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** לקוח HTTP אחד לכל האפליקציה, עם עוגיות בזיכרון (כמו google-play-scraper) */
object Http {
    private val cookies = HashMap<String, List<Cookie>>()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                synchronized(this@Http) {
                    val old = this@Http.cookies[url.host].orEmpty().filterNot { o -> cookies.any { it.name == o.name } }
                    this@Http.cookies[url.host] = old + cookies
                }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(this@Http) {
                cookies[url.host].orEmpty().filter { it.expiresAt > System.currentTimeMillis() }
            }
        })
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
    /** null = חינם; אחרת המחיר כפי שגוגל מציגה אותו */
    val price: String? = null,
    val summary: String? = null,
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
    val updatedAt: Long? = null,
    val released: String? = null,
    val whatsNew: String? = null,
    val minAndroid: String? = null,
    val contentRating: String? = null,
    val contentRatingNote: String? = null,
    val installsExact: Long? = null,
    val inAppPurchases: Boolean = false,
    val iapRange: String? = null,
    /** null = חינם */
    val price: String? = null,
    val preregister: Boolean = false,
    val categories: List<String> = emptyList(),
    val video: String? = null,
    val developerEmail: String? = null,
    val developerWebsite: String? = null,
    val developerAddress: String? = null,
    val developerLegalName: String? = null,
    val privacyPolicy: String? = null,
    val dataSafety: List<String> = emptyList(),
    val permissions: List<PermissionGroup> = emptyList(),
    val reviews: List<Review> = emptyList(),
    val similar: List<AppSummary> = emptyList(),
) {
    val free get() = price == null
}

data class PermissionGroup(val name: String, val items: List<String>)

data class Review(
    val id: String,
    val author: String,
    val avatar: String?,
    val score: Int,
    val text: String,
    val time: Long?,
    val likes: Int,
    val appVersion: String?,
    val reply: String?,
    val replyTime: Long? = null,
)

/** מיון ביקורות - אותם ערכים כמו בספריות */
enum class ReviewSort(val code: Int) { Relevant(1), Newest(2), Rating(3) }

data class Page<T>(val items: List<T>, val next: String?)

/**
 * חיפוש, דף אפליקציה וביקורות ישירות מ-play.google.com.
 *
 * מבוסס על google-play-scraper (Node ו-Python): הנתונים יושבים בבלוקים AF_initDataCallback בתוך ה-HTML,
 * ועמודים נוספים מגיעים מ-batchexecute. כל נתיב כאן תואם לנתיב בספריות, כולל הנתיבים החלופיים שלהן.
 * כשגוגל משנה את המבנה - כאן מעדכנים.
 */
object PlayApi {
    private const val HL = "he"
    private const val GL = "il"
    private const val BASE = "https://play.google.com"

    private val SCRIPT = Regex("""AF_initDataCallback[\s\S]*?</script""")
    private val KEY = Regex("""(ds:.*?)'""")
    private val VALUE = Regex("""data:([\s\S]*?), sideChannel: \{\}\}\);</""")
    /** מיפוי ds:N למזהה הבקשה (AF_dataServiceRequests) - יציב יותר מהמספר */
    private val SERVICE = Regex("""'(ds:\d+)'\s*:\s*\{id:'(\w+)'""")

    private const val RPC_DETAILS = "Ws7gDc"
    private const val RPC_REVIEWS = "oCPfdb"
    private const val RPC_SIMILAR = "ag2B9c"
    private const val RPC_SEARCH_MORE = "qnKhOb"

    // ================= חיפוש =================

    suspend fun search(query: String): Page<AppSummary> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val page = parse(get("$BASE/store/search?q=$q&c=apps&hl=$HL&gl=$GL"))
        val sections = page.ds["ds:4"].at(0, 1) as? JSONArray ?: return@withContext Page(emptyList(), null)

        // הרשימה: האזור הראשון שיש בו אפליקציות
        val out = mutableListOf<AppSummary>()
        var token: String? = null
        for (i in 0 until sections.length()) {
            val list = sections.at(i, 22, 0) as? JSONArray ?: continue
            if (list.length() == 0) continue
            out += list(list) { parseItem(it) }
            token = sections.str(i, 22, 1, 3, 1)
            break
        }
        // התוצאה המודגשת (התאמה מדויקת) - יכולה להופיע בכל אזור, ותמיד ראשונה
        for (i in 0 until sections.length()) {
            val top = sections.at(i, 23) as? JSONArray ?: continue
            val t = parseTop(top) ?: continue
            out.removeAll { it.pkg == t.pkg }
            out.add(0, t)
            break
        }
        Page(out.distinctBy { it.pkg }, token)
    }

    /** עמוד נוסף של תוצאות חיפוש */
    suspend fun searchMore(token: String): Page<AppSummary> = withContext(Dispatchers.IO) {
        val req = """[[null,[[10,[10,50]],true,null,[96,27,4,8,57,30,110,79,11,16,49,1,3,9,12,104,55,56,51,10,34,77]],null,${JSONObject.quote(token)}]]"""
        val data = batch(RPC_SEARCH_MORE, req) ?: return@withContext Page(emptyList(), null)
        Page(list(data.at(0, 0, 0)) { parseListItem(it) }, data.str(0, 0, 7, 1))
    }

    // ================= דף אפליקציה =================

    suspend fun details(pkg: String): AppDetails = withContext(Dispatchers.IO) {
        val id = URLEncoder.encode(pkg, "UTF-8")
        // כמו בספריית Python: אם האפליקציה לא זמינה בישראל, מנסים בלי מדינה
        val html = try { get("$BASE/store/apps/details?id=$id&hl=$HL&gl=$GL") }
        catch (e: NotFoundException) { get("$BASE/store/apps/details?id=$id&hl=$HL") }
        val page = parse(html)
        val a = (page.byRpc(RPC_DETAILS) ?: page.ds["ds:5"]).at(1, 2) as? JSONArray
            ?: throw IOException("האפליקציה לא נמצאה בפליי")
        // קודם התיאור המתורגם לעברית, ואם אין - המקורי (כמו בספריות)
        val descHtml = a.str(12, 0, 0, 1) ?: a.str(72, 0, 1)
        val priceMicros = a.num(57, 0, 0, 0, 0, 1, 0, 0)
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
            updatedAt = a.num(145, 0, 1, 0)?.toLong()?.times(1000),
            released = a.str(10, 0),
            whatsNew = a.str(144, 1, 1)?.let(::html),
            minAndroid = a.str(140, 1, 1, 0, 0, 1),
            contentRating = a.str(9, 0),
            contentRatingNote = a.str(9, 2, 1) ?: a.str(9, 3, 1),
            installsExact = a.num(13, 2)?.toLong(),
            inAppPurchases = a.at(19, 0) != null,
            iapRange = a.str(19, 0),
            price = if (priceMicros != null && priceMicros > 0) a.str(57, 0, 0, 0, 0, 1, 0, 2) ?: "%.2f".format(priceMicros / 1e6) else null,
            preregister = a.num(18, 0)?.toInt() == 1,
            categories = categories(a.at(118)).ifEmpty { listOfNotNull(a.str(79, 0, 0, 0)) },
            video = a.str(100, 0, 0, 3, 2),
            developerEmail = a.str(69, 1, 0),
            developerWebsite = a.str(69, 0, 5, 2),
            developerAddress = a.str(69, 2, 0),
            developerLegalName = a.str(69, 4, 0),
            privacyPolicy = a.str(99, 0, 5, 2),
            dataSafety = list(a.at(136, 1)) { it.str(1) },
            permissions = permissions(a.at(74, 2)),
            reviews = (page.byRpc(RPC_REVIEWS)?.let(::reviewsFrom) ?: page.ds.values.firstNotNullOfOrNull(::reviewsFrom)).orEmpty(),
            similar = (page.byRpc(RPC_SIMILAR)?.let(::similarFrom) ?: page.ds.values.firstNotNullOfOrNull(::similarFrom))
                .orEmpty().filter { it.pkg != pkg },
        )
    }

    // ================= ביקורות =================

    /** ביקורות לפי מיון, בעמודים (token = null לעמוד הראשון) */
    suspend fun reviews(pkg: String, sort: ReviewSort, token: String? = null, count: Int = 40): Page<Review> = withContext(Dispatchers.IO) {
        val paging = if (token != null) JSONArray().put(count).put(JSONObject.NULL).put(token) else JSONArray().put(count)
        val filters = JSONArray().apply { repeat(9) { put(JSONObject.NULL) } }
        val req = JSONArray()
            .put(JSONObject.NULL)
            .put(JSONArray().put(2).put(sort.code).put(paging).put(JSONObject.NULL).put(filters))
            .put(JSONArray().put(pkg).put(7))
        val data = batch(RPC_REVIEWS, req.toString()) ?: return@withContext Page(emptyList(), null)
        Page(reviewList(data.at(0)), data.str(1, 1))
    }

    // ================= פענוח =================

    /** פריט ברשימת חיפוש (עמוד ראשון) ובאפליקציות דומות */
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
            price = it.num(0, 8, 1, 0, 0)?.takeIf { p -> p > 0 }?.let { _ -> it.str(0, 8, 1, 0, 2) },
            summary = it.str(0, 13, 1)?.let(::html),
        )
    }

    /** פריט בעמודים הנוספים של החיפוש (appList בספרייה) */
    private fun parseListItem(raw: Any?): AppSummary? {
        val it = raw as? JSONArray ?: return null
        val pkg = it.str(12, 0) ?: return null
        return AppSummary(
            pkg = pkg,
            title = it.str(2) ?: pkg,
            icon = it.str(1, 1, 0, 3, 2),
            developer = it.str(4, 0, 0, 0),
            score = it.num(6, 0, 2, 1, 1),
            genre = null,
            installs = null,
            price = it.str(7, 0, 3, 2, 1, 0, 2),
            summary = it.str(4, 1, 1, 1, 1)?.let(::html),
        )
    }

    /** התוצאה המודגשת: האזור [23], הנתונים ב-[16] */
    private fun parseTop(section23: JSONArray): AppSummary? {
        val t = section23.at(16) as? JSONArray ?: return null
        // [16,3,12,0,0] בספרייה = שדה 12 של [3] (JSPB) - ובגרסה הישנה [11,0,0]
        val pkg = t.str(3, 11, 0, 0) ?: t.str(11, 0, 0) ?: return null
        val priceMicros = section23.num(17, 0, 2, 0, 1, 0, 0) ?: t.num(2, 57, 0, 0, 0, 0, 1, 0, 0)
        return AppSummary(
            pkg = pkg,
            title = t.str(2, 0, 0) ?: pkg,
            icon = t.str(2, 95, 0, 3, 2),
            developer = t.str(2, 68, 0),
            score = t.num(2, 51, 0, 1),
            genre = t.str(2, 79, 0, 0, 0),
            installs = t.str(2, 13, 0),
            screenshots = shots(t.at(2, 78, 0)),
            price = priceMicros?.takeIf { it > 0 }?.let { section23.str(17, 0, 2, 0, 1, 0, 2) ?: t.str(2, 57, 0, 0, 0, 0, 1, 0, 2) },
            summary = t.str(2, 73, 0, 1)?.let(::html),
        )
    }

    private fun <T> list(raw: Any?, f: (Any?) -> T?): List<T> {
        val arr = raw as? JSONArray ?: return emptyList()
        return (0 until arr.length()).mapNotNull { f(arr.opt(it)) }
    }

    /** קטגוריות: חיפוש רקורסיבי כמו extractCategories בספריות */
    private fun categories(raw: Any?, out: MutableList<String> = mutableListOf()): List<String> {
        val arr = raw as? JSONArray ?: return out
        if (arr.length() >= 4 && arr.opt(0) is String) {
            arr.optString(0).takeIf { it.isNotBlank() && it !in out }?.let(out::add)
        } else for (i in 0 until arr.length()) categories(arr.opt(i), out)
        return out
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

    /** ביקורות מתוך דף האפליקציה - מזהים לפי הצורה, כמו extractComments בספרייה */
    private fun reviewsFrom(d: JSONArray): List<Review>? {
        val first = d.at(0, 0) as? JSONArray ?: return null
        if (first.str(0) == null || first.str(1, 0) == null || first.num(2) == null) return null
        return reviewList(d.at(0)).takeIf { it.isNotEmpty() }
    }

    private fun reviewList(raw: Any?): List<Review> = list(raw) { r ->
        Review(
            id = r.str(0) ?: return@list null,
            author = r.str(1, 0) ?: return@list null,
            avatar = r.str(1, 1, 3, 2),
            score = r.num(2)?.toInt() ?: return@list null,
            text = r.str(4) ?: return@list null,
            time = r.num(5, 0)?.toLong()?.times(1000),
            likes = r.num(6)?.toInt() ?: 0,
            appVersion = r.str(10),
            reply = r.str(7, 1),
            replyTime = r.num(7, 2, 0)?.toLong()?.times(1000),
        )
    }

    /** אפליקציות דומות - הבלוק ag2B9c, או לפי התוכן */
    private fun similarFrom(d: JSONArray): List<AppSummary>? {
        val clusters = d.at(1, 1) as? JSONArray ?: return null
        for (i in 0 until clusters.length()) {
            val items = clusters.at(i, 21, 0) as? JSONArray ?: continue
            val apps = list(items) { parseItem(JSONArray().put(it)) }
            if (apps.isNotEmpty()) return apps
        }
        return null
    }

    private fun shots(raw: Any?): List<String> = list(raw) { it.str(3, 2) }

    private fun html(s: String) =
        HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()

    // ================= רשת =================

    private class Parsed(val ds: Map<String, JSONArray>, val rpc: Map<String, String>) {
        /** הבלוק לפי מזהה הבקשה; אם יש כמה - הגדול מביניהם */
        fun byRpc(id: String): JSONArray? =
            rpc.filterValues { it == id }.keys.mapNotNull { ds[it] }.maxByOrNull { it.toString().length }
    }

    private fun parse(dom: String): Parsed {
        val map = HashMap<String, JSONArray>()
        for (m in SCRIPT.findAll(dom)) {
            val block = m.value
            val key = KEY.find(block)?.groupValues?.get(1) ?: continue
            val value = VALUE.find(block)?.groupValues?.get(1) ?: continue
            (runCatching { JSONTokener(value).nextValue() }.getOrNull() as? JSONArray)?.let { map[key] = it }
        }
        val rpc = SERVICE.findAll(dom).associate { it.groupValues[1] to it.groupValues[2] }
        return Parsed(map, rpc)
    }

    private class NotFoundException : IOException("לא נמצא")

    private suspend fun get(url: String): String = call(Request.Builder().url(url).build())

    /**
     * קריאת batchexecute (עמודים נוספים, ביקורות). התשובה: ")]}'" ואז מערך שבו
     * ["wrb.fr", rpc, "<json כמחרוזת>", ...]
     */
    private suspend fun batch(rpc: String, request: String): JSONArray? {
        val envelope = JSONArray().put(JSONArray().put(JSONArray().put(rpc).put(request).put(JSONObject.NULL).put("generic")))
        val body = FormBody.Builder().add("f.req", envelope.toString()).build()
        val req = Request.Builder().url("$BASE/_/PlayStoreUi/data/batchexecute?rpcids=$rpc&hl=$HL&gl=$GL").post(body).build()
        val text = call(req)
        val json = JSONTokener(text.substring(text.indexOf('[').coerceAtLeast(0))).nextValue() as? JSONArray ?: return null
        for (i in 0 until json.length()) {
            val e = json.optJSONArray(i) ?: continue
            if (e.optString(0) == "wrb.fr" && e.optString(1) == rpc) {
                val inner = e.opt(2) as? String ?: return null
                return JSONTokener(inner).nextValue() as? JSONArray
            }
        }
        return null
    }

    /**
     * בקשה עם ניסיונות חוזרים (כמו בספריית Python): על 429 / 5xx / PlayGatewayError
     * מחכים ומנסים שוב, עד 3 פעמים. חסימת נטפרי ו-404 לא מנסים שוב.
     */
    private suspend fun call(req: Request): String {
        val withLang = req.newBuilder().header("Accept-Language", "he-IL,he;q=0.9").build()
        var last: IOException? = null
        for (attempt in 1..3) {
            try {
                Http.client.newCall(withLang).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    NetFree.detect(resp.header("Content-Type"), body)?.let { throw NetFreeBlockException(it) }
                    if (resp.code == 404) throw NotFoundException()
                    if (resp.code == 429 || resp.code >= 500 || body.contains("PlayGatewayError")) {
                        throw RetryableException("פליי עמוס כרגע (${resp.code})")
                    }
                    if (!resp.isSuccessful) throw IOException("שגיאה בגישה לפליי (${resp.code})")
                    return body
                }
            } catch (e: RetryableException) {
                last = e
            } catch (e: java.net.SocketTimeoutException) {
                last = e
            }
            delay(1500L * attempt)
        }
        throw last ?: IOException("שגיאה בגישה לפליי")
    }

    private class RetryableException(msg: String) : IOException(msg)
}

/** כתובת תמונה מ-play-lh בגודל מבוקש (חוסך הורדת תמונות ענק) */
fun sizedImage(url: String?, px: Int): String? = url?.substringBefore('=')?.plus("=s$px")
fun screenshotImage(url: String?, h: Int): String? = url?.substringBefore('=')?.plus("=h$h")

// ---- עזרים לניווט במערכים המקוננים ----

/**
 * איבר במערך בפורמט JSPB של גוגל: כשהמערך "מתקצר", השדות הגבוהים עוברים לאובייקט
 * שבסוף המערך, תחת המפתח index+1. כך הנתיב [140] נמצא לפעמים ב-[-1]["141"]
 * (זה מה שהספריות מכנות fallbackPath), ו-[11] של התוצאה המודגשת נמצא ב-[3]["12"].
 */
private fun JSONArray.jspb(i: Int): Any? {
    val n = length()
    val last = if (n > 0) opt(n - 1) else null
    if (i < n) {
        val v = opt(i)
        if (v != null && v != JSONObject.NULL) return v
    }
    return (last as? JSONObject)?.opt((i + 1).toString())
}

fun Any?.at(vararg path: Int): Any? {
    var cur: Any? = this
    for (i in path) {
        cur = when (cur) {
            is JSONArray -> cur.jspb(i)
            // אובייקט JSPB שהגענו אליו ישירות: המפתח הוא מספר השדה
            is JSONObject -> cur.opt((i + 1).toString())
            else -> return null
        }
        if (cur == null || cur == JSONObject.NULL) return null
    }
    return cur
}

fun Any?.str(vararg path: Int): String? = (at(*path) as? String)?.takeIf { it.isNotBlank() }
fun Any?.num(vararg path: Int): Double? = (at(*path) as? Number)?.toDouble()
