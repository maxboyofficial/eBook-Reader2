package com.example.data.annotations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.preferences.ImportedFontInfo
import com.example.theme.MochiThemePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object QuoteCardGenerator {

    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1350

    suspend fun generateQuoteCardBitmap(
        context: Context,
        quote: String,
        note: String,
        bookTitle: String,
        bookAuthor: String,
        chapterTitle: String,
        palette: MochiThemePalette,
        fontId: String,
        importedFonts: List<ImportedFontInfo>,
        sticker: CozySticker,
        colorRole: Int,
        colorBlindSafe: Boolean
    ): Bitmap = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgColor = palette.background.toInt()
        val surfaceColor = palette.surface.toInt()
        val accentColor = palette.accent.toInt()
        val textColor = palette.text.toInt()
        val textSecColor = palette.textSecondary.toInt()
        val highlightLong = HighlightColorHelper.resolveColors(palette, colorBlindSafe)
            .let { list -> list[((colorRole % list.size) + list.size) % list.size] }
        val highlightColor = highlightLong.toInt()

        // Outer soft gradient backdrop
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(),
                intArrayOf(bgColor, palette.surfaceVariant.toInt(), bgColor),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

        // Subtle decorative corner glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = highlightColor
            alpha = if (palette.isDark) 75 else 135
        }
        canvas.drawCircle(940f, 160f, 240f, glowPaint)
        canvas.drawCircle(140f, 1220f, 200f, glowPaint)

        // Inner elevated paper card
        val cardRect = RectF(76f, 92f, CARD_WIDTH - 76f, CARD_HEIGHT - 92f)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22000000
        }
        canvas.drawRoundRect(
            RectF(cardRect.left, cardRect.top + 14f, cardRect.right, cardRect.bottom + 14f),
            48f, 48f, shadowPaint
        )

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = surfaceColor
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = accentColor
            alpha = 90
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, borderPaint)

        // Top washi tape strip
        val washiRect = RectF(CARD_WIDTH / 2f - 135f, 68f, CARD_WIDTH / 2f + 135f, 118f)
        val washiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = highlightColor
            alpha = 230
        }
        canvas.save()
        canvas.rotate(-2.2f, CARD_WIDTH / 2f, 93f)
        canvas.drawRoundRect(washiRect, 12f, 12f, washiPaint)
        canvas.restore()

        val typeface = resolveAndroidTypeface(context, fontId, importedFonts)

        // Oversized quote glyph
        val glyphPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 152f
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            alpha = 195
        }
        canvas.drawText("“", 132f, 278f, glyphPaint)

        // Cozy sticker badge top-right of card
        if (sticker != CozySticker.NONE) {
            drawCozySticker(canvas, sticker, CARD_WIDTH - 196f, 214f, accentColor, highlightColor)
        }

        // Quote text area
        val quoteBody = quote.trim().ifBlank { "A quiet passage remembered." }
        val quoteFontSize = when {
            quoteBody.length > 360 -> 34f
            quoteBody.length > 220 -> 40f
            quoteBody.length > 110 -> 46f
            else -> 54f
        }
        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = quoteFontSize
            this.typeface = typeface
        }
        val contentWidth = (CARD_WIDTH - 276)
        val quoteLayout = StaticLayout.Builder
            .obtain(quoteBody, 0, quoteBody.length, quotePaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(12f, 1.18f)
            .setMaxLines(12)
            .build()

        canvas.save()
        canvas.translate(138f, 304f)
        quoteLayout.draw(canvas)
        canvas.restore()

        var nextY = 304f + quoteLayout.height + 36f

        // Optional Note snippet
        val trimmedNote = note.trim()
        if (trimmedNote.isNotEmpty() && nextY < CARD_HEIGHT - 300f) {
            val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textSecColor
                textSize = 30f
                this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            }
            val noteLayout = StaticLayout.Builder
                .obtain(trimmedNote, 0, trimmedNote.length, notePaint, contentWidth - 40)
                .setMaxLines(3)
                .build()
            val noteBgRect = RectF(138f, nextY, CARD_WIDTH - 138f, nextY + noteLayout.height + 32f)
            val noteBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = highlightColor
                alpha = if (palette.isDark) 95 else 120
            }
            canvas.drawRoundRect(noteBgRect, 22f, 22f, noteBgPaint)
            canvas.save()
            canvas.translate(158f, nextY + 16f)
            noteLayout.draw(canvas)
            canvas.restore()
        }

        // Divider above citation footer
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            strokeWidth = 3f
            alpha = 110
        }
        canvas.drawLine(138f, CARD_HEIGHT - 276f, CARD_WIDTH - 138f, CARD_HEIGHT - 276f, divPaint)

        // Book citation footer
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 36f
            this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val titleLayout = StaticLayout.Builder
            .obtain(bookTitle, 0, bookTitle.length, titlePaint, contentWidth - 110)
            .setMaxLines(2)
            .build()
        canvas.save()
        canvas.translate(138f, CARD_HEIGHT - 246f)
        titleLayout.draw(canvas)
        canvas.restore()

        val metaText = listOfNotNull(
            bookAuthor.takeIf { it.isNotBlank() },
            chapterTitle.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textSecColor
            textSize = 26f
            this.typeface = Typeface.SANS_SERIF
        }
        canvas.drawText(metaText.take(54), 138f, CARD_HEIGHT - 144f, metaPaint)

        // Mini Mochi wordmark in bottom right
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 26f
            this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("mochi", CARD_WIDTH - 224f, CARD_HEIGHT - 144f, brandPaint)

        bmp
    }

    private fun drawCozySticker(
        canvas: Canvas,
        sticker: CozySticker,
        cx: Float,
        cy: Float,
        accentColor: Int,
        highlightColor: Int
    ) {
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = highlightColor
        }
        canvas.drawCircle(cx, cy, 44f, badgePaint)
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.FILL
            textSize = 44f
            textAlign = Paint.Align.CENTER
        }
        when (sticker) {
            CozySticker.HEART -> {
                val p = Path().apply {
                    moveTo(cx, cy + 18f)
                    cubicTo(cx - 28f, cy, cx - 26f, cy - 22f, cx, cy - 8f)
                    cubicTo(cx + 26f, cy - 22f, cx + 28f, cy, cx, cy + 18f)
                }
                canvas.drawPath(p, iconPaint)
            }
            CozySticker.MOCHI -> {
                val mochiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
                canvas.drawRoundRect(RectF(cx - 24f, cy - 16f, cx + 24f, cy + 18f), 20f, 18f, mochiPaint)
                val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
                canvas.drawCircle(cx - 9f, cy - 2f, 3.5f, facePaint)
                canvas.drawCircle(cx + 9f, cy - 2f, 3.5f, facePaint)
            }
            else -> {
                canvas.drawText(sticker.badgeText, cx, cy + 15f, iconPaint)
            }
        }
    }

    private fun resolveAndroidTypeface(
        context: Context,
        fontId: String,
        importedFonts: List<ImportedFontInfo>
    ): Typeface {
        val custom = importedFonts.find { it.id == fontId }
        if (custom != null && File(custom.filePath).exists()) {
            runCatching { return Typeface.createFromFile(custom.filePath) }
        }
        val assetName = when (fontId) {
            "literata" -> "fonts/literata.ttf"
            "inter" -> "fonts/inter.ttf"
            "lexend" -> "fonts/lexend.ttf"
            "jetbrains_mono" -> "fonts/jetbrains_mono.ttf"
            "caveat" -> "fonts/caveat.ttf"
            "patrick_hand" -> "fonts/patrick_hand.ttf"
            else -> null
        }
        if (assetName != null) {
            runCatching { return Typeface.createFromAsset(context.assets, assetName) }
        }
        return when (fontId) {
            "literata" -> Typeface.SERIF
            "jetbrains_mono" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
    }

    suspend fun saveBitmapToUri(context: Context, bitmap: Bitmap, destUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG compression failed" }
                } ?: error("Could not open PNG output stream")
                Unit
            }
        }

    suspend fun createShareablePngUri(context: Context, bitmap: Bitmap): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "quote_cards").apply { mkdirs() }
                val file = File(dir, "mochi_quote_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        }
}
