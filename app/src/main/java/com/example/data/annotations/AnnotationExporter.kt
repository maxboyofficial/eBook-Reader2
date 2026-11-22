package com.example.data.annotations

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.data.db.AnnotationEntity
import com.example.data.db.BookEntity
import com.example.data.reader.ReaderLocator
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@JsonClass(generateAdapter = true)
data class ExportAnnotationJsonRecord(
    val id: Long,
    val bookTitle: String,
    val bookAuthor: String,
    val type: String,
    val style: String,
    val color: String,
    val chapter: String,
    val progressPercent: Int,
    val quote: String,
    val noteMarkdown: String,
    val tags: List<String>,
    val sticker: String?,
    val createdAtIso: String,
    val updatedAtIso: String
)

object AnnotationExporter {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, ExportAnnotationJsonRecord::class.java)
    private val jsonAdapter = moshi.adapter<List<ExportAnnotationJsonRecord>>(listType).indent("  ")

    private fun formatDate(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return sdf.format(Date(millis))
    }

    fun applyTemplate(
        templateBody: String,
        annotation: AnnotationEntity,
        book: BookEntity?
    ): String {
        val locator = ReaderLocator.fromJson(annotation.locatorJson) ?: ReaderLocator()
        val pct = (locator.progression * 100f).roundToInt().coerceIn(0, 100)
        val pageLabel = "$pct% (ch. ${locator.chapterIndex + 1})"
        val chapterLabel = locator.chapterTitle.ifBlank { book?.title ?: "Section ${locator.chapterIndex + 1}" }
        val colorName = HighlightColorHelper.nameForRole(annotation.colorRole)
        val styleLabel = HighlightStyle.fromRaw(annotation.style).label
        val tagsFormatted = annotation.tags
            .split(',')
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { "#$it" }

        val replacements = mapOf(
            "{{quote}}" to annotation.quoteExact,
            "{{note}}" to annotation.noteMarkdown,
            "{{page}}" to pageLabel,
            "{{chapter}}" to chapterLabel,
            "{{color}}" to colorName,
            "{{tags}}" to tagsFormatted,
            "{{date}}" to formatDate(annotation.createdAt),
            "{{title}}" to (book?.title ?: "Unknown Title"),
            "{{author}}" to (book?.authors ?: "Unknown Author"),
            "{{type}}" to AnnotationType.fromRaw(annotation.type).label,
            "{{style}}" to styleLabel
        )

        var rendered = templateBody
        replacements.forEach { (token, value) ->
            rendered = rendered.replace(token, value)
        }
        // Collapse excessive blank lines left by optional empty placeholders
        return rendered.lines()
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
    }

    fun formatCopyMarkdown(
        annotation: AnnotationEntity,
        book: BookEntity?,
        withCitation: Boolean
    ): String {
        val locator = ReaderLocator.fromJson(annotation.locatorJson) ?: ReaderLocator()
        val sb = StringBuilder()
        if (annotation.quoteExact.isNotBlank()) {
            sb.append("> ").append(annotation.quoteExact.replace("\n", "\n> "))
        }
        if (annotation.noteMarkdown.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(annotation.noteMarkdown)
        }
        if (withCitation && book != null) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            val chapter = locator.chapterTitle.ifBlank { "Chapter ${locator.chapterIndex + 1}" }
            val pct = (locator.progression * 100f).roundToInt().coerceIn(0, 100)
            sb.append("— *${book.title}* (${book.authors}) · $chapter ($pct%)")
        }
        return sb.toString().trim()
    }

    fun formatQuickSelectionCopy(
        quote: String,
        bookTitle: String,
        bookAuthor: String,
        chapterTitle: String,
        progression: Float,
        withCitation: Boolean
    ): String {
        val clean = quote.trim()
        if (!withCitation) return clean
        val pct = (progression * 100f).roundToInt().coerceIn(0, 100)
        val ch = chapterTitle.ifBlank { "Passage" }
        return "> $clean\n\n— *$bookTitle* ($bookAuthor) · $ch ($pct%)"
    }

    fun exportBookMarkdown(
        book: BookEntity,
        annotations: List<AnnotationEntity>,
        templateBody: String
    ): String {
        val header = buildString {
            appendLine("---")
            appendLine("title: \"${book.title.replace("\"", "'")}\"")
            appendLine("author: \"${book.authors.replace("\"", "'")}\"")
            appendLine("format: ${book.format.displayName}")
            appendLine("exported: ${formatDate(System.currentTimeMillis())}")
            appendLine("annotations: ${annotations.size}")
            appendLine("---")
            appendLine()
            appendLine("# ${book.title}")
            appendLine("*by ${book.authors}*")
            appendLine()
        }
        if (annotations.isEmpty()) return header + "_No highlights, notes, or bookmarks recorded yet._\n"
        val body = annotations.joinToString("\n\n---\n\n") { ann ->
            applyTemplate(templateBody, ann, book)
        }
        return header + body + "\n"
    }

    fun exportLibraryMarkdown(
        items: List<AnnotationWithBook>,
        templateBody: String
    ): String {
        if (items.isEmpty()) return "# Mochi Notes Export\n\n_No annotations yet._\n"
        val grouped = items.groupBy { it.book?.id ?: it.annotation.bookId }
        val sb = StringBuilder()
        sb.appendLine("# Mochi Reader — Notes & Highlights")
        sb.appendLine("_Exported ${formatDate(System.currentTimeMillis())} · ${items.size} entries_")
        sb.appendLine()
        grouped.values.forEach { group ->
            val book = group.firstOrNull()?.book
            sb.appendLine("## ${book?.title ?: "Untitled Book"}")
            if (!book?.authors.isNullOrBlank()) {
                sb.appendLine("*by ${book!!.authors}*")
            }
            sb.appendLine()
            group.forEach { item ->
                sb.appendLine(applyTemplate(templateBody, item.annotation, item.book))
                sb.appendLine()
                sb.appendLine("---")
                sb.appendLine()
            }
        }
        return sb.toString()
    }

    fun exportJson(items: List<AnnotationWithBook>): String {
        val records = items.map { (ann, book) ->
            val locator = ReaderLocator.fromJson(ann.locatorJson) ?: ReaderLocator()
            ExportAnnotationJsonRecord(
                id = ann.id,
                bookTitle = book?.title ?: "Unknown",
                bookAuthor = book?.authors ?: "Unknown",
                type = ann.type,
                style = ann.style,
                color = HighlightColorHelper.nameForRole(ann.colorRole),
                chapter = locator.chapterTitle,
                progressPercent = (locator.progression * 100f).roundToInt().coerceIn(0, 100),
                quote = ann.quoteExact,
                noteMarkdown = ann.noteMarkdown,
                tags = ann.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                sticker = ann.sticker,
                createdAtIso = formatDate(ann.createdAt),
                updatedAtIso = formatDate(ann.updatedAt)
            )
        }
        return jsonAdapter.toJson(records)
    }

    fun exportCsv(items: List<AnnotationWithBook>): String {
        val sb = StringBuilder()
        sb.appendLine("id,book_title,book_author,type,style,color,chapter,progress_percent,quote,note_markdown,tags,created_at")
        items.forEach { (ann, book) ->
            val locator = ReaderLocator.fromJson(ann.locatorJson) ?: ReaderLocator()
            val pct = (locator.progression * 100f).roundToInt().coerceIn(0, 100)
            val cols = listOf(
                ann.id.toString(),
                book?.title ?: "Unknown",
                book?.authors ?: "Unknown",
                ann.type,
                ann.style,
                HighlightColorHelper.nameForRole(ann.colorRole),
                locator.chapterTitle,
                pct.toString(),
                ann.quoteExact,
                ann.noteMarkdown,
                ann.tags,
                formatDate(ann.createdAt)
            )
            sb.appendLine(cols.joinToString(",") { escapeCsv(it) })
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    suspend fun writeTextToUri(context: Context, uri: Uri, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot write to destination URI")
            }
        }

    suspend fun writeLibraryToVaultFolder(
        context: Context,
        treeUri: Uri,
        items: List<AnnotationWithBook>,
        templateBody: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("Cannot access selected Vault folder")
            val grouped = items.groupBy { it.book?.id ?: it.annotation.bookId }
            var written = 0
            grouped.values.forEach { group ->
                val book = group.firstOrNull()?.book ?: return@forEach
                val safeTitle = book.title.replace(Regex("[^a-zA-Z0-9._\\- ]"), "").trim().ifBlank { "Book_${book.id}" }
                val fileName = "$safeTitle - Mochi Notes.md"
                val content = exportBookMarkdown(book, group.map { it.annotation }, templateBody)
                val existing = root.findFile(fileName)
                val target = existing ?: root.createFile("text/markdown", fileName)
                    ?: error("Could not create $fileName in Vault folder")
                context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
                written++
            }
            written
        }
    }
}
