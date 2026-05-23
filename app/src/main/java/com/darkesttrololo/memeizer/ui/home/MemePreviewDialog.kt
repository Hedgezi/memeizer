package com.darkesttrololo.memeizer.ui.home

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.darkesttrololo.memeizer.data.search.SearchResult

@Composable
fun MemePreviewDialog(result: SearchResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text(result.displayName) },
        text = {
            Column {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    model = Uri.parse(result.uri),
                    contentDescription = result.displayName,
                    contentScale = ContentScale.Fit,
                )
                Text(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    text = result.text.ifBlank { "No OCR text" },
                )
            }
        },
    )
}
