package com.darkesttrololo.memeizer.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darkesttrololo.memeizer.data.AppContainer
import com.darkesttrololo.memeizer.ui.folders.FoldersScreen
import com.darkesttrololo.memeizer.ui.folders.FoldersViewModel
import com.darkesttrololo.memeizer.ui.home.HomeScreen
import com.darkesttrololo.memeizer.ui.home.HomeViewModel

@Composable
fun MemeizerAppUi(container: AppContainer) {
    var selectedTab by remember { mutableStateOf(Tab.Search) }
    val context = LocalContext.current
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == Tab.Search,
                    onClick = { selectedTab = Tab.Search },
                    label = { Text("Search") },
                    icon = {},
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.Folders,
                    onClick = { selectedTab = Tab.Folders },
                    label = { Text("Folders") },
                    icon = {},
                )
            }
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                Tab.Search -> HomeScreen(homeViewModel, paddingValues)
                Tab.Folders -> FoldersScreen(
                    viewModel = foldersViewModel,
                    paddingValues = paddingValues,
                    onAddFolder = { folderPicker.launch(null) },
                )
            }
        }
    }
}

private enum class Tab {
    Search,
    Folders,
}
