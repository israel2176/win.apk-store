package app.apkstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.apkstore.data.AppSummary
import app.apkstore.data.DlState
import app.apkstore.data.DownloadItem
import app.apkstore.data.screenshotImage
import coil.compose.AsyncImage

// ================= שורת חיפוש =================

@Composable
fun SearchBar(vm: StoreViewModel, showBack: Boolean, onBack: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
        TextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            placeholder = { Text("חיפוש אפליקציות") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Icons.Default.Close, "ניקוי") }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Surface2, unfocusedContainerColor = Surface2,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

// ================= דף ראשי =================

@Composable
fun HomeScreen(vm: StoreViewModel) {
    Column {
        SearchBar(vm, showBack = false)
        when (val h = vm.home) {
            is Load.Loading -> Loading()
            is Load.Error -> CenterMessage(h.message, "נסה שוב") { vm.loadHome() }
            is Load.Done -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(h.value) { section ->
                    Text(
                        section.title, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
                    )
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(section.apps) { app -> AppTile(app) { vm.open(app.pkg) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTile(app: AppSummary, onClick: () -> Unit) {
    Column(Modifier.width(84.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppIcon(app.icon, 84.dp)
        Text(app.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        RatingText(app.score)
    }
}

// ================= תוצאות חיפוש =================

@Composable
fun SearchScreen(vm: StoreViewModel) {
    val items by vm.downloads.items.collectAsState()
    Column {
        SearchBar(vm, showBack = false)
        when (val r = vm.results) {
            null -> CenterMessage("חפשו אפליקציה לפי שם")
            is Load.Loading -> Loading()
            is Load.Error -> CenterMessage(r.message, "נסה שוב") { vm.search() }
            is Load.Done -> if (r.value.isEmpty()) CenterMessage("לא נמצאו תוצאות") else LazyColumn {
                val first = r.value.first()
                item {
                    TopResult(first, items.stateFor(first.pkg), vm)
                    HorizontalDivider()
                }
                items(r.value.drop(1)) { app -> AppRow(app) { vm.open(app.pkg) } }
            }
        }
    }
}

@Composable
private fun TopResult(app: AppSummary, state: DlState?, vm: StoreViewModel) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.clickable { vm.open(app.pkg) },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.icon, 64.dp)
            Column(Modifier.weight(1f)) {
                Text(app.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                app.developer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
                RatingText(app.score, app.installs)
            }
        }
        if (app.screenshots.isNotEmpty()) Screenshots(app.screenshots, 180)
        DownloadButton(
            state, Modifier.fillMaxWidth(),
            onDownload = { vm.download(app.pkg, app.title, app.icon) },
            onInstall = vm::install,
            onShowDownloads = { vm.selectTab(Tab.Downloads) },
        )
    }
}

@Composable
private fun Screenshots(urls: List<String>, heightDp: Int) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(urls) { u ->
            AsyncImage(
                model = screenshotImage(u, heightDp * 3),
                contentDescription = null,
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.height(heightDp.dp).clip(RoundedCornerShape(10.dp)).background(Surface2),
            )
        }
    }
}

// ================= דף אפליקציה =================

@Composable
fun AppScreen(vm: StoreViewModel, pkg: String) {
    LaunchedEffect(pkg) { vm.loadDetails(pkg) }
    val items by vm.downloads.items.collectAsState()
    Column {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
        }
        when (val d = vm.detailsCache[pkg] ?: Load.Loading) {
            is Load.Loading -> Loading()
            is Load.Error -> CenterMessage(d.message, "נסה שוב") { vm.loadDetails(pkg) }
            is Load.Done -> {
                val a = d.value
                var expanded by remember { mutableStateOf(false) }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Row(
                            Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            AppIcon(a.icon, 72.dp)
                            Column {
                                Text(a.title, style = MaterialTheme.typography.headlineSmall)
                                a.developer?.let { Text(it, color = Green, fontWeight = FontWeight.Medium) }
                                if (a.containsAds) Text("כולל פרסומות", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp)) {
                            Stat(a.score?.let { "%.1f ★".format(it) } ?: "—", "דירוג")
                            Stat(a.installs ?: "—", "הורדות")
                            Stat(a.genre ?: "—", "קטגוריה")
                        }
                        DownloadButton(
                            items.stateFor(pkg), Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            onDownload = { vm.download(pkg, a.title, a.icon) },
                            onInstall = vm::install,
                            onShowDownloads = { vm.selectTab(Tab.Downloads) },
                        )
                        Spacer(Modifier.height(20.dp))
                        if (a.screenshots.isNotEmpty()) Box(Modifier.padding(start = 20.dp)) { Screenshots(a.screenshots, 220) }
                    }
                    item {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("מידע על האפליקציה", style = MaterialTheme.typography.titleMedium)
                            val text = if (expanded) a.description ?: a.summary else a.summary ?: a.description
                            text?.let {
                                Text(
                                    it, color = Muted, maxLines = if (expanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (a.description != null) TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "פחות" else "עוד")
                            }
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface2).padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                InfoLine("גרסה", a.version ?: "—")
                                InfoLine("שם חבילה", a.pkg)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(value: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ================= הורדות =================

@Composable
fun DownloadsScreen(vm: StoreViewModel) {
    val all by vm.downloads.items.collectAsState()
    val attention = all.filter { it.state is DlState.NotChecked || it.state is DlState.Blocked || it.state is DlState.Failed }
    val active = all.filter { it.state is DlState.Preparing || it.state is DlState.Downloading }
    val ready = all.filter { it.state is DlState.Ready }

    Column {
        Text(
            "הורדות", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        )
        if (all.isEmpty()) {
            CenterMessage("עדיין לא הורדו אפליקציות")
            return
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            if (attention.isNotEmpty()) {
                item { GroupTitle("דורשות טיפול") }
                items(attention, key = { "a" + it.pkg }) { AttentionCard(it, vm) }
            }
            if (active.isNotEmpty()) {
                item { GroupTitle("בהורדה") }
                items(active, key = { "d" + it.pkg }) { ActiveRow(it, vm) }
            }
            if (ready.isNotEmpty()) {
                item { GroupTitle("מוכנות להתקנה") }
                items(ready, key = { "r" + it.pkg }) { ReadyRow(it, vm) }
            }
        }
    }
}

@Composable
private fun GroupTitle(t: String) =
    Text(t, color = Muted, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))

@Composable
private fun AttentionCard(item: DownloadItem, vm: StoreViewModel) {
    val s = item.state
    val (bg, fg, status) = when (s) {
        is DlState.NotChecked -> Triple(Color(0xFFFDF1E3), Color(0xFF7A3A05), "הקובץ לא נבדק ע״י נטפרי")
        is DlState.Blocked -> Triple(Color(0xFFFCE8E6), Color(0xFF8C1D18), "נחסם ע״י נטפרי")
        is DlState.Failed -> Triple(Surface2, Muted, s.message)
        else -> return
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppIcon(item.icon, 52.dp)
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, color = fg, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { vm.remove(item.pkg) }) { Icon(Icons.Default.Close, "הסרה מהרשימה", tint = Muted) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s is DlState.NotChecked) {
                Button(
                    onClick = { vm.openCheckPage(s.pageUrl) },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = fg),
                ) { Text("שליחה לבדיקה") }
            }
            OutlinedButton(onClick = { vm.retry(item.pkg) }) { Text("נסה שוב", color = fg) }
        }
    }
}

@Composable
private fun ActiveRow(item: DownloadItem, vm: StoreViewModel) {
    val p = (item.state as? DlState.Downloading)?.progress ?: -1f
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppIcon(item.icon, 52.dp)
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (p >= 0) "${(p * 100).toInt()}%" else "מתחיל…",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
            }
            IconButton(onClick = { vm.remove(item.pkg) }) { Icon(Icons.Default.Close, "ביטול הורדה", tint = Muted) }
        }
        if (p >= 0) LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth())
        else LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReadyRow(item: DownloadItem, vm: StoreViewModel) {
    val s = item.state as DlState.Ready
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(item.icon, 52.dp)
        Column(Modifier.weight(1f)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (s.xapk) "XAPK" else "APK", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        IconButton(onClick = { vm.remove(item.pkg) }) { Icon(Icons.Default.Close, "מחיקת הקובץ", tint = Muted, modifier = Modifier.size(20.dp)) }
        Button(onClick = { vm.install(s) }) { Text("התקנה") }
    }
}

fun List<DownloadItem>.stateFor(pkg: String): DlState? = firstOrNull { it.pkg == pkg }?.state
