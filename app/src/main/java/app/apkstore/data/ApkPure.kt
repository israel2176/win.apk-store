package app.apkstore.data

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

data class ApkSource(val url: String, val isXapk: Boolean)

/**
 * מוצא קישור הורדה ב-APKPure לפי שם חבילה - אותה קריאה ש-apkeep עושה
 * (src/download_sources/apkpure.rs). עובד גם לאפליקציות שלא מופיעות באתר שלהם.
 */
object ApkPure {
    private const val VERSIONS_URL = "https://api.pureapk.com/m/v3/cms/app_version?hl=en-US&package_name="
    private val DOWNLOAD = Regex(
        """(X?APKJ)[\s\S]{2}(https?://(www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_+.~#?&/=]*))"""
    )

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
            if (!resp.isSuccessful) throw IOException("APKPure החזיר שגיאה (${resp.code})")
            // התשובה בינארית (protobuf) - מפענחים כ-Latin-1 כדי לא לשבור בתים
            val text = String(bytes, Charsets.ISO_8859_1)
            val m = DOWNLOAD.find(text) ?: throw IOException("לא נמצא קובץ להורדה עבור $pkg")
            ApkSource(url = m.groupValues[2], isXapk = m.groupValues[1] == "XAPKJ")
        }
    }
}
