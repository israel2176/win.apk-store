package app.apkstore.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.apkstore.data.AppDetails
import app.apkstore.data.AppSummary
import app.apkstore.data.DlState
import app.apkstore.data.Downloads
import app.apkstore.data.NetFreeBlockException
import app.apkstore.data.PlayApi
import app.apkstore.install.Installer
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

enum class Tab { Home, Search, Downloads }

data class HomeSection(val title: String, val apps: List<AppSummary>)

sealed class Load<out T> {
    data object Loading : Load<Nothing>()
    data class Done<T>(val value: T) : Load<T>()
    data class Error(val message: String) : Load<Nothing>()
}

class StoreViewModel(app: Application) : AndroidViewModel(app) {
    val downloads = Downloads(app)

    var tab by mutableStateOf(Tab.Home)
    val detailStack = mutableStateListOf<String>()

    var home by mutableStateOf<Load<List<HomeSection>>>(Load.Loading)
        private set

    var query by mutableStateOf("")
    var results by mutableStateOf<Load<List<AppSummary>>?>(null)
        private set
    private var searchJob: Job? = null

    val detailsCache = mutableStateMapOf<String, Load<AppDetails>>()

    /** שורות הדף הראשי - כל שורה היא חיפוש חי בפליי. אפשר לשנות כאן בחופשיות */
    private val homeRows = listOf(
        "כלים" to "כלים שימושיים",
        "תחבורה" to "תחבורה ציבורית",
        "פיננסים" to "בנק",
        "קניות" to "קניות",
    )

    init { loadHome() }

    fun loadHome() {
        home = Load.Loading
        viewModelScope.launch {
            home = try {
                val sections = homeRows.map { (title, q) ->
                    async { HomeSection(title, runCatching { PlayApi.search(q) }.getOrDefault(emptyList()).take(12)) }
                }.awaitAll().filter { it.apps.isNotEmpty() }
                if (sections.isEmpty()) Load.Error("לא ניתן לטעון מפליי כרגע") else Load.Done(sections)
            } catch (e: Exception) {
                Load.Error(errorText(e))
            }
        }
    }

    fun search(q: String = query) {
        val text = q.trim()
        if (text.isEmpty()) return
        query = text
        tab = Tab.Search
        detailStack.clear()
        searchJob?.cancel()
        results = Load.Loading
        searchJob = viewModelScope.launch {
            results = try { Load.Done(PlayApi.search(text)) } catch (e: Exception) { Load.Error(errorText(e)) }
        }
    }

    fun loadDetails(pkg: String) {
        val cur = detailsCache[pkg]
        if (cur != null && cur !is Load.Error) return
        detailsCache[pkg] = Load.Loading
        viewModelScope.launch {
            detailsCache[pkg] = try { Load.Done(PlayApi.details(pkg)) } catch (e: Exception) { Load.Error(errorText(e)) }
        }
    }

    fun open(pkg: String) { detailStack.add(pkg) }
    fun back(): Boolean = if (detailStack.isNotEmpty()) { detailStack.removeAt(detailStack.lastIndex); true } else false
    fun selectTab(t: Tab) { tab = t; detailStack.clear() }

    fun download(pkg: String, title: String, icon: String?) = downloads.start(pkg, title, icon)
    fun retry(pkg: String) = downloads.retry(pkg)
    fun remove(pkg: String) = downloads.remove(pkg)

    fun install(state: DlState.Ready) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try { Installer.install(ctx, state.path, state.xapk) }
            catch (e: Exception) { Toast.makeText(ctx, e.message ?: "ההתקנה נכשלה", Toast.LENGTH_LONG).show() }
        }
    }

    /** פותח את דף החסימה של נטפרי, שבו אפשר לשלוח את הקובץ לבדיקה */
    fun openCheckPage(url: String) {
        val ctx = getApplication<Application>()
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun errorText(e: Exception) = when (e) {
        is NetFreeBlockException -> "הגישה לדף נחסמה"
        is java.net.UnknownHostException -> "אין חיבור לאינטרנט"
        else -> e.message ?: "שגיאה"
    }
}
