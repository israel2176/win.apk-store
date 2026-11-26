package app.apkstore.data

import okhttp3.Request

/**
 * מה יש בקובץ APK / XAPK שב-APKPure, בלי להוריד אותו: רק התוכן של ה-zip (הספרייה המרכזית),
 * בבקשות Range. כמו remote_abis של EFForg/apkeep#259.
 */
object RemoteZip {
    private class Entry(val name: String, val stored: Boolean, val compressedSize: Long, val headerOffset: Long)

    /** ה-ABIs שבקובץ (lib/<abi>/, או config.<abi>.apk ב-XAPK). ריק = בלי קוד מקומי; null = אי אפשר לדעת */
    fun abis(url: String): Set<String>? = runCatching {
        val entries = entries(url, 0, null) ?: return null
        val abis = HashSet<String>()
        libAbis(entries, abis)

        // ב-XAPK הקוד המקומי נמצא ב-APK מפוצל לפי ארכיטקטורה, או בתוך ה-APK הבסיסי
        val apks = entries.filter { it.name.endsWith(".apk") && '/' !in it.name }
        for (apk in apks) {
            val split = apk.name.removeSuffix(".apk").let {
                when {
                    it.startsWith("split_config.") -> it.removePrefix("split_config.")
                    it.startsWith("config.") -> it.removePrefix("config.")
                    else -> null
                }
            } ?: continue
            SPLIT_ABIS[split]?.let { abis += it }
        }
        if (abis.isEmpty()) {
            for (apk in apks.filter { !it.name.startsWith("config.") && !it.name.startsWith("split_config.") }) {
                // APK דחוס בתוך ה-XAPK - אי אפשר לקרוא את התוכן שלו בטווחים
                if (!apk.stored) return null
                val (h, _) = range(url, apk.headerOffset, apk.headerOffset + 29) ?: return null
                if (h.size != 30 || !h.startsWith(LOCAL)) return null
                val data = apk.headerOffset + 30 + u16(h, 26) + u16(h, 28)
                libAbis(entries(url, data, apk.compressedSize) ?: return null, abis)
            }
        }
        abis
    }.getOrNull()

    /** שמות ה-ABI בקבצים המפוצלים (config.arm64_v8a.apk) */
    private val SPLIT_ABIS = mapOf(
        "arm64_v8a" to "arm64-v8a", "armeabi_v7a" to "armeabi-v7a", "armeabi" to "armeabi",
        "x86" to "x86", "x86_64" to "x86_64", "mips" to "mips", "mips64" to "mips64",
    )

    private val EOCD = byteArrayOf(0x50, 0x4b, 0x05, 0x06)
    private val CENTRAL = byteArrayOf(0x50, 0x4b, 0x01, 0x02)
    private val LOCAL = byteArrayOf(0x50, 0x4b, 0x03, 0x04)

    private fun libAbis(entries: List<Entry>, abis: MutableSet<String>) {
        for (e in entries) {
            if (!e.name.startsWith("lib/")) continue
            val rest = e.name.substring(4)
            val slash = rest.indexOf('/')
            if (slash > 0) abis += rest.substring(0, slash)
        }
    }

    /** הקבצים שב-zip. ה-zip יכול להיות בתוך הקובץ, ב-offset ובגודל size (APK בתוך XAPK) */
    private fun entries(url: String, offset: Long, size: Long?): List<Entry>? {
        val tailSize = 65536L + 22
        val (tail, total) = if (size != null) {
            val n = minOf(tailSize, size)
            val (t, _) = range(url, offset + size - n, offset + size - 1) ?: return null
            t to size
        } else range(url, -tailSize, null) ?: return null
        val at = lastIndexOf(tail, EOCD)
        if (at < 0 || at + 22 > tail.size) return null
        val dirSize = u32(tail, at + 12)
        val dirOffset = u32(tail, at + 16)
        if (dirSize == 0xFFFFFFFFL || dirOffset == 0xFFFFFFFFL || dirOffset + dirSize > total) return null
        val tailOffset = total - tail.size
        val dir = if (dirOffset >= tailOffset) {
            val s = (dirOffset - tailOffset).toInt()
            if (s + dirSize > tail.size) return null
            tail.copyOfRange(s, s + dirSize.toInt())
        } else {
            range(url, offset + dirOffset, offset + dirOffset + dirSize - 1)?.first ?: return null
        }

        val out = ArrayList<Entry>()
        var pos = 0
        while (pos + 46 <= dir.size && dir.startsWith(CENTRAL, pos)) {
            val nameLen = u16(dir, pos + 28).toInt()
            if (pos + 46 + nameLen > dir.size) return null
            out += Entry(
                name = String(dir, pos + 46, nameLen, Charsets.UTF_8),
                stored = u16(dir, pos + 10) == 0L,
                compressedSize = u32(dir, pos + 20),
                headerOffset = u32(dir, pos + 42),
            )
            pos += 46 + nameLen + u16(dir, pos + 30).toInt() + u16(dir, pos + 32).toInt()
        }
        return out
    }

    /**
     * בקשת Range: from..to, או (from שלילי, to null) הבתים האחרונים. מחזיר את הבתים וגודל הקובץ כולו.
     * שרת (או מסנן) שעונה בקובץ שלם (200) במקום טווח - null, בלי לקרוא את הגוף.
     */
    private fun range(url: String, from: Long, to: Long?): Pair<ByteArray, Long>? {
        val spec = if (to == null) "bytes=$from" else "bytes=$from-$to"
        val req = Request.Builder().url(url).header("Range", spec).build()
        Http.client.newCall(req).execute().use { resp ->
            if (resp.code != 206) return null
            val total = resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: return null
            return (resp.body?.bytes() ?: return null) to total
        }
    }

    private fun u16(b: ByteArray, at: Int): Long = (b[at].toLong() and 0xff) or ((b[at + 1].toLong() and 0xff) shl 8)
    private fun u32(b: ByteArray, at: Int): Long = u16(b, at) or (u16(b, at + 2) shl 16)

    private fun ByteArray.startsWith(p: ByteArray, at: Int = 0): Boolean =
        at + p.size <= size && p.indices.all { this[at + it] == p[it] }

    private fun lastIndexOf(b: ByteArray, p: ByteArray): Int {
        for (i in b.size - p.size downTo 0) if (b.startsWith(p, i)) return i
        return -1
    }
}
