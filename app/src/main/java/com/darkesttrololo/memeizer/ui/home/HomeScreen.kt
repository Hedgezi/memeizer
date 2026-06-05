package com.darkesttrololo.memeizer.ui.home

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkesttrololo.memeizer.data.search.SearchResult

@Composable
fun HomeScreen(viewModel: HomeViewModel, paddingValues: PaddingValues) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedResult by remember { mutableStateOf<SearchResult?>(null) }

    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                label = { Text("Search meme text") },
                singleLine = true,
            )
        }

        if (state.indexedImageCount == 0) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Add a folder first. Memeizer will OCR images locally with PaddleOCR and ML Kit.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else if (state.query.isBlank()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text("Indexed images: ${state.indexedImageCount}. Type text to search.")
            }
        }

        items(state.results, key = { it.imageId }) { result ->
            Card(modifier = Modifier.clickable { selectedResult = result }) {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    model = Uri.parse(result.uri),
                    contentDescription = result.displayName,
                    contentScale = ContentScale.Crop,
                )
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = result.displayName,
                    maxLines = 1,
                )
            }
        }
    }

    selectedResult?.let { result ->
        MemePreviewDialog(result = result, onDismiss = { selectedResult = null })
    }
}
