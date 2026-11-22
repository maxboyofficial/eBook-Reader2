package com.example.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SeededSampleFile(
    val file: File,
    val relativeFolder: String,
    val title: String,
    val authors: String,
    val series: String? = null,
    val seriesIndex: Float? = null,
    val progress: Float = 0f,
    val lastOpenedOffsetMs: Long? = null
)

object SampleBookSeeder {

    suspend fun seedIfNeeded(context: Context): List<SeededSampleFile> = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "samples")
        val classicsDir = File(root, "Classics").apply { mkdirs() }
        val guidesDir = File(root, "Guides").apply { mkdirs() }
        val comicsDir = File(root, "Comics").apply { mkdirs() }

        val epubFile = File(classicsDir, "the_secret_garden_of_kyoto.epub")
        if (!epubFile.exists()) {
            writeSampleEpub(epubFile)
        }

        val pdfFile = File(guidesDir, "cozy_typography_guide.pdf")
        if (!pdfFile.exists()) {
            writeSamplePdf(pdfFile)
        }

        val mdFile = File(guidesDir, "mochi_field_notes.md")
        if (!mdFile.exists()) {
            writeSampleMarkdown(mdFile)
        }

        val txtFile = File(classicsDir, "the_gift_of_the_magi.txt")
        if (!txtFile.exists()) {
            writeSampleTxt(txtFile)
        }

        val cbzFile = File(comicsDir, "mochis_midnight_snack.cbz")
        if (!cbzFile.exists()) {
            writeSampleCbz(cbzFile)
        }

        val now = System.currentTimeMillis()
        listOf(
            SeededSampleFile(
                file = epubFile,
                relativeFolder = "Classics",
                title = "The Secret Garden of Kyoto",
                authors = "Hana Takahashi",
                series = "Kyoto Seasons",
                seriesIndex = 1f,
                progress = 0.38f,
                lastOpenedOffsetMs = now - 1000L * 60 * 15
            ),
            SeededSampleFile(
                file = pdfFile,
                relativeFolder = "Guides",
                title = "A Field Guide to Cozy Typography",
                authors = "Mochi Press",
                progress = 0.62f,
                lastOpenedOffsetMs = now - 1000L * 60 * 120
            ),
            SeededSampleFile(
                file = mdFile,
                relativeFolder = "Guides",
                title = "Mochi Reader Field Notes",
                authors = "Studio Notes",
                progress = 0.15f,
                lastOpenedOffsetMs = now - 1000L * 60 * 60 * 18
            ),
            SeededSampleFile(
                file = txtFile,
                relativeFolder = "Classics",
                title = "The Gift of the Magi",
                authors = "O. Henry",
                series = "Pocket Classics",
                seriesIndex = 1f,
                progress = 0f
            ),
            SeededSampleFile(
                file = cbzFile,
                relativeFolder = "Comics",
                title = "Mochi's Midnight Snack",
                authors = "K. Sato",
                series = "Mochi Shorts",
                seriesIndex = 1f,
                progress = 1.0f
            )
        )
    }

    private fun renderCoverPng(
        title: String,
        subtitle: String,
        topColor: Int,
        bottomColor: Int,
        accentColor: Int
    ): ByteArray {
        val width = 420
        val height = 600
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                topColor, bottomColor, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val decorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 70
        }
        canvas.drawCircle(width * 0.78f, height * 0.22f, 110f, decorPaint)
        canvas.drawCircle(width * 0.22f, height * 0.78f, 140f, decorPaint)

        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 180
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRoundRect(RectF(28f, 28f, width - 28f, height - 28f), 20f, 20f, framePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF231820.toInt()
            textSize = 36f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val words = title.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (titlePaint.measureText(candidate) <= width - 104f) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = w
            }
        }
        if (current.isNotEmpty()) lines.add(current)

        var y = 170f
        for (line in lines.take(4)) {
            canvas.drawText(line, 52f, y, titlePaint)
            y += 46f
        }

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5A3D49.toInt()
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(subtitle, 52f, height - 76f, subPaint)

        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
            bmp.recycle()
            out.toByteArray()
        }
    }

    private fun writeSampleEpub(target: File) {
        val coverPng = renderCoverPng(
            title = "The Secret Garden of Kyoto",
            subtitle = "Hana Takahashi",
            topColor = 0xFFFFE4EC.toInt(),
            bottomColor = 0xFFFFC2D4.toInt(),
            accentColor = 0xFFFF6F9C.toInt()
        )
        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()

        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="uid">mochi-sample-kyoto-01</dc:identifier>
                <dc:title>The Secret Garden of Kyoto</dc:title>
                <dc:creator>Hana Takahashi</dc:creator>
                <dc:language>en</dc:language>
                <meta property="belongs-to-collection" id="c1">Kyoto Seasons</meta>
                <meta refines="#c1" property="group-position">1</meta>
                <meta name="cover" content="cover-image"/>
              </metadata>
              <manifest>
                <item id="cover-image" href="cover.png" media-type="image/png" properties="cover-image"/>
                <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="ch1"/>
                <itemref idref="ch2"/>
              </spine>
            </package>
        """.trimIndent()

        val ch1 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 1: Morning Mist</title></head>
            <body>
              <h1>Chapter 1: Morning Mist</h1>
              <p>Behind the cedar gate of the old teahouse, moss softened every stone step. Morning light slipped through maple branches and warmed the paper screen.</p>
              <p>Aoi opened her notebook and traced the quiet geometry of the courtyard—three lanterns, a stone basin, and a single camellia tree in bloom.</p>
            </body>
            </html>
        """.trimIndent()

        val ch2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 2: Rain on Cedar</title></head>
            <body>
              <h1>Chapter 2: Rain on Cedar</h1>
              <p>By noon a gentle rain arrived from the northern hills. Drops gathered along the bamboo spout and measured the afternoon in steady beats.</p>
              <p>Inside the tatami room, tea steamed beside a stack of hand-bound volumes waiting to be cataloged before dusk.</p>
            </body>
            </html>
        """.trimIndent()

        FileOutputStream(target).use { fos ->
            ZipOutputStream(fos).use { zip ->
                val mimeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
                val mimeEntry = ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimeBytes.size.toLong()
                    compressedSize = mimeBytes.size.toLong()
                    crc = CRC32().apply { update(mimeBytes) }.value
                }
                zip.putNextEntry(mimeEntry)
                zip.write(mimeBytes)
                zip.closeEntry()

                fun addText(path: String, body: String) {
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(body.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                addText("META-INF/container.xml", containerXml)
                addText("OEBPS/content.opf", opf)
                addText("OEBPS/ch1.xhtml", ch1)
                addText("OEBPS/ch2.xhtml", ch2)

                zip.putNextEntry(ZipEntry("OEBPS/cover.png"))
                zip.write(coverPng)
                zip.closeEntry()
            }
        }
    }

    private fun writeSamplePdf(target: File) {
        val pagesContent = listOf(
            buildString {
                appendLine("q 0.96 0.94 1 rg 0 0 595 842 re f Q")
                appendLine("q 0.72 0.61 1 rg 56 640 483 120 re f Q")
                appendLine("BT /F1 26 Tf 0.14 0.11 0.22 rg 76 708 Td (A Field Guide to Cozy Typography) Tj ET")
                appendLine("BT /F1 14 Tf 0.24 0.21 0.34 rg 76 664 Td (By Mochi Press - Edition 1) Tj ET")
                appendLine("BT /F1 14 Tf 0.18 0.15 0.26 rg 76 560 Td (1. Line height and breathing room) Tj ET")
                appendLine("BT /F1 14 Tf 0.18 0.15 0.26 rg 76 525 Td (2. Paper tint and contrast harmony) Tj ET")
                appendLine("BT /F1 14 Tf 0.18 0.15 0.26 rg 76 490 Td (3. Serif rhythm for long sessions) Tj ET")
            },
            buildString {
                appendLine("q 1 1 1 rg 0 0 595 842 re f Q")
                appendLine("BT /F1 20 Tf 0.14 0.11 0.22 rg 64 740 Td (Chapter 1 - Generous Margins) Tj ET")
                appendLine("BT /F1 13 Tf 0.20 0.18 0.26 rg 64 696 Td (Comfortable reading begins before a single word is read.) Tj ET")
                appendLine("BT /F1 13 Tf 0.20 0.18 0.26 rg 64 670 Td (Margins frame the text block and anchor the eye.) Tj ET")
            },
            buildString {
                appendLine("q 1 1 1 rg 0 0 595 842 re f Q")
                appendLine("BT /F1 20 Tf 0.14 0.11 0.22 rg 64 740 Td (Chapter 2 - Calm Contrast) Tj ET")
                appendLine("BT /F1 13 Tf 0.20 0.18 0.26 rg 64 696 Td (Warm paper tints reduce glare during late-night reading.) Tj ET")
            }
        )

        val objects = mutableListOf<String>()
        // 1: Catalog, 2: Pages, 3: Helvetica Font, then pairs of (Page, ContentStream)
        val pageObjIds = pagesContent.indices.map { 4 + it * 2 }
        objects.add("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        objects.add("2 0 obj\n<< /Type /Pages /Kids [${pageObjIds.joinToString(" ") { "$it 0 R" }}] /Count ${pagesContent.size} >>\nendobj\n")
        objects.add("3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")

        pagesContent.forEachIndexed { idx, stream ->
            val pageId = 4 + idx * 2
            val streamId = pageId + 1
            val streamBytes = stream.toByteArray(Charsets.US_ASCII)
            objects.add(
                "$pageId 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents $streamId 0 R >>\nendobj\n"
            )
            objects.add(
                "$streamId 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n${stream}endstream\nendobj\n"
            )
        }

        val out = ByteArrayOutputStream()
        fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))

        writeAscii("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        for (obj in objects) {
            offsets.add(out.size())
            writeAscii(obj)
        }
        val xrefPos = out.size()
        writeAscii("xref\n0 ${objects.size + 1}\n")
        writeAscii("0000000000 65535 f \n")
        for (off in offsets) {
            writeAscii(String.format("%010d 00000 n \n", off))
        }
        writeAscii("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        target.writeBytes(out.toByteArray())
    }

    private fun writeSampleMarkdown(target: File) {
        val md = """
            ---
            title: Mochi Reader Field Notes
            author: Studio Notes
            tags: [offline, typography, workflow]
            ---

            # Welcome to Mochi Reader

            Mochi keeps **every book, highlight, and note** 100% offline on your device.

            ## Core Principles
            - [x] Non-destructive annotations (original file hash never changes)
            - [x] Liquid Glass floating chrome with WCAG AA contrast protection
            - [ ] Explore custom themes in Theme Studio

            ## Quick Shortcuts
            | Gesture | Action |
            | --- | --- |
            | Center tap | Toggle reader chrome |
            | Long press cover | Drag & drop to create stack |

            See also [[The Secret Garden of Kyoto]] in your Classics folder.
        """.trimIndent()
        target.writeText(md, Charsets.UTF_8)
    }

    private fun writeSampleTxt(target: File) {
        val txt = """
            The Gift of the Magi
            By O. Henry

            One dollar and eighty-seven cents. That was all. And sixty cents of it was in pennies.
            Pennies saved one and two at a time by bulldozing the grocer and the vegetable man and the butcher
            until one's cheeks burned with the silent imputation of parsimony that such close dealing implied.

            Three times Della counted it. One dollar and eighty-seven cents. And the next day would be Christmas.
            There was clearly nothing to do but flop down on the shabby little couch and howl.
            So Della did it. Which instigates the moral reflection that life is made up of sobs, sniffles, and smiles,
            with sniffles predominating.
        """.trimIndent()
        target.writeText(txt, Charsets.UTF_8)
    }

    private fun writeSampleCbz(target: File) {
        val pages = listOf(
            renderCoverPng("Mochi's Midnight Snack", "Episode 1 · K. Sato", 0xFFD9F3E9.toInt(), 0xFFB8E2D2.toInt(), 0xFF3E9C7B.toInt()),
            renderCoverPng("Panel 2: Warm Matcha", "Steaming quietly...", 0xFFF2F7E9.toInt(), 0xFFD8E8C5.toInt(), 0xFF6B9E4B.toInt()),
            renderCoverPng("Panel 3: Sweet Dreams", "Goodnight, shelf.", 0xFFEAE2FF.toInt(), 0xFFD1C2FF.toInt(), 0xFF7B5CD6.toInt())
        )
        FileOutputStream(target).use { fos ->
            ZipOutputStream(fos).use { zip ->
                pages.forEachIndexed { idx, bytes ->
                    zip.putNextEntry(ZipEntry("page_0${idx + 1}.png"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }
}
