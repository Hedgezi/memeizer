package com.darkesttrololo.memeizer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkesttrololo.memeizer.R

@Composable
fun SettingsScreen(
    onOpenFolders: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
) {
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
    }
}
