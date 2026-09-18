package com.darkesttrololo.memeizer.ui.home

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.darkesttrololo.memeizer.R
import com.darkesttrololo.memeizer.data.search.SearchResult
import java.text.DateFormat
import java.util.Date
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemePreviewDialog(
    session: ViewerSession,
    onPageChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = session.page) { session.results.size }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = session.results[pager.currentPage]
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { onPageChanged(it) }
    }
    Dialog(
        onDismissRequest = { if (showInfo) showInfo = false else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as DialogWindowProvider).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose { }
        }
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101010), contentColor = Color.White) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, stringResource(R.string.viewer_close))
                        }
                        Text(
                            stringResource(R.string.viewer_counter, pager.currentPage + 1, session.results.size),
                            modifier = Modifier.weight(1f).padding(end = 48.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        key = { session.results[it].imageId },
                        userScrollEnabled = !zoomed,
                    ) { page ->
                        ZoomableImage(
                            result = session.results[page],
                            active = page == pager.currentPage,
                            onZoomChanged = { if (page == pager.currentPage) zoomed = it },
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        ViewerAction(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.viewer_open)) {
                            scope.launch { launchImageAction(context, current, share = false) }
                        }
                        ViewerAction(Icons.Default.Share, stringResource(R.string.viewer_share)) {
                            scope.launch { launchImageAction(context, current, share = true) }
                        }
                        ViewerAction(Icons.Default.Info, stringResource(R.string.viewer_info)) { showInfo = true }
                    }
                }
            }
            if (showInfo) {
                ModalBottomSheet(
                    onDismissRequest = { showInfo = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
                ) {
                    BackHandler { showInfo = false }
                    ImageInformation(current)
                }
            }
        }
    }
}

@Composable
private fun RowScope.ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(label, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ZoomableImage(result: SearchResult, active: Boolean, onZoomChanged: (Boolean) -> Unit) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var intrinsic by remember(result.uri) { mutableStateOf(Size.Zero) }
    var loaded by remember(result.uri) { mutableStateOf(false) }
    var failed by remember(result.uri) { mutableStateOf(false) }
    var scale by remember(result.uri, viewport, active) { mutableFloatStateOf(1f) }
    var offset by remember(result.uri, viewport, active) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(scale, active, viewport) { if (active) onZoomChanged(scale > 1f) }

    fun bounded(value: Offset, zoom: Float): Offset {
        if (intrinsic.width <= 0 || intrinsic.height <= 0) return Offset.Zero
        val fit = min(viewport.width / intrinsic.width, viewport.height / intrinsic.height)
        val maxX = ((intrinsic.width * fit * zoom - viewport.width) / 2).coerceAtLeast(0f)
        val maxY = ((intrinsic.height * fit * zoom - viewport.height) / 2).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
    val transform = rememberTransformableState { centroid, zoom, pan, _ ->
        val next = (scale * zoom).coerceIn(1f, 5f)
        val ratio = next / scale
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val pivot = if (centroid.isSpecified) centroid - center else Offset.Zero
        offset = bounded(offset * ratio + pivot * (1f - ratio) + pan, next)
        scale = next
    }
    val context = LocalContext.current
    // Read the source on each viewer session, so a cached thumbnail cannot hide a deleted file.
    val request = remember(result.uri, context) {
        ImageRequest.Builder(context).data(Uri.parse(result.uri))
            .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
    }
    val zoomDescription = stringResource(R.string.viewer_zoom, (scale * 100).toInt())
    Box(
        Modifier.semantics { stateDescription = zoomDescription }.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }
            .transformable(transform, canPan = { scale > 1f }, enabled = loaded && active)
            .pointerInput(loaded, active, viewport) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (loaded && active) {
                        val next = if (scale > 1f) 1f else 2f
                        val center = Offset(viewport.width / 2f, viewport.height / 2f)
                        offset = if (next == 1f) Offset.Zero else bounded((center - tap) * (next - 1f), next)
                        scale = next
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = result.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
            onSuccess = {
                intrinsic = Size(it.result.drawable.intrinsicWidth.toFloat(), it.result.drawable.intrinsicHeight.toFloat())
                loaded = true
                failed = false
            },
            onError = { failed = true; loaded = false },
        )
        if (failed) Text(stringResource(R.string.viewer_unavailable), Modifier.padding(24.dp))
        else if (!loaded) CircularProgressIndicator()
    }
}

@Composable
private fun ImageInformation(result: SearchResult) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.viewer_unknown)
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.viewer_info), style = MaterialTheme.typography.titleLarge)
        InformationField(stringResource(R.string.viewer_filename), result.displayName.ifBlank { unknown })
        InformationField(stringResource(R.string.viewer_ocr), result.text.ifBlank { stringResource(R.string.viewer_no_text) })
        InformationField(stringResource(R.string.viewer_format), result.mimeType?.takeIf { it.isNotBlank() } ?: unknown)
        InformationField(stringResource(R.string.viewer_size), result.size?.takeIf { it >= 0 }?.let { Formatter.formatFileSize(context, it) } ?: unknown)
        InformationField(stringResource(R.string.viewer_modified), result.lastModified?.takeIf { it > 0 }?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
        } ?: unknown)
    }
}

@Composable
private fun InformationField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
    }
}

private suspend fun launchImageAction(context: Context, result: SearchResult, share: Boolean) {
    try {
        val uri = Uri.parse(result.uri)
        require(uri.scheme == "content")
        val mimeType = withContext(Dispatchers.IO) {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { }
                ?: error("Image unavailable")
            result.mimeType?.takeIf { it.isNotBlank() } ?: context.contentResolver.getType(uri) ?: "image/*"
        }
        val intent = Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
            if (share) {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
            } else setDataAndType(uri, mimeType)
            clipData = ClipData.newRawUri(result.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(
            if (share) R.string.viewer_share else R.string.viewer_open,
        )))
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Toast.makeText(context, R.string.viewer_action_failed, Toast.LENGTH_LONG).show()
    }
}
