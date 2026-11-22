package com.example.data.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.example.data.db.AnnotationLayerEntity
import com.example.data.db.MarkupObjectEntity
import com.example.data.db.PageScaleEntity
import com.example.data.reader.PdfPageEngine
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

@JsonClass(generateAdapter = true)
data class StudioExportBundle(
    val version: Int = 1,
    val bookTitle: String,
    val layers: List<ExportedLayer>,
    val markups: List<ExportedMarkup>
) {
    @JsonClass(generateAdapter = true)
    data class ExportedLayer(
        val id: Long,
        val name: String,
        val visible: Boolean,
        val locked: Boolean,
        val opacity: Float
    )

    @JsonClass(generateAdapter = true)
    data class ExportedMarkup(
        val pageIndex: Int,
        val layerId: Long,
        val kind: String,
        val geometryJson: String,
        val styleJson: String,
        val label: String
    )

    companion object {
        private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        private val adapter = moshi.adapter(StudioExportBundle::class.java).indent("  ")

        fun toJson(bundle: StudioExportBundle): String = adapter.toJson(bundle)
        fun fromJson(raw: String): StudioExportBundle? = runCatching { adapter.fromJson(raw) }.getOrNull()
    }
}

/**
 * Non-destructive export suite for Milestone E:
 *  - Annotated PDF Copy (never modifies original PDF)
 *  - Layered SVG vector export per page
 *  - Portable JSON vector project export / import
 */
object StudioExporter {

    suspend fun exportAnnotatedPdfCopy(
        context: Context,
        sourcePdfUri: String,
        outputUri: Uri,
        layers: List<AnnotationLayerEntity>,
        markups: List<MarkupObjectEntity>,
        scales: Map<Int, PageScaleEntity>
    ): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(outputUri)?.use { out ->
            exportAnnotatedPdfCopyToStream(context, sourcePdfUri, out, layers, markups, scales)
        } ?: throw IllegalStateException("Unable to open output stream for Annotated PDF.")
    }

    suspend fun exportAnnotatedPdfCopyToStream(
        context: Context,
        sourcePdfUri: String,
        outputStream: OutputStream,
        layers: List<AnnotationLayerEntity>,
        markups: List<MarkupObjectEntity>,
        scales: Map<Int, PageScaleEntity>
    ): Int = withContext(Dispatchers.IO) {
        val pfd = PdfPageEngine.openFileDescriptor(context, sourcePdfUri)
            ?: throw IllegalStateException("Original PDF unavailable.")
        val layerMap = layers.associateBy { it.id }
        val byPage = markups.groupBy { it.pageIndex }
        var exportedPages = 0

        pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                val doc = PdfDocument()
                try {
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val ptW = page.width.coerceAtLeast(100)
                            val ptH = page.height.coerceAtLeast(100)
                            val pageInfo = PdfDocument.PageInfo.Builder(ptW, ptH, i + 1).create()
                            val outPage = doc.startPage(pageInfo)

                            // Render original page content at 2x crispness into a temp bitmap, draw onto PdfDocument canvas, then draw sharp vector markups on top
                            val scaleFactor = 2
                            val bmp = Bitmap.createBitmap(ptW * scaleFactor, ptH * scaleFactor, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            outPage.canvas.drawBitmap(bmp, null, Rect(0, 0, ptW, ptH), null)
                            bmp.recycle()

                            val pageMarkups = byPage[i].orEmpty()
                            if (pageMarkups.isNotEmpty()) {
                                StudioCanvasRenderer.drawPageMarkups(
                                    canvas = outPage.canvas,
                                    pageWidth = ptW.toFloat(),
                                    pageHeight = ptH.toFloat(),
                                    markups = pageMarkups,
                                    layersById = layerMap,
                                    pageScale = scales[i],
                                    selectedIds = emptySet(),
                                    pageWidthPts = ptW.toFloat(),
                                    pageHeightPts = ptH.toFloat(),
                                    strokeScale = 1f
                                )
                            }
                            doc.finishPage(outPage)
                            exportedPages++
                        }
                    }
                    doc.writeTo(outputStream)
                } finally {
                    doc.close()
                }
            }
        }
        exportedPages
    }

    fun exportSvg(
        pageIndex: Int,
        pageWidthPts: Int,
        pageHeightPts: Int,
        layers: List<AnnotationLayerEntity>,
        markups: List<MarkupObjectEntity>
    ): String {
        val w = pageWidthPts.coerceAtLeast(100)
        val h = pageHeightPts.coerceAtLeast(100)
        val byLayer = markups.filter { it.pageIndex == pageIndex }.groupBy { it.layerId }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$w" height="$h" viewBox="0 0 $w $h">""")
            for (layer in layers.sortedBy { it.sortOrder }) {
                if (!layer.visible) continue
                val objs = byLayer[layer.id].orEmpty()
                appendLine("""  <g id="layer-${escapeXml(layer.name)}" opacity="${layer.opacity}">""")
                for (m in objs) {
                    appendLine("    " + markupToSvgElement(m, w.toFloat(), h.toFloat()))
                }
                appendLine("  </g>")
            }
            appendLine("</svg>")
        }
    }

    private fun markupToSvgElement(m: MarkupObjectEntity, w: Float, h: Float): String {
        val kind = StudioTool.entries.firstOrNull { it.name == m.kind } ?: StudioTool.LINE
        val g = StudioGeometryJson.fromJson(m.geometryJson)
        val s = StudioStyleJson.fromJson(m.styleJson)
        val x1 = g.x1 * w
        val y1 = g.y1 * h
        val x2 = g.x2 * w
        val y2 = g.y2 * h
        val stroke = s.strokeColorHex.ifBlank { "#E03A3E" }
        val fill = s.fillColorHex.ifBlank { "none" }
        val dashAttr = when (DashStyle.entries.firstOrNull { it.name == s.dashStyle }) {
            DashStyle.DASHED -> """ stroke-dasharray="8 5""""
            DashStyle.DOTTED -> """ stroke-dasharray="2 4""""
            else -> ""
        }
        return when (kind) {
            StudioTool.LINE, StudioTool.ARROW, StudioTool.DIMENSION_DISTANCE ->
                """<line x1="$x1" y1="$y1" x2="$x2" y2="$y2" stroke="$stroke" stroke-width="${s.strokeWidthPt}" opacity="${s.opacity}"$dashAttr />"""
            StudioTool.RECT, StudioTool.TEXT_NOTE, StudioTool.STAMP, StudioTool.SPOTLIGHT -> {
                val l = min(x1, x2)
                val t = min(y1, y2)
                val rw = max(12f, kotlin.math.abs(x2 - x1))
                val rh = max(12f, kotlin.math.abs(y2 - y1))
                """<rect x="$l" y="$t" width="$rw" height="$rh" rx="${s.cornerRadiusPt}" stroke="$stroke" fill="$fill" stroke-width="${s.strokeWidthPt}" opacity="${s.opacity}"$dashAttr />"""
            }
            StudioTool.ELLIPSE -> {
                val cx = (x1 + x2) * 0.5f
                val cy = (y1 + y2) * 0.5f
                val rx = kotlin.math.abs(x2 - x1) * 0.5f
                val ry = kotlin.math.abs(y2 - y1) * 0.5f
                """<ellipse cx="$cx" cy="$cy" rx="$rx" ry="$ry" stroke="$stroke" fill="$fill" stroke-width="${s.strokeWidthPt}" opacity="${s.opacity}"$dashAttr />"""
            }
            else -> {
                if (g.points.isNotEmpty()) {
                    val d = g.points.mapIndexed { idx, pt ->
                        "${if (idx == 0) "M" else "L"}${pt.x * w} ${pt.y * h}"
                    }.joinToString(" ")
                    """<path d="$d" stroke="$stroke" fill="none" stroke-width="${s.strokeWidthPt}" opacity="${s.opacity}"$dashAttr />"""
                } else {
                    """<line x1="$x1" y1="$y1" x2="$x2" y2="$y2" stroke="$stroke" stroke-width="${s.strokeWidthPt}" opacity="${s.opacity}" />"""
                }
            }
        }
    }

    fun buildExportBundle(
        bookTitle: String,
        layers: List<AnnotationLayerEntity>,
        markups: List<MarkupObjectEntity>
    ): StudioExportBundle = StudioExportBundle(
        bookTitle = bookTitle,
        layers = layers.map { StudioExportBundle.ExportedLayer(it.id, it.name, it.visible, it.locked, it.opacity) },
        markups = markups.map {
            StudioExportBundle.ExportedMarkup(
                pageIndex = it.pageIndex,
                layerId = it.layerId,
                kind = it.kind,
                geometryJson = it.geometryJson,
                styleJson = it.styleJson,
                label = it.label
            )
        }
    )

    private fun escapeXml(input: String): String =
        input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
