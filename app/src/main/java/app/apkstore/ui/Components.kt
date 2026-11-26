package app.apkstore.ui

import app.apkstore.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.apkstore.data.AppSummary
import app.apkstore.data.InstalledApp
import app.apkstore.data.Versions
import app.apkstore.data.DlState
import app.apkstore.data.screenshotImage
import app.apkstore.data.sizedImage
import coil.compose.AsyncImage

// ================= אייקונים =================

private fun icon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black), pathBuilder = block).build()

val DownloadIcon: ImageVector by lazy {
    icon("download") {
        moveTo(5f, 20f); horizontalLineToRelative(14f); verticalLineToRelative(-2f); horizontalLineTo(5f); close()
        moveTo(19f, 9f); horizontalLineToRelative(-4f); verticalLineTo(3f); horizontalLineTo(9f)
        verticalLineToRelative(6f); horizontalLineTo(5f); lineToRelative(7f, 7f); close()
    }
}

/** טלפון עם חץ פנימה - "התקנה" (שונה מחץ ההורדה) */
val InstallIcon: ImageVector by lazy {
    ImageVector.Builder("install", 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd) {
        moveTo(8f, 1f); horizontalLineTo(16f); curveTo(17.1f, 1f, 18f, 1.9f, 18f, 3f); verticalLineTo(21f)
        curveTo(18f, 22.1f, 17.1f, 23f, 16f, 23f); horizontalLineTo(8f); curveTo(6.9f, 23f, 6f, 22.1f, 6f, 21f); verticalLineTo(3f)
        curveTo(6f, 1.9f, 6.9f, 1f, 8f, 1f); close()
        moveTo(8f, 4f); verticalLineTo(19f); horizontalLineTo(16f); verticalLineTo(4f); close()
        moveTo(11f, 6f); horizontalLineTo(13f); verticalLineTo(11f); horizontalLineTo(15.5f); lineTo(12f, 15f); lineTo(8.5f, 11f); horizontalLineTo(11f); close()
    }.build()
}

/** ריבוע עם חץ החוצה - "פתיחה" */
val OpenIcon: ImageVector by lazy {
    icon("open") {
        moveTo(19f, 19f); horizontalLineTo(5f); verticalLineTo(5f); horizontalLineToRelative(7f); verticalLineTo(3f); horizontalLineTo(5f)
        curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f); verticalLineToRelative(14f); curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
        horizontalLineToRelative(14f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineToRelative(-7f); horizontalLineToRelative(-2f); close()
        moveTo(14f, 3f); verticalLineToRelative(2f); horizontalLineToRelative(3.59f); lineToRelative(-9.83f, 9.83f); lineToRelative(1.41f, 1.41f)
        lineTo(19f, 6.41f); verticalLineTo(10f); horizontalLineToRelative(2f); verticalLineTo(3f); close()
    }
}

/** מגן - חסימה / בדיקה של נטפרי */
val ShieldIcon: ImageVector by lazy {
    icon("shield") {
        moveTo(12f, 1f); lineTo(3f, 5f); verticalLineToRelative(6f)
        curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f); curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f)
        verticalLineTo(5f); close()
    }
}

// ================= כיווניות =================

/**
 * בידוד כיוון לפי התו החזק הראשון (FSI): כותרת באנגלית בתוך ממשק בעברית
 * נשארת מיושרת לימין, אבל הסימנים שלה ("!", ":") נשארים במקום הנכון.
 * כותרת שיש בה עברית (גם אם מעורבת) נשארת כמו שהיא - בכיוון הרגיל של הממשק.
 */
fun bidi(s: String) = if (s.any { it in '\u0590'..'\u06FF' }) s else "\u2068$s\u2069"

// ================= אייקון אפליקציה ודירוג =================

@Composable
fun AppIcon(url: String?, size: Dp) {
    // בדיוק לפי צפיפות המסך - כדי שלא יהיה מטושטש במסכים צפופים (טאבלטים, מסכים גדולים)
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(48, 1024)
    AsyncImage(
        model = sizedImage(url, px),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            // עיגול חזק יותר מאשר בחנות של גוגל - "סקוויקרל"
            .clip(RoundedCornerShape(size * 0.3f))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

/** ★ 4.5 - כוכב בצבע ענבר ומספר */
@Composable
fun Rating(score: Double?, modifier: Modifier = Modifier) {
    if (score == null || score <= 0) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("%.1f".format(score), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Default.Star, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.tertiary)
    }
}

/** שורת אפליקציה ברשימה: אייקון, שם, מפתח, דירוג, וכפתור הורדה עגול */
@Composable
fun AppRow(
    app: AppSummary, state: DlState?, onClick: () -> Unit, onDownload: () -> Unit, onInstall: (DlState.Ready) -> Unit, onShowDownloads: () -> Unit,
    installed: InstalledApp? = null, onOpen: () -> Unit = {}, onProblem: () -> Unit = {}, installing: Float? = null,
) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(app.icon, 56.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(bidi(app.title), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            app.developer?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Rating(app.score)
                app.genre?.let { GenrePill(it) }
            }
        }
        // אפליקציה בתשלום: מציגים את המחיר ולא מורידים
        if (app.price != null && installed == null) PricePill(app.price) else DownloadIconButton(state, onDownload, onInstall, onShowDownloads, installed, onOpen, onProblem, installing)
    }
}

@Composable
fun GenrePill(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// ================= כפתורי הורדה =================

/**
 * מה הכפתור מציע, לפי מצב ההורדה ומה שמותקן בטלפון:
 * הורדה → (מוריד) → התקנה → (מתקין) → פתיחה. אם מותקנת גרסה ישנה: עדכון.
 * installing: ההתקנה כבר התחילה ועוד לא נגמרה - הכפתור נעול עד הסוף.
 */
enum class Act { Download, Progress, NetFree, Failed, Install, UpdateInstall, Installing, UpdateDownload, Open }

fun actOf(state: DlState?, installed: InstalledApp?, updateAvailable: Boolean = false, installing: Boolean = false): Act = if (installing) Act.Installing else when (state) {
    is DlState.Preparing, is DlState.Downloading -> Act.Progress
    is DlState.NotChecked, is DlState.Blocked -> Act.NetFree
    is DlState.Failed -> if (installed != null) Act.Open else Act.Failed
    is DlState.Ready -> when {
        installed == null -> Act.Install
        // הקובץ שירד חדש מהמותקן (או שאי אפשר להשוות) - מציעים לעדכן; אחרת כבר מותקן
        (state.version?.let { v -> installed.versionName?.let { Versions.compare(v, it) } } ?: 1) > 0 -> Act.UpdateInstall
        else -> Act.Open
    }
    null -> when {
        installed == null -> Act.Download
        updateAvailable -> Act.UpdateDownload
        else -> Act.Open
    }
}

/** כפתור עגול בלי טקסט - לרשימות */
@Composable
fun DownloadIconButton(
    state: DlState?,
    onDownload: () -> Unit,
    onInstall: (DlState.Ready) -> Unit,
    onShowDownloads: () -> Unit,
    installed: InstalledApp? = null,
    onOpen: () -> Unit = {},
    onProblem: () -> Unit = {},
    installing: Float? = null,
) {
    val cs = MaterialTheme.colorScheme
    val filled = IconButtonDefaults.filledTonalIconButtonColors(containerColor = cs.primary, contentColor = cs.onPrimary)
    when (actOf(state, installed, installing = installing != null)) {
        Act.Install, Act.UpdateInstall -> FilledTonalIconButton(onClick = { onInstall(state as DlState.Ready) }, colors = filled) {
            Icon(if (installed != null) Icons.Default.Refresh else InstallIcon, stringResource(if (installed != null) R.string.update else R.string.install))
        }
        // מתקין: הכפתור נעול, ובתוכו עיגול התקדמות קטן
        Act.Installing -> {
            val label = stringResource(R.string.installing)
            FilledTonalIconButton(onClick = {}, Modifier.semantics { contentDescription = label }, enabled = false) {
                InstallProgress(installing ?: -1f, 22.dp, 2.5.dp)
            }
        }
        Act.Open -> FilledTonalIconButton(onClick = onOpen) { Icon(OpenIcon, stringResource(R.string.open)) }
        Act.UpdateDownload -> FilledTonalIconButton(onClick = onDownload, colors = filled) { Icon(Icons.Default.Refresh, stringResource(R.string.update)) }
        Act.Progress -> Box(Modifier.size(48.dp).clickable(onClick = onShowDownloads), contentAlignment = Alignment.Center) {
            val p = (state as? DlState.Downloading)?.progress ?: -1f
            if (p >= 0) CircularProgressIndicator(progress = { p }, Modifier.size(34.dp), strokeWidth = 3.dp, trackColor = cs.surfaceContainerHighest)
            else CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 3.dp)
        }
        Act.NetFree -> FilledTonalIconButton(
            onClick = onShowDownloads,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (state is DlState.NotChecked) cs.tertiaryContainer else cs.errorContainer,
                contentColor = if (state is DlState.NotChecked) cs.onTertiaryContainer else cs.onErrorContainer,
            ),
        ) { Icon(ShieldIcon, stringResource(R.string.netfree)) }
        // נכשל: סמל אזהרה אדום. לחיצה מציגה שוב את הסיבה, עם "נסה שוב"
        Act.Failed -> FilledTonalIconButton(
            onClick = onProblem,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer),
        ) { Icon(Icons.Default.Warning, stringResource(R.string.failed_retry)) }
        Act.Download -> FilledTonalIconButton(onClick = onDownload) { Icon(DownloadIcon, stringResource(R.string.download)) }
    }
}

/** עיגול ההתקנה: מלא לפי ההתקדמות, או מסתובב כשעוד לא ידוע (‎-1) */
@Composable
private fun InstallProgress(progress: Float, size: Dp, stroke: Dp) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (progress >= 0) CircularProgressIndicator(progress = { progress }, Modifier.size(size), color = color, strokeWidth = stroke)
    else CircularProgressIndicator(Modifier.size(size), color = color, strokeWidth = stroke)
}

@Composable
fun PricePill(price: String) {
    Text(
        price, style = MaterialTheme.typography.labelLarge, maxLines = 1,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * כפתור רחב - בדף האפליקציה ובתוצאה הראשונה.
 * price != null: אפליקציה בתשלום - לא מורידים, רק מציגים את המחיר.
 * preregister: עוד לא יצאה - הרשמה מוקדמת בלבד.
 * installed / updateAvailable: מה מותקן בטלפון, והאם יש גרסה חדשה יותר להורדה.
 */
@Composable
fun DownloadButton(
    state: DlState?,
    modifier: Modifier = Modifier,
    price: String? = null,
    preregister: Boolean = false,
    unavailable: Boolean = false,
    installed: InstalledApp? = null,
    updateAvailable: Boolean = false,
    installing: Float? = null,
    onDownload: () -> Unit,
    onInstall: (DlState.Ready) -> Unit,
    onShowDownloads: () -> Unit,
    onOpen: () -> Unit = {},
    onRetry: () -> Unit = onDownload,
) {
    val cs = MaterialTheme.colorScheme
    val mod = modifier.height(48.dp)
    if (state == null && installed == null && installing == null && (price != null || preregister || unavailable)) {
        Button(onClick = {}, mod, enabled = false) {
            Text(price ?: stringResource(if (preregister) R.string.preregister else R.string.unavailable))
        }
        return
    }
    @Composable fun Label(icon: ImageVector, text: Int) {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(text))
    }
    when (actOf(state, installed, updateAvailable && !unavailable, installing = installing != null)) {
        Act.Install -> Button(onClick = { onInstall(state as DlState.Ready) }, mod) { Label(InstallIcon, R.string.install) }
        Act.UpdateInstall -> Button(onClick = { onInstall(state as DlState.Ready) }, mod) { Label(Icons.Default.Refresh, R.string.update) }
        Act.Installing -> Button(onClick = {}, mod, enabled = false) {
            InstallProgress(installing ?: -1f, 20.dp, 2.5.dp); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.installing))
        }
        Act.UpdateDownload -> Button(onClick = onDownload, mod) { Label(Icons.Default.Refresh, R.string.update) }
        Act.Open -> androidx.compose.material3.FilledTonalButton(onClick = onOpen, mod) { Label(OpenIcon, R.string.open) }
        Act.Progress -> OutlinedButton(onClick = onShowDownloads, mod) {
            val p = (state as? DlState.Downloading)?.progress ?: -1f
            if (p >= 0) CircularProgressIndicator(progress = { p }, Modifier.size(20.dp), strokeWidth = 2.5.dp)
            else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
            if (p >= 0) { Spacer(Modifier.width(10.dp)); Text("${(p * 100).toInt()}%") }
        }
        Act.NetFree -> Button(
            onClick = onShowDownloads, mod,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = if (state is DlState.NotChecked) cs.tertiaryContainer else cs.errorContainer,
                contentColor = if (state is DlState.NotChecked) cs.onTertiaryContainer else cs.onErrorContainer,
            ),
        ) { Label(ShieldIcon, if (state is DlState.NotChecked) R.string.dl_pending_check else R.string.dl_blocked) }
        Act.Failed -> Button(
            onClick = onRetry, mod,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer),
        ) { Label(Icons.Default.Refresh, R.string.failed_retry) }
        Act.Download -> Button(onClick = onDownload, mod) { Label(DownloadIcon, R.string.download) }
    }
}

// ================= צילומי מסך =================

@Composable
fun Screenshots(urls: List<String>, height: Dp, startPad: Dp = 0.dp) {
    val shotPx = with(LocalDensity.current) { height.roundToPx() }
    var open by remember { mutableStateOf<Int?>(null) }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = startPad),
    ) {
        itemsIndexed(urls) { i, u ->
            AsyncImage(
                model = screenshotImage(u, shotPx),
                contentDescription = null,
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.height(height).clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { open = i },
            )
        }
    }
    open?.let { start -> ScreenshotViewer(urls, start) { open = null } }
}

/** צפייה במסך מלא עם החלקה */
@Composable
private fun ScreenshotViewer(urls: List<String>, start: Int, onClose: () -> Unit) {
    // מסך מלא: לפי הצד הארוך של המסך בפיקסלים
    val fullPx = with(LocalDensity.current) { maxOf(LocalWindow.current.heightDp, LocalWindow.current.widthDp).roundToPx() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val pager = rememberPagerState(initialPage = start) { urls.size }
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = screenshotImage(urls[page], fullPx),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(onClick = onClose, Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.close), tint = Color.White)
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(urls.size) { i ->
                    Box(
                        Modifier.size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.4f))
                    )
                }
            }
        }
    }
}

// ================= מצבים =================

/** הודעה במרכז המסך: אייקון גדול, שורה קצרה, וכפתור רענון עגול */
@Composable
fun CenterMessage(icon: ImageVector, text: String? = null, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            text?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
            onRetry?.let { FilledTonalIconButton(onClick = it) { Icon(Icons.Default.Refresh, stringResource(R.string.retry)) } }
        }
    }
}

@Composable
fun ErrorMessage(text: String, onRetry: () -> Unit) = CenterMessage(Icons.Default.Warning, text, onRetry)

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = modifier)
