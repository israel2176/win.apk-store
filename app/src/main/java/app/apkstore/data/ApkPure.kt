package app.apkstore.data

import android.os.Build
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

/** האפליקציה לא קיימת ב-APKPure בכלל */
class NotOnApkPureException(val pkg: String) : AppException(app.apkstore.R.string.err_apkpure_missing)

/**
 * מוצא קישור הורדה ב-APKPure לפי שם חבילה - אותה קריאה ש-apkeep עושה
 * (src/download_sources/apkpure.rs). עובד גם לאפליקציות שלא מופיעות באתר שלהם.
 *
 * התשובה בינארית (protobuf). לכל גרסה יש בה, לפי הסדר: שם גרסה, קישור הורדה,
 * ותאריך העלאה. הגרסה הראשונה היא העדכנית ביותר.
 */
object ApkPure {
    private const val VERSIONS_URL = "https://api.pureapk.com/m/v3/cms/app_version?hl=en-US&package_name="
    private val DOWNLOAD = Regex(
        """(X?APKJ)[\s\S]{2}(https?://(www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_+.~#?&/=]*))"""
    )
    /** כמו list_versions ב-apkeep: "5.28.0.0" ואחריו sha1 */
    private val VERSION = Regex("""([A-Za-z0-9.\-]+):\([0-9a-fA-F]{40,}""")
    private val DATE = Regex("""20\d\d-\d\d-\d\dT\d\d:\d\d:\d\d[+\-]\d\d:\d\d""")

    suspend fun resolve(pkg: String): ApkSource = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(VERSIONS_URL + pkg)
            .header("x-cv", "3172501")
            .header("x-sv", "29")
            .header("x-abis", Build.SUPPORTED_ABIS.joinToString(","))
            .header("x-gp", "1")
            .build()
        Http.client.newCall(req).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            val ct = resp.header("Content-Type")
            if (ct?.contains("html", true) == true) {
                NetFree.detect(ct, String(bytes, Charsets.UTF_8))?.let { throw NetFreeBlockException(it) }
            }
            if (!resp.isSuccessful) throw AppException(app.apkstore.R.string.err_apkpure_http, resp.code)
            // מפענחים כ-Latin-1 כדי לא לשבור בתים
            parse(String(bytes, Charsets.ISO_8859_1)) ?: throw NotOnApkPureException(pkg)
        }
    }

    /** null = אין קובץ (APKPure מחזיר תשובה כמעט ריקה לאפליקציה שאין לו) */
    internal fun parse(text: String): ApkSource? {
        val m = DOWNLOAD.find(text) ?: return null
        val url = m.groupValues[2]
        // שם הגרסה - האחרון שלפני הקישור; התאריך - הראשון שאחרי הקישור
        val name = VERSION.findAll(text.substring(0, m.range.first)).lastOrNull()?.groupValues?.get(1)
        val date = DATE.find(text, m.range.last)?.value
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
