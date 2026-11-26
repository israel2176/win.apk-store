package app.apkstore.data

import android.os.Build
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.time.OffsetDateTime

/** הקובץ שנמצא ב-APKPure: קישור ההורדה ופרטי הגרסה */
data class ApkSource(
    val url: String,
    val isXapk: Boolean,
    val versionName: String? = null,
    val versionCode: Long? = null,
    /** גודל בבתים, אם ידוע */
    val size: Long? = null,
    /** מתי הגרסה הזו עלתה ל-APKPure (מילישניות) */
    val updatedAt: Long? = null,
)

/**
 * אין ב-APKPure קובץ שאפשר להוריד. title/body - מה שדף האפליקציה מציג במקום פרטי הקובץ.
 */
open class NotOnApkPureException(
    val pkg: String,
    res: Int = app.apkstore.R.string.err_apkpure_missing,
    @StringRes val titleRes: Int = app.apkstore.R.string.apk_missing_title,
    @StringRes val bodyRes: Int = app.apkstore.R.string.apk_missing_body,
) : AppException(res)

/** יש ב-APKPure קבצים, אבל אף אחד מהם לא למעבד של המכשיר (ויש בהם קוד מקומי) */
class NoAbiException(pkg: String) : NotOnApkPureException(
    pkg, app.apkstore.R.string.err_apkpure_no_abi,
    app.apkstore.R.string.apk_no_abi_title, app.apkstore.R.string.apk_no_abi_body,
)

/**
 * מוצא קישור הורדה ב-APKPure לפי שם חבילה - אותה קריאה ש-apkeep עושה
 * (src/download_sources/apkpure.rs). עובד גם לאפליקציות שלא מופיעות באתר שלהם.
 *
 * התשובה בינארית (protobuf). לכל וריאנט יש בה, לפי הסדר: שם גרסה, קישור הורדה,
 * ותאריך העלאה. הגרסה הראשונה היא העדכנית ביותר, ולכל גרסה יכולים להיות כמה וריאנטים.
 *
 * כמו Google Play: הקובץ למעבד של המכשיר, לפי Build.SUPPORTED_ABIS ובסדר שלו
 * (ב-Win.apk: x86_64, ואחריו arm64-v8a שמתורגם). APKPure מחזיר כל מה שמכשיר כזה יכול
 * להריץ (למשל גם x86 בלבד), בלי לומר מה יש בכל קובץ - ולכן בודקים את הקבצים עצמם
 * (ראו [RemoteZip]), כמו EFForg/apkeep#259: הגרסה העדכנית ביותר שיש לה וריאנט עם
 * ABI של המכשיר (הראשון ברשימה קודם), או בלי קוד מקומי בכלל.
 */
object ApkPure {
    /** מה המכשיר מריץ, מהמועדף */
    val deviceAbis: List<String> get() = Build.SUPPORTED_ABIS.toList()

    private const val VERSIONS_URL = "https://api.pureapk.com/m/v3/cms/app_version?hl=en-US&package_name="
    private val DOWNLOAD = Regex(
        """(X?APKJ)[\s\S]{2}(https?://(www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_+.~#?&/=]*))"""
    )
    /**
     * שם גרסה ואחריו sha1. בשם יכולים להיות רווחים, סוגריים וסוגריים מרובעים
     * ("26.43.30 (040700-1006944126)", "54.3.08-24 [0] [PR] 1022323148") - כמו
     * התיקונים של Win.apk ל-apkeep (windows/scripts/apkeep-patches/).
     */
    private val VERSION = Regex("""([A-Za-z0-9][A-Za-z0-9 ._()\[\]+\-]*):\([0-9a-fA-F]{40,}""")
    private val DATE = Regex("""20\d\d-\d\d-\d\dT\d\d:\d\d:\d\d[+\-]\d\d:\d\d""")

    /** גרסה אחת ברשימה של APKPure, והווריאנטים שלה לפי הסדר */
    internal data class Listed(val version: String, val variants: List<ApkSource>)

    suspend fun resolve(pkg: String): ApkSource = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(VERSIONS_URL + pkg)
            .header("x-cv", "3172501")
            // רמת ה-API של המכשיר, כמו שהאפליקציה של APKPure שולחת (Win.apk: 34)
            .header("x-sv", Build.VERSION.SDK_INT.toString())
            // ה-ABIs של המכשיר. רק עם ", " ביניהם (EFForg/apkeep#258): עם "," בלבד
            // APKPure מתעלם מהכותרת ומחזיר arm64-v8a.
            .header("x-abis", deviceAbis.joinToString(", "))
            .header("x-gp", "1")
            .build()
        val text = Http.client.newCall(req).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            val ct = resp.header("Content-Type")
            if (ct?.contains("html", true) == true) {
                NetFree.detect(ct, String(bytes, Charsets.UTF_8))?.let { throw NetFreeBlockException(it) }
            }
            if (!resp.isSuccessful) throw AppException(app.apkstore.R.string.err_apkpure_http, resp.code)
            // מפענחים כ-Latin-1 כדי לא לשבור בתים: כל תו הוא בית אחד
            String(bytes, Charsets.ISO_8859_1)
        }
        val listed = list(text)
        if (listed.isEmpty()) {
            // לא הצלחנו לפרק לגרסאות - הקובץ הראשון, כמו apkeep
            return@withContext parse(text) ?: throw NotOnApkPureException(pkg)
        }
        choose(pkg, listed, deviceAbis) { RemoteZip.abis(it) }
    }

    /**
     * בוחר וריאנט: בכל גרסה (מהעדכנית), לכל ABI של המכשיר לפי הסדר - הווריאנט הראשון שיש בו
     * אותו, או שאין בו קוד מקומי.
     * abisOf: ה-ABIs שבקובץ; ריק = בלי קוד מקומי; null = אי אפשר לדעת.
     * גרסה שאי אפשר לדעת מה יש בה - הקובץ הראשון (מה ש-APKPure נתן למכשיר כזה), כמו apkeep#259.
     * כל הקבצים ידועים ואף אחד לא למעבד של המכשיר - [NoAbiException].
     */
    internal suspend fun choose(
        pkg: String, listed: List<Listed>, device: List<String>, abisOf: suspend (String) -> Set<String>?,
    ): ApkSource {
        for (v in listed) {
            val abis = v.variants.map { abisOf(it.url) }
            for (abi in device) {
                v.variants.zip(abis).firstOrNull { (_, a) -> a != null && (a.isEmpty() || abi in a) }?.let { return it.first }
            }
            if (abis.any { it == null }) return listed[0].variants[0]
        }
        throw NoAbiException(pkg)
    }

    /** הגרסאות שבתשובה, מהעדכנית, עם הווריאנטים של כל אחת (בלי כפילויות) */
    internal fun list(text: String): List<Listed> {
        val names = VERSION.findAll(text).map { m ->
            val g = m.groups[1]!!
            // ה-protobuf: לפני השם בא האורך שלו. אם ההתאמה תפסה גם תווים מהשדה שלפני -
            // השם מתחיל במקום שהבית שלפניו הוא אורך היתרה.
            var start = g.range.first
            for (at in g.range) {
                if (at > 0 && text[at - 1].code == g.range.last + 1 - at) { start = at; break }
            }
            Triple(start, m.range.last + 1, text.substring(start, g.range.last + 1))
        }.toList()
        val out = LinkedHashMap<String, MutableList<ApkSource>>()
        names.forEachIndexed { i, (_, end, name) ->
            val next = names.getOrNull(i + 1)?.first ?: text.length
            val seg = text.substring(end, next)
            val m = DOWNLOAD.find(seg) ?: return@forEachIndexed
            val src = source(m, name, DATE.find(seg, m.range.last)?.value)
            val variants = out.getOrPut(name) { mutableListOf() }
            if (variants.none { it.url == src.url }) variants += src
        }
        return out.map { (k, v) -> Listed(k, v) }
    }

    /** הקובץ הראשון בתשובה. null = אין קובץ (APKPure מחזיר תשובה כמעט ריקה לאפליקציה שאין לו) */
    internal fun parse(text: String): ApkSource? {
        val m = DOWNLOAD.find(text) ?: return null
        // שם הגרסה - האחרון שלפני הקישור; התאריך - הראשון שאחרי הקישור
        val name = VERSION.findAll(text.substring(0, m.range.first)).lastOrNull()?.groupValues?.get(1)
        return source(m, name, DATE.find(text, m.range.last)?.value)
    }

    private fun source(m: MatchResult, name: String?, date: String?): ApkSource {
        val url = m.groupValues[2]
        val meta = urlMeta(url)
        return ApkSource(
            url = url,
            isXapk = m.groupValues[1] == "XAPKJ",
            versionName = meta["vn"] ?: name,
            versionCode = meta["vc"]?.toLongOrNull(),
            size = meta["s"]?.toLongOrNull(),
            updatedAt = date?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() },
        )
    }

    /**
     * הפרמטר c בקישור: "1|CATEGORY|<base64>", וה-base64 הוא
     * "dev=...&t=xapk&s=<size>&vn=<version>&vc=<code>"
     */
    private fun urlMeta(url: String): Map<String, String> = runCatching {
        val c = android.net.Uri.parse(url).getQueryParameter("c") ?: return emptyMap()
        val b64 = c.substringAfterLast('|').filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        val decoded = String(android.util.Base64.decode(b64, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP))
        decoded.split('&').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    }.getOrDefault(emptyMap())
}

/** איך הגרסה ב-APKPure ביחס לפליי */
enum class Freshness { Same, Newer, Older, Unknown }

object Versions {
    /** השוואת "5.27.5.0" מול "5.28.0.0" - חלק אחרי חלק, מספרית. null = הפורמט לא מספרי */
    fun compare(a: String, b: String): Int? {
        fun parts(v: String) = v.trim().split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() }
        val pa = parts(a)
        val pb = parts(b)
        if (pa.any { it == null } || pb.any { it == null }) return null
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i) ?: 0
            val y = pb.getOrNull(i) ?: 0
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /**
     * לפי מספר הגרסה, אם פליי מציג אותו. אחרת ("משתנה בהתאם למכשיר") לפי התאריכים:
     * אם הגרסה ב-APKPure עלתה יותר מיומיים לפני העדכון האחרון בפליי - כנראה ישנה יותר.
     */
    fun freshness(apk: ApkSource, playVersion: String?, playUpdatedAt: Long?): Freshness {
        val vn = apk.versionName
        val c = if (vn != null && playVersion != null) compare(vn, playVersion) else null
        if (c != null) return when { c < 0 -> Freshness.Older; c > 0 -> Freshness.Newer; else -> Freshness.Same }
        if (apk.updatedAt != null && playUpdatedAt != null) {
            return if (apk.updatedAt < playUpdatedAt - 2 * 24 * 3600_000L) Freshness.Older else Freshness.Same
        }
        return Freshness.Unknown
    }
}
