package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.BookEntity
import com.example.data.db.BookFormat
import com.example.data.db.MochiDatabase
import com.example.data.db.ReadingSessionEntity
import com.example.data.preferences.ReaderSettings
import com.example.data.reader.ContentBlock
import com.example.data.reader.PdfReflowExtractor
import com.example.data.stats.ReadingStatsRepository
import com.example.theme.BundledThemes
import com.example.theme.GlassIntensity
import com.example.ui.stats.ReadingStatsSheet
import com.example.ui.theme.MochiExpressiveTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.Deflater

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfReflowAndStatsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pdfReflowExtractsUncompressedFlateAndCMapPagesAndDetectsScannedPage() {
        val page1Stream = """
            BT
            /F1 22 Tf
            72 720 Td
            (Chapter One: Timber Frames) Tj
            /F1 12 Tf
            0 -32 Td
            [(Joinery relies on ) -20 (clean mortise) 0 ( and tenon cuts.)] TJ
            0 -26 Td
            (Seasoned oak resists torsion over decades.) Tj
            ET
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)

        val page2PlainStream = """
            BT
            /F2 12 Tf
            72 700 Td
            <00480065006C006C006F> Tj
            0 -16 Td
            (Flate compressed paragraph text.) Tj
            ET
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)
        val page2Compressed = deflateZlib(page2PlainStream)

        val cmapStream = """
            /CIDInit /ProcSet findresource begin
            12 dict begin
            begincmap
            beginbfchar
            <0048> <0048>
            <0065> <0065>
            <006C> <006C>
            <006F> <006F>
            endbfchar
            endcmap
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)

        // Page 3: pure vector/image graphics operator, zero text operators (scanned page)
        val page3ScannedStream = "q 300 0 0 400 72 150 cm /Im1 Do Q".toByteArray(Charsets.ISO_8859_1)

        val baos = ByteArrayOutputStream()
        fun emit(s: String) = baos.write(s.toByteArray(Charsets.ISO_8859_1))

        emit("%PDF-1.4\n")
        emit("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        emit("2 0 obj\n<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 >>\nendobj\n")
        emit("3 0 obj\n<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 8 0 R >> >> /Contents 6 0 R >>\nendobj\n")
        emit("4 0 obj\n<< /Type /Page /Parent 2 0 R /Resources << /Font << /F2 9 0 R >> >> /Contents 7 0 R >>\nendobj\n")
        emit("5 0 obj\n<< /Type /Page /Parent 2 0 R /Contents 10 0 R >>\nendobj\n")

        emit("6 0 obj\n<< /Length ${page1Stream.size} >>\nstream\n")
        baos.write(page1Stream)
        emit("\nendstream\nendobj\n")

        emit("7 0 obj\n<< /Filter /FlateDecode /Length ${page2Compressed.size} >>\nstream\n")
        baos.write(page2Compressed)
        emit("\nendstream\nendobj\n")

        emit("8 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")
        emit("9 0 obj\n<< /Type /Font /Subtype /Type0 /ToUnicode 11 0 R >>\nendobj\n")

        emit("10 0 obj\n<< /Length ${page3ScannedStream.size} >>\nstream\n")
        baos.write(page3ScannedStream)
        emit("\nendstream\nendobj\n")

        emit("11 0 obj\n<< /Length ${cmapStream.size} >>\nstream\n")
        baos.write(cmapStream)
        emit("\nendstream\nendobj\n")

        emit("trailer\n<< /Root 1 0 R >>\n%%EOF\n")

        val pdfBytes = baos.toByteArray()
        val digestBefore = sha256(pdfBytes)

        val result = PdfReflowExtractor.extractFromBytes(pdfBytes, expectedPageCount = 3)

        assertEquals(3, result.chapters.size)
        assertEquals(2, result.extractedPageCount)
        assertEquals(setOf(2), result.scannedPageIndices)

        // Page 1: heading + paragraph text preserved cleanly
        val ch1 = result.chapters[0]
        assertTrue(ch1.blocks.any { it is ContentBlock.Heading && it.content.text.contains("Chapter One") })
        assertTrue(ch1.plainText.contains("clean mortise and tenon cuts"))
        assertTrue(ch1.plainText.contains("Seasoned oak resists torsion"))

        // Page 2: CMap hex decode + FlateDecode decompression
        val ch2 = result.chapters[1]
        assertTrue(ch2.plainText.contains("Hello"))
        assertTrue(ch2.plainText.contains("Flate compressed paragraph text"))

        // Page 3: Scanned page fallback chapter
        assertTrue(result.chapters[2].plainText.contains("scanned", ignoreCase = true))

        // Non-destructive guarantee: bytes untouched
        assertEquals(digestBefore, sha256(pdfBytes))
    }

    @Test
    fun readingStatsStreakWeeklyPaceAndRoomAccumulationWork() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MochiDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val bookId = db.bookDao().upsertBook(
                BookEntity(
                    uri = "file:///tmp/demo.epub",
                    contentHash = "abc123",
                    title = "Timber & Tea",
                    authors = "H. Takahashi",
                    format = BookFormat.EPUB,
                    addedAt = 1000L
                )
            )
            val statsRepo = ReadingStatsRepository(db.readingStatsDao())

            // Record two incremental deltas on same date -> should accumulate cleanly
            statsRepo.recordReadingDelta(bookId, "Timber & Tea", 90L, 4, dateIso = "2026-11-21")
            statsRepo.recordReadingDelta(bookId, "Timber & Tea", 150L, 7, dateIso = "2026-11-21")
            // Also record previous days to form a 3-day streak ending 2026-11-21
            statsRepo.recordReadingDelta(bookId, "Timber & Tea", 300L, 10, dateIso = "2026-11-20")
            statsRepo.recordReadingDelta(bookId, "Timber & Tea", 120L, 5, dateIso = "2026-11-19")
            // Gap on 2026-11-18, plus an older 4-day streak (Nov 10-13)
            val syntheticOlder = listOf(
                ReadingSessionEntity(bookId = bookId, bookTitle = "Timber & Tea", dateIso = "2026-11-10", durationSeconds = 120L, pagesTurned = 3),
                ReadingSessionEntity(bookId = bookId, bookTitle = "Timber & Tea", dateIso = "2026-11-11", durationSeconds = 180L, pagesTurned = 4),
                ReadingSessionEntity(bookId = bookId, bookTitle = "Timber & Tea", dateIso = "2026-11-12", durationSeconds = 240L, pagesTurned = 6),
                ReadingSessionEntity(bookId = bookId, bookTitle = "Timber & Tea", dateIso = "2026-11-13", durationSeconds = 60L, pagesTurned = 2)
            )

            val dbSessions = db.readingStatsDao().getAllSessionsOnce()
            assertEquals(3, dbSessions.size)
            val nov21 = dbSessions.first { it.dateIso == "2026-11-21" }
            assertEquals(240L, nov21.durationSeconds)
            assertEquals(11, nov21.pagesTurned)

            val insights = ReadingStatsRepository.computeInsights(
                sessions = dbSessions + syntheticOlder,
                todayIso = "2026-11-21"
            )
            assertEquals(3, insights.currentStreakDays)
            assertEquals(4, insights.longestStreakDays)
            assertEquals(240L, insights.todaySeconds)
            assertEquals(1260L, insights.totalSeconds)
            assertEquals(41, insights.totalPagesTurned)
            assertEquals(7, insights.last7Days.size)
            assertEquals(1, insights.topBooks.size)

            // Verify ReaderSettings Moshi round-trip for pdfReflowEnabled
            val original = ReaderSettings(pdfReflowEnabled = true)
            val decoded = ReaderSettings.fromJson(ReaderSettings.toJson(original))
            assertTrue(decoded?.pdfReflowEnabled == true)
            assertFalse(ReaderSettings().pdfReflowEnabled)
        } finally {
            db.close()
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp")
    fun readingStatsSheetDisplaysMetricsAndWeeklyChart() {
        val palette = BundledThemes.byId(BundledThemes.DEFAULT_LIGHT_ID)
        val insights = ReadingStatsRepository.computeInsights(
            sessions = listOf(
                ReadingSessionEntity(
                    bookId = 1L,
                    bookTitle = "Design Principles",
                    dateIso = "2026-11-21",
                    durationSeconds = 1860L,
                    pagesTurned = 24
                )
            ),
            todayIso = "2026-11-21"
        )

        composeRule.setContent {
            MochiExpressiveTheme(
                palette = palette,
                glassIntensity = GlassIntensity.RICH,
                solidChrome = false,
                reduceMotion = true,
                useDynamicColor = false,
                matchSystemBars = false,
                warmLightEnabled = false,
                warmLightIntensity = 0f
            ) {
                ReadingStatsSheet(
                    insights = insights,
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("reading_stats_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("weekly_reading_chart").assertIsDisplayed()
        composeRule.onNodeWithText("Reading Insights").assertIsDisplayed()
        composeRule.onNodeWithText("Design Principles").performScrollTo().assertIsDisplayed()
    }

    private fun deflateZlib(input: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val len = deflater.deflate(buf)
            out.write(buf, 0, len)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
