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
import app.apkstore.R
import app.apkstore.data.ApkPure
import app.apkstore.data.localized
import app.apkstore.data.ApkSource
import app.apkstore.data.NotOnApkPureException
import app.apkstore.data.PlayApi
import app.apkstore.data.DownloadItem
import app.apkstore.data.InstalledApps
import app.apkstore.data.sizedImage
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.flow.MutableSharedFlow
import app.apkstore.data.Review
import app.apkstore.data.ReviewSort
import app.apkstore.install.Installer
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

enum class Tab { Home, Search, Downloads }

data class HomeSection(val title: String, val query: String, val apps: List<AppSummary>)

data class ReviewsState(
    val sort: ReviewSort,
    val items: List<Review>,
    val next: String?,
    val loading: Boolean,
    val error: String? = null,
)

sealed class Load<out T> {
    data object Loading : Load<Nothing>()
    data class Done<T>(val value: T) : Load<T>()
    data class Error(val message: String) : Load<Nothing>()
}

class StoreViewModel(app: Application) : AndroidViewModel(app) {
    val downloads = Downloads(app)

    /** מה מותקן בטלפון - כדי להציג "פתיחה" / "עדכון" במקום "הורדה" */
    val installed = InstalledApps(app)

    /** הודעה בתחתית המסך (Snackbar); retryPkg != null = עם כפתור "נסה שוב" */
    data class Snack(val text: String, val retryPkg: String? = null)
    val snacks = MutableSharedFlow<Snack>(extraBufferCapacity = 4)

    var tab by mutableStateOf(Tab.Home)
    val detailStack = mutableStateListOf<String>()

    var home by mutableStateOf<Load<List<HomeSection>>>(Load.Loading)
        private set

    var query by mutableStateOf("")
    var results by mutableStateOf<Load<List<AppSummary>>?>(null)
        private set
    private var searchJob: Job? = null
    /** token לעמוד הבא של תוצאות החיפוש; null = אין עוד */
    private var searchNext: String? = null
    var loadingMore by mutableStateOf(false)
        private set
    val canLoadMore get() = searchNext != null

    /**
     * מה יש ב-APKPure לכל אפליקציה: Done(null) = אין שם קובץ בכלל.
     * נבדק כשנכנסים לדף האפליקציה, כדי להציג מראש אם אפשר להוריד ואיזו גרסה.
     */
    val apkCache = mutableStateMapOf<String, Load<ApkSource?>>()

    fun checkApk(pkg: String) {
        val cur = apkCache[pkg]
        if (cur != null && cur !is Load.Error) return
        apkCache[pkg] = Load.Loading
        viewModelScope.launch {
            apkCache[pkg] = try { Load.Done(ApkPure.resolve(pkg)) }
            catch (e: NotOnApkPureException) { Load.Done(null) }
            catch (e: Exception) { Load.Error(errorText(e)) }
        }
    }

    /** ביקורות נוספות לכל אפליקציה (מעבר ל-20 שבדף) */
    val reviewsState = mutableStateMapOf<String, ReviewsState>()

    val detailsCache = mutableStateMapOf<String, Load<AppDetails>>()

    /** שורות הדף הראשי - כל שורה היא חיפוש חי בפליי. אפשר לשנות כאן בחופשיות */
    private val homeRows get() = listOf(
        R.string.home_tools to R.string.home_tools_q,
        R.string.home_transport to R.string.home_transport_q,
        R.string.home_finance to R.string.home_finance_q,
        R.string.home_shopping to R.string.home_shopping_q,
    ).map { (t, q) -> str(t) to str(q) }

    private fun str(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    init {
        loadHome()
        // הורדה שנכשלה - מודיעים מיד, בכל מסך שבו נמצאים
        viewModelScope.launch { downloads.problems.collect { snacks.emit(problemSnack(it)) } }
    }

    private fun problemSnack(item: DownloadItem): Snack {
        val why = when (val s = item.state) {
            is DlState.Failed -> s.message
            is DlState.NotChecked -> str(R.string.nf_not_checked)
            is DlState.Blocked -> str(R.string.nf_blocked)
            else -> str(R.string.err_generic)
        }
        // לחסימת נטפרי אין טעם לנסות שוב מיד - מפנים למסך ההורדות
        // שם האפליקציה מבודד (אנגלית בתוך עברית); "נסה שוב" רק כשיש בזה טעם
        return Snack("\u2068${item.title}\u2069: $why", item.pkg.takeIf { (item.state as? DlState.Failed)?.retryable == true })
    }

    /** לחיצה על סמל השגיאה ברשימה - מציג שוב את הסיבה */
    fun showProblem(pkg: String) {
        downloads.items.value.firstOrNull { it.pkg == pkg }?.let { snacks.tryEmit(problemSnack(it)) }
    }

    fun openApp(pkg: String) {
        if (!installed.open(pkg)) Toast.makeText(getApplication(), str(R.string.cant_open), Toast.LENGTH_SHORT).show()
    }

    fun loadHome() {
        home = Load.Loading
        viewModelScope.launch {
            home = try {
                val sections = homeRows.map { (title, q) ->
                    async { HomeSection(title, q, runCatching { PlayApi.search(q).items }.getOrDefault(emptyList()).take(12)) }
                }.awaitAll().filter { it.apps.isNotEmpty() }
                if (sections.isEmpty()) Load.Error(str(R.string.err_play_load)) else Load.Done(sections)
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
        searchNext = null
        loadingMore = false
        searchJob = viewModelScope.launch {
            results = try {
                val page = PlayApi.search(text)
                searchNext = page.next
                Load.Done(page.items)
            } catch (e: Exception) { Load.Error(errorText(e)) }
        }
    }

    /** גלילה לסוף הרשימה - טוען עוד תוצאות */
    fun loadMoreResults() {
        val token = searchNext ?: return
        val cur = (results as? Load.Done)?.value ?: return
        if (loadingMore) return
        loadingMore = true
        searchJob = viewModelScope.launch {
            try {
                val page = PlayApi.searchMore(token)
                searchNext = page.next
                results = Load.Done((cur + page.items).distinctBy { it.pkg })
            } catch (e: Exception) {
                searchNext = null
            } finally { loadingMore = false }
        }
    }

    /** ביקורות: החלפת מיון או טעינת עמוד נוסף */
    fun loadReviews(pkg: String, sort: ReviewSort, more: Boolean) {
        val cur = reviewsState[pkg]
        if (cur?.loading == true) return
        val token = if (more && cur?.sort == sort) cur.next ?: return else null
        val base = if (more && cur?.sort == sort) cur.items else emptyList()
        reviewsState[pkg] = ReviewsState(sort, base, cur?.next, loading = true)
        viewModelScope.launch {
            reviewsState[pkg] = try {
                val page = PlayApi.reviews(pkg, sort, token)
                ReviewsState(sort, (base + page.items).distinctBy { it.id }, page.next, loading = false)
            } catch (e: Exception) {
                ReviewsState(sort, base, null, loading = false, error = errorText(e))
            }
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
        val item = downloads.items.value.firstOrNull { it.state == state } ?: return
        viewModelScope.launch {
            try {
                // האייקון מפליי, כדי שמסך ההתקנה של המערכת יציג אותו עם השם
                val icon = runCatching {
                    val req = ImageRequest.Builder(ctx).data(sizedImage(item.icon, 192)).allowHardware(false).build()
                    (ctx.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap()
                }.getOrNull()
                Installer.install(ctx, state.path, state.xapk, item.pkg, item.title, icon)
            } catch (e: Exception) { Toast.makeText(ctx, e.localized(ctx), Toast.LENGTH_LONG).show() }
        }
    }

    /** פותח את דף החסימה של נטפרי, שבו אפשר לשלוח את הקובץ לבדיקה */
    fun openCheckPage(url: String) = openUrl(url)

    /** פותח קישור (אתר, מדיניות פרטיות, mailto:) באפליקציה המתאימה */
    fun openUrl(url: String) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(ctx, str(R.string.err_no_app_for_link), Toast.LENGTH_SHORT).show() }
    }

    private fun errorText(e: Exception) = e.localized(getApplication())
}
