package com.darkesttrololo.memeizer.ui.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun FoldersScreen(
    viewModel: FoldersViewModel,
    paddingValues: PaddingValues,
    onNavigateBack: () -> Unit,
    onAddFolder: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to search")
                    }
                    Text("Settings", style = MaterialTheme.typography.headlineSmall)
                }
                Text("Folders", style = MaterialTheme.typography.titleMedium)
                state.errorMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAddFolder) { Text("Add folder") }
                    OutlinedButton(onClick = { viewModel.startIndexing(replace = true, forceReindex = true) }) {
                        Text("Reindex now")
                    }
                }
            }
        }

        if (state.folders.isEmpty()) {
            item {
                Text("No folders yet.")
            }
        }

        items(state.folders, key = { it.id }) { folder ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(folder.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(folder.treeUri, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { viewModel.removeFolder(folder.id) }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}
