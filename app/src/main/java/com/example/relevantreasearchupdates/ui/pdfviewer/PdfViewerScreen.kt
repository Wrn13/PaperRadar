package com.example.relevantreasearchupdates.ui.pdfviewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    paperId: Long,
    viewModel: PdfViewerViewModel = viewModel()
) {
    LaunchedEffect(paperId) { viewModel.load(paperId) }
    val state by viewModel.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Reader") }) }) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                is PdfViewerState.Loading -> CircularProgressIndicator()
                is PdfViewerState.Error -> Text(
                    s.message,
                    modifier = Modifier.padding(24.dp)
                )
                is PdfViewerState.Ready -> PdfPages(file = s.file)
            }
        }
    }
}

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private const val CACHE_SIZE = 6

/** While zoomed in, a single-finger swipe at least this fraction of the page width turns the page. */
private const val PAGE_TURN_SWIPE_FRACTION = 0.35f

/** Keeps a zoomed page covering the viewport instead of letting it be dragged off into blank space. */
private fun clampOffset(offset: Offset, scale: Float, width: Int, height: Int): Offset {
    val maxX = (scale - 1f) * width / 2f
    val maxY = (scale - 1f) * height / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/**
 * Wraps a single [PdfRenderer], confining all access behind a [Mutex] since PdfRenderer isn't
 * safe to use concurrently. Pages are rendered on demand rather than all up front, since holding
 * every page of a multi-page document as a full-resolution bitmap at once is what was causing
 * out-of-memory crashes.
 */
private class PdfPageSource(file: File) : AutoCloseable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val mutex = Mutex()

    val pageCount: Int get() = renderer.pageCount

    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap = mutex.withLock {
        renderer.openPage(index).use { page ->
            val width = targetWidthPx.coerceAtLeast(1)
            val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    override fun close() {
        renderer.close()
        pfd.close()
    }
}

/** Bounded so at most [maxEntries] full-resolution page bitmaps are ever held in memory at once. */
private class PageBitmapCache(private val maxEntries: Int) {
    private val map = LinkedHashMap<Int, Bitmap>(16, 0.75f, true)

    @Synchronized
    fun get(index: Int): Bitmap? = map[index]

    @Synchronized
    fun put(index: Int, bitmap: Bitmap) {
        map[index] = bitmap
        while (map.size > maxEntries) {
            map.remove(map.keys.first())
        }
    }
}

@Composable
private fun PdfPages(file: File) {
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    // Modest oversampling for pinch-zoom headroom without rendering pages large enough to
    // exhaust memory once more than a couple are cached.
    val targetWidthPx = (screenWidthPx * 1.5f).toInt().coerceIn(screenWidthPx, 1600)

    var source by remember(file) { mutableStateOf<PdfPageSource?>(null) }
    var pageCount by remember(file) { mutableStateOf(0) }
    var error by remember(file) { mutableStateOf<String?>(null) }

    DisposableEffect(file) {
        onDispose { source?.close() }
    }

    LaunchedEffect(file) {
        error = null
        source = null
        try {
            val opened = withContext(Dispatchers.IO) { PdfPageSource(file) }
            pageCount = opened.pageCount
            source = opened
        } catch (e: Exception) {
            error = e.message ?: "Failed to open PDF"
        }
    }

    val currentSource = source
    when {
        error != null -> Text(error!!, modifier = Modifier.padding(24.dp))
        currentSource == null -> CircularProgressIndicator()
        else -> {
            val cache = remember(file) { PageBitmapCache(CACHE_SIZE) }
            val pagerState = rememberPagerState(pageCount = { pageCount })
            val scope = rememberCoroutineScope()

            Box(modifier = Modifier.fillMaxSize()) {
                // One page fills the screen at a time; swiping moves to the next/previous page,
                // like turning a page in an e-reader. Zooming and panning happen within a page
                // without disturbing that — see the pointerInput gating in PdfPageItem below.
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { index ->
                    PdfPageItem(
                        index = index,
                        source = currentSource,
                        cache = cache,
                        targetWidthPx = targetWidthPx,
                        isCurrent = pagerState.settledPage == index,
                        onSwipePage = { direction ->
                            val target = (pagerState.currentPage + direction).coerceIn(0, pageCount - 1)
                            if (target != pagerState.currentPage) {
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (pageCount > 1) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            "${pagerState.currentPage + 1} / $pageCount",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    index: Int,
    source: PdfPageSource,
    cache: PageBitmapCache,
    targetWidthPx: Int,
    isCurrent: Boolean,
    onSwipePage: (direction: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(index) { mutableStateOf(cache.get(index)) }

    LaunchedEffect(index) {
        if (bitmap == null) {
            val rendered = withContext(Dispatchers.Default) { source.renderPage(index, targetWidthPx) }
            cache.put(index, rendered)
            bitmap = rendered
        }
    }

    var scale by remember(index) { mutableStateOf(1f) }
    var offset by remember(index) { mutableStateOf(Offset.Zero) }

    // Coming back to a page after turning away should show it whole again, not stuck zoomed in.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            scale = MIN_ZOOM
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(index) {
                // A single-finger drag is left untouched while at normal scale, so the pager's
                // own swipe-to-turn-page gesture still works. Once the page is zoomed in, a
                // single finger pans it instead — but a long, mostly-horizontal swipe still turns
                // the page, so you never have to pinch back out just to move on.
                val swipeThresholdPx = size.width * PAGE_TURN_SWIPE_FRACTION
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var handledHere = false
                    var multiTouch = false
                    var totalDrag = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) multiTouch = true
                        if (pressedCount >= 2 || scale > MIN_ZOOM) {
                            handledHere = true
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (!multiTouch) totalDrag += panChange
                            val newScale = (scale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            scale = newScale
                            offset = if (newScale <= MIN_ZOOM) {
                                Offset.Zero
                            } else {
                                clampOffset(offset + panChange, newScale, size.width, size.height)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    // Pinches never count as page turns, even if the fingers drift sideways.
                    if (handledHere && !multiTouch &&
                        abs(totalDrag.x) >= swipeThresholdPx &&
                        abs(totalDrag.x) > abs(totalDrag.y) * 1.5f
                    ) {
                        onSwipePage(if (totalDrag.x < 0) 1 else -1)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}
