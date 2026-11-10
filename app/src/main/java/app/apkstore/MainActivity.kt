package app.apkstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import app.apkstore.ui.AppScreen
import app.apkstore.ui.DownloadIcon
import app.apkstore.ui.DownloadsScreen
import app.apkstore.ui.HomeScreen
import app.apkstore.ui.SearchScreen
import app.apkstore.ui.StoreTheme
import app.apkstore.ui.StoreViewModel
import app.apkstore.ui.Tab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StoreTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    StoreApp()
                }
            }
        }
    }
}

@Composable
fun StoreApp(vm: StoreViewModel = viewModel()) {
    val detail = vm.detailStack.lastOrNull()
    BackHandler(enabled = detail != null || vm.tab != Tab.Home) {
        if (!vm.back()) vm.selectTab(Tab.Home)
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = vm.tab == Tab.Home && detail == null, onClick = { vm.selectTab(Tab.Home) },
                    icon = { Icon(Icons.Default.Home, null) }, label = { Text("אפליקציות") },
                )
                NavigationBarItem(
                    selected = vm.tab == Tab.Search && detail == null, onClick = { vm.selectTab(Tab.Search) },
                    icon = { Icon(Icons.Default.Search, null) }, label = { Text("חיפוש") },
                )
                NavigationBarItem(
                    selected = vm.tab == Tab.Downloads && detail == null, onClick = { vm.selectTab(Tab.Downloads) },
                    icon = { Icon(DownloadIcon, null) }, label = { Text("הורדות") },
                )
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                detail != null -> AppScreen(vm, detail)
                vm.tab == Tab.Home -> HomeScreen(vm)
                vm.tab == Tab.Search -> SearchScreen(vm)
                else -> DownloadsScreen(vm)
            }
        }
    }
}
