package com.example.data.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.db.BookDao
import com.example.data.db.BookEntity
import com.example.data.db.BookSettingsDao
import com.example.data.db.BookSettingsEntity
import com.example.data.db.PositionDao
import com.example.data.db.PositionEntity
import com.example.data.db.ReadingStatus
import com.example.data.preferences.ImportedFontInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ReaderRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val positionDao: PositionDao,
    private val bookSettingsDao: BookSettingsDao
) {
    suspend fun getBook(bookId: Long): BookEntity? = withContext(Dispatchers.IO) {
        bookDao.getBookById(bookId)
    }

    suspend fun loadDocument(book: BookEntity): ParsedDocument = withContext(Dispatchers.IO) {
        val allBooks = bookDao.getAllBooksOnce()
        DocumentParsers.parseBook(context, book, allBooks)
    }

    suspend fun getSavedLocator(bookId: Long): ReaderLocator? = withContext(Dispatchers.IO) {
        positionDao.getPosition(bookId)?.let { ReaderLocator.fromJson(it.locatorJson) }
    }

    suspend fun savePosition(bookId: Long, locator: ReaderLocator) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        positionDao.upsertPosition(
            PositionEntity(
                bookId = bookId,
                locatorJson = ReaderLocator.toJson(locator),
                updatedAt = now
            )
        )
        val status = when {
            locator.progression >= 0.99f -> ReadingStatus.FINISHED
            locator.progression > 0f -> ReadingStatus.READING
            else -> ReadingStatus.READING
        }
        bookDao.updateReadingProgress(
            bookId = bookId,
            progress = locator.progression.coerceIn(0f, 1f),
            lastOpenedAt = now,
            status = status
        )
    }

    fun observeBookSettings(bookId: Long): Flow<BookSettingsEntity?> =
        bookSettingsDao.observeBookSettings(bookId)

    suspend fun setBookThemeOverride(bookId: Long, themeId: String?) = withContext(Dispatchers.IO) {
        val existing = bookSettingsDao.getBookSettings(bookId)
        if (themeId == null && existing?.fontOverride == null && existing?.layoutOverrideJson == null) {
            bookSettingsDao.clearBookSettings(bookId)
        } else {
            bookSettingsDao.upsertBookSettings(
                BookSettingsEntity(
                    bookId = bookId,
                    themeId = themeId,
                    fontOverride = existing?.fontOverride,
                    layoutOverrideJson = existing?.layoutOverrideJson
                )
            )
        }
    }

    suspend fun importFontFromSaf(uri: Uri): ImportedFontInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
            var displayName = "Custom Font"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) displayName = cursor.getString(idx) ?: displayName
                }
            }
            val cleanName = displayName.substringBeforeLast('.')
            val ext = if (displayName.endsWith(".otf", true)) "otf" else "ttf"
            val id = "font_${UUID.randomUUID().toString().take(8)}"
            val dest = File(fontsDir, "$id.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out) }
            } ?: return@withContext null

            ImportedFontInfo(
                id = id,
                displayName = cleanName,
                filePath = dest.absolutePath
            )
        }.getOrNull()
    }
}
