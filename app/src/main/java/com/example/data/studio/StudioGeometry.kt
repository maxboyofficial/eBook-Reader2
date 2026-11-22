package com.example.data.studio

import com.example.data.db.MarkupObjectEntity
import com.example.data.db.PageScaleEntity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class SnapKind(val label: String) {
    GRID("Grid"),
    ENDPOINT("Endpoint"),
    MIDPOINT("Midpoint"),
    CENTER("Center"),
    INTERSECTION("Intersection"),
    PAGE_EDGE("Page Edge")
}

data class SnapResult(
    val point: StudioPoint,
    val kind: SnapKind? = null
)

data class NormBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0.002f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.002f)
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
}

/**
 * Pure geometry engine for Milestone E CAD Studio:
 * - Precision snapping (Grid, Endpoints, Midpoints, Centers, Segment Intersections, Page Margins/Edges)
 * - Polar/Ortho angle locking (0/45/90 or 15° steps)
 * - Calibrated distance, angle, and shoelace polygon area math
 * - Hit testing & multi-object align/distribute transforms
 */
object StudioGeometry {

    fun boundsOf(markup: MarkupObjectEntity): NormBounds {
        val g = StudioGeometryJson.fromJson(markup.geometryJson)
        return boundsOf(StudioTool.entries.firstOrNull { it.name == markup.kind } ?: StudioTool.LINE, g)
    }

    fun boundsOf(kind: StudioTool, g: StudioGeometryJson): NormBounds {
        if (g.points.isNotEmpty()) {
            var l = 1f
            var t = 1f
            var r = 0f
            var b = 0f
            for (pt in g.points) {
                if (pt.x < l) l = pt.x
                if (pt.y < t) t = pt.y
                if (pt.x > r) r = pt.x
                if (pt.y > b) b = pt.y
            }
            if (kind == StudioTool.CALLOUT) {
                l = min(l, g.calloutTargetX)
                r = max(r, g.calloutTargetX)
                t = min(t, g.calloutTargetY)
                b = max(b, g.calloutTargetY)
            }
            return NormBounds(l, t, r, b)
        }
        val baseL = min(g.x1, g.x2)
        val baseR = max(g.x1, g.x2)
        val baseT = min(g.y1, g.y2)
        val baseB = max(g.y1, g.y2)
        return when (kind) {
            StudioTool.CALLOUT -> NormBounds(
                min(baseL, g.calloutTargetX),
                min(baseT, g.calloutTargetY),
                max(baseR, g.calloutTargetX),
                max(baseB, g.calloutTargetY)
            )
            StudioTool.TEXT_NOTE, StudioTool.STAMP -> {
                val w = max(0.18f, abs(g.x2 - g.x1))
                val h = max(0.06f, abs(g.y2 - g.y1))
                NormBounds(g.x1, g.y1, (g.x1 + w).coerceAtMost(1f), (g.y1 + h).coerceAtMost(1f))
            }
            else -> NormBounds(baseL, baseT, baseR, baseB)
        }
    }

    fun extractSnapAnchors(markups: List<MarkupObjectEntity>): List<Pair<StudioPoint, SnapKind>> {
        val anchors = mutableListOf<Pair<StudioPoint, SnapKind>>()
        val lineSegments = mutableListOf<Pair<StudioPoint, StudioPoint>>()

        for (m in markups) {
            val g = StudioGeometryJson.fromJson(m.geometryJson)
            val kind = StudioTool.entries.firstOrNull { it.name == m.kind } ?: continue
            when (kind) {
                StudioTool.LINE, StudioTool.ARROW, StudioTool.DIMENSION_DISTANCE -> {
                    val a = StudioPoint(g.x1, g.y1)
                    val b = StudioPoint(g.x2, g.y2)
                    anchors.add(a to SnapKind.ENDPOINT)
                    anchors.add(b to SnapKind.ENDPOINT)
                    anchors.add(StudioPoint((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f) to SnapKind.MIDPOINT)
                    lineSegments.add(a to b)
                }
                StudioTool.POLYLINE, StudioTool.DIMENSION_AREA -> {
                    for (i in g.points.indices) {
                        anchors.add(g.points[i] to SnapKind.ENDPOINT)
                        if (i + 1 < g.points.size) {
                            val a = g.points[i]
                            val b = g.points[i + 1]
                            anchors.add(StudioPoint((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f) to SnapKind.MIDPOINT)
                            lineSegments.add(a to b)
                        }
                    }
                }
                StudioTool.RECT, StudioTool.ELLIPSE, StudioTool.POLYGON, StudioTool.CLOUD, StudioTool.SPOTLIGHT -> {
                    val b = boundsOf(kind, g)
                    anchors.add(StudioPoint(b.left, b.top) to SnapKind.ENDPOINT)
                    anchors.add(StudioPoint(b.right, b.top) to SnapKind.ENDPOINT)
                    anchors.add(StudioPoint(b.right, b.bottom) to SnapKind.ENDPOINT)
                    anchors.add(StudioPoint(b.left, b.bottom) to SnapKind.ENDPOINT)
                    anchors.add(StudioPoint(b.centerX, b.centerY) to SnapKind.CENTER)
                    anchors.add(StudioPoint(b.centerX, b.top) to SnapKind.MIDPOINT)
                    anchors.add(StudioPoint(b.centerX, b.bottom) to SnapKind.MIDPOINT)
                    anchors.add(StudioPoint(b.left, b.centerY) to SnapKind.MIDPOINT)
                    anchors.add(StudioPoint(b.right, b.centerY) to SnapKind.MIDPOINT)
                }
                else -> {}
            }
        }

        // Compute pairwise line-segment intersections for snap precision
        val limit = min(lineSegments.size, 32)
        for (i in 0 until limit) {
            for (j in (i + 1) until limit) {
                segmentIntersection(
                    lineSegments[i].first,
                    lineSegments[i].second,
                    lineSegments[j].first,
                    lineSegments[j].second
                )?.let { inter ->
                    anchors.add(inter to SnapKind.INTERSECTION)
                }
            }
        }
        return anchors
    }

    fun segmentIntersection(
        p1: StudioPoint,
        p2: StudioPoint,
        p3: StudioPoint,
        p4: StudioPoint
    ): StudioPoint? {
        val denom = (p1.x - p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x - p4.x)
        if (abs(denom) < 1e-6f) return null
        val t = ((p1.x - p3.x) * (p3.y - p4.y) - (p1.y - p3.y) * (p3.x - p4.x)) / denom
        val u = -((p1.x - p2.x) * (p1.y - p3.y) - (p1.y - p2.y) * (p1.x - p3.x)) / denom
        if (t in 0f..1f && u in 0f..1f) {
            return StudioPoint(
                x = p1.x + t * (p2.x - p1.x),
                y = p1.y + t * (p2.y - p1.y)
            )
        }
        return null
    }

    fun snapPoint(
        raw: StudioPoint,
        existingMarkups: List<MarkupObjectEntity>,
        snapEnabled: Boolean,
        gridSnapEnabled: Boolean,
        snapThresholdNorm: Float = 0.024f,
        gridStepNorm: Float = 0.025f
    ): SnapResult {
        val clamped = StudioPoint(raw.x.coerceIn(0f, 1f), raw.y.coerceIn(0f, 1f), raw.pressure)
        if (!snapEnabled) return SnapResult(clamped, null)

        val anchors = extractSnapAnchors(existingMarkups)
        var bestAnchor: Pair<StudioPoint, SnapKind>? = null
        var bestDist = snapThresholdNorm

        for ((pt, kind) in anchors) {
            val d = hypot(pt.x - clamped.x, pt.y - clamped.y)
            if (d < bestDist) {
                bestDist = d
                bestAnchor = pt to kind
            }
        }
        if (bestAnchor != null) {
            return SnapResult(
                point = StudioPoint(bestAnchor.first.x, bestAnchor.first.y, raw.pressure),
                kind = bestAnchor.second
            )
        }

        // Page borders / margin guides (5% and 95% + center axes)
        val marginThreshold = snapThresholdNorm * 0.7f
        val snapX = when {
            abs(clamped.x - 0.05f) < marginThreshold -> 0.05f
            abs(clamped.x - 0.50f) < marginThreshold -> 0.50f
            abs(clamped.x - 0.95f) < marginThreshold -> 0.95f
            else -> null
        }
        val snapY = when {
            abs(clamped.y - 0.05f) < marginThreshold -> 0.05f
            abs(clamped.y - 0.50f) < marginThreshold -> 0.50f
            abs(clamped.y - 0.95f) < marginThreshold -> 0.95f
            else -> null
        }
        if (snapX != null || snapY != null) {
            return SnapResult(
                point = StudioPoint(snapX ?: clamped.x, snapY ?: clamped.y, raw.pressure),
                kind = SnapKind.PAGE_EDGE
            )
        }

        if (gridSnapEnabled && gridStepNorm > 0f) {
            val gx = ((clamped.x / gridStepNorm).roundToInt() * gridStepNorm).coerceIn(0f, 1f)
            val gy = ((clamped.y / gridStepNorm).roundToInt() * gridStepNorm).coerceIn(0f, 1f)
            if (hypot(gx - clamped.x, gy - clamped.y) <= gridStepNorm * 0.5f) {
                return SnapResult(StudioPoint(gx, gy, raw.pressure), SnapKind.GRID)
            }
        }

        return SnapResult(clamped, null)
    }

    /**
     * Constrains segment angle to multiples of [stepDegrees] (e.g. 45° for Ortho/8-way or 15° polar lock).
     */
    fun applyAngleConstraint(
        origin: StudioPoint,
        target: StudioPoint,
        stepDegrees: Float = 45f
    ): StudioPoint {
        val dx = target.x - origin.x
        val dy = target.y - origin.y
        val len = hypot(dx, dy)
        if (len < 1e-4f) return target
        val rawDeg = atan2(dy, dx) * 180f / PI.toFloat()
        val snappedDeg = (rawDeg / stepDegrees).roundToInt() * stepDegrees
        val rad = snappedDeg * PI.toFloat() / 180f
        return StudioPoint(
            x = (origin.x + len * cos(rad)).coerceIn(0f, 1f),
            y = (origin.y + len * sin(rad)).coerceIn(0f, 1f),
            pressure = target.pressure
        )
    }

    fun formatMeasuredDistance(
        p1: StudioPoint,
        p2: StudioPoint,
        pageWidthPts: Float,
        pageHeightPts: Float,
        scale: PageScaleEntity?
    ): String {
        val dxPts = (p2.x - p1.x) * pageWidthPts
        val dyPts = (p2.y - p1.y) * pageHeightPts
        val distPts = hypot(dxPts, dyPts)
        return formatPtsDistance(distPts, scale)
    }

    fun formatPtsDistance(distPts: Float, scale: PageScaleEntity?): String {
        if (scale != null && scale.pagePtsPerUnit > 0f) {
            val units = distPts / scale.pagePtsPerUnit
            val suffix = MeasurementUnit.entries.firstOrNull { it.name == scale.unit }?.suffix ?: "u"
            return String.format("%.2f %s", units, suffix)
        }
        val mm = distPts * 0.352778f // 1 PDF point = 1/72 inch = 0.352778 mm
        return String.format("%.1f mm", mm)
    }

    fun computeAngleDegrees(vertex: StudioPoint, a: StudioPoint, b: StudioPoint): Float {
        val ax = a.x - vertex.x
        val ay = a.y - vertex.y
        val bx = b.x - vertex.x
        val by = b.y - vertex.y
        val angA = atan2(ay, ax)
        val angB = atan2(by, bx)
        var diff = abs((angB - angA) * 180f / PI.toFloat())
        if (diff > 180f) diff = 360f - diff
        return diff
    }

    fun formatPolygonArea(
        points: List<StudioPoint>,
        pageWidthPts: Float,
        pageHeightPts: Float,
        scale: PageScaleEntity?
    ): String {
        if (points.size < 3) return "0.0"
        var sum = 0f
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            val x1 = p1.x * pageWidthPts
            val y1 = p1.y * pageHeightPts
            val x2 = p2.x * pageWidthPts
            val y2 = p2.y * pageHeightPts
            sum += (x1 * y2 - x2 * y1)
        }
        val areaPts2 = abs(sum) * 0.5f
        return if (scale != null && scale.pagePtsPerUnit > 0f) {
            val areaUnits = areaPts2 / (scale.pagePtsPerUnit * scale.pagePtsPerUnit)
            val suffix = MeasurementUnit.entries.firstOrNull { it.name == scale.unit }?.suffix ?: "u"
            String.format("%.2f %s²", areaUnits, suffix)
        } else {
            val cm2 = areaPts2 * 0.352778f * 0.352778f / 100f
            String.format("%.2f cm²", cm2)
        }
    }

    fun hitTest(point: StudioPoint, markups: List<MarkupObjectEntity>, tolerance: Float = 0.032f): MarkupObjectEntity? {
        for (m in markups.asReversed()) {
            val g = StudioGeometryJson.fromJson(m.geometryJson)
            val kind = StudioTool.entries.firstOrNull { it.name == m.kind } ?: continue
            val hit = when (kind) {
                StudioTool.LINE, StudioTool.ARROW, StudioTool.DIMENSION_DISTANCE ->
                    distToSegment(point, StudioPoint(g.x1, g.y1), StudioPoint(g.x2, g.y2)) <= tolerance
                StudioTool.POLYLINE, StudioTool.FREEHAND, StudioTool.HIGHLIGHTER -> {
                    g.points.zipWithNext().any { (a, b) -> distToSegment(point, a, b) <= tolerance }
                }
                else -> {
                    val b = boundsOf(kind, g)
                    point.x in (b.left - tolerance)..(b.right + tolerance) &&
                        point.y in (b.top - tolerance)..(b.bottom + tolerance)
                }
            }
            if (hit) return m
        }
        return null
    }

    private fun distToSegment(p: StudioPoint, v: StudioPoint, w: StudioPoint): Float {
        val dx = w.x - v.x
        val dy = w.y - v.y
        val l2 = dx * dx + dy * dy
        if (l2 < 1e-8f) return hypot(p.x - v.x, p.y - v.y)
        val t = (((p.x - v.x) * dx + (p.y - v.y) * dy) / l2).coerceIn(0f, 1f)
        val projX = v.x + t * dx
        val projY = v.y + t * dy
        return hypot(p.x - projX, p.y - projY)
    }

    fun translateMarkup(markup: MarkupObjectEntity, dx: Float, dy: Float): MarkupObjectEntity {
        val g = StudioGeometryJson.fromJson(markup.geometryJson)
        val moved = g.copy(
            x1 = (g.x1 + dx).coerceIn(0f, 1f),
            y1 = (g.y1 + dy).coerceIn(0f, 1f),
            x2 = (g.x2 + dx).coerceIn(0f, 1f),
            y2 = (g.y2 + dy).coerceIn(0f, 1f),
            calloutTargetX = (g.calloutTargetX + dx).coerceIn(0f, 1f),
            calloutTargetY = (g.calloutTargetY + dy).coerceIn(0f, 1f),
            points = g.points.map { StudioPoint((it.x + dx).coerceIn(0f, 1f), (it.y + dy).coerceIn(0f, 1f), it.pressure) }
        )
        return markup.copy(
            geometryJson = StudioGeometryJson.toJson(moved),
            updatedAt = System.currentTimeMillis()
        )
    }

    enum class AlignMode { LEFT, CENTER_X, RIGHT, TOP, CENTER_Y, BOTTOM }

    fun alignMarkups(selected: List<MarkupObjectEntity>, mode: AlignMode): List<MarkupObjectEntity> {
        if (selected.size < 2) return selected
        val bounds = selected.map { it to boundsOf(it) }
        val minL = bounds.minOf { it.second.left }
        val maxR = bounds.maxOf { it.second.right }
        val minT = bounds.minOf { it.second.top }
        val maxB = bounds.maxOf { it.second.bottom }
        val midX = (minL + maxR) * 0.5f
        val midY = (minT + maxB) * 0.5f

        return bounds.map { (m, b) ->
            when (mode) {
                AlignMode.LEFT -> translateMarkup(m, minL - b.left, 0f)
                AlignMode.CENTER_X -> translateMarkup(m, midX - b.centerX, 0f)
                AlignMode.RIGHT -> translateMarkup(m, maxR - b.right, 0f)
                AlignMode.TOP -> translateMarkup(m, 0f, minT - b.top)
                AlignMode.CENTER_Y -> translateMarkup(m, 0f, midY - b.centerY)
                AlignMode.BOTTOM -> translateMarkup(m, 0f, maxB - b.bottom)
            }
        }
    }
}
