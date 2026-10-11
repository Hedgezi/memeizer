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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import com.darkesttrololo.memeizer.data.search.SearchMode
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkesttrololo.memeizer.R

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    paddingValues: PaddingValues,
    gridState: LazyGridState,
    bottomContentPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val viewerSession by viewModel.viewerSession.collectAsStateWithLifecycle()
    // Input must update synchronously; asynchronous result/progress flows may lag fast IME edits.
    var queryText by rememberSaveable { mutableStateOf(viewModel.uiState.value.query) }
    LaunchedEffect(viewModel) { viewModel.onQueryChanged(queryText) }

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
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = queryText,
                onValueChange = { queryText = it; viewModel.onQueryChanged(it) },
                placeholder = { Text(stringResource(if (state.mode == SearchMode.SEMANTIC) R.string.semantic_search_hint else R.string.search_hint)) },
                singleLine = true,
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = state.mode == SearchMode.TEXT, onClick = { viewModel.onModeChanged(SearchMode.TEXT) },
                label = { Text(stringResource(R.string.search_mode_text)) })
            FilterChip(selected = state.mode == SearchMode.SEMANTIC, onClick = { viewModel.onModeChanged(SearchMode.SEMANTIC) },
                label = { Text(stringResource(R.string.search_mode_semantic)) })
        }
        if (state.mode == SearchMode.SEMANTIC) {
            Text(
                stringResource(R.string.semantic_english_description),
                modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.semantic_progress, state.clipProgress.ready, state.clipProgress.total, state.clipProgress.failed),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
            )
            if (state.showSemanticRetry) {
                TextButton(
                    onClick = viewModel::retryFailedImages,
                    enabled = state.semanticRetryEnabled,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text(stringResource(R.string.semantic_retry_failed)) }
            }
            if (state.semanticRetryFailed) {
                Text(stringResource(R.string.semantic_retry_scheduling_error), modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.modelFailed && state.content != HomeContentState.ModelError) {
                Text(stringResource(R.string.semantic_model_error), modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state.content) {
                HomeContentState.Images -> LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp + bottomContentPadding),
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
                HomeContentState.SemanticIndexNotReady -> EmptyContent(R.string.semantic_index_not_ready)
                HomeContentState.SemanticIndexFailed -> EmptyContent(R.string.semantic_index_failed)
                HomeContentState.ModelError -> EmptyContent(R.string.semantic_model_error)
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
