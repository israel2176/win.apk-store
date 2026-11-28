package app.apkstore.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.apkstore.R
import app.apkstore.data.AppSummary
import app.apkstore.data.sizedImage
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/*
 * הדף הראשי - באותו מבנה כמו הדף הראשי של גוגל פליי:
 * שורת חיפוש, לשוניות ("בשבילך" / "קטגוריות"), קרוסלת כרטיסים גדולים,
 * ואז שורות לסירוגין: שורת אייקונים גדולים, ושורת "רשימה" (שלוש אפליקציות בכל טור, בדפדוף).
 * רקע שטוח, בלי כרטיסים צבעוניים מסביב לשורות.
 *
 * גלילה במחשב: כל שורה אופקית (HScrollRow) מציגה חיצים לדפדוף כשהעכבר מעליה (ובמסך רחב - תמיד),
 * Shift + גלגלת גוללת אותה הצידה, וגלגלת רגילה ממשיכה לגלול את הדף למטה.
 */

private val Gutter = 16.dp

@Composable
fun HomeScreen(vm: StoreViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    when (val h = vm.home) {
        is Load.Loading -> Column { SearchBar(vm); HomeTabs(tab) { tab = it }; Loading() }
        is Load.Error -> Column { SearchBar(vm); HomeTabs(tab) { tab = it }; ErrorMessage(h.message) { vm.loadHome() } }
        is Load.Done -> {
            val forYouState = rememberLazyListState()
            val categoriesState = rememberLazyListState()
            LazyColumn(
                state = if (tab == 0) forYouState else categoriesState,
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item("search") { SearchBar(vm) }
                stickyTabs(tab) { tab = it }
                if (tab == 0) forYou(h.value, vm) else categoryList(h.value, vm)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun LazyListScope.stickyTabs(tab: Int, onTab: (Int) -> Unit) =
    stickyHeader("tabs") { Surface(color = MaterialTheme.colorScheme.surface) { HomeTabs(tab, onTab) } }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HomeTabs(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        PrimaryScrollableTabRow(
            selectedTabIndex = selected,
            edgePadding = Gutter,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {},
        ) {
            listOf(R.string.tab_for_you, R.string.tab_categories).forEachIndexed { i, label ->
                Tab(
                    selected = selected == i,
                    onClick = { onSelect(i) },
                    text = { Text(stringResource(label), fontWeight = if (selected == i) FontWeight.SemiBold else FontWeight.Normal) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

// ================= "בשבילך" =================

private fun LazyListScope.forYou(sections: List<HomeSection>, vm: StoreViewModel) {
    // הכרטיסים הגדולים: האפליקציה הראשונה מכל קטגוריה שיש לה צילום מסך
    val featured = sections.mapNotNull { s -> s.apps.firstOrNull { it.screenshots.isNotEmpty() } }.distinctBy { it.pkg }
    if (featured.isNotEmpty()) item("featured") { FeaturedRow(featured, vm) }
    sections.forEachIndexed { i, s ->
        item(s.title) {
            Column(Modifier.padding(top = 20.dp)) {
                SectionHeader(s.title) { vm.search(s.query) }
                // כמו בפליי: שורת אייקונים, ואחריה שורת רשימה, לסירוגין
                if (i % 2 == 0) TileRow(s.apps, vm) else ListRow(s.apps, vm)
            }
        }
    }
}

/** כותרת שורה: שם וחץ, כל השורה לחיצה (פותחת את כל התוצאות) */
@Composable
private fun SectionHeader(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = Gutter, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(12.dp).size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** קרוסלת כרטיסים גדולים: צילום מסך כתמונה, ומתחתיו אייקון, שם ודירוג */
@Composable
private fun FeaturedRow(apps: List<AppSummary>, vm: StoreViewModel) {
    val w = LocalWindow.current
    val cardW = if (w.compact) (w.widthDp - 56.dp).coerceAtMost(400.dp) else 380.dp
    val bannerH = cardW * 0.5f
    val state = rememberLazyListState()
    HScrollRow(
        state = state, contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 16.dp),
        spacing = 12.dp, snap = true, arrowCenter = 16.dp + bannerH / 2,
    ) {
        items(apps, key = { it.pkg }) { app -> FeaturedCard(app, cardW, bannerH) { vm.open(app.pkg) } }
    }
}

@Composable
private fun FeaturedCard(app: AppSummary, width: Dp, bannerH: Dp, onClick: () -> Unit) {
    val px = with(LocalDensity.current) { width.roundToPx() }
    Column(
        Modifier.width(width).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            model = sizedImage(app.screenshots.first(), px),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxWidth().height(bannerH).clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(app.icon, 48.dp)
            Column(Modifier.weight(1f)) {
                Text(bidi(app.title), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MetaLine(app)
            }
        }
    }
}

/** שורת אייקונים גדולים, כמו "מומלצות בשבילך" בפליי */
@Composable
private fun TileRow(apps: List<AppSummary>, vm: StoreViewModel) {
    val size = if (LocalWindow.current.compact) 88.dp else 104.dp
    HScrollRow(
        contentPadding = PaddingValues(horizontal = Gutter), spacing = 12.dp,
        arrowCenter = size / 2,
    ) {
        items(apps, key = { it.pkg }) { app -> AppTile(app, size) { vm.open(app.pkg) } }
    }
}

/** שורת רשימה: שלוש אפליקציות בכל טור, דפדוף טור-טור */
@Composable
private fun ListRow(apps: List<AppSummary>, vm: StoreViewModel) {
    val w = LocalWindow.current
    val colW = if (w.compact) (w.widthDp - 64.dp).coerceAtMost(400.dp) else 340.dp
    HScrollRow(contentPadding = PaddingValues(horizontal = Gutter), spacing = 12.dp, snap = true) {
        items(apps.chunked(3), key = { it.first().pkg }) { col ->
            Column(Modifier.width(colW)) { col.forEach { app -> ListItem(app) { vm.open(app.pkg) } } }
        }
    }
}

@Composable
private fun ListItem(app: AppSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(app.icon, 56.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(bidi(app.title), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MetaLine(app)
        }
    }
}

/** "קטגוריה · 4.5★" בשורה אחת, באפור */
@Composable
private fun MetaLine(app: AppSummary) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        app.genre?.let {
            Text(
                it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (app.score != null && app.score > 0) Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Rating(app.score)
    }
}

/** אייקון גדול, ומתחתיו שם ודירוג */
@Composable
internal fun AppTile(app: AppSummary, size: Dp, onClick: () -> Unit) {
    Column(
        Modifier.width(size).clip(MaterialTheme.shapes.small).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppIcon(app.icon, size)
        Text(bidi(app.title), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(34.dp))
        Rating(app.score)
    }
}

// ================= "קטגוריות" =================

private fun LazyListScope.categoryList(sections: List<HomeSection>, vm: StoreViewModel) {
    items(sections, key = { "c" + it.title }) { s ->
        Row(
            Modifier.fillMaxWidth().clickable { vm.search(s.query) }.padding(horizontal = Gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AppIcon(s.apps.firstOrNull()?.icon, 36.dp)
            Text(s.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

// ================= שורה אופקית נוחה גם לעכבר =================

/**
 * LazyRow שאפשר לדפדף בו גם בלי מסך מגע:
 * - חיצים בשני הצדדים: מופיעים כשהעכבר מעל השורה, ובמסך רחב (מחשב / טאבלט) - תמיד.
 *   כל לחיצה מזיזה עמוד שלם, ומיישרת לפריט הבא.
 * - Shift + גלגלת: גלילה הצידה. גלגלת אופקית (משטח מגע) עובדת ממילא.
 * - גלגלת רגילה לא נתפסת כאן - הדף ממשיך לגלול למטה.
 * arrowCenter: גובה מרכז החיצים מראש השורה (למשל מרכז האייקונים); null = באמצע.
 */
@Composable
fun HScrollRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    spacing: Dp = 12.dp,
    snap: Boolean = false,
    arrowCenter: Dp? = null,
    content: LazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val wheelStep = with(LocalDensity.current) { 120.dp.toPx() }
    var hovered by remember { mutableStateOf(false) }
    val showArrows = hovered || !LocalWindow.current.compact

    fun page(forward: Boolean) = scope.launch {
        val info = state.layoutInfo
        val start = info.viewportStartOffset + info.beforeContentPadding
        val end = info.viewportEndOffset - info.afterContentPadding
        val full = info.visibleItemsInfo.filter { it.offset >= start - 1 && it.offset + it.size <= end + 1 }
        val count = full.size.coerceAtLeast(1)
        val first = state.firstVisibleItemIndex
        val target = if (forward) first + count else first - count
        state.animateScrollToItem(target.coerceIn(0, (info.totalItemsCount - 1).coerceAtLeast(0)))
    }

    Box(
        modifier.fillMaxWidth().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val c = e.changes.firstOrNull() ?: continue
                    if (c.type != PointerType.Mouse) continue
                    when (e.type) {
                        PointerEventType.Enter, PointerEventType.Move -> hovered = true
                        PointerEventType.Exit -> hovered = false
                        PointerEventType.Scroll -> {
                            val d = c.scrollDelta
                            if (d.x == 0f && d.y != 0f && e.keyboardModifiers.isShiftPressed) {
                                scope.launch { state.scrollBy(d.y * wheelStep) }
                                e.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
        },
    ) {
        LazyRow(
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            flingBehavior = if (snap) rememberSnapFlingBehavior(state) else ScrollableDefaults.flingBehavior(),
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
        val arrowMod = { a: Alignment ->
            if (arrowCenter != null) Modifier.align(if (a == Alignment.CenterStart) Alignment.TopStart else Alignment.TopEnd)
                .padding(top = (arrowCenter - 20.dp).coerceAtLeast(0.dp))
            else Modifier.align(a)
        }
        ScrollArrow(showArrows && state.canScrollBackward, Icons.AutoMirrored.Filled.KeyboardArrowLeft, R.string.scroll_back, arrowMod(Alignment.CenterStart)) { page(false) }
        ScrollArrow(showArrows && state.canScrollForward, Icons.AutoMirrored.Filled.KeyboardArrowRight, R.string.scroll_forward, arrowMod(Alignment.CenterEnd)) { page(true) }
    }
}

@Composable
private fun ScrollArrow(visible: Boolean, icon: ImageVector, label: Int, modifier: Modifier, onClick: () -> Unit) {
    AnimatedVisibility(visible, modifier.padding(horizontal = 6.dp), enter = fadeIn(), exit = fadeOut()) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 3.dp,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, stringResource(label)) }
        }
    }
}
