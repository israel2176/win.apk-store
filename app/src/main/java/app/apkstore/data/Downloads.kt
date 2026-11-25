package app.apkstore.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

sealed class DlState {
    data object Preparing : DlState()
    data class Downloading(val progress: Float) : DlState() // -1 = גודל לא ידוע
    data class NotChecked(val pageUrl: String) : DlState()
    data class Blocked(val pageUrl: String) : DlState()
    data class Failed(val message: String) : DlState()
    data class Ready(val path: String, val xapk: Boolean) : DlState()
}

data class DownloadItem(val pkg: String, val title: String, val icon: String?, val state: DlState)

/** ניהול ההורדות: פתרון קישור ב-APKPure, הורדה, וזיהוי חסימת נטפרי */
class Downloads(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "apks").apply { mkdirs() }
    private val prefs = app.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<DownloadItem>> = _items

    fun stateOf(pkg: String): DlState? = _items.value.firstOrNull { it.pkg == pkg }?.state

    fun start(pkg: String, title: String, icon: String?) {
        synchronized(jobs) { if (jobs[pkg]?.isActive == true) return }
        set(DownloadItem(pkg, title, icon, DlState.Preparing))
        val job = scope.launch {
            val result = runCatching { download(pkg) }
            val state = result.getOrElse { e ->
                when (e) {
                    is NetFreeBlockException -> e.block.toState()
                    is kotlinx.coroutines.CancellationException -> throw e
                    else -> DlState.Failed(e.message ?: "שגיאה לא ידועה")
                }
            }
            update(pkg, state)
            persist()
        }
        synchronized(jobs) { jobs[pkg] = job }
    }

    /** "נסה שוב" - תמיד מבקש קישור חדש, כי הקישורים החתומים פגים אחרי זמן קצר */
    fun retry(pkg: String) {
        val item = _items.value.firstOrNull { it.pkg == pkg } ?: return
        start(item.pkg, item.title, item.icon)
    }

    fun remove(pkg: String) {
        synchronized(jobs) { jobs.remove(pkg)?.cancel() }
        dir.listFiles { f -> f.name.startsWith("$pkg.") }?.forEach { it.delete() }
        _items.update { list -> list.filterNot { it.pkg == pkg } }
        persist()
    }

    /**
     * כמו apkeep: אם ההעברה נקטעה, מנסים שוב עד פעמיים נוספות.
     * חסימת נטפרי או דף אינטרנט במקום קובץ - לא מנסים שוב.
     */
    private suspend fun download(pkg: String): DlState {
        val src = ApkPure.resolve(pkg)
        var last: IOException? = null
        repeat(3) { attempt ->
            try {
                return transfer(pkg, src)
            } catch (e: NotAFileException) {
                throw e
            } catch (e: IOException) {
                last = e
                if (attempt < 2) kotlinx.coroutines.delay(2000L * (attempt + 1))
            }
        }
        throw last ?: IOException("ההורדה נכשלה")
    }

    private class NotAFileException(msg: String) : IOException(msg)

    private suspend fun transfer(pkg: String, src: ApkSource): DlState {
        val ext = if (src.isXapk) "xapk" else "apk"
        val part = File(dir, "$pkg.$ext.part")
        val target = File(dir, "$pkg.$ext")

        val req = Request.Builder().url(src.url).build()
        Http.client.newCall(req).execute().use { resp ->
            val ct = resp.header("Content-Type")
            val looksHtml = ct?.contains("html", true) == true ||
                resp.peekBody(64).string().trimStart().startsWith("<")
            if (looksHtml) {
                val body = resp.peekBody(256L * 1024).string()
                NetFree.detect(ct ?: "text/html", body)?.let { return it.toState() }
                throw NotAFileException("התקבל דף אינטרנט במקום קובץ")
            }
            if (resp.code in 400..499) throw NotAFileException("ההורדה נכשלה (${resp.code})")
            if (!resp.isSuccessful) throw IOException("ההורדה נכשלה (${resp.code})")

            val body = resp.body ?: throw IOException("תשובה ריקה")
            val total = body.contentLength()
            var done = 0L
            var lastShown = -1
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else -1
                        if (pct != lastShown) {
                            lastShown = pct
                            update(pkg, DlState.Downloading(if (total > 0) done.toFloat() / total else -1f))
                        }
                    }
                }
            }
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("שמירת הקובץ נכשלה")
        return DlState.Ready(target.absolutePath, src.isXapk)
    }

    private fun NfBlock.toState(): DlState = when (this) {
        is NfBlock.NotChecked -> DlState.NotChecked(pageUrl)
        is NfBlock.Denied -> DlState.Blocked(pageUrl)
        is NfBlock.Other -> DlState.Blocked(pageUrl)
    }

    private fun set(item: DownloadItem) = _items.update { list ->
        listOf(item) + list.filterNot { it.pkg == item.pkg }
    }

    private fun update(pkg: String, state: DlState) = _items.update { list ->
        list.map { if (it.pkg == pkg) it.copy(state = state) else it }
    }

    // ---- שמירה בין הפעלות ----

    private fun persist() {
        val arr = JSONArray()
        for (it in _items.value) {
            val o = JSONObject().put("pkg", it.pkg).put("title", it.title).put("icon", it.icon)
            when (val s = it.state) {
                is DlState.Ready -> o.put("t", "ready").put("path", s.path).put("xapk", s.xapk)
                is DlState.NotChecked -> o.put("t", "notchecked").put("page", s.pageUrl)
                is DlState.Blocked -> o.put("t", "blocked").put("page", s.pageUrl)
                is DlState.Failed -> o.put("t", "failed").put("msg", s.message)
                else -> o.put("t", "failed").put("msg", "ההורדה הופסקה")
            }
            arr.put(o)
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun load(): List<DownloadItem> {
        val arr = runCatching { JSONArray(prefs.getString("items", "[]")) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val state = when (o.optString("t")) {
                "ready" -> {
                    val path = o.optString("path")
                    if (File(path).exists()) DlState.Ready(path, o.optBoolean("xapk"))
                    else DlState.Failed("הקובץ נמחק")
                }
                "notchecked" -> DlState.NotChecked(o.optString("page"))
                "blocked" -> DlState.Blocked(o.optString("page"))
                else -> DlState.Failed(o.optString("msg", "ההורדה הופסקה"))
            }
            DownloadItem(o.optString("pkg"), o.optString("title"), o.optString("icon").ifBlank { null }, state)
        }
    }
}
