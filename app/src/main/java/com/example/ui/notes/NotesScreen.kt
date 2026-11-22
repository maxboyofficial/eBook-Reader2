package com.example.ui.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.example.data.dictionary.DictionaryLookupResult
import com.example.ui.designsystem.MochiConnectedSegments
import com.example.ui.dictionary.DictionaryGlassPopup
import com.example.ui.dictionary.DictionaryManagerSheet
import com.example.ui.dictionary.DictionaryViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.annotations.AnnotationExporter
import com.example.data.annotations.AnnotationType
import com.example.data.annotations.AnnotationWithBook
import com.example.data.annotations.CozySticker
import com.example.data.annotations.HighlightColorHelper
import com.example.data.annotations.HighlightStyle
import com.example.data.db.AnnotationEntity
import com.example.data.preferences.UserPreferencesState
import com.example.data.reader.DocumentParsers
import com.example.data.reader.ReaderLocator
import com.example.data.reader.ReaderPaginator
import com.example.theme.MochiThemePalette
import com.example.ui.designsystem.LiquidGlassSurface
import com.example.ui.designsystem.LocalMochiWidthClass
import com.example.ui.designsystem.MochiBlobMascot
import com.example.ui.designsystem.MochiWidthClass
import com.example.ui.theme.LocalMochiPalette
import kotlin.math.roundToInt

private enum class NotesHubTab(val label: String) {
    ANNOTATIONS("Annotations"),
    VOCABULARY("Vocabulary")
}

@Composable
fun NotesScreen(
    prefs: UserPreferencesState,
    themes: List<MochiThemePalette>,
    activeTheme: MochiThemePalette,
    onJumpToBookLocator: (bookId: Long, locator: ReaderLocator?) -> Unit,
    onSaveVaultFolderUri: (String?) -> Unit,
    onSetPerLanguageDefaultDict: (String, Long?) -> Unit = { _, _ -> },
    onShowMessage: (String) -> Unit,
    notesViewModel: NotesViewModel = viewModel(),
    dictionaryViewModel: DictionaryViewModel = viewModel()
) {
    val state by notesViewModel.uiState.collectAsStateWithLifecycle()
    val dictState by dictionaryViewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalMochiPalette.current
    val widthClass = LocalMochiWidthClass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val useTwoColumns = widthClass != MochiWidthClass.COMPACT
    val colorBlindSafe = prefs.readerSettings.colorBlindPaletteEnabled
    val highlightColors = remember(palette, colorBlindSafe) {
        HighlightColorHelper.resolveColors(palette, colorBlindSafe)
    }

    var activeHubTab by remember { mutableStateOf(NotesHubTab.ANNOTATIONS) }
    var editingItem by remember { mutableStateOf<AnnotationWithBook?>(null) }
    var quoteCardItem by remember { mutableStateOf<AnnotationWithBook?>(null) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showDictLookupPopup by remember { mutableStateOf(false) }
    var dictPopupQuery by remember { mutableStateOf("parsimony") }
    var dictLookupResult by remember { mutableStateOf<DictionaryLookupResult?>(null) }
    var showDictManagerSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(palette.background))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1120.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (activeHubTab == NotesHubTab.ANNOTATIONS) "Notes" else "Vocabulary",
                        style = MaterialTheme.typography.displayLarge,
                        color = Color(palette.text)
                    )
                    Text(
                        text = if (activeHubTab == NotesHubTab.ANNOTATIONS) {
                            "${state.allItems.size} saved annotation(s)"
                        } else {
                            "${dictState.vocabulary.size} saved · ${dictState.dueVocabulary.size} due"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(palette.textSecondary)
                    )
                }
                if (activeHubTab == NotesHubTab.ANNOTATIONS) {
                    OutlinedButton(
                        onClick = { showExportSheet = true },
                        modifier = Modifier.testTag("notes_open_export_button")
                    ) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export")
                    }
                } else {
                    OutlinedButton(
                        onClick = { showDictManagerSheet = true },
                        modifier = Modifier.testTag("vocab_open_dicts_button")
                    ) {
                        Text("Dictionaries (${dictState.dictionaries.count { it.enabled }})")
                    }
                }
            }

            MochiConnectedSegments(
                items = NotesHubTab.entries,
                selected = activeHubTab,
                labelProvider = { tab ->
                    if (tab == NotesHubTab.VOCABULARY && dictState.dueVocabulary.isNotEmpty()) {
                        "Vocabulary (${dictState.dueVocabulary.size} due)"
                    } else tab.label
                },
                testTagProvider = { "notes_tab_${it.name.lowercase()}" },
                onSelect = { activeHubTab = it }
            )

            if (activeHubTab == NotesHubTab.VOCABULARY) {
                VocabularyPanelContent(
                    vocabulary = dictState.vocabulary,
                    dueWords = dictState.dueVocabulary,
                    mascotEnabled = prefs.mascotEnabled,
                    onJumpToBook = { bookId -> onJumpToBookLocator(bookId, null) },
                    onRecordReview = dictionaryViewModel::recordSrsReview,
                    onDeleteWord = dictionaryViewModel::deleteVocabularyWord,
                    onOpenDictionarySearch = {
                        dictPopupQuery = ""
                        dictLookupResult = null
                        showDictLookupPopup = true
                    },
                    onShowMessage = onShowMessage,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item(key = "notes_search") {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = notesViewModel::setQuery,
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            placeholder = { Text("Search quotes, notes, #tags, or books…") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notes_search_input")
                        )
                    }

            item(key = "notes_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.typeFilter == null,
                            onClick = { notesViewModel.setTypeFilter(null) },
                            label = { Text("All (${state.allItems.size})") }
                        )
                        AnnotationType.entries.forEach { type ->
                            val count = state.allItems.count { AnnotationType.fromRaw(it.annotation.type) == type }
                            FilterChip(
                                selected = state.typeFilter == type,
                                onClick = { notesViewModel.setTypeFilter(type) },
                                label = { Text("${type.label} ($count)") }
                            )
                        }
                        state.booksWithAnnotations.forEach { b ->
                            FilterChip(
                                selected = state.bookIdFilter == b.id,
                                onClick = { notesViewModel.setBookFilter(b.id) },
                                label = { Text(b.title.take(18)) }
                            )
                        }
                    }

                    // Color role filter dots
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Color:", style = MaterialTheme.typography.labelMedium, color = Color(palette.textSecondary))
                        highlightColors.forEachIndexed { idx, argb ->
                            val selected = state.colorRoleFilter == idx
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) Color(palette.accent) else Color(palette.textSecondary).copy(alpha = 0.25f),
                                        shape = CircleShape
                                    )
                                    .clickable { notesViewModel.setColorFilter(idx) }
                            )
                        }
                    }
                }
            }

            if (state.filteredItems.isEmpty()) {
                item(key = "notes_empty") {
                    LiquidGlassSurface(
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (prefs.mascotEnabled) {
                                MochiBlobMascot(size = 104.dp)
                            }
                            Text(
                                text = if (state.allItems.isEmpty()) "No highlights or notes yet" else "No matching annotations",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(palette.text),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Long-press any passage inside the Reader to highlight, attach a Markdown note, bookmark, or craft a Quote Card. Your original files stay 100% untouched.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(palette.textSecondary),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (useTwoColumns) {
                val rows = state.filteredItems.chunked(2)
                items(rows, key = { pair -> pair.joinToString("_") { it.annotation.id.toString() } }) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { item ->
                            Box(modifier = Modifier.weight(1f)) {
                                GlobalAnnotationCard(
                                    item = item,
                                    highlightColor = Color(highlightColors[item.annotation.colorRole.coerceIn(0, highlightColors.lastIndex)]),
                                    onJump = {
                                        onJumpToBookLocator(item.annotation.bookId, ReaderLocator.fromJson(item.annotation.locatorJson))
                                    },
                                    onEdit = { editingItem = item },
                                    onQuoteCard = { quoteCardItem = item },
                                    onCopyMarkdown = {
                                        val md = AnnotationExporter.formatCopyMarkdown(
                                            item.annotation,
                                            item.book,
                                            prefs.readerSettings.copyWithCitationEnabled
                                        )
                                        copyToClipboard(context, md)
                                        onShowMessage("Copied highlight with citation")
                                    },
                                    onDelete = { notesViewModel.deleteAnnotation(item.annotation.id) }
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            } else {
                items(state.filteredItems, key = { it.annotation.id }) { item ->
                    GlobalAnnotationCard(
                        item = item,
                        highlightColor = Color(highlightColors[item.annotation.colorRole.coerceIn(0, highlightColors.lastIndex)]),
                        onJump = {
                            onJumpToBookLocator(item.annotation.bookId, ReaderLocator.fromJson(item.annotation.locatorJson))
                        },
                        onEdit = { editingItem = item },
                        onQuoteCard = { quoteCardItem = item },
                        onCopyMarkdown = {
                            val md = AnnotationExporter.formatCopyMarkdown(
                                item.annotation,
                                item.book,
                                prefs.readerSettings.copyWithCitationEnabled
                            )
                            copyToClipboard(context, md)
                            onShowMessage("Copied highlight with citation")
                        },
                        onDelete = { notesViewModel.deleteAnnotation(item.annotation.id) }
                    )
                }
            }
        }
    }
        }
    }

    val editTarget = editingItem
    if (editTarget != null) {
        MarkdownNoteEditorSheet(
            title = "Edit Note & Highlight",
            quoteText = editTarget.annotation.quoteExact,
            initialNote = editTarget.annotation.noteMarkdown,
            initialTags = editTarget.annotation.tags,
            initialColorRole = editTarget.annotation.colorRole,
            initialStyle = HighlightStyle.fromRaw(editTarget.annotation.style),
            initialSticker = CozySticker.fromRaw(editTarget.annotation.sticker),
            colorBlindSafe = colorBlindSafe,
            showStyleAndColor = AnnotationType.fromRaw(editTarget.annotation.type) != AnnotationType.BOOKMARK,
            onDismiss = { editingItem = null },
            onSave = { note, tags, role, style, sticker ->
                notesViewModel.updateAnnotation(editTarget.annotation, note, tags, role, style, sticker)
                editingItem = null
                onShowMessage("Note saved")
            }
        )
    }

    val cardTarget = quoteCardItem
    if (cardTarget != null) {
        QuoteCardStudioSheet(
            annotation = cardTarget.annotation,
            book = cardTarget.book,
            themes = themes,
            initialTheme = activeTheme,
            initialFontId = prefs.readerSettings.fontId,
            importedFonts = prefs.readerSettings.importedFonts,
            colorBlindSafe = colorBlindSafe,
            onDismiss = { quoteCardItem = null },
            onShowMessage = onShowMessage
        )
    }

    if (showExportSheet) {
        ExportTemplatesSheet(
            items = state.filteredItems.ifEmpty { state.allItems },
            templates = state.templates,
            vaultFolderUri = prefs.readerSettings.vaultExportFolderUri,
            onSaveTemplate = notesViewModel::saveTemplate,
            onDeleteTemplate = notesViewModel::deleteTemplate,
            onSaveVaultFolderUri = onSaveVaultFolderUri,
            onDismiss = { showExportSheet = false },
            onShowMessage = onShowMessage
        )
    }

    if (showDictLookupPopup) {
        DictionaryGlassPopup(
            initialQuery = dictPopupQuery,
            result = dictLookupResult,
            contextSentence = "",
            bookTitle = "Vocabulary Hub",
            savedVocabulary = dictState.vocabulary,
            onSearchQuery = { q ->
                dictPopupQuery = q
                scope.launch {
                    dictLookupResult = dictionaryViewModel.lookupWord(
                        query = q,
                        perLanguageDefaults = prefs.readerSettings.perLanguageDefaultDict
                    )
                }
            },
            onSaveToVocabulary = { word, lemma, pos, def, example ->
                dictionaryViewModel.saveVocabularyWord(
                    word = word,
                    lemma = lemma,
                    partOfSpeech = pos,
                    definition = def,
                    exampleSentence = example,
                    contextSentence = "",
                    bookId = null,
                    bookTitle = "Dictionary Lookup"
                )
            },
            onOpenDictionaryManager = {
                showDictLookupPopup = false
                showDictManagerSheet = true
            },
            onShowMessage = onShowMessage,
            onDismiss = { showDictLookupPopup = false }
        )
    }

    if (showDictManagerSheet) {
        DictionaryManagerSheet(
            dictionaries = dictState.dictionaries,
            perLanguageDefaults = prefs.readerSettings.perLanguageDefaultDict,
            isImporting = dictState.isImporting,
            onToggleEnabled = dictionaryViewModel::toggleDictionaryEnabled,
            onMoveOrder = dictionaryViewModel::moveDictionaryOrder,
            onUpdateLanguage = dictionaryViewModel::updateDictionaryLanguage,
            onDeleteCustom = dictionaryViewModel::deleteCustomDictionary,
            onSetPerLanguageDefault = onSetPerLanguageDefaultDict,
            onImportSafTree = { uri ->
                dictionaryViewModel.importFromSafTree(uri, onShowMessage)
            },
            onImportSafFile = { uri ->
                dictionaryViewModel.importFromSafFile(uri, onShowMessage)
            },
            onDismiss = { showDictManagerSheet = false }
        )
    }
}

@Composable
private fun GlobalAnnotationCard(
    item: AnnotationWithBook,
    highlightColor: Color,
    onJump: () -> Unit,
    onEdit: () -> Unit,
    onQuoteCard: () -> Unit,
    onCopyMarkdown: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalMochiPalette.current
    val ann: AnnotationEntity = item.annotation
    val locator = remember(ann.locatorJson) { ReaderLocator.fromJson(ann.locatorJson) }
    val type = AnnotationType.fromRaw(ann.type)
    val style = HighlightStyle.fromRaw(ann.style)
    val sticker = CozySticker.fromRaw(ann.sticker)
    val pct = ((locator?.progression ?: 0f) * 100f).roundToInt().coerceIn(0, 100)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(palette.surface),
        onClick = onJump,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.14f), RoundedCornerShape(22.dp))
            .testTag("global_annotation_card_${ann.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (type == AnnotationType.BOOKMARK) {
                        Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = Color(palette.accent), modifier = Modifier.size(16.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(highlightColor)
                        )
                    }
                    Text(
                        text = item.book?.title ?: "Unknown Book",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(palette.text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (sticker != CozySticker.NONE) {
                        Text(sticker.badgeText, color = Color(palette.accent), style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        text = "${locator?.chapterTitle?.take(18).orEmpty()} · $pct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(palette.link)
                    )
                }
            }

            if (ann.quoteExact.isNotBlank()) {
                val deco = when (style) {
                    HighlightStyle.STRIKETHROUGH -> TextDecoration.LineThrough
                    HighlightStyle.UNDERLINE, HighlightStyle.SQUIGGLY -> TextDecoration.Underline
                    else -> TextDecoration.None
                }
                val bg = when (style) {
                    HighlightStyle.HIGHLIGHT, HighlightStyle.WASHI_TAPE -> highlightColor.copy(alpha = 0.45f)
                    else -> Color(palette.surfaceVariant).copy(alpha = 0.35f)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = bg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "“${ann.quoteExact}”",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDecoration = deco,
                            fontStyle = FontStyle.Italic
                        ),
                        color = Color(palette.text),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (ann.noteMarkdown.isNotBlank()) {
                val renderedNote = remember(ann.noteMarkdown, palette.id) {
                    val inline = DocumentParsers.parseMarkdownInline(ann.noteMarkdown)
                    ReaderPaginator.toAnnotatedString(inline, palette, false)
                }
                Text(
                    text = renderedNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(palette.text)
                )
            }

            if (ann.tags.isNotBlank()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ann.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
                        Surface(
                            shape = CircleShape,
                            color = Color(palette.accentContainer).copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(palette.text),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onJump, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open in book", tint = Color(palette.link), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit note", tint = Color(palette.textSecondary), modifier = Modifier.size(18.dp))
                }
                if (ann.quoteExact.isNotBlank()) {
                    IconButton(onClick = onQuoteCard, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Image, contentDescription = "Quote card", tint = Color(palette.textSecondary), modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onCopyMarkdown, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Markdown", tint = Color(palette.textSecondary), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete annotation", tint = Color(palette.textSecondary), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Mochi Highlight", text))
}
