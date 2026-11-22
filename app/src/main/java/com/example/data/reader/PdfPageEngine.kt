package com.example.data.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class PdfDocumentSummary(
    val pageCount: Int,
    val pageSizes: List<Pair<Int, Int>>
)

/**
 * Thread-safe PDF rasterization engine backed by Android's native [PdfRenderer].
 * - Serializes native PdfRenderer page opens via a coroutine [Mutex].
 * - Maintains a memory-bounded LRU Bitmap cache (keyed by bookUri + pageIndex + bucket width).
 * - Supports non-destructive auto-margin crop detection, smart dark-mode inversion, and
 *   column/content focus detection for smart double-tap zoom.
 */
object PdfPageEngine {

    private val rendererMutex = Mutex()
    private val maxCacheKb = (Runtime.getRuntime().maxMemory() / 1024L / 8L).toInt().coerceIn(8 * 1024, 48 * 1024)
    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun trimMemory(level: Int) {
        when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE -> bitmapCache.evictAll()
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> bitmapCache.trimToSize(maxCacheKb / 2)
        }
    }

    fun openFileDescriptor(context: Context, uriString: String): ParcelFileDescriptor? {
        val uri = Uri.parse(uriString)
        return when {
            uri.scheme == "file" && uri.path != null -> {
                val f = File(uri.path!!)
                if (f.exists()) ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY) else null
            }
            uri.scheme == null && uriString.startsWith("/") -> {
                val f = File(uriString)
                if (f.exists()) ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY) else null
            }
            else -> context.contentResolver.openFileDescriptor(uri, "r")
        }
    }

    suspend fun inspectPdf(context: Context, uriString: String): PdfDocumentSummary = withContext(Dispatchers.IO) {
        rendererMutex.withLock {
            val pfd = openFileDescriptor(context, uriString)
                ?: throw IllegalStateException("PDF file is no longer accessible.")
            pfd.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val count = renderer.pageCount
                    if (count <= 0) {
                        throw IllegalStateException("PDF contains zero readable pages.")
                    }
                    val sizes = ArrayList<Pair<Int, Int>>(count)
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            sizes.add(page.width to page.height)
                        }
                    }
                    PdfDocumentSummary(pageCount = count, pageSizes = sizes)
                }
            }
        }
    }

    suspend fun renderPageBitmap(
        context: Context,
        uriString: String,
        pageIndex: Int,
        targetWidthPx: Int,
        smartDarkInvert: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        val widthBucket = ((targetWidthPx.coerceIn(280, 3200) + 127) / 128) * 128
        val cacheKey = "${uriString}_${pageIndex}_${widthBucket}_${if (smartDarkInvert) 1 else 0}"
        bitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        val rendered = rendererMutex.withLock {
            bitmapCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) return@withLock cached
            }
            val pfd = openFileDescriptor(context, uriString) ?: return@withLock null
            pfd.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@withLock null
                    renderer.openPage(pageIndex).use { page ->
                        val aspect = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                        val w = widthBucket
                        val h = (w * aspect).roundToInt().coerceIn(200, 4200)
                        val rawBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(rawBmp).drawColor(Color.WHITE)
                        page.render(rawBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val finalBmp = if (smartDarkInvert) {
                            val inverted = applySmartDarkInvert(rawBmp)
                            rawBmp.recycle()
                            inverted
                        } else {
                            rawBmp
                        }
                        bitmapCache.put(cacheKey, finalBmp)
                        finalBmp
                    }
                }
            }
        }
        rendered
    }

    /**
     * Produces a dark-mode friendly inversion with warm charcoal paper & preserved hue readability.
     */
    fun applySmartDarkInvert(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        // Hue-preserving lightness inversion filter
        val invertMatrix = ColorMatrix(
            floatArrayOf(
                -0.88f, 0f, 0f, 0f, 240f,
                0f, -0.88f, 0f, 0f, 238f,
                0f, 0f, -0.85f, 0f, 232f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(invertMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    /**
     * Scans a downsampled version of the page bitmap to compute tight normalized content bounds
     * (`0f..1f` LTRB) for Auto Margin Crop or smart double-tap column zooming.
     */
    fun detectNormalizedContentBounds(bitmap: Bitmap): RectF {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 16 || h < 16) return RectF(0f, 0f, 1f, 1f)

        val stepX = (w / 64).coerceAtLeast(1)
        val stepY = (h / 64).coerceAtLeast(1)
        var minX = w
        var maxX = 0
        var minY = h
        var maxY = 0
        var foundInk = false

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                // Treat non-near-white and non-near-pure-black background margins as ink
                if (lum in 15..240) {
                    foundInk = true
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                x += stepX
            }
            y += stepY
        }

        if (!foundInk || maxX <= minX || maxY <= minY) return RectF(0f, 0f, 1f, 1f)

        val padX = (w * 0.03f)
        val padY = (h * 0.03f)
        return RectF(
            ((minX - padX) / w).coerceIn(0f, 0.35f),
            ((minY - padY) / h).coerceIn(0f, 0.35f),
            ((maxX + padX) / w).coerceIn(0.65f, 1f),
            ((maxY + padY) / h).coerceIn(0.65f, 1f)
        )
    }
}
