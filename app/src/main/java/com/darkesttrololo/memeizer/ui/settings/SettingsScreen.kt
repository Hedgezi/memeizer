package com.darkesttrololo.memeizer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkesttrololo.memeizer.R
import com.darkesttrololo.memeizer.data.indexing.IndexingMode

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenFolders: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
) {
    val indexingMode by viewModel.indexingMode.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = bottomContentPadding),
    ) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(24.dp).semantics { heading() },
        )
        ListItem(
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenFolders),
            headlineContent = { Text(stringResource(R.string.folders)) },
            supportingContent = { Text(stringResource(R.string.folders_description)) },
            leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
        )
        Text(
            text = stringResource(R.string.auto_indexing),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
                .semantics { heading() },
        )
        Column(Modifier.selectableGroup()) {
            IndexingMode.entries.forEach { mode ->
                val economical = mode == IndexingMode.ECONOMICAL
                ListItem(
                    modifier = Modifier.selectable(
                        selected = indexingMode == mode,
                        role = Role.RadioButton,
                        onClick = { viewModel.setIndexingMode(mode) },
                    ),
                    headlineContent = {
                        Text(stringResource(if (economical) R.string.indexing_economical else R.string.indexing_active))
                    },
                    supportingContent = {
                        Text(stringResource(if (economical) R.string.indexing_economical_description else R.string.indexing_active_description))
                    },
                    leadingContent = { RadioButton(selected = indexingMode == mode, onClick = null) },
                )
            }
        }
        Text(
            text = stringResource(R.string.indexing_schedule_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}
