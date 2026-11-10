package app.apkstore.data

import android.net.Uri
import org.json.JSONObject

/** סוג החסימה שנטפרי החזירה */
sealed class NfBlock(val pageUrl: String) {
    /** "risk-type" - הקובץ עדיין לא נבדק */
    class NotChecked(url: String) : NfBlock(url)
    /** "deny" - נבדק ונחסם */
    class Denied(url: String) : NfBlock(url)
    /** כל סוג אחר שיופיע בעתיד */
    class Other(val type: String, url: String) : NfBlock(url)
}

class NetFreeBlockException(val block: NfBlock) : Exception("blocked by NetFree")

object NetFree {
    private val IFRAME = Regex("""src="(//netfree\.link/block/#[^"]+)"""")

    /** מחזיר null אם התשובה אינה דף חסימה של נטפרי */
    fun detect(contentType: String?, body: String): NfBlock? {
        if (contentType != null && !contentType.contains("html", ignoreCase = true)) return null
        val m = IFRAME.find(body) ?: return null
        val pageUrl = "https:" + m.groupValues[1]
        // Uri.decode ולא URLDecoder - כדי ש-'+' לא יהפוך לרווח
        val json = runCatching { JSONObject(Uri.decode(pageUrl.substringAfter('#'))) }.getOrNull()
        return when (val type = json?.optString("block").orEmpty()) {
            "risk-type" -> NfBlock.NotChecked(pageUrl)
            "deny" -> NfBlock.Denied(pageUrl)
            else -> NfBlock.Other(type, pageUrl)
        }
    }
}
