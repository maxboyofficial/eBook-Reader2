package com.example.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.annotations.AnnotationType
import com.example.data.annotations.HighlightColorHelper
import com.example.data.annotations.HighlightStyle
import com.example.data.db.AnnotationEntity
import com.example.data.dictionary.DictionaryLookupResult
import com.example.data.reader.ReaderLocator
import com.example.theme.ContrastChecker
import com.example.ui.designsystem.LiquidGlassSurface
import com.example.ui.theme.LocalMochiPalette
import kotlin.math.roundToInt

data class TextSelectionDraft(
    val fullSliceText: String,
    val wordRanges: List<IntRange>,
    val startWordIndex: Int,
    val endWordIndex: Int,
    val chapterIndex: Int,
    val chapterTitle: String,
    val baseCharOffset: Int,
    val progression: Float
) {
    val selectedText: String
        get() {
            if (wordRanges.isEmpty()) return fullSliceText.trim()
            val s = wordRanges[startWordIndex.coerceIn(0, wordRanges.lastIndex)].first
            val e = wordRanges[endWordIndex.coerceIn(0, wordRanges.lastIndex)].last + 1
            return fullSliceText.substring(s.coerceIn(0, fullSliceText.length), e.coerceIn(s, fullSliceText.length)).trim()
        }

    val charOffset: Int
        get() {
            if (wordRanges.isEmpty()) return baseCharOffset
            val s = wordRanges[startWordIndex.coerceIn(0, wordRanges.lastIndex)].first
            return baseCharOffset + s
        }

    val prefixSnippet: String
        get() {
            if (wordRanges.isEmpty()) return ""
            val s = wordRanges[startWordIndex.coerceIn(0, wordRanges.lastIndex)].first
            return fullSliceText.substring((s - 28).coerceAtLeast(0), s).trim()
        }

    val suffixSnippet: String
        get() {
            if (wordRanges.isEmpty()) return ""
            val e = (wordRanges[endWordIndex.coerceIn(0, wordRanges.lastIndex)].last + 1).coerceAtMost(fullSliceText.length)
            return fullSliceText.substring(e, (e + 28).coerceAtMost(fullSliceText.length)).trim()
        }

    fun extractContextSentence(): String {
        if (fullSliceText.isBlank() || wordRanges.isEmpty()) return fullSliceText.trim()
        val sChar = wordRanges[startWordIndex.coerceIn(0, wordRanges.lastIndex)].first
        val eChar = wordRanges[endWordIndex.coerceIn(0, wordRanges.lastIndex)].last + 1
        val sentStart = fullSliceText.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), (sChar - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val sentEnd = fullSliceText.indexOfAny(charArrayOf('.', '!', '?', '\n'), eChar.coerceAtMost(fullSliceText.length))
            .let { if (it < 0) fullSliceText.length else it + 1 }
        return fullSliceText.substring(sentStart.coerceIn(0, fullSliceText.length), sentEnd.coerceIn(0, fullSliceText.length)).trim()
    }

    fun expandToSentence(): TextSelectionDraft {
        if (fullSliceText.isBlank() || wordRanges.isEmpty()) return this
        val sChar = wordRanges[startWordIndex.coerceIn(0, wordRanges.lastIndex)].first
        val eChar = wordRanges[endWordIndex.coerceIn(0, wordRanges.lastIndex)].last + 1
        val sentStart = fullSliceText.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), (sChar - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val sentEnd = fullSliceText.indexOfAny(charArrayOf('.', '!', '?', '\n'), eChar.coerceAtMost(fullSliceText.length))
            .let { if (it < 0) fullSliceText.length else it + 1 }
        val newStartIdx = wordRanges.indexOfFirst { it.last >= sentStart }.coerceAtLeast(0)
        val newEndIdx = wordRanges.indexOfLast { it.first < sentEnd }.coerceAtLeast(newStartIdx)
        return copy(startWordIndex = newStartIdx, endWordIndex = newEndIdx)
    }

    companion object {
        fun fromTapOffset(
            fullText: String,
            tapCharOffset: Int,
            chapterIndex: Int,
            chapterTitle: String,
            baseCharOffset: Int,
            progression: Float
        ): TextSelectionDraft? {
            val words = Regex("\\S+").findAll(fullText).map { it.range }.toList()
            if (words.isEmpty()) return null
            val hitIdx = words.indexOfFirst { tapCharOffset in it.first..(it.last + 1) }
                .takeIf { it >= 0 }
                ?: words.indexOfLast { it.first <= tapCharOffset }.coerceAtLeast(0)
            // Start on the exact tapped word for immediate 1-tap dictionary lookup; easily trimmed via RangeSlider
            return TextSelectionDraft(
                fullSliceText = fullText,
                wordRanges = words,
                startWordIndex = hitIdx,
                endWordIndex = hitIdx,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                baseCharOffset = baseCharOffset,
                progression = progression
            )
        }
    }
}

@Composable
fun TextSelectionGlassToolbar(
    draft: TextSelectionDraft,
    activeColorRole: Int,
    activeStyle: HighlightStyle,
    colorBlindSafe: Boolean,
    quickDefinition: DictionaryLookupResult? = null,
    onDraftChange: (TextSelectionDraft) -> Unit,
    onSelectColorAndStyle: (Int, HighlightStyle) -> Unit,
    onSaveHighlight: (TextSelectionDraft, Int, HighlightStyle) -> Unit,
    onOpenNoteEditor: (TextSelectionDraft, Int, HighlightStyle) -> Unit,
    onDefineSelection: (TextSelectionDraft) -> Unit = {},
    onTranslateSelection: (TextSelectionDraft) -> Unit = {},
    onCopyText: (TextSelectionDraft) -> Unit,
    onCreateQuoteCard: (TextSelectionDraft, Int) -> Unit,
    onSearchSelection: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMochiPalette.current
    val highlightColors = remember(palette, colorBlindSafe) {
        HighlightColorHelper.resolveColors(palette, colorBlindSafe)
    }
    var selectedColor by remember(activeColorRole) { mutableIntStateOf(activeColorRole) }
    var selectedStyle by remember(activeStyle) { mutableStateOf(activeStyle) }
    val quote = draft.selectedText
    val wordCount = draft.endWordIndex - draft.startWordIndex + 1

    LiquidGlassSurface(
        shape = RoundedCornerShape(24.dp),
        elevation = 14.dp,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 620.dp)
            .testTag("text_selection_glass_toolbar")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (wordCount <= 2) "Selected Word" else "Selected Passage",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(palette.accent)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close selection", tint = Color(palette.textSecondary), modifier = Modifier.size(18.dp))
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(highlightColors[selectedColor.coerceIn(0, highlightColors.lastIndex)]).copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "“$quote”",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = Color(palette.text),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            }

            val previewEntry = quickDefinition?.primaryEntry
            if (wordCount <= 3 && previewEntry != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(palette.accentContainer).copy(alpha = 0.52f),
                    onClick = { onDefineSelection(draft) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selection_quick_def_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = Color(palette.accent), modifier = Modifier.size(16.dp))
                        if (previewEntry.pos.isNotBlank()) {
                            Text(
                                text = previewEntry.pos,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(palette.accent)
                            )
                        }
                        Text(
                            text = previewEntry.definition,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(palette.text),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (draft.wordRanges.size > 1) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trim Range ($wordCount ${if (wordCount == 1) "word" else "words"})",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(palette.textSecondary)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Select Sentence",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(palette.link),
                                modifier = Modifier.clickable {
                                    onDraftChange(draft.expandToSentence())
                                }
                            )
                            Text(
                                text = "Select Paragraph",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(palette.link),
                                modifier = Modifier.clickable {
                                    onDraftChange(draft.copy(startWordIndex = 0, endWordIndex = draft.wordRanges.lastIndex))
                                }
                            )
                        }
                    }
                    RangeSlider(
                        value = draft.startWordIndex.toFloat()..draft.endWordIndex.toFloat(),
                        onValueChange = { range ->
                            val s = range.start.roundToInt().coerceIn(0, draft.wordRanges.lastIndex)
                            val e = range.endInclusive.roundToInt().coerceIn(s, draft.wordRanges.lastIndex)
                            onDraftChange(draft.copy(startWordIndex = s, endWordIndex = e))
                        },
                        valueRange = 0f..draft.wordRanges.lastIndex.toFloat()
                    )
                }
            }

            // 8 color circles
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                highlightColors.forEachIndexed { idx, argb ->
                    val picked = idx == selectedColor
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (picked) 2.5.dp else 1.dp,
                                color = if (picked) Color(palette.accent) else Color(palette.textSecondary).copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                            .clickable {
                                selectedColor = idx
                                onSelectColorAndStyle(idx, selectedStyle)
                            }
                            .testTag("selection_color_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (picked) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = HighlightColorHelper.nameForRole(idx),
                                tint = ContrastChecker.bestOnColor(Color(argb)),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // 5 highlight styles
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HighlightStyle.entries.forEach { st ->
                    FilterChip(
                        selected = selectedStyle == st,
                        onClick = {
                            selectedStyle = st
                            onSelectColorAndStyle(selectedColor, st)
                        },
                        label = { Text(st.label) }
                    )
                }
            }

            // Actions row
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onSaveHighlight(draft, selectedColor, selectedStyle) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(palette.accent),
                        contentColor = ContrastChecker.bestOnColor(Color(palette.accent))
                    ),
                    modifier = Modifier.testTag("selection_highlight_button")
                ) {
                    Icon(Icons.Rounded.Highlight, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Highlight")
                }
                OutlinedButton(
                    onClick = { onDefineSelection(draft) },
                    modifier = Modifier.testTag("selection_define_button")
                ) {
                    Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Define")
                }
                OutlinedButton(
                    onClick = { onOpenNoteEditor(draft, selectedColor, selectedStyle) },
                    modifier = Modifier.testTag("selection_note_button")
                ) {
                    Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Note")
                }
                OutlinedButton(
                    onClick = { onCopyText(draft) },
                    modifier = Modifier.testTag("selection_copy_button")
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy")
                }
                OutlinedButton(
                    onClick = { onCreateQuoteCard(draft, selectedColor) },
                    modifier = Modifier.testTag("selection_quote_card_button")
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Card")
                }
                OutlinedButton(
                    onClick = { onTranslateSelection(draft) },
                    modifier = Modifier.testTag("selection_translate_button")
                ) {
                    Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Translate")
                }
                OutlinedButton(onClick = { onSearchSelection(quote) }) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Search")
                }
            }
        }
    }
}

@Composable
fun HighlightPopoverDialog(
    annotation: AnnotationEntity,
    colorBlindSafe: Boolean,
    onChangeColorStyle: (Int, HighlightStyle) -> Unit,
    onEditNote: () -> Unit,
    onCreateQuoteCard: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current
    val highlightColors = remember(palette, colorBlindSafe) {
        HighlightColorHelper.resolveColors(palette, colorBlindSafe)
    }
    var currentStyle by remember(annotation.id, annotation.style) {
        mutableStateOf(HighlightStyle.fromRaw(annotation.style))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface),
        title = { Text("Highlight", color = Color(palette.text)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(highlightColors[annotation.colorRole.coerceIn(0, highlightColors.lastIndex)]).copy(alpha = 0.45f)
                ) {
                    Text(
                        text = "“${annotation.quoteExact}”",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = Color(palette.text),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (annotation.noteMarkdown.isNotBlank()) {
                    Text(
                        text = annotation.noteMarkdown,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(palette.text),
                        maxLines = 3
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    highlightColors.forEachIndexed { idx, argb ->
                        val picked = idx == annotation.colorRole
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (picked) 2.5.dp else 1.dp,
                                    color = if (picked) Color(palette.accent) else Color(palette.textSecondary).copy(alpha = 0.25f),
                                    shape = CircleShape
                                )
                                .clickable { onChangeColorStyle(idx, currentStyle) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HighlightStyle.entries.forEach { st ->
                        FilterChip(
                            selected = currentStyle == st,
                            onClick = {
                                currentStyle = st
                                onChangeColorStyle(annotation.colorRole, st)
                            },
                            label = { Text(st.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onEditNote) { Text(if (annotation.noteMarkdown.isBlank()) "Add Note" else "Edit Note") }
                    OutlinedButton(onClick = onCreateQuoteCard) { Text("Quote Card") }
                    OutlinedButton(onClick = onCopy) { Text("Copy") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color(palette.link))
            }
        }
    )
}

@Composable
fun BookmarkDialog(
    chapterTitle: String,
    pageNumber: Int,
    existingBookmark: AnnotationEntity?,
    onSave: (label: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current
    var label by remember(existingBookmark?.id) {
        mutableStateOf(existingBookmark?.noteMarkdown ?: "$chapterTitle · p. $pageNumber")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = Color(palette.accent))
                Text(if (existingBookmark != null) "Edit Bookmark" else "Bookmark Page", color = Color(palette.text))
            }
        },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Bookmark Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bookmark_label_input")
            )
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(palette.textSecondary))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(label) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(palette.accent),
                    contentColor = ContrastChecker.bestOnColor(Color(palette.accent))
                ),
                modifier = Modifier.testTag("save_bookmark_button")
            ) {
                Text("Save")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookAnnotationsSheet(
    annotations: List<AnnotationEntity>,
    colorBlindSafe: Boolean,
    onJumpToAnnotation: (AnnotationEntity) -> Unit,
    onEditAnnotation: (AnnotationEntity) -> Unit,
    onQuoteCard: (AnnotationEntity) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onOpenExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface),
        modifier = Modifier.testTag("book_annotations_sheet")
    ) {
        BookAnnotationsPanelContent(
            annotations = annotations,
            colorBlindSafe = colorBlindSafe,
            onJumpToAnnotation = {
                onJumpToAnnotation(it)
                onDismiss()
            },
            onEditAnnotation = onEditAnnotation,
            onQuoteCard = onQuoteCard,
            onDeleteAnnotation = onDeleteAnnotation,
            onOpenExport = onOpenExport,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .align(Alignment.CenterHorizontally)
                .height(480.dp)
        )
    }
}

@Composable
fun BookAnnotationsPanelContent(
    annotations: List<AnnotationEntity>,
    colorBlindSafe: Boolean,
    onJumpToAnnotation: (AnnotationEntity) -> Unit,
    onEditAnnotation: (AnnotationEntity) -> Unit,
    onQuoteCard: (AnnotationEntity) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onOpenExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMochiPalette.current
    val highlightColors = remember(palette, colorBlindSafe) {
        HighlightColorHelper.resolveColors(palette, colorBlindSafe)
    }
    var typeFilter by remember { mutableStateOf<AnnotationType?>(null) }
    var colorFilter by remember { mutableStateOf<Int?>(null) }

    val filtered = remember(annotations, typeFilter, colorFilter) {
        annotations.filter { ann ->
            (typeFilter == null || AnnotationType.fromRaw(ann.type) == typeFilter) &&
                (colorFilter == null || ann.colorRole == colorFilter)
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Annotations (${annotations.size})",
                style = MaterialTheme.typography.titleLarge,
                color = Color(palette.text)
            )
            OutlinedButton(onClick = onOpenExport) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export")
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = typeFilter == null,
                onClick = { typeFilter = null },
                label = { Text("All") }
            )
            AnnotationType.entries.forEach { t ->
                FilterChip(
                    selected = typeFilter == t,
                    onClick = { typeFilter = if (typeFilter == t) null else t },
                    label = { Text(t.label) }
                )
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            highlightColors.forEachIndexed { idx, argb ->
                val picked = colorFilter == idx
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (picked) 2.5.dp else 1.dp,
                            color = if (picked) Color(palette.accent) else Color(palette.textSecondary).copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                        .clickable { colorFilter = if (colorFilter == idx) null else idx }
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Long-press any passage to highlight or tap Bookmark to save a spot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(palette.textSecondary)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { ann ->
                    val locator = ReaderLocator.fromJson(ann.locatorJson)
                    val color = Color(highlightColors[ann.colorRole.coerceIn(0, highlightColors.lastIndex)])
                    val style = HighlightStyle.fromRaw(ann.style)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(palette.surfaceVariant).copy(alpha = 0.48f),
                        onClick = { onJumpToAnnotation(ann) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                                    Text(
                                        text = locator?.chapterTitle?.take(22) ?: AnnotationType.fromRaw(ann.type).label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(palette.link)
                                    )
                                }
                                Text(
                                    text = "${((locator?.progression ?: 0f) * 100f).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(palette.textSecondary)
                                )
                            }
                            if (ann.quoteExact.isNotBlank()) {
                                val deco = when (style) {
                                    HighlightStyle.STRIKETHROUGH -> TextDecoration.LineThrough
                                    HighlightStyle.UNDERLINE, HighlightStyle.SQUIGGLY -> TextDecoration.Underline
                                    else -> TextDecoration.None
                                }
                                Text(
                                    text = "“${ann.quoteExact}”",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontStyle = FontStyle.Italic,
                                        textDecoration = deco
                                    ),
                                    color = Color(palette.text),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (ann.noteMarkdown.isNotBlank()) {
                                Text(
                                    text = ann.noteMarkdown,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(palette.text),
                                    maxLines = 3
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = { onEditAnnotation(ann) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.EditNote, contentDescription = "Edit note", tint = Color(palette.textSecondary), modifier = Modifier.size(18.dp))
                                }
                                if (ann.quoteExact.isNotBlank()) {
                                    IconButton(onClick = { onQuoteCard(ann) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Rounded.Image, contentDescription = "Quote card", tint = Color(palette.textSecondary), modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(onClick = { onDeleteAnnotation(ann.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color(palette.textSecondary), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
