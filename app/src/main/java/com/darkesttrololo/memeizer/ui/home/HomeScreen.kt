package com.darkesttrololo.memeizer.ui.home

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkesttrololo.memeizer.R

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    paddingValues: PaddingValues,
    onOpenDrawer: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val viewerSession by viewModel.viewerSession.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                onClick = onOpenDrawer,
            ) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.open_menu))
            }
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
            )
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state.content) {
                HomeContentState.Images -> LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.results, key = { it.imageId }) { result ->
                        Card(modifier = Modifier.clickable { viewModel.openViewer(result) }) {
                            AsyncImage(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                model = Uri.parse(result.uri),
                                contentDescription = result.displayName,
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }

                HomeContentState.InitialLoading,
                HomeContentState.Loading,
                -> CircularProgressIndicator()

                HomeContentState.NoFolders -> EmptyContent(R.string.home_add_folders)
                HomeContentState.EmptyFolders -> EmptyContent(R.string.home_no_images)
                HomeContentState.IndexingError -> EmptyContent(R.string.home_indexing_error)
                HomeContentState.NoResults -> EmptyContent(R.string.home_no_results)
            }
        }
    }

    viewerSession?.let { session ->
        MemePreviewDialog(
            session = session,
            onPageChanged = viewModel::selectViewerPage,
            onDismiss = viewModel::closeViewer,
        )
    }
}

@Composable
private fun EmptyContent(textRes: Int) {
    Text(
        text = stringResource(textRes),
        modifier = Modifier.padding(24.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
}
