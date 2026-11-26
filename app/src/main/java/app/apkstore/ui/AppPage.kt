package app.apkstore.ui

import app.apkstore.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.apkstore.data.AppDetails
import app.apkstore.data.Freshness
import app.apkstore.data.Versions
import app.apkstore.data.Review
import app.apkstore.data.ReviewSort
import app.apkstore.data.sizedImage
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val Pad = 20.dp
/** טקסט ארוך (תיאור, ביקורת) - הכיוון לפי התוכן: אנגלית משמאל לימין, עברית מימין לשמאל */
private val ContentDir = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content)
private val HE get() = Locale.getDefault() // שפת המערכת

private fun count(n: Long) = "%,d".format(n)
/** בידוד כיוון משמאל לימין - כדי ש-"Android 10+" לא יוצג הפוך */
private fun ltr(s: String) = "\u2066$s\u2069"
private fun date(ms: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM, HE).format(Date(ms))

// ================= דף אפליקציה =================

@Composable
fun AppScreen(vm: StoreViewModel, pkg: String) {
    LaunchedEffect(pkg) { vm.loadDetails(pkg) }
    // בודקים מראש מה יש ב-APKPure (רק לאפליקציה חינמית שכבר יצאה)
    val inst = vm.installed.state.collectAsState().value[pkg]
    LaunchedEffect(pkg) { vm.installed.track(pkg) }
    val apk = (vm.apkCache[pkg] as? Load.Done)?.value
    val loaded = (vm.detailsCache[pkg] as? Load.Done)?.value
    LaunchedEffect(pkg, loaded != null) { if (loaded != null && loaded.free && !loaded.preregister) vm.checkApk(pkg) }
    val items by vm.downloads.items.collectAsState()
    val w = LocalWindow.current
    Column(Modifier.fillMaxSize()) {
        IconButton(onClick = { vm.back() }, Modifier.padding(4.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        when (val d = vm.detailsCache[pkg] ?: Load.Loading) {
            is Load.Loading -> Loading()
            is Load.Error -> ErrorMessage(d.message) { vm.loadDetails(pkg) }
            is Load.Done -> {
                val a = d.value
                val button: @Composable (Modifier) -> Unit = { mod ->
                    DownloadButton(
                        items.stateFor(pkg), mod, price = a.price, preregister = a.preregister,
                        installed = inst,
                        installing = vm.installing[pkg],
                        // יש עדכון: הגרסה ב-APKPure חדשה מהמותקנת
                        updateAvailable = inst != null && apk?.versionCode?.let { it > inst.versionCode } == true,
                        onOpen = { vm.openApp(pkg) },
                        onRetry = { vm.retry(pkg) },
                        unavailable = (vm.apkCache[pkg] as? Load.Done)?.value == null && vm.apkCache[pkg] is Load.Done,
                        onDownload = { vm.download(pkg, a.title, a.icon) },
                        onInstall = vm::install,
                        onShowDownloads = { vm.selectTab(Tab.Downloads) },
                    )
                }
                if (w.twoPane) {
                    // שני טורים: מימין זהות, כפתור, פרטים ומפתח; משמאל כל התוכן
                    Row(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.width(if (w.width == WidthClass.Expanded) 380.dp else 300.dp).fillMaxHeight()
                                .verticalScroll(rememberScrollState()).padding(horizontal = Pad),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            AppHeader(a, vertical = true)
                            StatsRow(a)
                            button(Modifier.fillMaxWidth())
                            SourceInfo(a, vm)
                            DetailsPanel(a)
                            DeveloperPanel(a, vm)
                            Spacer(Modifier.height(Pad))
                        }
                        LazyColumn(
                            Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = PaddingValues(end = Pad, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            a.video?.let { v -> item { VideoLink(v, vm) } }
                            if (a.screenshots.isNotEmpty()) item { Screenshots(a.screenshots, w.shotHeight) }
                            contentSections(a, vm, padded = false)
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { Box(Modifier.padding(horizontal = Pad)) { AppHeader(a, vertical = false) } }
                        item { Box(Modifier.padding(horizontal = 12.dp)) { StatsRow(a) } }
                        item {
                            Column(Modifier.padding(horizontal = Pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                button(Modifier.fillMaxWidth())
                                SourceInfo(a, vm)
                            }
                        }
                        a.video?.let { v -> item { Box(Modifier.padding(horizontal = Pad)) { VideoLink(v, vm) } } }
                        if (a.screenshots.isNotEmpty()) item { Screenshots(a.screenshots, w.shotHeight, Pad) }
                        contentSections(a, vm, padded = true)
                        item { Box(Modifier.padding(horizontal = Pad)) { DetailsPanel(a) } }
                        item { Box(Modifier.padding(horizontal = Pad)) { DeveloperPanel(a, vm) } }
                    }
                }
            }
        }
    }
}

/** האזורים המשותפים לשתי הפריסות, לפי הסדר */
private fun LazyListScope.contentSections(a: AppDetails, vm: StoreViewModel, padded: Boolean) {
    val m = if (padded) Modifier.padding(horizontal = Pad) else Modifier
    item { Box(m) { About(a) } }
    a.whatsNew?.let { text -> item { Box(m) { WhatsNewPanel(text, a.updated) } } }
    if (a.histogram.isNotEmpty() || a.reviews.isNotEmpty()) item { Box(m) { RatingsPanel(a, vm) } }
    if (a.dataSafety.isNotEmpty()) item { Box(m) { DataSafetyPanel(a) } }
    if (a.permissions.isNotEmpty()) item { Box(m) { PermissionsPanel(a) } }
    if (a.similar.isNotEmpty()) item { SimilarPanel(a, vm, padded) }
}

// ================= מקור ההורדה (APKPure) =================

private fun mb(bytes: Long) = ltr("%.1f MB".format(bytes / 1_048_576.0))

/**
 * מה יש ב-APKPure, מתחת לכפתור ההורדה:
 * - אין שם קובץ בכלל: הודעה אדומה, והכפתור "לא זמין להורדה"
 * - יש, אבל גרסה ישנה יותר מבפליי: הודעה כתומה עם שתי הגרסאות (או התאריכים)
 * - יש ומעודכן: שורה שקטה עם גרסה, גודל, סוג ותאריך
 */
@Composable
private fun SourceInfo(a: AppDetails, vm: StoreViewModel) {
    if (!a.free || a.preregister) return
    val cs = MaterialTheme.colorScheme
    when (val s = vm.apkCache[a.pkg]) {
        null, is Load.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.apk_checking), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        is Load.Error -> Note(Icons.Default.Warning, cs.surfaceContainerHigh, cs.onSurfaceVariant,
            stringResource(R.string.apk_check_failed), s.message)
        is Load.Done -> {
            val src = s.value
            if (src == null) {
                Note(Icons.Default.Warning, cs.errorContainer, cs.onErrorContainer,
                    stringResource(R.string.apk_missing_title),
                    stringResource(R.string.apk_missing_body))
                return
            }
            val facts = listOfNotNull(
                src.versionName?.let { ltr("v$it") },
                src.size?.let(::mb),
                if (src.isXapk) "XAPK" else "APK",
                src.updatedAt?.let(::date),
            ).joinToString(" · ") { "\u2068$it\u2069" } // כל פרט מבודד, כדי שהתאריך לא יתערבב
            when (Versions.freshness(src, a.version, a.updatedAt)) {
                Freshness.Older -> Note(
                    Icons.Default.Warning, cs.tertiaryContainer, cs.onTertiaryContainer,
                    stringResource(R.string.apk_older_title),
                    if (a.version != null && src.versionName != null)
                        stringResource(R.string.apk_older_versions, ltr("v" + src.versionName), ltr("v" + a.version))
                    else stringResource(R.string.apk_older_dates, src.updatedAt?.let(::date) ?: "?", a.updated ?: "?"),
                )
                Freshness.Newer -> IconLine(Icons.Default.Check, stringResource(R.string.apk_newer, facts))
                else -> IconLine(Icons.Default.Check, facts)
            }
        }
    }
}

@Composable
private fun Note(icon: ImageVector, bg: Color, fg: Color, title: String, body: String?) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(bg).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = fg)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            body?.let { Text(it, color = fg, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** סרטון האפליקציה (יוטיוב) - נפתח באפליקציה החיצונית */
@Composable
private fun VideoLink(url: String, vm: StoreViewModel) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).clickable { vm.openUrl(url) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(stringResource(R.string.video), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

// ================= ראש הדף =================

@Composable
private fun AppHeader(a: AppDetails, vertical: Boolean) {
    val texts: @Composable ColumnScope.() -> Unit = {
        Text(bidi(a.title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
            textAlign = if (vertical) TextAlign.Center else TextAlign.Start)
        a.developer?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) }
    }
    if (vertical) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppIcon(a.icon, 96.dp); texts()
    } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        AppIcon(a.icon, 76.dp); Column(content = texts)
    }
}

/** נתונים כאייקון + ערך, בלי כותרות */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsRow(a: AppDetails) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        a.score?.takeIf { it > 0 }?.let { s ->
            StatChip(Icons.Default.Star, "%.1f".format(s), MaterialTheme.colorScheme.tertiary)
        }
        a.reviewsCount?.let { StatChip(Icons.Default.Person, count(it)) }
        a.installs?.let { StatChip(DownloadIcon, it) }
        a.contentRating?.let { StatChip(Icons.Default.Face, it) }
        a.genre?.let { StatChip(null, it) }
        if (a.containsAds) StatChip(Icons.Default.Info, stringResource(R.string.ads))
        if (a.inAppPurchases) StatChip(Icons.Default.ShoppingCart, stringResource(R.string.purchases))
    }
}

@Composable
private fun StatChip(icon: ImageVector?, text: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(16.dp), tint = tint) }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

// ================= תיאור =================

/** תיאור: מקופל עם חץ לפתיחה */
@Composable
private fun About(a: AppDetails) {
    var expanded by rememberSaveable(a.pkg) { mutableStateOf(false) }
    val text = (if (expanded) a.description ?: a.summary else a.summary ?: a.description) ?: return
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(enabled = a.description != null) { expanded = !expanded }
            .animateContentSize().padding(vertical = 4.dp),
    ) {
        Text(text, style = ContentDir, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
        if (a.description != null) ExpandArrow(expanded, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ExpandArrow(expanded: Boolean, modifier: Modifier = Modifier) = Icon(
    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null,
    modifier, tint = MaterialTheme.colorScheme.primary,
)

// ================= לוח (כרטיס עם כותרת) =================

/**
 * כרטיס אזור: אייקון בעיגול, כותרת קצרה, ותוכן.
 * כשהוא מתקפל - לחיצה על הכותרת פותחת וסוגרת.
 */
@Composable
private fun Panel(
    icon: ImageVector,
    title: String,
    trailing: String? = null,
    collapsible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(!collapsible) }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .animateContentSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().then(if (collapsible) Modifier.clip(MaterialTheme.shapes.small).clickable { open = !open } else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (collapsible) ExpandArrow(open)
        }
        if (open) content()
    }
}

// ================= מה חדש =================

@Composable
private fun WhatsNewPanel(text: String, updated: String?) {
    Panel(Icons.Default.Notifications, stringResource(R.string.whats_new), trailing = updated) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        Text(
            text, style = ContentDir, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { expanded = !expanded },
        )
    }
}

// ================= דירוגים וביקורות =================

@Composable
private fun Stars(score: Double, size: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        repeat(5) { i ->
            Icon(
                Icons.Default.Star, null, Modifier.size(size),
                tint = if (score >= i + 0.5) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun RatingsPanel(a: AppDetails, vm: StoreViewModel) {
    Panel(Icons.Default.Star, stringResource(R.string.ratings_reviews)) {
        if (a.histogram.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(a.score?.let { "%.1f".format(it) } ?: "—", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Medium)
                    Stars(a.score ?: 0.0, 14.dp)
                    a.ratingsCount?.let {
                        Stat(Icons.Default.Person, count(it))
                    }
                }
                // התפלגות: 5 כוכבים למעלה
                val max = a.histogram.max().coerceAtLeast(1)
                Column(Modifier.weight(1f, fill = false).widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (stars in 5 downTo 1) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$stars", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LinearProgressIndicator(
                                progress = { a.histogram[stars - 1].toFloat() / max },
                                modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                strokeCap = StrokeCap.Round,
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                        }
                    }
                }
            }
        }
        if (a.reviews.isNotEmpty()) ReviewsList(a, vm)
    }
}

@Composable
private fun ReviewCard(r: Review) {
    var expanded by rememberSaveable(r.author + r.time) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable { expanded = !expanded }.animateContentSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = sizedImage(r.avatar, with(androidx.compose.ui.platform.LocalDensity.current) { 32.dp.roundToPx() }), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            )
            Column(Modifier.weight(1f)) {
                Text(bidi(r.author), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stars(r.score.toDouble(), 12.dp)
                    r.time?.let { Text(date(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        Text(
            r.text, style = MaterialTheme.typography.bodyMedium.merge(ContentDir), color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
        )
        r.reply?.let { reply ->
            Text(
                reply, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer).padding(10.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (r.likes > 0) Stat(Icons.Default.ThumbUp, "${r.likes}")
            r.appVersion?.let { Text("v$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/**
 * ביקורות: בהתחלה 3 מתוך אלה שבדף. אחרי לחיצה - כל השאר, ואז טעינת עמודים נוספים מפליי
 * (batchexecute oCPfdb, כמו reviews() בספריות), עם מיון: רלוונטיות / חדשות / דירוג.
 */
@Composable
private fun ReviewsList(a: AppDetails, vm: StoreViewModel) {
    val st = vm.reviewsState[a.pkg]
    var open by rememberSaveable(a.pkg) { mutableStateOf(false) }
    if (open) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ReviewSort.Relevant to stringResource(R.string.sort_relevant), ReviewSort.Newest to stringResource(R.string.sort_newest), ReviewSort.Rating to stringResource(R.string.sort_rating)).forEach { (sort, label) ->
            val selected = (st?.sort ?: ReviewSort.Relevant) == sort && (st != null || sort == ReviewSort.Relevant)
            FilterChip(
                selected = selected, onClick = { vm.loadReviews(a.pkg, sort, more = false) },
                label = { Text(label) }, shape = CircleShape,
            )
        }
    }
    val list = st?.items?.takeIf { it.isNotEmpty() || st.loading.not() } ?: a.reviews
    val shown = if (open) list else list.take(3)
    shown.forEach { ReviewCard(it) }
    st?.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    val more = !open && list.size > 3 || open && (st == null || st.next != null)
    if (st?.loading == true) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
    } else if (more) Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable {
            if (!open) open = true
            else vm.loadReviews(a.pkg, st?.sort ?: ReviewSort.Relevant, more = st != null)
        }.padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!open) Text("+${list.size - 3}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            ExpandArrow(false)
        }
    }
}

// ================= בטיחות נתונים והרשאות =================

@Composable
private fun DataSafetyPanel(a: AppDetails) {
    Panel(ShieldIcon, stringResource(R.string.data_safety), collapsible = true) {
        a.dataSafety.forEach { IconLine(ShieldIcon, it) }
        a.privacyPolicy?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun PermissionsPanel(a: AppDetails) {
    Panel(Icons.Default.Settings, stringResource(R.string.permissions), trailing = "${a.permissions.sumOf { it.items.size }}", collapsible = true) {
        a.permissions.forEach { g ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(g.name.ifBlank { stringResource(R.string.permissions_other) }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                g.items.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

// ================= פרטים ומפתח =================

@Composable
private fun DetailsPanel(a: AppDetails) {
    Panel(Icons.Default.Info, stringResource(R.string.details)) {
        a.updated?.let { IconLine(Icons.Default.DateRange, it) }
        a.version?.let { IconLine(Icons.AutoMirrored.Filled.List, ltr("v$it")) }
        a.minAndroid?.let { v ->
            // כמו normalizeAndroidVersion: מספר -> "Android 8.0+", אחרת הטקסט כמו שהוא ("משתנה בהתאם למכשיר")
            val n = v.substringBefore(' ')
            IconLine(Icons.Default.Build, if (n.toDoubleOrNull() != null) ltr("Android $n+") else v)
        }
        a.released?.let { IconLine(Icons.Default.Star, it) }
        a.iapRange?.let { IconLine(Icons.Default.ShoppingCart, it) }
        if (a.categories.size > 1) IconLine(Icons.Default.Home, a.categories.joinToString(" · "))
        a.developerLegalName?.takeIf { it != a.developer }?.let { IconLine(Icons.Default.Person, it) }
        (a.installsExact?.let(::count) ?: a.installs)?.let { IconLine(DownloadIcon, it) }
        a.contentRating?.let { IconLine(Icons.Default.Face, listOfNotNull(it, a.contentRatingNote).joinToString(" · ")) }
        IconLine(Icons.Default.Lock, a.pkg)
    }
}

@Composable
private fun DeveloperPanel(a: AppDetails, vm: StoreViewModel) {
    val links = listOfNotNull(
        a.developerWebsite?.let { Triple(Icons.Default.Home, it.removePrefix("https://").removePrefix("http://").trimEnd('/'), it) },
        a.developerEmail?.let { Triple(Icons.Default.Email, it, "mailto:$it") },
        a.privacyPolicy?.let { Triple(ShieldIcon, stringResource(R.string.privacy_policy), it) },
    )
    if (links.isEmpty() && a.developerAddress == null) return
    Panel(Icons.Default.Person, a.developer ?: stringResource(R.string.developer)) {
        links.forEach { (icon, text, url) -> IconLine(icon, text, link = true) { vm.openUrl(url) } }
        a.developerAddress?.let { IconLine(Icons.Default.Place, it) }
    }
}

@Composable
private fun IconLine(icon: ImageVector, text: String, link: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text, style = MaterialTheme.typography.bodyMedium,
            color = if (link) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ================= אפליקציות דומות =================

@Composable
private fun SimilarPanel(a: AppDetails, vm: StoreViewModel, padded: Boolean) {
    val w = LocalWindow.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.padding(horizontal = if (padded) Pad else 0.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) { SectionTitle(stringResource(R.string.similar_apps)) }
        LazyRow(
            contentPadding = PaddingValues(horizontal = if (padded) Pad else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(a.similar, key = { it.pkg }) { app -> AppTile(app, w.tileSize) { vm.open(app.pkg) } }
        }
    }
}
