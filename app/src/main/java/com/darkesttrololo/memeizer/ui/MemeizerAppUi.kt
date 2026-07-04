package com.darkesttrololo.memeizer.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darkesttrololo.memeizer.data.AppContainer
import com.darkesttrololo.memeizer.ui.folders.FoldersScreen
import com.darkesttrololo.memeizer.ui.folders.FoldersViewModel
import com.darkesttrololo.memeizer.ui.home.HomeScreen
import com.darkesttrololo.memeizer.ui.home.HomeViewModel
import kotlinx.coroutines.launch

@Composable
fun MemeizerAppUi(container: AppContainer) {
    var selectedScreen by remember { mutableStateOf(Screen.Search) }
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val foldersViewModel: FoldersViewModel = viewModel(
        factory = FoldersViewModel.factory(context.applicationContext, container),
    )
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(container),
    )

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        foldersViewModel.addFolder(uri)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerItem(
                    selected = selectedScreen == Screen.Settings,
                    onClick = {
                        selectedScreen = Screen.Settings
                        coroutineScope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) {
        Scaffold { paddingValues ->
            Column(modifier = Modifier.fillMaxSize()) {
                when (selectedScreen) {
                    Screen.Search -> HomeScreen(
                        viewModel = homeViewModel,
                        paddingValues = paddingValues,
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    )

                    Screen.Settings -> FoldersScreen(
                        viewModel = foldersViewModel,
                        paddingValues = paddingValues,
                        onNavigateBack = { selectedScreen = Screen.Search },
                        onAddFolder = { folderPicker.launch(null) },
                    )
                }
            }
        }
    }
}

private enum class Screen {
    Search,
    Settings,
}
