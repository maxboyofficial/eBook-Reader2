package com.example.ui.reader

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.db.AnnotationLayerEntity
import com.example.data.db.MarkupObjectEntity
import com.example.data.db.PageScaleEntity
import com.example.data.preferences.PageTurnMode
import com.example.data.preferences.PdfFitMode
import com.example.data.preferences.ReaderSettings
import com.example.data.reader.ParsedDocument
import com.example.data.reader.PdfPageEngine
import com.example.data.studio.StudioGeometryJson
import com.example.data.studio.StudioSessionState
import com.example.data.studio.StudioTool
import com.example.theme.MochiThemePalette
import com.example.ui.designsystem.LocalMochiMotion
import com.example.ui.studio.AnnotationStudioCanvasLayer
import com.example.ui.theme.LocalMochiPalette
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * High-resolution Fixed-Layout PDF Viewport (Milestone E) + GPU Page Curl & Page Turns (Milestone F):
 * - Renders via [PdfPageEngine] (LRU cached, Smart Dark Invert, Auto Margin Crop).
 * - Supports Fit-Page / Fit-Width, pinch zoom (1x-10x), smart double-tap zoom, and pan.
 * - Aligns [AnnotationStudioCanvasLayer] 1:1 with the PDF page coordinate frame.
 * - Integrates [InteractivePageCurlContainer] when zoomed to ~1x and not actively drawing in Studio.
 */
@Composable
fun FixedLayoutPdfViewport(
    document: ParsedDocument,
    currentPageIndex: Int,
    settings: ReaderSettings,
    palette: MochiThemePalette,
    studioActive: Boolean,
    studioLayers: List<AnnotationLayerEntity>,
    studioMarkups: List<MarkupObjectEntity>,
    studioScales: List<PageScaleEntity>,
    studioSession: StudioSessionState,
    programmaticTurnRequest: Pair<Int, Long>?,
    onPageChanged: (Int) -> Unit,
    onAnimationActiveChanged: (Boolean) -> Unit,
    onCenterTap: (Float) -> Unit,
    onCreateMarkupDraft: (Int, StudioTool, StudioGeometryJson) -> Unit,
    onSelectMarkups: (Set<Long>) -> Unit,
    onMoveSelectedMarkups: (Float, Float) -> Unit,
    onCalibrateSegmentMeasured: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val motion = LocalMochiMotion.current
    val scope = rememberCoroutineScope()

    val pageCount = document.pdfPageCount.coerceAtLeast(1)
    val safePage = currentPageIndex.coerceIn(0, pageCount - 1)

    var zoomScale by remember(safePage) { mutableFloatStateOf(1f) }
    var panOffset by remember(safePage) { mutableStateOf(Offset.Zero) }

    val pagerState = rememberPagerState(initialPage = safePage) { pageCount }
    var lastReportedPage by remember { mutableIntStateOf(safePage) }

    // Pre-warm neighbor pages in PdfPageEngine LRU cache
    LaunchedEffect(safePage, settings.pdfSmartDarkInvert, document.bookUri) {
        val warmWidth = 1200
        if (safePage + 1 < pageCount) {
            PdfPageEngine.renderPageBitmap(context, document.bookUri, safePage + 1, warmWidth, settings.pdfSmartDarkInvert)
        }
        if (safePage - 1 >= 0) {
            PdfPageEngine.renderPageBitmap(context, document.bookUri, safePage - 1, warmWidth, settings.pdfSmartDarkInvert)
        }
    }

    // Sync pager when jumping externally (scrubber / TOC / undo / thumbnails / buttons)
    LaunchedEffect(safePage, settings.pageTurnMode) {
        if (settings.pageTurnMode != PageTurnMode.CURL &&
            !pagerState.isScrollInProgress &&
            pagerState.currentPage != safePage &&
            safePage != lastReportedPage
        ) {
            pagerState.scrollToPage(safePage)
        }
        lastReportedPage = safePage
    }

    LaunchedEffect(programmaticTurnRequest, settings.pageTurnMode) {
        val req = programmaticTurnRequest ?: return@LaunchedEffect
        if (settings.pageTurnMode == PageTurnMode.CURL) return@LaunchedEffect
        val dest = req.first.coerceIn(0, pageCount - 1)
        if (dest != pagerState.currentPage) {
            if (motion.reduceMotion || settings.pageTurnMode == PageTurnMode.NONE) {
                pagerState.scrollToPage(dest)
            } else {
                pagerState.animateScrollToPage(dest, animationSpec = motion.spatialDefault())
            }
        }
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress, settings.pageTurnMode) {
        if (settings.pageTurnMode != PageTurnMode.CURL) {
            onAnimationActiveChanged(pagerState.isScrollInProgress)
            if (pagerState.currentPage != lastReportedPage) {
                lastReportedPage = pagerState.currentPage
                onPageChanged(pagerState.currentPage)
            }
        }
    }

    val useCurl = settings.pageTurnMode == PageTurnMode.CURL && zoomScale <= 1.05f && !studioActive

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("pdf_fixed_viewport")
    ) {
        if (useCurl) {
            InteractivePageCurlContainer(
                currentPageIndex = safePage,
                pageCount = pageCount,
                paperTintColor = Color(palette.paperTint),
                curlStrength = settings.curlStrength,
                curlShadowStrength = settings.curlShadowStrength,
                curlShowPageBack = settings.curlShowPageBack,
                curlSpeed = settings.curlSpeed,
                grabMode = settings.curlGrabMode,
                reduceMotion = motion.reduceMotion,
                onTurnToPage = onPageChanged,
                onAnimationActiveChanged = onAnimationActiveChanged,
                onCenterTap = onCenterTap,
                programmaticTurnRequest = programmaticTurnRequest
            ) { pageIdx ->
                PdfSinglePageSurface(
                    document = document,
                    pageIndex = pageIdx,
                    settings = settings,
                    palette = palette,
                    studioActive = false,
                    studioLayers = studioLayers,
                    pageMarkups = studioMarkups.filter { it.pageIndex == pageIdx },
                    pageScale = studioScales.firstOrNull { it.pageIndex == pageIdx },
                    studioSession = studioSession,
                    zoomScale = 1f,
                    panOffset = Offset.Zero,
                    onTransformChange = { newZoom, newPan ->
                        zoomScale = newZoom
                        panOffset = newPan
                    },
                    onCreateMarkupDraft = { tool, geo -> onCreateMarkupDraft(pageIdx, tool, geo) },
                    onSelectMarkups = onSelectMarkups,
                    onMoveSelectedMarkups = onMoveSelectedMarkups,
                    onCalibrateSegmentMeasured = onCalibrateSegmentMeasured
                )
            }
        } else {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = !studioActive && zoomScale <= 1.05f,
                modifier = Modifier.fillMaxSize()
            ) { pageIdx ->
                val offsetFraction = (pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction
                val transformModifier = when (settings.pageTurnMode) {
                    PageTurnMode.SLIDE -> Modifier
                    PageTurnMode.COVER_FLIP -> Modifier.graphicsLayer {
                        if (!motion.reduceMotion) {
                            translationX = offsetFraction * size.width * 0.42f
                            rotationY = (offsetFraction * -34f).coerceIn(-45f, 45f)
                            cameraDistance = 16f * density.density
                            transformOrigin = TransformOrigin(if (offsetFraction >= 0f) 0f else 1f, 0.5f)
                            alpha = (1f - offsetFraction.absoluteValue * 0.35f).coerceIn(0f, 1f)
                        }
                    }
                    PageTurnMode.STACK -> Modifier.graphicsLayer {
                        if (!motion.reduceMotion) {
                            val absOff = offsetFraction.absoluteValue
                            if (offsetFraction < 0f) {
                                // Underneath incoming/previous sheet: stays stacked & grows from 0.91 -> 1.0
                                translationX = offsetFraction * size.width
                                val s = (1f - absOff * 0.09f).coerceAtLeast(0.88f)
                                scaleX = s
                                scaleY = s
                                alpha = (1f - absOff * 0.30f).coerceIn(0.2f, 1f)
                            } else {
                                alpha = (1f - absOff * 0.15f).coerceIn(0.4f, 1f)
                            }
                        }
                    }
                    PageTurnMode.FADE -> Modifier.graphicsLayer {
                        translationX = offsetFraction * size.width * 0.94f
                        alpha = (1f - offsetFraction.absoluteValue * 1.1f).coerceIn(0f, 1f)
                    }
                    PageTurnMode.CURL, PageTurnMode.NONE, PageTurnMode.SCROLL -> Modifier
                }

                PdfSinglePageSurface(
                    document = document,
                    pageIndex = pageIdx,
                    settings = settings,
                    palette = palette,
                    studioActive = studioActive && pageIdx == pagerState.currentPage,
                    studioLayers = studioLayers,
                    pageMarkups = studioMarkups.filter { it.pageIndex == pageIdx },
                    pageScale = studioScales.firstOrNull { it.pageIndex == pageIdx },
                    studioSession = studioSession,
                    zoomScale = if (pageIdx == pagerState.currentPage) zoomScale else 1f,
                    panOffset = if (pageIdx == pagerState.currentPage) panOffset else Offset.Zero,
                    onTransformChange = { z, p ->
                        if (pageIdx == pagerState.currentPage) {
                            zoomScale = z
                            panOffset = p
                        }
                    },
                    onCenterTap = if (!studioActive && zoomScale <= 1.05f) onCenterTap else null,
                    onCreateMarkupDraft = { tool, geo -> onCreateMarkupDraft(pageIdx, tool, geo) },
                    onSelectMarkups = onSelectMarkups,
                    onMoveSelectedMarkups = onMoveSelectedMarkups,
                    onCalibrateSegmentMeasured = onCalibrateSegmentMeasured,
                    modifier = Modifier.then(transformModifier)
                )
            }
        }
    }
}

@Composable
private fun PdfSinglePageSurface(
    document: ParsedDocument,
    pageIndex: Int,
    settings: ReaderSettings,
    palette: MochiThemePalette,
    studioActive: Boolean,
    studioLayers: List<AnnotationLayerEntity>,
    pageMarkups: List<MarkupObjectEntity>,
    pageScale: PageScaleEntity?,
    studioSession: StudioSessionState,
    zoomScale: Float,
    panOffset: Offset,
    onTransformChange: (Float, Offset) -> Unit,
    onCreateMarkupDraft: (StudioTool, StudioGeometryJson) -> Unit,
    onSelectMarkups: (Set<Long>) -> Unit,
    onMoveSelectedMarkups: (Float, Float) -> Unit,
    onCalibrateSegmentMeasured: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onCenterTap: ((Float) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val ptPair = document.pdfPageSizes.getOrNull(pageIndex) ?: (612 to 792)
    val pageWidthPts = ptPair.first.coerceAtLeast(100).toFloat()
    val pageHeightPts = ptPair.second.coerceAtLeast(100).toFloat()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = if (settings.pdfFitMode == PdfFitMode.FIT_WIDTH) 0.dp else 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val maxWPx = with(density) { maxWidth.toPx() }.coerceAtLeast(300f)
        val maxHPx = with(density) { maxHeight.toPx() }.coerceAtLeast(300f)

        // Increase raster resolution dynamically up to 2.5x when zooming in
        val desiredRenderPx = (maxWPx * zoomScale.coerceIn(1f, 2.5f)).roundToInt().coerceIn(480, 2600)
        var bitmap by remember(document.bookUri, pageIndex, settings.pdfSmartDarkInvert) { mutableStateOf<Bitmap?>(null) }
        var cropRect by remember(document.bookUri, pageIndex, settings.pdfAutoMarginCrop) {
            mutableStateOf(RectF(0f, 0f, 1f, 1f))
        }

        LaunchedEffect(document.bookUri, pageIndex, desiredRenderPx, settings.pdfSmartDarkInvert, settings.pdfAutoMarginCrop) {
            val bmp = PdfPageEngine.renderPageBitmap(
                context = context,
                uriString = document.bookUri,
                pageIndex = pageIndex,
                targetWidthPx = desiredRenderPx,
                smartDarkInvert = settings.pdfSmartDarkInvert
            )
            bitmap = bmp
            if (bmp != null && settings.pdfAutoMarginCrop) {
                cropRect = PdfPageEngine.detectNormalizedContentBounds(bmp)
            } else {
                cropRect = RectF(0f, 0f, 1f, 1f)
            }
        }

        val effectiveCropW = (cropRect.width()).coerceIn(0.3f, 1f)
        val effectiveCropH = (cropRect.height()).coerceIn(0.3f, 1f)
        val effectiveAspect = (pageWidthPts * effectiveCropW) / (pageHeightPts * effectiveCropH)

        // Compute displayed page dimensions cleanly
        val (pageBoxW, pageBoxH) = if (settings.pdfFitMode == PdfFitMode.FIT_WIDTH) {
            maxWidth to (maxWidth / effectiveAspect)
        } else {
            val containerAspect = maxWPx / maxHPx
            if (containerAspect > effectiveAspect) {
                (maxHeight * effectiveAspect) to maxHeight
            } else {
                maxWidth to (maxWidth / effectiveAspect)
            }
        }

        val bmp = bitmap
        if (bmp == null) {
            Box(
                modifier = Modifier
                    .size(pageBoxW, pageBoxH)
                    .background(Color(palette.surfaceVariant).copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(palette.accent), modifier = Modifier.size(32.dp))
            }
        } else {
            val gestureModifier = if (!studioActive) {
                Modifier
                    .pointerInput(pageIndex, zoomScale, panOffset) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextZ = (zoomScale * zoom).coerceIn(1f, 10f)
                            val nextPan = if (nextZ <= 1.03f) Offset.Zero else panOffset + pan
                            onTransformChange(nextZ, nextPan)
                        }
                    }
                    .pointerInput(pageIndex, zoomScale, cropRect) {
                        detectTapGestures(
                            onDoubleTap = { tap ->
                                if (zoomScale > 1.25f) {
                                    onTransformChange(1f, Offset.Zero)
                                } else {
                                    val targetZoom = 2.35f
                                    val dx = (size.width * 0.5f - tap.x) * (targetZoom - 1f)
                                    val dy = (size.height * 0.5f - tap.y) * (targetZoom - 1f)
                                    onTransformChange(targetZoom, Offset(dx, dy))
                                }
                            },
                            onTap = { tap ->
                                if (zoomScale <= 1.05f) {
                                    onCenterTap?.invoke(tap.x)
                                }
                            }
                        )
                    }
            } else Modifier

            Box(
                modifier = Modifier
                    .size(pageBoxW, pageBoxH)
                    .then(gestureModifier)
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
                    .shadow(8.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (settings.pdfSmartDarkInvert) Color(0xFF1D1F24) else Color.White)
            ) {
                val croppedBitmap = remember(bmp, cropRect, settings.pdfAutoMarginCrop) {
                    if (!settings.pdfAutoMarginCrop || (cropRect.left <= 0.01f && cropRect.right >= 0.99f)) {
                        bmp.asImageBitmap()
                    } else {
                        val sx = (cropRect.left * bmp.width).roundToInt().coerceIn(0, bmp.width - 2)
                        val sy = (cropRect.top * bmp.height).roundToInt().coerceIn(0, bmp.height - 2)
                        val sw = (cropRect.width() * bmp.width).roundToInt().coerceIn(2, bmp.width - sx)
                        val sh = (cropRect.height() * bmp.height).roundToInt().coerceIn(2, bmp.height - sy)
                        Bitmap.createBitmap(bmp, sx, sy, sw, sh).asImageBitmap()
                    }
                }

                Image(
                    bitmap = croppedBitmap,
                    contentDescription = "PDF Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )

                // CAD Studio vector overlay sits directly over PDF page inside zoom/pan transform
                AnnotationStudioCanvasLayer(
                    pageWidthPts = pageWidthPts,
                    pageHeightPts = pageHeightPts,
                    pageMarkups = pageMarkups,
                    layers = studioLayers,
                    pageScale = pageScale,
                    session = studioSession,
                    studioActive = studioActive,
                    palmRejectionEnabled = settings.studioPalmRejection,
                    onCreateMarkupDraft = onCreateMarkupDraft,
                    onSelectMarkups = onSelectMarkups,
                    onMoveSelected = onMoveSelectedMarkups,
                    onCalibrateSegmentMeasured = onCalibrateSegmentMeasured,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfThumbnailsSheet(
    bookUri: String,
    pageCount: Int,
    currentPageIndex: Int,
    smartDarkInvert: Boolean,
    onSelectPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current
    val context = LocalContext.current
    val pageNumbers = remember(pageCount) { (0 until pageCount).toList() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface),
        modifier = Modifier.testTag("pdf_thumbnails_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Pages ($pageCount)",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(palette.text)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(420.dp)
            ) {
                items(pageNumbers, key = { it }) { idx ->
                    var thumb by remember(bookUri, idx, smartDarkInvert) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(bookUri, idx, smartDarkInvert) {
                        thumb = PdfPageEngine.renderPageBitmap(
                            context = context,
                            uriString = bookUri,
                            pageIndex = idx,
                            targetWidthPx = 300,
                            smartDarkInvert = smartDarkInvert
                        )
                    }
                    val selected = idx == currentPageIndex
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(palette.surfaceVariant).copy(alpha = 0.5f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) Color(palette.accent) else Color(palette.textSecondary).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onSelectPage(idx)
                                onDismiss()
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.76f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                val t = thumb
                                if (t != null) {
                                    Image(
                                        bitmap = t.asImageBitmap(),
                                        contentDescription = "Thumbnail page ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text("${idx + 1}", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                                }
                            }
                            Text(
                                text = "Page ${idx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color(palette.accent) else Color(palette.textSecondary)
                            )
                        }
                    }
                }
            }
        }
    }
}
