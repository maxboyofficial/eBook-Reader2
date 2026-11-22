package com.example.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MochiApplication
import com.example.data.annotations.AnnotationType
import com.example.data.annotations.AnnotationWithBook
import com.example.data.annotations.CozySticker
import com.example.data.annotations.HighlightStyle
import com.example.data.db.AnnotationEntity
import com.example.data.db.BookEntity
import com.example.data.db.ExportTemplateEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesUiState(
    val allItems: List<AnnotationWithBook> = emptyList(),
    val filteredItems: List<AnnotationWithBook> = emptyList(),
    val booksWithAnnotations: List<BookEntity> = emptyList(),
    val templates: List<ExportTemplateEntity> = emptyList(),
    val query: String = "",
    val typeFilter: AnnotationType? = null,
    val colorRoleFilter: Int? = null,
    val bookIdFilter: Long? = null
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MochiApplication).container
    private val annotationRepo = container.annotationRepository

    private val queryFlow = MutableStateFlow("")
    private val typeFilterFlow = MutableStateFlow<AnnotationType?>(null)
    private val colorFilterFlow = MutableStateFlow<Int?>(null)
    private val bookFilterFlow = MutableStateFlow<Long?>(null)

    private data class FilterTuple(
        val query: String,
        val type: AnnotationType?,
        val color: Int?,
        val bookId: Long?
    )

    private val filtersFlow = combine(
        queryFlow,
        typeFilterFlow,
        colorFilterFlow,
        bookFilterFlow
    ) { q, t, c, b -> FilterTuple(q, t, c, b) }

    val uiState: StateFlow<NotesUiState> = combine(
        annotationRepo.observeAllAnnotations(),
        annotationRepo.observeTemplates(),
        filtersFlow
    ) { items, templates, f ->
        val q = f.query.trim().lowercase()
        val books = items.mapNotNull { it.book }.distinctBy { it.id }.sortedBy { it.title }
        val filtered = items.filter { (ann, book) ->
            val matchType = f.type == null || AnnotationType.fromRaw(ann.type) == f.type
            val matchColor = f.color == null || ann.colorRole == f.color
            val matchBook = f.bookId == null || ann.bookId == f.bookId
            val matchQuery = q.isEmpty() ||
                ann.quoteExact.lowercase().contains(q) ||
                ann.noteMarkdown.lowercase().contains(q) ||
                ann.tags.lowercase().contains(q) ||
                (book?.title?.lowercase()?.contains(q) == true)
            matchType && matchColor && matchBook && matchQuery
        }
        NotesUiState(
            allItems = items,
            filteredItems = filtered,
            booksWithAnnotations = books,
            templates = templates,
            query = f.query,
            typeFilter = f.type,
            colorRoleFilter = f.color,
            bookIdFilter = f.bookId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotesUiState()
    )

    fun setQuery(q: String) {
        queryFlow.value = q
    }

    fun setTypeFilter(type: AnnotationType?) {
        typeFilterFlow.value = if (typeFilterFlow.value == type) null else type
    }

    fun setColorFilter(role: Int?) {
        colorFilterFlow.value = if (colorFilterFlow.value == role) null else role
    }

    fun setBookFilter(bookId: Long?) {
        bookFilterFlow.value = if (bookFilterFlow.value == bookId) null else bookId
    }

    fun updateAnnotation(
        existing: AnnotationEntity,
        noteMarkdown: String,
        tags: String,
        colorRole: Int,
        style: HighlightStyle,
        sticker: CozySticker
    ) = viewModelScope.launch {
        annotationRepo.upsertAnnotation(
            existing.copy(
                noteMarkdown = noteMarkdown.trim(),
                tags = tags,
                colorRole = colorRole,
                style = style.name,
                sticker = sticker.takeIf { it != CozySticker.NONE }?.name
            )
        )
    }

    fun deleteAnnotation(id: Long) = viewModelScope.launch {
        annotationRepo.deleteAnnotation(id)
    }

    fun saveTemplate(name: String, body: String, existingId: Long) = viewModelScope.launch {
        annotationRepo.saveTemplate(name, body, existingId)
    }

    fun deleteTemplate(id: Long) = viewModelScope.launch {
        annotationRepo.deleteTemplate(id)
    }
}
