package com.example.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.CallMissedOutgoing
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Hexagon
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.db.AnnotationLayerEntity
import com.example.data.db.MarkupObjectEntity
import com.example.data.db.PageScaleEntity
import com.example.data.studio.SnapKind
import com.example.data.studio.StudioCanvasRenderer
import com.example.data.studio.StudioGeometry
import com.example.data.studio.StudioGeometryJson
import com.example.data.studio.StudioPoint
import com.example.data.studio.StudioSessionState
import com.example.data.studio.StudioStyleJson
import com.example.data.studio.StudioTool
import com.example.theme.ContrastChecker
import com.example.ui.designsystem.LiquidGlassSurface
import com.example.ui.theme.LocalMochiPalette
import kotlin.math.hypot

@Composable
fun AnnotationStudioCanvasLayer(
    pageWidthPts: Float,
    pageHeightPts: Float,
    pageMarkups: List<MarkupObjectEntity>,
    layers: List<AnnotationLayerEntity>,
    pageScale: PageScaleEntity?,
    session: StudioSessionState,
    studioActive: Boolean,
    palmRejectionEnabled: Boolean,
    onCreateMarkupDraft: (StudioTool, StudioGeometryJson) -> Unit,
    onSelectMarkups: (Set<Long>) -> Unit,
    onMoveSelected: (Float, Float) -> Unit,
    onCalibrateSegmentMeasured: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMochiPalette.current
    val layersMap = remember(layers) { layers.associateBy { it.id } }

    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    var draftPreview by remember { mutableStateOf<MarkupObjectEntity?>(null) }
    var activeSnapBadge by remember { mutableStateOf<Pair<StudioPoint, SnapKind>?>(null) }
    var hoverPoint by remember { mutableStateOf<StudioPoint?>(null) }
    var loupeFocusNorm by remember { mutableStateOf<StudioPoint?>(null) }

    // Drag state for MOVE mode on selected objects
    var isDraggingSelected by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().onSizeChanged { canvasSize = it }) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!studioActive) Modifier
                    else Modifier
                        .pointerInput(layersMap, pageScale) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val first = event.changes.firstOrNull() ?: continue
                                    val isStylusOrMouse = first.type == PointerType.Stylus || first.type == PointerType.Mouse
                                    if (event.type == PointerEventType.Move && isStylusOrMouse) {
                                        hoverPoint = StudioPoint(
                                            (first.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                            (first.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        )
                                    } else if (event.type == PointerEventType.Exit) {
                                        hoverPoint = null
                                    }
                                }
                            }
                        }
                        .pointerInput(
                            session.activeTool,
                            session.activeLayerId,
                            session.snapEnabled,
                            session.gridSnapEnabled,
                            session.orthoLockEnabled,
                            session.activeStyle,
                            pageMarkups,
                            palmRejectionEnabled
                        ) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Multi-touch / palm rejection guard: when palm rejection is on and non-stylus down happens while stylus present, or 2+ fingers touch (zoom/pan), yield
                                if (currentEvent.changes.count { it.pressed } > 1) return@awaitEachGesture
                                if (palmRejectionEnabled && down.type == PointerType.Touch && session.activeTool == StudioTool.FREEHAND) {
                                    // Allow single finger only if contact pressure/size isn't a huge palm blob
                                    if (down.pressure > 0.98f && down.position == Offset.Zero) return@awaitEachGesture
                                }

                                val activeLayer = layersMap[session.activeLayerId]
                                if (activeLayer?.locked == true && session.activeTool.isCreation) return@awaitEachGesture

                                down.consume()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)

                                val rawStart = StudioPoint(down.position.x / w, down.position.y / h, down.pressure)
                                val snappedStart = StudioGeometry.snapPoint(
                                    raw = rawStart,
                                    existingMarkups = pageMarkups,
                                    snapEnabled = session.snapEnabled,
                                    gridSnapEnabled = session.gridSnapEnabled
                                )
                                activeSnapBadge = snappedStart.kind?.let { snappedStart.point to it }

                                if (session.activeTool == StudioTool.SELECT) {
                                    val hit = StudioGeometry.hitTest(rawStart, pageMarkups)
                                    if (hit == null) {
                                        onSelectMarkups(emptySet())
                                        return@awaitEachGesture
                                    }
                                    val layer = layersMap[hit.layerId]
                                    if (layer?.locked == true) return@awaitEachGesture
                                    val nextSel = if (hit.id in session.selectedIds) session.selectedIds else setOf(hit.id)
                                    onSelectMarkups(nextSel)

                                    var accumDx = 0f
                                    var accumDy = 0f
                                    isDraggingSelected = true
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        if (ev.changes.count { it.pressed } > 1) {
                                            isDraggingSelected = false
                                            return@awaitEachGesture
                                        }
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!ch.pressed) {
                                            isDraggingSelected = false
                                            if (hypot(accumDx, accumDy) > 0.004f) {
                                                onMoveSelected(accumDx, accumDy)
                                            }
                                            break
                                        }
                                        val d = ch.positionChange()
                                        accumDx += d.x / w
                                        accumDy += d.y / h
                                        ch.consume()
                                    }
                                    return@awaitEachGesture
                                }

                                // Instant single-tap placement tools (Stamp / Text Note)
                                if (session.activeTool == StudioTool.STAMP || session.activeTool == StudioTool.TEXT_NOTE) {
                                    val p = snappedStart.point
                                    onCreateMarkupDraft(
                                        session.activeTool,
                                        StudioGeometryJson(
                                            x1 = p.x,
                                            y1 = p.y,
                                            x2 = (p.x + 0.22f).coerceAtMost(0.98f),
                                            y2 = (p.y + 0.07f).coerceAtMost(0.98f),
                                            stampKind = session.activeStamp.name,
                                            text = if (session.activeTool == StudioTool.STAMP) session.activeStamp.badgeText else ""
                                        )
                                    )
                                    activeSnapBadge = null
                                    return@awaitEachGesture
                                }

                                // Drag-create geometry (Lines, Arrows, Shapes, Measure, Freehand, Calibrate)
                                val freehandPts = mutableListOf(snappedStart.point)
                                val startPt = snappedStart.point
                                var endPt = startPt

                                while (true) {
                                    val ev = awaitPointerEvent()
                                    if (ev.changes.count { it.pressed } > 1) {
                                        draftPreview = null
                                        loupeFocusNorm = null
                                        activeSnapBadge = null
                                        return@awaitEachGesture
                                    }
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) {
                                        loupeFocusNorm = null
                                        activeSnapBadge = null
                                        val draft = draftPreview
                                        draftPreview = null
                                        if (session.activeTool == StudioTool.CALIBRATE) {
                                            val ptsDist = hypot(
                                                (endPt.x - startPt.x) * pageWidthPts,
                                                (endPt.y - startPt.y) * pageHeightPts
                                            )
                                            if (ptsDist >= 4f) {
                                                onCalibrateSegmentMeasured(ptsDist)
                                            }
                                        } else if (draft != null) {
                                            val distNorm = hypot(endPt.x - startPt.x, endPt.y - startPt.y)
                                            if (distNorm > 0.01f || freehandPts.size > 3) {
                                                onCreateMarkupDraft(session.activeTool, StudioGeometryJson.fromJson(draft.geometryJson))
                                            }
                                        }
                                        break
                                    }

                                    ch.consume()
                                    val rawCur = StudioPoint(ch.position.x / w, ch.position.y / h, ch.pressure)
                                    loupeFocusNorm = rawCur

                                    if (session.activeTool == StudioTool.FREEHAND || session.activeTool == StudioTool.HIGHLIGHTER) {
                                        val last = freehandPts.last()
                                        if (hypot(rawCur.x - last.x, rawCur.y - last.y) >= 0.004f) {
                                            freehandPts.add(rawCur)
                                        }
                                        endPt = rawCur
                                        val geo = StudioGeometryJson(points = freehandPts.toList())
                                        draftPreview = MarkupObjectEntity(
                                            bookId = 0L,
                                            pageIndex = 0,
                                            layerId = session.activeLayerId,
                                            kind = session.activeTool.name,
                                            geometryJson = StudioGeometryJson.toJson(geo),
                                            styleJson = StudioStyleJson.toJson(session.activeStyle)
                                        )
                                    } else {
                                        val constrained = if (session.orthoLockEnabled) {
                                            StudioGeometry.applyAngleConstraint(startPt, rawCur, 45f)
                                        } else rawCur
                                        val snappedEnd = StudioGeometry.snapPoint(
                                            raw = constrained,
                                            existingMarkups = pageMarkups,
                                            snapEnabled = session.snapEnabled,
                                            gridSnapEnabled = session.gridSnapEnabled
                                        )
                                        endPt = snappedEnd.point
                                        activeSnapBadge = snappedEnd.kind?.let { endPt to it }

                                        val geo = when (session.activeTool) {
                                            StudioTool.CALLOUT -> StudioGeometryJson(
                                                x1 = endPt.x,
                                                y1 = endPt.y,
                                                x2 = (endPt.x + 0.22f).coerceAtMost(0.98f),
                                                y2 = (endPt.y + 0.07f).coerceAtMost(0.98f),
                                                calloutTargetX = startPt.x,
                                                calloutTargetY = startPt.y,
                                                text = "Detail"
                                            )
                                            StudioTool.DIMENSION_ANGLE -> StudioGeometryJson(
                                                points = listOf(
                                                    startPt,
                                                    StudioPoint((startPt.x + 0.14f).coerceAtMost(0.98f), startPt.y),
                                                    endPt
                                                )
                                            )
                                            StudioTool.DIMENSION_AREA -> StudioGeometryJson(
                                                points = listOf(
                                                    startPt,
                                                    StudioPoint(endPt.x, startPt.y),
                                                    endPt,
                                                    StudioPoint(startPt.x, endPt.y)
                                                )
                                            )
                                            else -> StudioGeometryJson(x1 = startPt.x, y1 = startPt.y, x2 = endPt.x, y2 = endPt.y)
                                        }

                                        val previewKind = if (session.activeTool == StudioTool.CALIBRATE) StudioTool.DIMENSION_DISTANCE.name else session.activeTool.name
                                        draftPreview = MarkupObjectEntity(
                                            bookId = 0L,
                                            pageIndex = 0,
                                            layerId = session.activeLayerId,
                                            kind = previewKind,
                                            geometryJson = StudioGeometryJson.toJson(geo),
                                            styleJson = StudioStyleJson.toJson(session.activeStyle)
                                        )
                                    }
                                }
                            }
                        }
                )
        ) {
            // Optional light snapping grid when Studio active & gridSnap on
            if (studioActive && session.snapEnabled && session.gridSnapEnabled) {
                val cols = 16
                val stepX = size.width / cols
                val dotCol = Color(palette.textSecondary).copy(alpha = 0.16f)
                var gx = stepX
                while (gx < size.width) {
                    var gy = stepX
                    while (gy < size.height) {
                        drawCircle(dotCol, radius = 1.2.dp.toPx(), center = Offset(gx, gy))
                        gy += stepX
                    }
                    gx += stepX
                }
            }

            // Persistent + live draft vector markups via shared native renderer
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                StudioCanvasRenderer.drawPageMarkups(
                    canvas = nc,
                    pageWidth = size.width,
                    pageHeight = size.height,
                    markups = pageMarkups,
                    layersById = layersMap,
                    pageScale = pageScale,
                    selectedIds = session.selectedIds,
                    pageWidthPts = pageWidthPts,
                    pageHeightPts = pageHeightPts,
                    strokeScale = size.width / pageWidthPts.coerceAtLeast(100f)
                )
                draftPreview?.let { draft ->
                    StudioCanvasRenderer.drawMarkup(
                        canvas = nc,
                        pageWidth = size.width,
                        pageHeight = size.height,
                        markup = draft,
                        layerOpacity = 0.9f,
                        pageScale = pageScale,
                        isSelected = false,
                        pageWidthPts = pageWidthPts,
                        pageHeightPts = pageHeightPts,
                        strokeScale = size.width / pageWidthPts.coerceAtLeast(100f)
                    )
                }
            }

            // Hover / snap target reticle
            val snap = activeSnapBadge
            if (snap != null) {
                val pt = Offset(snap.first.x * size.width, snap.first.y * size.height)
                drawCircle(Color(0xFF2F7CF6), radius = 8.dp.toPx(), center = pt, style = Stroke(2.dp.toPx()))
            }
            val hover = hoverPoint
            if (hover != null && studioActive) {
                val pt = Offset(hover.x * size.width, hover.y * size.height)
                drawCircle(Color(palette.accent), radius = 6.dp.toPx(), center = pt, style = Stroke(1.5.dp.toPx()))
            }
        }

        // Precision 2.5x Loupe HUD in top corner while dragging a geometry endpoint
        val loupe = loupeFocusNorm
        if (loupe != null && studioActive && session.activeTool != StudioTool.FREEHAND) {
            LiquidGlassSurface(
                shape = RoundedCornerShape(18.dp),
                elevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(116.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                    val cx = size.width * 0.5f
                    val cy = size.height * 0.5f
                    drawRect(Color(palette.background).copy(alpha = 0.85f))
                    // Crosshair at zoomed focus point
                    drawLine(Color(palette.accent), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.dp.toPx())
                    drawLine(Color(palette.accent), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1.dp.toPx())
                    drawCircle(Color(palette.accent), radius = 4.dp.toPx(), center = Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                }
            }
        }

        // Snap indicator pill
        activeSnapBadge?.let { (_, kind) ->
            Surface(
                shape = CircleShape,
                color = Color(palette.accent),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = "Snap: ${kind.label}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = ContrastChecker.bestOnColor(Color(palette.accent)),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private data class ToolDockItem(
    val tool: StudioTool,
    val icon: ImageVector
)

private val studioToolItems = listOf(
    ToolDockItem(StudioTool.SELECT, Icons.Rounded.NearMe),
    ToolDockItem(StudioTool.FREEHAND, Icons.Rounded.Draw),
    ToolDockItem(StudioTool.HIGHLIGHTER, Icons.Rounded.BorderColor),
    ToolDockItem(StudioTool.LINE, Icons.Rounded.Timeline),
    ToolDockItem(StudioTool.ARROW, Icons.Rounded.CallMissedOutgoing),
    ToolDockItem(StudioTool.RECT, Icons.Rounded.CheckBoxOutlineBlank),
    ToolDockItem(StudioTool.ELLIPSE, Icons.Rounded.RadioButtonUnchecked),
    ToolDockItem(StudioTool.POLYGON, Icons.Rounded.Hexagon),
    ToolDockItem(StudioTool.CLOUD, Icons.Rounded.BubbleChart),
    ToolDockItem(StudioTool.CALLOUT, Icons.Rounded.Edit),
    ToolDockItem(StudioTool.DIMENSION_DISTANCE, Icons.Rounded.Straighten),
    ToolDockItem(StudioTool.DIMENSION_AREA, Icons.Rounded.GridOn),
    ToolDockItem(StudioTool.TEXT_NOTE, Icons.Rounded.NoteAdd),
    ToolDockItem(StudioTool.STAMP, Icons.Rounded.Verified),
    ToolDockItem(StudioTool.SPOTLIGHT, Icons.Rounded.Highlight),
    ToolDockItem(StudioTool.CALIBRATE, Icons.Rounded.Architecture)
)

@Composable
fun StudioFloatingDock(
    session: StudioSessionState,
    onSelectTool: (StudioTool) -> Unit,
    onToggleSnap: () -> Unit,
    onToggleOrtho: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenInspector: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenExport: () -> Unit,
    onCopySelected: () -> Unit,
    onPasteClipboard: () -> Unit,
    onAlignLeft: () -> Unit,
    onAlignCenter: () -> Unit,
    onAlignRight: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExitStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMochiPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Multi-select / object contextual bar
        AnimatedVisibility(visible = session.selectedIds.isNotEmpty()) {
            LiquidGlassSurface(shape = CircleShape, elevation = 10.dp) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${session.selectedIds.size} sel",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(palette.link),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    IconButton(onClick = onOpenInspector) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Properties", tint = Color(palette.text))
                    }
                    IconButton(onClick = onCopySelected) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy markup", tint = Color(palette.text))
                    }
                    if (session.selectedIds.size >= 2) {
                        IconButton(onClick = onAlignLeft) {
                            Icon(Icons.Rounded.FormatAlignLeft, contentDescription = "Align left", tint = Color(palette.text))
                        }
                        IconButton(onClick = onAlignCenter) {
                            Icon(Icons.Rounded.FormatAlignCenter, contentDescription = "Align center", tint = Color(palette.text))
                        }
                        IconButton(onClick = onAlignRight) {
                            Icon(Icons.Rounded.FormatAlignRight, contentDescription = "Align right", tint = Color(palette.text))
                        }
                    }
                    IconButton(onClick = onDeleteSelected) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Main Studio CAD Dock
        LiquidGlassSurface(
            shape = RoundedCornerShape(24.dp),
            elevation = 12.dp,
            modifier = Modifier.testTag("studio_floating_dock")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Action strip: Undo, Redo, Snap, Ortho, Paste, Layers, Inspector, Export, Exit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onExitStudio, modifier = Modifier.testTag("studio_close_button")) {
                        Icon(Icons.Rounded.Close, contentDescription = "Exit Studio", tint = Color(palette.text))
                    }
                    IconButton(onClick = onUndo, enabled = session.undoStack.isNotEmpty(), modifier = Modifier.testTag("studio_undo_button")) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = "Undo markup",
                            tint = if (session.undoStack.isNotEmpty()) Color(palette.text) else Color(palette.textSecondary).copy(alpha = 0.4f)
                        )
                    }
                    IconButton(onClick = onRedo, enabled = session.redoStack.isNotEmpty(), modifier = Modifier.testTag("studio_redo_button")) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Redo,
                            contentDescription = "Redo markup",
                            tint = if (session.redoStack.isNotEmpty()) Color(palette.text) else Color(palette.textSecondary).copy(alpha = 0.4f)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (session.snapEnabled) Color(palette.accentContainer) else Color.Transparent,
                        onClick = onToggleSnap,
                        modifier = Modifier.testTag("studio_snap_toggle")
                    ) {
                        Text(
                            text = if (session.snapEnabled) "Snap ON" else "Snap",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(palette.text),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (session.orthoLockEnabled) Color(palette.accentContainer) else Color.Transparent,
                        onClick = onToggleOrtho,
                        modifier = Modifier.testTag("studio_ortho_toggle")
                    ) {
                        Text(
                            text = if (session.orthoLockEnabled) "Ortho 45°" else "Free°",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(palette.text),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    if (session.clipboard.isNotEmpty()) {
                        IconButton(onClick = onPasteClipboard) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", tint = Color(palette.accent))
                        }
                    }

                    IconButton(onClick = onOpenLayers, modifier = Modifier.testTag("studio_layers_button")) {
                        Icon(Icons.Rounded.Layers, contentDescription = "Layers", tint = Color(palette.text))
                    }
                    IconButton(onClick = onOpenInspector, modifier = Modifier.testTag("studio_inspector_button")) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Style & Inspector", tint = Color(palette.text))
                    }
                    IconButton(onClick = onOpenExport, modifier = Modifier.testTag("studio_export_button")) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = "Export PDF / SVG / JSON", tint = Color(palette.accent))
                    }
                }

                // CAD Tool Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    studioToolItems.forEach { item ->
                        val selected = session.activeTool == item.tool
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) Color(palette.accentContainer) else Color.Transparent)
                                .clickable { onSelectTool(item.tool) }
                                .testTag("studio_tool_${item.tool.name.lowercase()}")
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.tool.label,
                                tint = if (selected) ContrastChecker.bestOnColor(Color(palette.accentContainer)) else Color(palette.text),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = item.tool.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) ContrastChecker.bestOnColor(Color(palette.accentContainer)) else Color(palette.text)
                            )
                        }
                    }
                }
            }
        }
    }
}
