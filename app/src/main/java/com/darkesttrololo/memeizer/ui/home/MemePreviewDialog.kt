package com.darkesttrololo.memeizer.ui.home

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.text.format.Formatter
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
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
import com.darkesttrololo.memeizer.ui.theme.MemeizerTheme
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Progress is shared by the panel and image layout; dismissal never removes the dialog early. */
private class PreviewMotion(private val scope: CoroutineScope, private val onDismiss: () -> Unit) {
    var info by mutableFloatStateOf(0f)
    var dismiss by mutableFloatStateOf(0f)
    var closing by mutableStateOf(false)
    var imageDragActive by mutableStateOf(false)
    private var animation: Job? = null

    fun stop() { animation?.cancel() }

    fun information(open: Boolean, cancelled: Boolean = false) {
        if (closing) return
        stop()
        dismiss = 0f
        animation = scope.launch {
            animate(info, if (open) 1f else 0f,
                animationSpec = if (cancelled) spring(dampingRatio = 1f, stiffness = 400f) else tween(240),
            ) { value, _ -> info = value }
        }
    }

    fun close() {
        if (closing) return
        closing = true
        stop()
        animation = scope.launch {
            animate(dismiss, 1f, animationSpec = tween(240)) { value, _ -> dismiss = value }
            onDismiss()
        }
    }

    fun restore() {
        if (closing) return
        stop()
        animation = scope.launch {
            animate(dismiss, 0f, animationSpec = spring(dampingRatio = 1f, stiffness = 400f)) {
                value, _ -> dismiss = value
            }
        }
    }
}

/** Locks the axis after slop, but leaves horizontal and multi-pointer input to pager/zoom. */
private fun Modifier.previewDrag(
    motion: PreviewMotion,
    enabled: () -> Boolean,
    panelHeight: () -> Float,
    dismissHeight: () -> Float,
    velocityThreshold: Float,
): Modifier = pointerInput(motion, velocityThreshold) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!enabled() || motion.closing) return@awaitEachGesture
        val wasOpen = motion.info > 0f
        var total = Offset.Zero
        var vertical = false
        var opening = false
        val tracker = VelocityTracker()
        var lastMotionTime = down.uptimeMillis
        tracker.addPosition(down.uptimeMillis, down.position)
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) {
                    if (vertical) {
                        if (opening) {
                            // Restore the viewport before handing the remaining pointers to zoom.
                            motion.info = 0f
                        } else if (wasOpen) motion.information(true, cancelled = true)
                        else motion.restore()
                    }
                    break
                }
                val change = event.changes.first()
                if (change.pressed && change.positionChange() != Offset.Zero) {
                    tracker.addPosition(change.uptimeMillis, change.position)
                    lastMotionTime = change.uptimeMillis
                }
                if (!change.pressed) {
                    if (vertical) {
                        change.consume()
                        val velocity = if (change.uptimeMillis - lastMotionTime <= 100) {
                            tracker.calculateVelocity().y
                        } else 0f
                        when {
                            wasOpen -> {
                                val collapse = motion.info < .75f || velocity > velocityThreshold
                                motion.information(!collapse, cancelled = !collapse)
                            }
                            opening -> {
                                val expand = motion.info > .25f || velocity < -velocityThreshold
                                motion.information(expand, cancelled = !expand)
                            }
                            motion.dismiss > .25f || velocity > velocityThreshold -> motion.close()
                            else -> motion.restore()
                        }
                    }
                    break
                }
                val delta = change.positionChange()
                total += delta
                if (!vertical) {
                    if (total.getDistance() <= viewConfiguration.touchSlop) continue
                    if (abs(total.x) >= abs(total.y) || change.isConsumed) break
                    vertical = true
                    opening = !wasOpen && total.y < 0f
                    motion.imageDragActive = !wasOpen
                    motion.stop()
                    if (wasOpen || opening) motion.dismiss = 0f
                }
                change.consume()
                when {
                    wasOpen -> motion.info = (motion.info - delta.y / panelHeight()).coerceIn(0f, 1f)
                    opening -> motion.info = (motion.info - delta.y / panelHeight()).coerceIn(0f, 1f)
                    else -> motion.dismiss = (motion.dismiss + delta.y / dismissHeight()).coerceIn(0f, 1f)
                }
            }
        } catch (cancelled: CancellationException) {
            if (vertical && !motion.closing) {
                if (wasOpen || opening) motion.information(wasOpen, cancelled = true) else motion.restore()
            }
            throw cancelled
        } finally {
            motion.imageDragActive = false
        }
    }
}

@Composable
fun MemePreviewDialog(
    session: ViewerSession,
    onPageChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = session.page) { session.results.size }
    var zoomed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismissCallback by rememberUpdatedState(onDismiss)
    val motion = remember { PreviewMotion(scope) { dismissCallback() } }
    val current = session.results[pager.currentPage]
    val velocityThreshold = with(LocalDensity.current) { 1000.dp.toPx() }
    val infoAction = stringResource(R.string.viewer_show_info)
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { onPageChanged(it) }
    }
    Dialog(
        onDismissRequest = { if (motion.info > 0f) motion.information(false) else motion.close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as DialogWindowProvider).window
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0f)
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose { }
        }
        MemeizerTheme(darkTheme = true) {
            Box(Modifier.fillMaxSize().background(Color(0xFF101010).copy(alpha = 1f - motion.dismiss))) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(
                        Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - motion.dismiss }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = motion::close) {
                            Icon(
                                Icons.Default.Close,
                                stringResource(R.string.viewer_close),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            stringResource(R.string.viewer_counter, pager.currentPage + 1, session.results.size),
                            modifier = Modifier.weight(1f).padding(end = 48.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                        val availableHeight = maxHeight
                        val panelHeight = availableHeight * .45f
                        val density = LocalDensity.current
                        val panelPx = with(density) { panelHeight.toPx() }
                        val heightPx = with(density) { maxHeight.toPx() }
                        val latestPanelPx by rememberUpdatedState(panelPx)
                        val latestHeightPx by rememberUpdatedState(heightPx)
                        Box(
                            Modifier.fillMaxSize().previewDrag(
                                motion, { !zoomed && !pager.isScrollInProgress },
                                { latestPanelPx }, { latestHeightPx }, velocityThreshold,
                            ).semantics {
                                customActions = listOf(CustomAccessibilityAction(infoAction) {
                                    if (!motion.closing) { motion.information(true); true } else false
                                })
                            },
                        ) {
                            HorizontalPager(
                                state = pager,
                                modifier = Modifier.fillMaxWidth().height(availableHeight - panelHeight * motion.info)
                                    .graphicsLayer {
                                        translationY = motion.dismiss * heightPx
                                        scaleX = 1f - .15f * motion.dismiss
                                        scaleY = scaleX
                                    },
                                key = { session.results[it].imageId },
                                userScrollEnabled = !zoomed && motion.info == 0f && motion.dismiss == 0f && !motion.closing,
                            ) { page ->
                                ZoomableImage(
                                    result = session.results[page],
                                    active = page == pager.currentPage,
                                    gesturesEnabled = (motion.info == 0f || motion.imageDragActive) && !motion.closing,
                                    onZoomChanged = { if (page == pager.currentPage) zoomed = it },
                                )
                            }
                        }
                        // Only unconsumed scroll at the start of the text can collapse the panel.
                        val nestedScroll = remember(motion, panelPx, velocityThreshold) {
                            object : NestedScrollConnection {
                                var dragging = false
                                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                    if (!dragging || source != NestedScrollSource.UserInput) return Offset.Zero
                                    motion.info = (motion.info - available.y / panelPx).coerceIn(0f, 1f)
                                    return Offset(0f, available.y)
                                }
                                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                                    if (source != NestedScrollSource.UserInput || available.y <= 0f || motion.closing) return Offset.Zero
                                    dragging = true
                                    motion.stop()
                                    motion.info = (motion.info - available.y / panelPx).coerceIn(0f, 1f)
                                    return Offset(0f, available.y)
                                }
                                override suspend fun onPreFling(available: Velocity): Velocity {
                                    if (!dragging) return Velocity.Zero
                                    dragging = false
                                    val close = motion.info < .75f || available.y > velocityThreshold
                                    motion.information(!close, cancelled = !close)
                                    return available
                                }
                            }
                        }
                        // Keep the panel mounted until a drag ends, even at zero progress.
                        Surface(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(panelHeight)
                                .offset(y = panelHeight * (1f - motion.info))
                                .graphicsLayer { alpha = 1f - motion.dismiss }
                                .nestedScroll(nestedScroll),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        ) {
                            Column {
                                Box(
                                    Modifier.fillMaxWidth().height(32.dp).previewDrag(
                                        motion, { true }, { latestPanelPx }, { latestHeightPx }, velocityThreshold,
                                    ), contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier.width(32.dp).height(4.dp).background(
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                            RoundedCornerShape(2.dp),
                                        ),
                                    )
                                }
                                key(current.imageId) { ImageInformation(current) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - motion.dismiss }.padding(vertical = 8.dp)) {
                        ViewerAction(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.viewer_open)) {
                            scope.launch { launchImageAction(context, current, share = false) }
                        }
                        ViewerAction(Icons.Default.Share, stringResource(R.string.viewer_share)) {
                            scope.launch { launchImageAction(context, current, share = true) }
                        }
                    }
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
private fun ZoomableImage(result: SearchResult, active: Boolean, gesturesEnabled: Boolean, onZoomChanged: (Boolean) -> Unit) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var intrinsic by remember(result.uri) { mutableStateOf(Size.Zero) }
    var loaded by remember(result.uri) { mutableStateOf(false) }
    var failed by remember(result.uri) { mutableStateOf(false) }
    var scale by remember(result.uri, active) { mutableFloatStateOf(1f) }
    var offset by remember(result.uri, active) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(viewport, active, result.uri) {
        scale = 1f
        offset = Offset.Zero
    }
    LaunchedEffect(scale, active, viewport) { if (active) onZoomChanged(scale > 1f) }

    fun bounded(value: Offset, zoom: Float): Offset {
        if (intrinsic.width <= 0 || intrinsic.height <= 0) return Offset.Zero
        val fit = min(viewport.width / intrinsic.width, viewport.height / intrinsic.height)
        val maxX = ((intrinsic.width * fit * zoom - viewport.width) / 2).coerceAtLeast(0f)
        val maxY = ((intrinsic.height * fit * zoom - viewport.height) / 2).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
    fun transform(centroid: Offset, zoom: Float, pan: Offset) {
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
            .pointerInput(loaded, active, gesturesEnabled) {
                if (!loaded || !active || !gesturesEnabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var transforming = false
                    var accumulatedZoom = 1f
                    var accumulatedPan = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val multiplePointers = event.changes.count { it.pressed } > 1
                        if (multiplePointers || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            accumulatedZoom *= zoom
                            accumulatedPan += pan
                            if (!transforming) {
                                val zoomMotion = abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false)
                                transforming = zoomMotion > viewConfiguration.touchSlop ||
                                    accumulatedPan.getDistance() > viewConfiguration.touchSlop
                            }
                            if (transforming) {
                                transform(event.calculateCentroid(useCurrent = false), zoom, pan)
                                event.changes.forEach { if (it.pressed) it.consume() }
                            }
                        } else {
                            // Keep observing after a single-pointer vertical drag was consumed:
                            // a second pointer may still take over this same gesture for zoom.
                            accumulatedZoom = 1f
                            accumulatedPan = Offset.Zero
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(loaded, active, viewport, gesturesEnabled) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (loaded && active && gesturesEnabled) {
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
        if (failed) {
            Text(
                stringResource(R.string.viewer_unavailable),
                Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else if (!loaded) {
            CircularProgressIndicator()
        }
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
