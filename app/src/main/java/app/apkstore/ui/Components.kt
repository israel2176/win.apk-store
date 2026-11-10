package app.apkstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.apkstore.data.AppSummary
import app.apkstore.data.DlState
import app.apkstore.data.sizedImage
import coil.compose.AsyncImage

val Green = Color(0xFF0B7A5A)
val Muted = Color(0xFF444746)
val Surface2 = Color(0xFFF0F2F1)

@Composable
fun StoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            secondaryContainer = Green.copy(alpha = 0.16f),
            surface = Color.White,
            background = Color.White,
            surfaceContainer = Color(0xFFF3F5F4),
        ),
        content = content,
    )
}

val DownloadIcon: ImageVector by lazy {
    ImageVector.Builder("download", 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black)) {
        moveTo(5f, 20f); horizontalLineToRelative(14f); verticalLineToRelative(-2f); horizontalLineTo(5f); close()
        moveTo(19f, 9f); horizontalLineToRelative(-4f); verticalLineTo(3f); horizontalLineTo(9f)
        verticalLineToRelative(6f); horizontalLineTo(5f); lineToRelative(7f, 7f); close()
    }.build()
}

/** אייקון האפליקציה כפי שהוא בפליי */
@Composable
fun AppIcon(url: String?, size: Dp) {
    val px = (size.value * 2.5f).toInt().coerceIn(64, 256)
    AsyncImage(
        model = sizedImage(url, px),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(Surface2),
    )
}

@Composable
fun RatingText(score: Double?, extra: String? = null) {
    val parts = listOfNotNull(score?.let { "%.1f".format(it) + " ★" }, extra)
    if (parts.isNotEmpty()) {
        Text(parts.joinToString("  "), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
fun AppRow(app: AppSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(app.icon, 56.dp)
        Column(Modifier.weight(1f)) {
            Text(app.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            app.developer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1) }
            RatingText(app.score, app.genre)
        }
    }
}

/** כפתור ההורדה בדף אפליקציה ובתוצאת חיפוש - מציג מצב לפי ההורדה */
@Composable
fun DownloadButton(
    state: DlState?,
    modifier: Modifier = Modifier,
    onDownload: () -> Unit,
    onInstall: (DlState.Ready) -> Unit,
    onShowDownloads: () -> Unit,
) {
    when (state) {
        is DlState.Ready -> Button(onClick = { onInstall(state) }, modifier) { Text("התקנה") }
        is DlState.Preparing, is DlState.Downloading -> OutlinedButton(onClick = onShowDownloads, modifier) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            val p = (state as? DlState.Downloading)?.progress ?: -1f
            Text(if (p >= 0) "מוריד ${(p * 100).toInt()}%" else "מוריד…")
        }
        is DlState.NotChecked, is DlState.Blocked -> OutlinedButton(onClick = onShowDownloads, modifier) {
            Text(if (state is DlState.NotChecked) "הקובץ לא נבדק ע״י נטפרי" else "נחסם ע״י נטפרי")
        }
        else -> Button(onClick = onDownload, modifier) {
            Icon(DownloadIcon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("הורדה")
        }
    }
}

@Composable
fun CenterMessage(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, color = Muted)
            if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
