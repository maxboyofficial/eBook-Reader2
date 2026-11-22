package com.example.ui.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.db.VocabularyWordEntity
import com.example.data.dictionary.MochiWordSpeaker
import com.example.data.dictionary.SrsGrade
import com.example.data.dictionary.SrsSm2Calculator
import com.example.data.dictionary.VocabularyExporter
import com.example.theme.ContrastChecker
import com.example.ui.designsystem.LiquidGlassSurface
import com.example.ui.designsystem.LocalMochiWidthClass
import com.example.ui.designsystem.MochiBlobMascot
import com.example.ui.designsystem.MochiWidthClass
import com.example.ui.theme.LocalMochiPalette
import kotlinx.coroutines.launch

private enum class VocabFilter(val label: String) {
    ALL("All"),
    DUE("Due"),
    LEARNING("Learning"),
    MASTERED("Mastered")
}

private enum class VocabExportKind(val ext: String, val mime: String, val label: String) {
    ANKI_TSV("tsv", "text/tab-separated-values", "Anki Deck (.tsv)"),
    ANKI_CSV("csv", "text/csv", "Anki / Spreadsheet (.csv)"),
    MARKDOWN("md", "text/markdown", "Markdown Notes (.md)")
}

@Composable
fun VocabularyPanelContent(
    vocabulary: List<VocabularyWordEntity>,
    dueWords: List<VocabularyWordEntity>,
    mascotEnabled: Boolean,
    onJumpToBook: (Long) -> Unit,
    onRecordReview: (VocabularyWordEntity, SrsGrade) -> Unit,
    onDeleteWord: (Long) -> Unit,
    onOpenDictionarySearch: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMochiPalette.current
    val widthClass = LocalMochiWidthClass.current
    val useTwoColumns = widthClass != MochiWidthClass.COMPACT
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val speaker = remember(context) { MochiWordSpeaker(context) }
    DisposableEffect(speaker) {
        onDispose { speaker.shutdown() }
    }

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(VocabFilter.ALL) }
    var showReviewSession by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var pendingExportKind by remember { mutableStateOf(VocabExportKind.ANKI_TSV) }

    val filtered = remember(vocabulary, dueWords, query, filter) {
        val q = query.trim().lowercase()
        val dueIds = dueWords.map { it.id }.toHashSet()
        vocabulary.filter { w ->
            val matchFilter = when (filter) {
                VocabFilter.ALL -> true
                VocabFilter.DUE -> w.id in dueIds
                VocabFilter.LEARNING -> w.masteryLevel != "MASTERED"
                VocabFilter.MASTERED -> w.masteryLevel == "MASTERED"
            }
            val matchQuery = q.isEmpty() ||
                w.word.lowercase().contains(q) ||
                w.lemma.lowercase().contains(q) ||
                w.definition.lowercase().contains(q) ||
                w.bookTitle.lowercase().contains(q)
            matchFilter && matchQuery
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val list = filtered.ifEmpty { vocabulary }
        val payload = when (pendingExportKind) {
            VocabExportKind.ANKI_TSV -> VocabularyExporter.exportAnkiTsv(list)
            VocabExportKind.ANKI_CSV -> VocabularyExporter.exportAnkiCsv(list)
            VocabExportKind.MARKDOWN -> VocabularyExporter.exportMarkdown(list)
        }
        scope.launch {
            val res = VocabularyExporter.writeToSafUri(context, uri, payload)
            onShowMessage(
                res.fold(
                    onSuccess = { "Exported ${list.size} vocabulary cards (${pendingExportKind.ext.uppercase()})" },
                    onFailure = { "Export failed: ${it.localizedMessage}" }
                )
            )
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item(key = "vocab_summary") {
            LiquidGlassSurface(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.School, contentDescription = null, tint = Color(palette.accent), modifier = Modifier.size(18.dp))
                            Text(
                                text = "${dueWords.size} due for SM-2 review",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(palette.text)
                            )
                        }
                        val masteredCount = vocabulary.count { it.masteryLevel == "MASTERED" }
                        Text(
                            text = "${vocabulary.size} total words · $masteredCount mastered",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(palette.textSecondary)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            OutlinedButton(
                                onClick = { showExportMenu = true },
                                enabled = vocabulary.isNotEmpty(),
                                modifier = Modifier.testTag("vocab_export_button")
                            ) {
                                Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Anki")
                            }
                            DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                                VocabExportKind.entries.forEach { kind ->
                                    DropdownMenuItem(
                                        text = { Text(kind.label) },
                                        onClick = {
                                            pendingExportKind = kind
                                            showExportMenu = false
                                            exportLauncher.launch("mochi_vocabulary.${kind.ext}")
                                        }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { showReviewSession = true },
                            enabled = dueWords.isNotEmpty() || vocabulary.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(palette.accent),
                                contentColor = ContrastChecker.bestOnColor(Color(palette.accent))
                            ),
                            modifier = Modifier.testTag("vocab_start_review_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (dueWords.isNotEmpty()) "Review" else "Practice")
                        }
                    }
                }
            }
        }

        item(key = "vocab_search_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        placeholder = { Text("Search vocabulary or book…") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onOpenDictionarySearch) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lookup")
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VocabFilter.entries.forEach { f ->
                        val count = when (f) {
                            VocabFilter.ALL -> vocabulary.size
                            VocabFilter.DUE -> dueWords.size
                            VocabFilter.LEARNING -> vocabulary.count { it.masteryLevel != "MASTERED" }
                            VocabFilter.MASTERED -> vocabulary.count { it.masteryLevel == "MASTERED" }
                        }
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text("${f.label} ($count)") }
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item(key = "vocab_empty") {
                LiquidGlassSurface(
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (mascotEnabled) MochiBlobMascot(size = 96.dp)
                        Text(
                            text = if (vocabulary.isEmpty()) "Your Vocabulary Deck is Empty" else "No Matching Words",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(palette.text),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Tap Define on any word in the Reader (or tap Lookup above) to explore offline definitions and save flashcards with context sentences.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(palette.textSecondary),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else if (useTwoColumns) {
            val rows = filtered.chunked(2)
            items(rows, key = { pair -> pair.joinToString("_") { it.id.toString() } }) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { word ->
                        Box(modifier = Modifier.weight(1f)) {
                            VocabularyWordCard(
                                word = word,
                                onPronounce = { speaker.speak(word.word) { onShowMessage(it) } },
                                onJumpToBook = { word.bookId?.let(onJumpToBook) },
                                onDelete = { onDeleteWord(word.id) }
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        } else {
            items(filtered, key = { it.id }) { word ->
                VocabularyWordCard(
                    word = word,
                    onPronounce = { speaker.speak(word.word) { onShowMessage(it) } },
                    onJumpToBook = { word.bookId?.let(onJumpToBook) },
                    onDelete = { onDeleteWord(word.id) }
                )
            }
        }
    }

    if (showReviewSession) {
        val deck = dueWords.ifEmpty { vocabulary }
        SrsReviewDialog(
            deck = deck,
            onPronounce = { w -> speaker.speak(w) { onShowMessage(it) } },
            onRate = { w, grade -> onRecordReview(w, grade) },
            onDismiss = { showReviewSession = false }
        )
    }
}

@Composable
private fun VocabularyWordCard(
    word: VocabularyWordEntity,
    onPronounce: () -> Unit,
    onJumpToBook: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalMochiPalette.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(palette.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.14f), RoundedCornerShape(20.dp))
            .testTag("vocab_card_${word.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(palette.text)
                    )
                    if (word.partOfSpeech.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(palette.accent).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = word.partOfSpeech,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(palette.accent),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color(palette.accentContainer).copy(alpha = 0.7f)
                ) {
                    Text(
                        text = word.masteryLevel,
                        style = MaterialTheme.typography.labelSmall,
                        color = ContrastChecker.bestOnColor(Color(palette.accentContainer)),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = word.definition,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(palette.text),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (word.contextSentence.isNotBlank()) {
                Text(
                    text = "“${word.contextSentence}”",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = Color(palette.textSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = word.bookTitle.ifBlank { "Dictionary Lookup" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(palette.link),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    IconButton(onClick = onPronounce, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Pronounce", tint = Color(palette.accent), modifier = Modifier.size(18.dp))
                    }
                    if (word.bookId != null) {
                        IconButton(onClick = onJumpToBook, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open source book", tint = Color(palette.link), modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete word", tint = Color(palette.textSecondary), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SrsReviewDialog(
    deck: List<VocabularyWordEntity>,
    onPronounce: (String) -> Unit,
    onRate: (VocabularyWordEntity, SrsGrade) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current
    val sessionCards = remember { deck.toList() }
    var cardIndex by remember { mutableIntStateOf(0) }
    var answerRevealed by remember { mutableStateOf(false) }

    val currentCard = sessionCards.getOrNull(cardIndex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlassSurface(
                shape = RoundedCornerShape(28.dp),
                elevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .testTag("srs_review_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentCard != null) "Review ${cardIndex + 1} of ${sessionCards.size}" else "Session Complete",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(palette.accent)
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close review", tint = Color(palette.textSecondary))
                        }
                    }

                    if (currentCard == null) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(palette.accent), modifier = Modifier.size(44.dp))
                            Text("All cards reviewed!", style = MaterialTheme.typography.headlineSmall, color = Color(palette.text))
                            Text(
                                text = "SM-2 intervals have been updated offline.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(palette.textSecondary)
                            )
                            Button(onClick = onDismiss) { Text("Done") }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(palette.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.16f), RoundedCornerShape(22.dp))
                                .clip(RoundedCornerShape(22.dp))
                                .clickable { answerRevealed = true }
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = currentCard.word,
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(palette.text)
                                    )
                                    IconButton(onClick = { onPronounce(currentCard.word) }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Pronounce", tint = Color(palette.accent))
                                    }
                                }
                                if (currentCard.partOfSpeech.isNotBlank()) {
                                    Text(currentCard.partOfSpeech, style = MaterialTheme.typography.labelLarge, color = Color(palette.link))
                                }
                                if (currentCard.contextSentence.isNotBlank()) {
                                    Text(
                                        text = "“${currentCard.contextSentence}”",
                                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                        color = Color(palette.textSecondary),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                if (answerRevealed) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = currentCard.definition,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color(palette.text),
                                        textAlign = TextAlign.Center
                                    )
                                    if (currentCard.exampleSentence.isNotBlank()) {
                                        Text(
                                            text = "e.g., ${currentCard.exampleSentence}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(palette.textSecondary),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tap card or button below to reveal meaning",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(palette.textSecondary)
                                    )
                                }
                            }
                        }

                        if (!answerRevealed) {
                            Button(
                                onClick = { answerRevealed = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(palette.accent),
                                    contentColor = ContrastChecker.bestOnColor(Color(palette.accent))
                                ),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Show Answer")
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SrsGrade.entries.forEach { grade ->
                                    val intervalText = SrsSm2Calculator.previewLabel(currentCard, grade)
                                    OutlinedButton(
                                        onClick = {
                                            onRate(currentCard, grade)
                                            answerRevealed = false
                                            cardIndex++
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(grade.label, style = MaterialTheme.typography.labelMedium, color = Color(palette.text))
                                            Text(intervalText, style = MaterialTheme.typography.labelSmall, color = Color(palette.link))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
