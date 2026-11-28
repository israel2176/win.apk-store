package app.apkstore.ui

import app.apkstore.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.apkstore.data.AppDetails
import app.apkstore.data.AppSummary
import app.apkstore.data.DlState
import app.apkstore.data.DownloadItem

private val Gutter = 16.dp

// ================= שורת חיפוש =================

@Composable
fun SearchBar(vm: StoreViewModel, autoFocus: Boolean = false, hPad: Dp = Gutter) {
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    TextField(
        value = vm.query,
        onValueChange = { vm.query = it },
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.clear)) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { fm.clearFocus(); vm.search() }),
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)
            .padding(horizontal = hPad, vertical = 10.dp).focusRequester(focus),
    )
}

private fun LazyGridScope.fullLine(key: Any? = null, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

// ================= תוצאות חיפוש =================

@Composable
fun SearchScreen(vm: StoreViewModel) {
    val items by vm.downloads.items.collectAsState()
    val installed by vm.installed.state.collectAsState()
    val w = LocalWindow.current
    Column(Modifier.fillMaxSize()) {
        SearchBar(vm, autoFocus = vm.results == null)
        when (val r = vm.results) {
            null -> CenterMessage(Icons.Default.Search)
            is Load.Loading -> Loading()
            is Load.Error -> ErrorMessage(r.message) { vm.search() }
            is Load.Done -> if (r.value.isEmpty()) CenterMessage(Icons.Default.Search, stringResource(R.string.no_results)) else LazyVerticalGrid(
                columns = GridCells.Adaptive(if (w.compact) 10_000.dp else 360.dp),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = Gutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val first = r.value.first()
                fullLine(first.pkg) { TopResult(first, items.stateFor(first.pkg), vm) }
                items(r.value.drop(1), key = { it.pkg }) { app ->
                    LaunchedEffect(app.pkg) { vm.installed.track(app.pkg) }
                    AppRow(
                        app, items.stateFor(app.pkg),
                        onClick = { vm.open(app.pkg) },
                        onDownload = { vm.download(app.pkg, app.title, app.icon) },
                        onInstall = vm::install,
                        onShowDownloads = { vm.selectTab(Tab.Downloads) },
                        installed = installed[app.pkg],
                        onOpen = { vm.openApp(app.pkg) },
                        onProblem = { vm.showProblem(app.pkg) },
                        installing = vm.installing[app.pkg],
                    )
                }
                // סוף הרשימה: טוען עוד תוצאות (כמו גלילה בפליי)
                if (vm.canLoadMore) fullLine("more") {
                    LaunchedEffect(r.value.size) { vm.loadMoreResults() }
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }
        }
    }
}

/** התוצאה הראשונה - כרטיס גדול עם צילומי מסך */
@Composable
private fun TopResult(app: AppSummary, state: DlState?, vm: StoreViewModel) {
    val w = LocalWindow.current
    val inst = vm.installed.state.collectAsState().value[app.pkg]
    LaunchedEffect(app.pkg) { vm.installed.track(app.pkg) }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = Gutter),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { vm.open(app.pkg) }.padding(horizontal = Gutter),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.icon, 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(bidi(app.title), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                app.developer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Rating(app.score)
                    app.installs?.let { Stat(DownloadIcon, it) }
                }
            }
            // ברוחב - הכפתור בצד; בטלפון - מתחת
            if (!w.compact) DownloadButton(
                state, Modifier.widthIn(min = 160.dp), price = app.price, installed = inst, installing = vm.installing[app.pkg], onOpen = { vm.openApp(app.pkg) }, onRetry = { vm.retry(app.pkg) },
                onDownload = { vm.download(app.pkg, app.title, app.icon) },
                onInstall = vm::install,
                onShowDownloads = { vm.selectTab(Tab.Downloads) },
            )
        }
        if (app.screenshots.isNotEmpty()) Screenshots(app.screenshots, w.shotHeight * 0.75f, Gutter)
        if (w.compact) DownloadButton(
            state, Modifier.fillMaxWidth().padding(horizontal = Gutter), price = app.price, installed = inst, installing = vm.installing[app.pkg], onOpen = { vm.openApp(app.pkg) }, onRetry = { vm.retry(app.pkg) },
            onDownload = { vm.download(app.pkg, app.title, app.icon) },
            onInstall = vm::install,
            onShowDownloads = { vm.selectTab(Tab.Downloads) },
        )
    }
}

@Composable
internal fun Stat(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ================= הורדות =================

@Composable
fun DownloadsScreen(vm: StoreViewModel) {
    val all by vm.downloads.items.collectAsState()
    val w = LocalWindow.current
    if (all.isEmpty()) { CenterMessage(DownloadIcon); return }

    val attention = all.filter { it.state is DlState.NotChecked || it.state is DlState.Blocked || it.state is DlState.Failed }
    val active = all.filter { it.state is DlState.Preparing || it.state is DlState.Downloading }
    val ready = all.filter { it.state is DlState.Ready }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (w.compact) 10_000.dp else 380.dp),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = Gutter),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (attention.isNotEmpty()) {
            fullLine("ha") { GroupTitle(ShieldIcon, attention.size) }
            items(attention, key = { "a" + it.pkg }) { AttentionCard(it, vm) }
        }
        if (active.isNotEmpty()) {
            fullLine("hd") { GroupTitle(DownloadIcon, active.size) }
            items(active, key = { "d" + it.pkg }) { ActiveCard(it, vm) }
        }
        if (ready.isNotEmpty()) {
            fullLine("hr") { GroupTitle(InstallIcon, ready.size) }
            items(ready, key = { "r" + it.pkg }) { ReadyCard(it, vm) }
        }
    }
}

/** כותרת קבוצה: אייקון ומספר */
@Composable
private fun GroupTitle(icon: ImageVector, count: Int) {
    Row(Modifier.padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text("$count", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CardShell(bg: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(bg).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

@Composable
private fun CardHeader(item: DownloadItem, sub: String?, subColor: Color, onRemove: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AppIcon(item.icon, 52.dp)
        Column(Modifier.weight(1f)) {
            Text(bidi(item.title), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            sub?.let { Text(it, color = subColor, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
        }
        trailing()
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AttentionCard(item: DownloadItem, vm: StoreViewModel) {
    val cs = MaterialTheme.colorScheme
    val s = item.state
    val (bg, fg, status) = when (s) {
        is DlState.NotChecked -> Triple(cs.tertiaryContainer, cs.onTertiaryContainer, stringResource(R.string.nf_not_checked))
        is DlState.Blocked -> Triple(cs.errorContainer, cs.onErrorContainer, stringResource(R.string.nf_blocked))
        is DlState.Failed -> Triple(cs.surfaceContainerLow, cs.onSurfaceVariant, s.message)
        else -> return
    }
    CardShell(bg) {
        CardHeader(item, status, fg, onRemove = { vm.remove(item.pkg) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s is DlState.NotChecked) {
                Button(onClick = { vm.openCheckPage(s.pageUrl) }, colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg)) {
                    Icon(ShieldIcon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.nf_send))
                }
            }
            OutlinedButton(onClick = { vm.retry(item.pkg) }) { Icon(Icons.Default.Refresh, stringResource(R.string.retry), tint = fg) }
        }
    }
}

@Composable
private fun ActiveCard(item: DownloadItem, vm: StoreViewModel) {
    val p = (item.state as? DlState.Downloading)?.progress ?: -1f
    CardShell(MaterialTheme.colorScheme.surfaceContainerLow) {
        CardHeader(item, if (p >= 0) "${(p * 100).toInt()}%" else null, MaterialTheme.colorScheme.onSurfaceVariant, onRemove = { vm.remove(item.pkg) })
        if (p >= 0) LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().clip(CircleShape))
        else LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
    }
}

@Composable
private fun ReadyCard(item: DownloadItem, vm: StoreViewModel) {
    val s = item.state as DlState.Ready
    CardShell(MaterialTheme.colorScheme.surfaceContainerLow) {
        val inst = vm.installed.state.collectAsState().value[item.pkg]
        LaunchedEffect(item.pkg) { vm.installed.track(item.pkg) }
        CardHeader(item, listOfNotNull(s.version?.let { "\u2066v$it\u2069" }, if (s.xapk) "XAPK" else "APK").joinToString(" · "), MaterialTheme.colorScheme.onSurfaceVariant, onRemove = { vm.remove(item.pkg) }) {
            // מוכן להתקנה / עדכון, או - אם כבר הותקן - פתיחה
            DownloadIconButton(s, onDownload = {}, onInstall = vm::install, onShowDownloads = {}, installed = inst, onOpen = { vm.openApp(item.pkg) }, installing = vm.installing[item.pkg])
        }
    }
}

fun List<DownloadItem>.stateFor(pkg: String): DlState? = firstOrNull { it.pkg == pkg }?.state
