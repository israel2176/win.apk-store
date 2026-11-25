package app.apkstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.apkstore.data.DlState
import app.apkstore.ui.AppScreen
import app.apkstore.ui.DownloadIcon
import app.apkstore.ui.DownloadsScreen
import app.apkstore.ui.HomeScreen
import app.apkstore.ui.LocalWindow
import app.apkstore.ui.SearchScreen
import app.apkstore.ui.StoreTheme
import app.apkstore.ui.StoreViewModel
import app.apkstore.ui.Tab
import app.apkstore.ui.WindowInfo
import app.apkstore.ui.widthClassOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StoreRoot() }
    }
}

/** שורש הממשק: ערכת צבעים, כיוון מימין לשמאל, ומידות החלון */
@Composable
fun StoreRoot(vm: StoreViewModel = viewModel()) {
    StoreTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val info = WindowInfo(widthClassOf(maxWidth), maxWidth, maxHeight)
                CompositionLocalProvider(LocalWindow provides info) { StoreApp(vm) }
            }
        }
    }
}

private data class NavDest(val tab: Tab, val icon: ImageVector, val label: String)

private val destinations = listOf(
    NavDest(Tab.Home, Icons.Default.Home, "ראשי"),
    NavDest(Tab.Search, Icons.Default.Search, "חיפוש"),
    NavDest(Tab.Downloads, DownloadIcon, "הורדות"),
)

@Composable
fun StoreApp(vm: StoreViewModel = viewModel()) {
    val window = LocalWindow.current
    val detail = vm.detailStack.lastOrNull()
    val downloads by vm.downloads.items.collectAsState()
    // מספר על לשונית ההורדות: כמה מוכנות להתקנה או דורשות טיפול
    val badge = downloads.count { it.state !is DlState.Preparing && it.state !is DlState.Downloading }
    val active = downloads.any { it.state is DlState.Preparing || it.state is DlState.Downloading }

    BackHandler(enabled = detail != null || vm.tab != Tab.Home) {
        if (!vm.back()) vm.selectTab(Tab.Home)
    }

    @Composable
    fun NavIcon(d: NavDest) {
        if (d.tab == Tab.Downloads && (badge > 0 || active)) {
            BadgedBox(badge = { if (active) Badge() else Badge { Text("$badge") } }) { Icon(d.icon, d.label) }
        } else Icon(d.icon, d.label)
    }

    val content: @Composable (Modifier) -> Unit = { mod ->
        Box(mod) {
            AnimatedContent(
                targetState = detail ?: vm.tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen",
            ) { target ->
                when (target) {
                    is String -> AppScreen(vm, target)
                    Tab.Home -> HomeScreen(vm)
                    Tab.Search -> SearchScreen(vm)
                    else -> DownloadsScreen(vm)
                }
            }
        }
    }

    if (window.compact) {
        // טלפון לאורך: סרגל תחתון, תוויות רק ללשונית הנבחרת
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    destinations.forEach { d ->
                        NavigationBarItem(
                            selected = vm.tab == d.tab && detail == null,
                            onClick = { vm.selectTab(d.tab) },
                            icon = { NavIcon(d) },
                            label = { Text(d.label) },
                            alwaysShowLabel = false,
                        )
                    }
                }
            },
        ) { pad -> content(Modifier.padding(pad).fillMaxSize()) }
    } else {
        // מסך רחב / לרוחב: סרגל צד, ללא תוויות
        Scaffold(containerColor = MaterialTheme.colorScheme.surface) { pad ->
            Row(Modifier.padding(pad).fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    windowInsets = WindowInsets(0),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Spacer(Modifier.weight(1f))
                    destinations.forEach { d ->
                        NavigationRailItem(
                            selected = vm.tab == d.tab && detail == null,
                            onClick = { vm.selectTab(d.tab) },
                            icon = { NavIcon(d) },
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                content(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
