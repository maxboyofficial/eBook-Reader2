package com.example.data.studio

import com.example.data.db.AnnotationLayerEntity
import com.example.data.db.AnnotationStudioDao
import com.example.data.db.MarkupObjectEntity
import com.example.data.db.PageScaleEntity
import kotlinx.coroutines.flow.Flow

class AnnotationStudioRepository(
    private val dao: AnnotationStudioDao
) {
    fun observeLayers(bookId: Long): Flow<List<AnnotationLayerEntity>> = dao.observeLayers(bookId)

    fun observePageMarkups(bookId: Long, pageIndex: Int): Flow<List<MarkupObjectEntity>> =
        dao.observePageMarkups(bookId, pageIndex)

    fun observeBookMarkups(bookId: Long): Flow<List<MarkupObjectEntity>> =
        dao.observeAllBookMarkups(bookId)

    fun observeScales(bookId: Long): Flow<List<PageScaleEntity>> = dao.observeScales(bookId)

    suspend fun ensureDefaultLayers(bookId: Long): List<AnnotationLayerEntity> {
        val existing = dao.getLayersOnce(bookId)
        if (existing.isNotEmpty()) return existing
        val now = System.currentTimeMillis()
        val notesId = dao.upsertLayer(
            AnnotationLayerEntity(
                bookId = bookId,
                name = "My notes",
                visible = true,
                locked = false,
                opacity = 1f,
                sortOrder = 0,
                createdAt = now
            )
        )
        val reviewId = dao.upsertLayer(
            AnnotationLayerEntity(
                bookId = bookId,
                name = "Review",
                visible = true,
                locked = false,
                opacity = 1f,
                sortOrder = 1,
                createdAt = now + 1
            )
        )
        return listOf(
            AnnotationLayerEntity(id = notesId, bookId = bookId, name = "My notes", sortOrder = 0, createdAt = now),
            AnnotationLayerEntity(id = reviewId, bookId = bookId, name = "Review", sortOrder = 1, createdAt = now + 1)
        )
    }

    suspend fun createLayer(bookId: Long, name: String): Long {
        val existing = dao.getLayersOnce(bookId)
        val clean = name.trim().ifBlank { "Layer ${existing.size + 1}" }
        return dao.upsertLayer(
            AnnotationLayerEntity(
                bookId = bookId,
                name = clean,
                visible = true,
                locked = false,
                opacity = 1f,
                sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
            )
        )
    }

    suspend fun updateLayer(layer: AnnotationLayerEntity) {
        dao.upsertLayer(layer)
    }

    suspend fun deleteLayer(layerId: Long) {
        dao.deleteMarkupsByLayer(layerId)
        dao.deleteLayer(layerId)
    }

    suspend fun upsertMarkup(markup: MarkupObjectEntity): MarkupObjectEntity {
        val now = System.currentTimeMillis()
        val stamped = markup.copy(updatedAt = now)
        val rowId = dao.upsertMarkup(stamped)
        return if (stamped.id == 0L) stamped.copy(id = rowId) else stamped
    }

    suspend fun upsertMarkups(markups: List<MarkupObjectEntity>) {
        dao.upsertMarkups(markups)
    }

    suspend fun deleteMarkup(id: Long) = dao.deleteMarkup(id)

    suspend fun deleteMarkups(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.deleteMarkups(ids)
    }

    suspend fun savePageScale(bookId: Long, pageIndex: Int, pagePtsPerUnit: Float, unit: MeasurementUnit) {
        dao.upsertScale(
            PageScaleEntity(
                bookId = bookId,
                pageIndex = pageIndex,
                pagePtsPerUnit = pagePtsPerUnit.coerceAtLeast(0.01f),
                unit = unit.name
            )
        )
    }

    suspend fun getBookMarkupsOnce(bookId: Long): List<MarkupObjectEntity> = dao.getAllBookMarkupsOnce(bookId)

    suspend fun getLayersOnce(bookId: Long): List<AnnotationLayerEntity> = dao.getLayersOnce(bookId)
}
