package com.example.data.studio

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.example.data.db.AnnotationLayerEntity
import com.example.data.db.MarkupObjectEntity
import com.example.data.db.PageScaleEntity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Single deterministic Android [Canvas] renderer shared between:
 *  - Interactive Jetpack Compose Fixed-Layout / Studio Viewport
 *  - 2.5x Precision Magnifier Loupe
 *  - Non-destructive Annotated PDF Copy export (`android.graphics.pdf.PdfDocument`)
 */
object StudioCanvasRenderer {

    fun drawPageMarkups(
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        markups: List<MarkupObjectEntity>,
        layersById: Map<Long, AnnotationLayerEntity>,
        pageScale: PageScaleEntity?,
        selectedIds: Set<Long> = emptySet(),
        pageWidthPts: Float = 595f,
        pageHeightPts: Float = 842f,
        strokeScale: Float = 1f
    ) {
        val sorted = markups.sortedBy { it.zOrder }
        for (markup in sorted) {
            val layer = layersById[markup.layerId]
            if (layer != null && !layer.visible) continue
            val layerOpacity = layer?.opacity ?: 1f
            drawMarkup(
                canvas = canvas,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                markup = markup,
                layerOpacity = layerOpacity,
                pageScale = pageScale,
                isSelected = markup.id in selectedIds && markup.id > 0L,
                pageWidthPts = pageWidthPts,
                pageHeightPts = pageHeightPts,
                strokeScale = strokeScale
            )
        }
    }

    fun drawMarkup(
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        markup: MarkupObjectEntity,
        layerOpacity: Float = 1f,
        pageScale: PageScaleEntity? = null,
        isSelected: Boolean = false,
        pageWidthPts: Float = 595f,
        pageHeightPts: Float = 842f,
        strokeScale: Float = 1f
    ) {
        val kind = StudioTool.entries.firstOrNull { it.name == markup.kind } ?: return
        val g = StudioGeometryJson.fromJson(markup.geometryJson)
        val s = StudioStyleJson.fromJson(markup.styleJson)

        val alpha = (s.opacity * layerOpacity).coerceIn(0f, 1f)
        val baseColor = parseHexColor(s.strokeColorHex, Color.RED)
        val effectiveAlpha = (alpha * Color.alpha(baseColor)).toInt().coerceIn(0, 255)
        val strokeWidthPx = max(1f, s.strokeWidthPt * strokeScale)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = baseColor
            this.alpha = effectiveAlpha
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            when (DashStyle.entries.firstOrNull { it.name == s.dashStyle } ?: DashStyle.SOLID) {
                DashStyle.DASHED -> pathEffect = DashPathEffect(floatArrayOf(strokeWidthPx * 4f, strokeWidthPx * 2.5f), 0f)
                DashStyle.DOTTED -> pathEffect = DashPathEffect(floatArrayOf(strokeWidthPx * 1.1f, strokeWidthPx * 2.4f), 0f)
                DashStyle.SOLID -> {}
            }
        }

        val fillColor = if (s.fillColorHex.isNotBlank()) parseHexColor(s.fillColorHex, Color.TRANSPARENT) else Color.TRANSPARENT
        val fillPaint = if (fillColor != Color.TRANSPARENT) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor
                this.alpha = (alpha * 0.45f * 255f).toInt().coerceIn(0, 255)
                style = Paint.Style.FILL
            }
        } else null

        val x1 = g.x1 * pageWidth
        val y1 = g.y1 * pageHeight
        val x2 = g.x2 * pageWidth
        val y2 = g.y2 * pageHeight

        when (kind) {
            StudioTool.LINE, StudioTool.ARROW -> {
                canvas.drawLine(x1, y1, x2, y2, strokePaint)
                val endArrow = if (kind == StudioTool.ARROW && s.endArrow == ArrowHeadStyle.NONE.name) {
                    ArrowHeadStyle.FILLED
                } else {
                    ArrowHeadStyle.entries.firstOrNull { it.name == s.endArrow } ?: ArrowHeadStyle.NONE
                }
                val startArrow = ArrowHeadStyle.entries.firstOrNull { it.name == s.startArrow } ?: ArrowHeadStyle.NONE
                if (endArrow != ArrowHeadStyle.NONE) {
                    drawArrowHead(canvas, x1, y1, x2, y2, endArrow, strokePaint, strokeWidthPx)
                }
                if (startArrow != ArrowHeadStyle.NONE) {
                    drawArrowHead(canvas, x2, y2, x1, y1, startArrow, strokePaint, strokeWidthPx)
                }
            }

            StudioTool.DIMENSION_DISTANCE -> {
                canvas.drawLine(x1, y1, x2, y2, strokePaint)
                drawArrowHead(canvas, x1, y1, x2, y2, ArrowHeadStyle.TICK, strokePaint, strokeWidthPx)
                drawArrowHead(canvas, x2, y2, x1, y1, ArrowHeadStyle.TICK, strokePaint, strokeWidthPx)
                val label = StudioGeometry.formatMeasuredDistance(
                    StudioPoint(g.x1, g.y1),
                    StudioPoint(g.x2, g.y2),
                    pageWidthPts,
                    pageHeightPts,
                    pageScale
                )
                drawBadgeText(canvas, (x1 + x2) * 0.5f, (y1 + y2) * 0.5f - strokeWidthPx * 2.5f, label, baseColor, s.fontSizeSp * strokeScale)
            }

            StudioTool.DIMENSION_ANGLE -> {
                val pts = g.points
                if (pts.size >= 3) {
                    val v = pts[0]
                    val a = pts[1]
                    val b = pts[2]
                    canvas.drawLine(v.x * pageWidth, v.y * pageHeight, a.x * pageWidth, a.y * pageHeight, strokePaint)
                    canvas.drawLine(v.x * pageWidth, v.y * pageHeight, b.x * pageWidth, b.y * pageHeight, strokePaint)
                    val deg = StudioGeometry.computeAngleDegrees(v, a, b)
                    drawBadgeText(
                        canvas,
                        v.x * pageWidth + 14f * strokeScale,
                        v.y * pageHeight - 14f * strokeScale,
                        String.format("%.1f°", deg),
                        baseColor,
                        s.fontSizeSp * strokeScale
                    )
                } else {
                    canvas.drawLine(x1, y1, x2, y2, strokePaint)
                }
            }

            StudioTool.DIMENSION_AREA -> {
                val pts = if (g.points.size >= 3) g.points else listOf(
                    StudioPoint(g.x1, g.y1),
                    StudioPoint(g.x2, g.y1),
                    StudioPoint(g.x2, g.y2),
                    StudioPoint(g.x1, g.y2)
                )
                val path = Path().apply {
                    moveTo(pts.first().x * pageWidth, pts.first().y * pageHeight)
                    for (i in 1 until pts.size) {
                        lineTo(pts[i].x * pageWidth, pts[i].y * pageHeight)
                    }
                    close()
                }
                val hatchFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = baseColor
                    this.alpha = (alpha * 0.16f * 255f).toInt().coerceIn(0, 255)
                    style = Paint.Style.FILL
                }
                canvas.drawPath(path, hatchFill)
                canvas.drawPath(path, strokePaint)
                val areaStr = StudioGeometry.formatPolygonArea(pts, pageWidthPts, pageHeightPts, pageScale)
                val cx = pts.map { it.x }.average().toFloat() * pageWidth
                val cy = pts.map { it.y }.average().toFloat() * pageHeight
                drawBadgeText(canvas, cx, cy, areaStr, baseColor, s.fontSizeSp * strokeScale)
            }

            StudioTool.RECT -> {
                val rect = RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
                val r = s.cornerRadiusPt * strokeScale
                if (fillPaint != null) canvas.drawRoundRect(rect, r, r, fillPaint)
                canvas.drawRoundRect(rect, r, r, strokePaint)
            }

            StudioTool.ELLIPSE -> {
                val rect = RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
                if (fillPaint != null) canvas.drawOval(rect, fillPaint)
                canvas.drawOval(rect, strokePaint)
            }

            StudioTool.ARC -> {
                val rect = RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
                if (rect.width() > 2f && rect.height() > 2f) {
                    canvas.drawArc(rect, g.startAngleDeg, g.sweepAngleDeg, false, strokePaint)
                }
            }

            StudioTool.POLYGON -> {
                val cx = (x1 + x2) * 0.5f
                val cy = (y1 + y2) * 0.5f
                val rx = max(6f, abs(x2 - x1) * 0.5f)
                val ry = max(6f, abs(y2 - y1) * 0.5f)
                val sides = g.sides.coerceIn(3, 12)
                val path = Path()
                for (i in 0 until sides) {
                    val theta = (-PI / 2.0) + (2.0 * PI * i / sides)
                    val px = cx + (rx * cos(theta)).toFloat()
                    val py = cy + (ry * sin(theta)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                if (fillPaint != null) canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)
            }

            StudioTool.CLOUD -> {
                val rect = RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
                val path = buildRevisionCloudPath(rect, max(12f, strokeWidthPx * 5.5f))
                if (fillPaint != null) canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)
            }

            StudioTool.POLYLINE, StudioTool.FREEHAND, StudioTool.HIGHLIGHTER -> {
                val pts = g.points
                if (pts.size >= 2) {
                    if (kind == StudioTool.HIGHLIGHTER) {
                        strokePaint.alpha = (alpha * 0.42f * 255f).toInt().coerceIn(0, 255)
                        strokePaint.strokeWidth = max(10f * strokeScale, strokeWidthPx * 3.5f)
                    }
                    if (kind == StudioTool.FREEHAND) {
                        // Draw variable pressure segments smoothly
                        for (i in 0 until pts.size - 1) {
                            val a = pts[i]
                            val b = pts[i + 1]
                            val pAvg = ((a.pressure + b.pressure) * 0.5f).coerceIn(0.45f, 1.55f)
                            strokePaint.strokeWidth = max(1f, strokeWidthPx * pAvg)
                            canvas.drawLine(a.x * pageWidth, a.y * pageHeight, b.x * pageWidth, b.y * pageHeight, strokePaint)
                        }
                    } else {
                        val path = Path().apply {
                            moveTo(pts.first().x * pageWidth, pts.first().y * pageHeight)
                            for (i in 1 until pts.size) {
                                lineTo(pts[i].x * pageWidth, pts[i].y * pageHeight)
                            }
                        }
                        canvas.drawPath(path, strokePaint)
                    }
                }
            }

            StudioTool.CALLOUT -> {
                val boxRect = RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
                if (boxRect.width() < 40f * strokeScale) boxRect.right = boxRect.left + 120f * strokeScale
                if (boxRect.height() < 26f * strokeScale) boxRect.bottom = boxRect.top + 48f * strokeScale
                val tx = g.calloutTargetX * pageWidth
                val ty = g.calloutTargetY * pageHeight
                canvas.drawLine(boxRect.centerX(), boxRect.centerY(), tx, ty, strokePaint)
                drawArrowHead(canvas, boxRect.centerX(), boxRect.centerY(), tx, ty, ArrowHeadStyle.FILLED, strokePaint, strokeWidthPx)

                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (fillColor != Color.TRANSPARENT) fillColor else Color.WHITE
                    this.alpha = (alpha * 0.94f * 255f).toInt().coerceIn(0, 255)
                    style = Paint.Style.FILL
                }
                val radius = 8f * strokeScale
                canvas.drawRoundRect(boxRect, radius, radius, bgPaint)
                canvas.drawRoundRect(boxRect, radius, radius, strokePaint)

                val label = g.text.ifBlank { markup.label.ifBlank { "Callout" } }
                val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1D1B20")
                    textSize = s.fontSizeSp * strokeScale
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                canvas.drawText(label, boxRect.left + 8f * strokeScale, boxRect.centerY() + txtPaint.textSize * 0.35f, txtPaint)
            }

            StudioTool.TEXT_NOTE -> {
                val w = max(96f * strokeScale, abs(x2 - x1))
                val h = max(44f * strokeScale, abs(y2 - y1))
                val rect = RectF(x1, y1, x1 + w, y1 + h)
                val noteBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (fillColor != Color.TRANSPARENT) fillColor else Color.parseColor("#FFF4B8")
                    this.alpha = (alpha * 0.95f * 255f).toInt().coerceIn(0, 255)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(rect, 8f * strokeScale, 8f * strokeScale, noteBg)
                canvas.drawRoundRect(rect, 8f * strokeScale, 8f * strokeScale, strokePaint)
                val noteText = g.text.ifBlank { markup.label.ifBlank { "Note" } }
                val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#2A2218")
                    textSize = s.fontSizeSp * strokeScale
                }
                canvas.drawText(noteText.take(36), rect.left + 8f * strokeScale, rect.top + txtPaint.textSize + 6f * strokeScale, txtPaint)
            }

            StudioTool.STAMP -> {
                val stamp = StampKind.entries.firstOrNull { it.name == g.stampKind } ?: StampKind.REVIEWED
                val w = max(120f * strokeScale, abs(x2 - x1))
                val h = max(36f * strokeScale, abs(y2 - y1))
                val rect = RectF(x1, y1, x1 + w, y1 + h)
                val stampCol = parseHexColor(s.strokeColorHex, Color.parseColor(stamp.defaultHex))
                val stPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stampCol
                    this.alpha = effectiveAlpha
                    style = Paint.Style.STROKE
                    strokeWidth = max(2f * strokeScale, strokeWidthPx)
                }
                canvas.drawRoundRect(rect, 6f * strokeScale, 6f * strokeScale, stPaint)
                val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stampCol
                    this.alpha = effectiveAlpha
                    textSize = (s.fontSizeSp + 1f) * strokeScale
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(
                    g.text.ifBlank { stamp.badgeText },
                    rect.centerX(),
                    rect.centerY() + lblPaint.textSize * 0.35f,
                    lblPaint
                )
            }

            StudioTool.SPOTLIGHT -> {
                val cutout = RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
                val sc = canvas.saveLayer(0f, 0f, pageWidth, pageHeight, null)
                val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(128, 12, 14, 22)
                }
                canvas.drawRect(0f, 0f, pageWidth, pageHeight, dimPaint)
                val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                }
                canvas.drawRoundRect(cutout, 10f * strokeScale, 10f * strokeScale, clearPaint)
                canvas.restoreToCount(sc)
                canvas.drawRoundRect(cutout, 10f * strokeScale, 10f * strokeScale, strokePaint)
            }

            StudioTool.SELECT, StudioTool.CALIBRATE -> {}
        }

        // Draw 8+1 transform selection box when selected
        if (isSelected) {
            val b = StudioGeometry.boundsOf(kind, g)
            val selRect = RectF(
                b.left * pageWidth - 6f * strokeScale,
                b.top * pageHeight - 6f * strokeScale,
                b.right * pageWidth + 6f * strokeScale,
                b.bottom * pageHeight + 6f * strokeScale
            )
            val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2F7CF6")
                style = Paint.Style.STROKE
                strokeWidth = 1.6f * strokeScale
                pathEffect = DashPathEffect(floatArrayOf(8f * strokeScale, 5f * strokeScale), 0f)
            }
            canvas.drawRect(selRect, selPaint)

            val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2F7CF6")
                style = Paint.Style.STROKE
                strokeWidth = 1.6f * strokeScale
            }
            val handlePts = listOf(
                selRect.left to selRect.top,
                selRect.centerX() to selRect.top,
                selRect.right to selRect.top,
                selRect.right to selRect.centerY(),
                selRect.right to selRect.bottom,
                selRect.centerX() to selRect.bottom,
                selRect.left to selRect.bottom,
                selRect.left to selRect.centerY()
            )
            val hr = 5f * strokeScale
            for ((hx, hy) in handlePts) {
                canvas.drawCircle(hx, hy, hr, handleFill)
                canvas.drawCircle(hx, hy, hr, handleStroke)
            }
        }
    }

    private fun drawArrowHead(
        canvas: Canvas,
        fromX: Float,
        fromY: Float,
        tipX: Float,
        tipY: Float,
        style: ArrowHeadStyle,
        paint: Paint,
        strokeWidthPx: Float
    ) {
        val len = max(10f, strokeWidthPx * 4.5f)
        val angle = atan2(tipY - fromY, tipX - fromX)
        when (style) {
            ArrowHeadStyle.NONE -> {}
            ArrowHeadStyle.TICK -> {
                val perp = angle + (PI / 2.0)
                val dx = (len * 0.55f * cos(perp)).toFloat()
                val dy = (len * 0.55f * sin(perp)).toFloat()
                canvas.drawLine(tipX - dx, tipY - dy, tipX + dx, tipY + dy, paint)
            }
            ArrowHeadStyle.OPEN, ArrowHeadStyle.FILLED -> {
                val spread = 0.46
                val xA = tipX - (len * cos(angle - spread)).toFloat()
                val yA = tipY - (len * sin(angle - spread)).toFloat()
                val xB = tipX - (len * cos(angle + spread)).toFloat()
                val yB = tipY - (len * sin(angle + spread)).toFloat()
                val path = Path().apply {
                    moveTo(xA, yA)
                    lineTo(tipX, tipY)
                    lineTo(xB, yB)
                    if (style == ArrowHeadStyle.FILLED) close()
                }
                if (style == ArrowHeadStyle.FILLED) {
                    val fillP = Paint(paint).apply { this.style = Paint.Style.FILL }
                    canvas.drawPath(path, fillP)
                } else {
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    private fun drawBadgeText(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        text: String,
        accentColor: Int,
        textSizePx: Float
    ) {
        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textSizePx
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val tw = txtPaint.measureText(text)
        val padX = textSizePx * 0.55f
        val padY = textSizePx * 0.42f
        val rect = RectF(cx - tw * 0.5f - padX, cy - textSizePx * 0.55f - padY, cx + tw * 0.5f + padX, cy + textSizePx * 0.35f + padY)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 230
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, 6f, 6f, bgPaint)
        canvas.drawText(text, cx, rect.centerY() + textSizePx * 0.34f, txtPaint)
    }

    private fun buildRevisionCloudPath(rect: RectF, scallopLen: Float): Path {
        val path = Path()
        val l = rect.left
        val t = rect.top
        val r = rect.right
        val b = rect.bottom
        val w = rect.width().coerceAtLeast(10f)
        val h = rect.height().coerceAtLeast(10f)
        val nx = max(2, (w / scallopLen).toInt())
        val ny = max(2, (h / scallopLen).toInt())
        path.moveTo(l, t)
        for (i in 0 until nx) {
            val sx = l + (w * i / nx)
            val ex = l + (w * (i + 1) / nx)
            val mx = (sx + ex) * 0.5f
            path.quadTo(mx, t - scallopLen * 0.48f, ex, t)
        }
        for (i in 0 until ny) {
            val sy = t + (h * i / ny)
            val ey = t + (h * (i + 1) / ny)
            val my = (sy + ey) * 0.5f
            path.quadTo(r + scallopLen * 0.48f, my, r, ey)
        }
        for (i in 0 until nx) {
            val sx = r - (w * i / nx)
            val ex = r - (w * (i + 1) / nx)
            val mx = (sx + ex) * 0.5f
            path.quadTo(mx, b + scallopLen * 0.48f, ex, b)
        }
        for (i in 0 until ny) {
            val sy = b - (h * i / ny)
            val ey = b - (h * (i + 1) / ny)
            val my = (sy + ey) * 0.5f
            path.quadTo(l - scallopLen * 0.48f, my, l, ey)
        }
        path.close()
        return path
    }

    fun parseHexColor(hex: String, fallback: Int): Int {
        if (hex.isBlank()) return fallback
        return runCatching { Color.parseColor(hex) }.getOrDefault(fallback)
    }
}
