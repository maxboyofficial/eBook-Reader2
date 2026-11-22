package com.example.data.annotations

import com.example.data.db.AnnotationDao
import com.example.data.db.AnnotationEntity
import com.example.data.db.BookDao
import com.example.data.db.ExportTemplateDao
import com.example.data.db.ExportTemplateEntity
import com.example.data.reader.ReaderLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class AnnotationRepository(
    private val annotationDao: AnnotationDao,
    private val exportTemplateDao: ExportTemplateDao,
    private val bookDao: BookDao
) {
    fun observeBookAnnotations(bookId: Long): Flow<List<AnnotationEntity>> =
        annotationDao.observeForBook(bookId)

    fun observeAllAnnotations(): Flow<List<AnnotationWithBook>> =
        combine(annotationDao.observeAll(), bookDao.observeAllBooks()) { annotations, books ->
            val byId = books.associateBy { it.id }
            annotations.map { ann -> AnnotationWithBook(ann, byId[ann.bookId]) }
        }

    fun observeTemplates(): Flow<List<ExportTemplateEntity>> =
        exportTemplateDao.observeTemplates()

    suspend fun getAllAnnotationsOnce(): List<AnnotationWithBook> = withContext(Dispatchers.IO) {
        val byId = bookDao.getAllBooksOnce().associateBy { it.id }
        annotationDao.getAllOnce().map { ann -> AnnotationWithBook(ann, byId[ann.bookId]) }
    }

    suspend fun getBookAnnotationsOnce(bookId: Long): List<AnnotationEntity> = withContext(Dispatchers.IO) {
        annotationDao.getForBookOnce(bookId)
    }

    suspend fun ensureDefaultTemplates() = withContext(Dispatchers.IO) {
        if (exportTemplateDao.getAllOnce().isEmpty()) {
            exportTemplateDao.insertAll(defaultTemplates())
        }
    }

    suspend fun createHighlight(
        bookId: Long,
        locator: ReaderLocator,
        quoteExact: String,
        quotePrefix: String = "",
        quoteSuffix: String = "",
        colorRole: Int = 0,
        style: HighlightStyle = HighlightStyle.HIGHLIGHT,
        noteMarkdown: String = "",
        tags: String = "",
        sticker: CozySticker = CozySticker.NONE
    ): AnnotationEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val type = if (noteMarkdown.isNotBlank() && quoteExact.isBlank()) {
            AnnotationType.NOTE.name
        } else {
            AnnotationType.HIGHLIGHT.name
        }
        val entity = AnnotationEntity(
            bookId = bookId,
            type = type,
            colorRole = colorRole.coerceIn(0, 7),
            locatorJson = ReaderLocator.toJson(locator),
            quoteExact = quoteExact.trim(),
            quotePrefix = quotePrefix,
            quoteSuffix = quoteSuffix,
            noteMarkdown = noteMarkdown.trim(),
            tags = normalizeTags(tags),
            style = style.name,
            sticker = sticker.takeIf { it != CozySticker.NONE }?.name,
            createdAt = now,
            updatedAt = now
        )
        val id = annotationDao.upsert(entity)
        entity.copy(id = id)
    }

    suspend fun createBookmark(
        bookId: Long,
        locator: ReaderLocator,
        label: String = "",
        snippet: String = "",
        colorRole: Int = 0
    ): AnnotationEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = AnnotationEntity(
            bookId = bookId,
            type = AnnotationType.BOOKMARK.name,
            colorRole = colorRole.coerceIn(0, 7),
            locatorJson = ReaderLocator.toJson(locator),
            quoteExact = snippet.ifBlank { locator.quoteAnchor }.trim(),
            noteMarkdown = label.trim(),
            style = HighlightStyle.HIGHLIGHT.name,
            createdAt = now,
            updatedAt = now
        )
        val id = annotationDao.upsert(entity)
        entity.copy(id = id)
    }

    suspend fun upsertAnnotation(entity: AnnotationEntity): AnnotationEntity = withContext(Dispatchers.IO) {
        val updated = entity.copy(
            tags = normalizeTags(entity.tags),
            updatedAt = System.currentTimeMillis()
        )
        val id = annotationDao.upsert(updated)
        if (updated.id == 0L) updated.copy(id = id) else updated
    }

    suspend fun deleteAnnotation(id: Long) = withContext(Dispatchers.IO) {
        annotationDao.deleteById(id)
    }

    suspend fun saveTemplate(name: String, body: String, existingId: Long = 0L): Long = withContext(Dispatchers.IO) {
        exportTemplateDao.upsert(
            ExportTemplateEntity(
                id = existingId,
                name = name.trim().ifBlank { "Custom Template" },
                body = body
            )
        )
    }

    suspend fun deleteTemplate(id: Long) = withContext(Dispatchers.IO) {
        exportTemplateDao.deleteById(id)
    }

    private fun normalizeTags(raw: String): String {
        return raw.split(',', ' ')
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString(", ")
    }

    companion object {
        fun defaultTemplates(): List<ExportTemplateEntity> = listOf(
            ExportTemplateEntity(
                name = "Obsidian / Logseq Note",
                body = """
                    ### {{title}} — {{chapter}} ({{page}})
                    > {{quote}}
                    
                    {{note}}
                    
                    - Color: {{color}} | Style: {{style}}
                    - Tags: {{tags}}
                    - Date: {{date}}
                """.trimIndent()
            ),
            ExportTemplateEntity(
                name = "Clean Study Digest",
                body = """
                    > "{{quote}}"
                    > — *{{title}}* by {{author}} ({{chapter}}, {{page}})
                    
                    {{note}}
                    {{tags}}
                """.trimIndent()
            ),
            ExportTemplateEntity(
                name = "Compact Citation",
                body = "- \"{{quote}}\" — {{title}} ({{chapter}}, {{page}}) {{note}}"
            )
        )
    }
}
