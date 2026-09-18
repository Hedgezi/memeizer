package com.darkesttrololo.memeizer.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darkesttrololo.memeizer.R
import com.darkesttrololo.memeizer.data.AppContainer
import com.darkesttrololo.memeizer.ui.folders.FoldersScreen
import com.darkesttrololo.memeizer.ui.folders.FoldersViewModel
import com.darkesttrololo.memeizer.ui.home.HomeScreen
import com.darkesttrololo.memeizer.ui.home.HomeViewModel
import com.darkesttrololo.memeizer.ui.settings.SettingsScreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemeizerAppUi(container: AppContainer) {
    var selectedScreen by rememberSaveable { mutableStateOf(Screen.Search) }
    val context = LocalContext.current
    val galleryState = rememberLazyGridState()
    val foldersViewModel: FoldersViewModel = viewModel(
        factory = FoldersViewModel.factory(container),
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

    BackHandler(enabled = selectedScreen != Screen.Search) {
        selectedScreen = if (selectedScreen == Screen.Folders) Screen.Settings else Screen.Search
    }

    // Inset padding consumes safe areas before IME padding, avoiding duplicate bottom insets.
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (selectedScreen) {
                    Screen.Search -> HomeScreen(
                        viewModel = homeViewModel,
                        paddingValues = PaddingValues(0.dp),
                        gridState = galleryState,
                    )
                    Screen.Settings -> SettingsScreen(
                        onOpenFolders = { selectedScreen = Screen.Folders },
                    )
                    Screen.Folders -> FoldersScreen(
                        viewModel = foldersViewModel,
                        paddingValues = PaddingValues(0.dp),
                        onNavigateBack = { selectedScreen = Screen.Settings },
                        onAddFolder = { folderPicker.launch(null) },
                    )
                }
            }
            if (selectedScreen != Screen.Folders && !WindowInsets.isImeVisible) {
                FloatingNavigation(
                    selectedScreen = selectedScreen,
                    onSelect = { selectedScreen = it },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun FloatingNavigation(
    selectedScreen: Screen,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 400.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.selectableGroup().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Screen.Search, Screen.Settings).forEach { screen ->
                val selected = selectedScreen == screen
                val label = stringResource(if (screen == Screen.Search) R.string.search else R.string.settings)
                Row(
                    modifier = Modifier.weight(1f, fill = false)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(screen) })
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    if (selected) {
                        Icon(
                            imageVector = if (screen == Screen.Search) Icons.Default.Search else Icons.Default.Settings,
                            contentDescription = null,
                            tint = color,
                        )
                    }
                    Text(label, color = color, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private enum class Screen {
    Search,
    Settings,
    Folders,
}
