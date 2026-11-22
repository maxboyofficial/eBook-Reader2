package com.example.ui.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.db.VocabularyWordEntity
import com.example.data.dictionary.DictionaryLookupResult
import com.example.data.dictionary.EnglishLemmatizer
import com.example.data.dictionary.MochiWordSpeaker
import com.example.data.dictionary.SystemTranslateIntentHelper
import com.example.theme.ContrastChecker
import com.example.ui.designsystem.LiquidGlassSurface
import com.example.ui.theme.LocalMochiPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DictionaryGlassPopup(
    initialQuery: String,
    result: DictionaryLookupResult?,
    contextSentence: String,
    bookTitle: String,
    bookLanguage: String = "en",
    savedVocabulary: List<VocabularyWordEntity>,
    onSearchQuery: (String) -> Unit,
    onSaveToVocabulary: (
        word: String,
        lemma: String,
        pos: String,
        definition: String,
        example: String
    ) -> Unit,
    onHighlightWord: (() -> Unit)? = null,
    onOpenDictionaryManager: () -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current
    val context = LocalContext.current
    val speaker = remember(context) { MochiWordSpeaker(context) }
    DisposableEffect(speaker) {
        onDispose { speaker.shutdown() }
    }

    var searchInput by remember(initialQuery) { mutableStateOf(initialQuery) }
    val primary = result?.primaryEntry
    val displayWord = primary?.word ?: result?.query ?: initialQuery
    val resolvedLemma = result?.resolvedLemma
    val normWord = EnglishLemmatizer.normalizeToken(displayWord)
    val alreadySaved = remember(savedVocabulary, normWord, resolvedLemma) {
        savedVocabulary.any {
            it.word.equals(normWord, ignoreCase = true) ||
                (!resolvedLemma.isNullOrBlank() && it.lemma.equals(resolvedLemma, ignoreCase = true))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlassSurface(
                shape = RoundedCornerShape(28.dp),
                elevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .testTag("dictionary_glass_popup")
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = Color(palette.accent), modifier = Modifier.size(20.dp))
                            Text("Offline Dictionary", style = MaterialTheme.typography.titleMedium, color = Color(palette.text))
                        }
                        Row {
                            IconButton(onClick = onOpenDictionaryManager, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Manage dictionaries", tint = Color(palette.textSecondary), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onDismiss, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close dictionary", tint = Color(palette.textSecondary), modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Search box for instant chain lookup
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = {
                            searchInput = it
                            onSearchQuery(it)
                        },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        placeholder = { Text("Look up a word or phrase…") },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dictionary_search_input")
                    )

                    // Headword + phonetic + lemma + Pronounce TTS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayWord,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(palette.text)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val phonetic = primary?.phonetic.orEmpty()
                                if (phonetic.isNotBlank()) {
                                    Text(phonetic, style = MaterialTheme.typography.bodySmall, color = Color(palette.link))
                                }
                                if (!resolvedLemma.isNullOrBlank() && !resolvedLemma.equals(displayWord, ignoreCase = true)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(palette.accentContainer)
                                    ) {
                                        Text(
                                            text = "lemma: $resolvedLemma",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ContrastChecker.bestOnColor(Color(palette.accentContainer)),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(palette.accentContainer),
                            onClick = {
                                speaker.speak(displayWord, bookLanguage) { err ->
                                    onShowMessage(err)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("dictionary_tts_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = "Pronounce word",
                                    tint = ContrastChecker.bestOnColor(Color(palette.accentContainer))
                                )
                            }
                        }
                    }

                    // Context sentence card
                    if (contextSentence.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(palette.surfaceVariant).copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "“${contextSentence.trim()}”",
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = Color(palette.textSecondary),
                                maxLines = 3,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Entries grouped by enabled dictionaries
                    if (result != null && result.groups.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            result.groups.forEach { group ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(palette.surface).copy(alpha = 0.72f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${group.dictionary.name} · ${group.dictionary.language.uppercase()}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color(palette.link)
                                        )
                                        group.entries.forEachIndexed { idx, entry ->
                                            if (idx > 0) HorizontalDivider(color = Color(palette.textSecondary).copy(alpha = 0.12f))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (entry.pos.isNotBlank()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(palette.accent).copy(alpha = 0.18f)
                                                    ) {
                                                        Text(
                                                            text = entry.pos,
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = Color(palette.accent),
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (!entry.word.equals(displayWord, ignoreCase = true)) {
                                                    Text(
                                                        text = entry.word,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = Color(palette.text)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = entry.definition,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(palette.text)
                                            )
                                            if (entry.example.isNotBlank()) {
                                                Text(
                                                    text = "e.g., ${entry.example}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                                    color = Color(palette.textSecondary)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (result.phraseMatches.isNotEmpty()) {
                                Text(
                                    text = "Phrases & Idioms in Context",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(palette.accent)
                                )
                                result.phraseMatches.flatMap { it.entries }.take(4).forEach { phrase ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(palette.accentContainer).copy(alpha = 0.42f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                text = "${phrase.word} (${phrase.pos.ifBlank { "phr." }})",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color(palette.text)
                                            )
                                            Text(
                                                text = phrase.definition,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(palette.text)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (result != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "No exact entry for “${result.query}” in active dictionaries.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(palette.textSecondary)
                            )
                            if (result.suggestions.isNotEmpty()) {
                                Text(
                                    text = "Did you mean:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(palette.accent)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    result.suggestions.forEach { sugg ->
                                        AssistChip(
                                            onClick = {
                                                searchInput = sugg
                                                onSearchQuery(sugg)
                                            },
                                            label = { Text(sugg) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Action Bar: Save to Vocabulary, Highlight, System Translate
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val saveEnabled = primary != null || normWord.isNotBlank()
                        Button(
                            onClick = {
                                val defText = primary?.definition ?: "Saved for study from $bookTitle"
                                onSaveToVocabulary(
                                    displayWord,
                                    primary?.lemma ?: resolvedLemma ?: normWord,
                                    primary?.pos.orEmpty(),
                                    defText,
                                    primary?.example.orEmpty()
                                )
                                onShowMessage("Saved “$displayWord” to Vocabulary Deck")
                            },
                            enabled = saveEnabled,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(palette.accent),
                                contentColor = ContrastChecker.bestOnColor(Color(palette.accent))
                            ),
                            modifier = Modifier.testTag("dictionary_save_vocab_button")
                        ) {
                            Icon(
                                imageVector = if (alreadySaved) Icons.Rounded.CheckCircle else Icons.Rounded.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (alreadySaved) "In Vocabulary" else "Save Word")
                        }

                        if (onHighlightWord != null) {
                            OutlinedButton(onClick = onHighlightWord) {
                                Icon(Icons.Rounded.Highlight, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Highlight")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val launched = SystemTranslateIntentHelper.launchSystemTranslate(context, displayWord)
                                if (!launched) {
                                    onShowMessage("No translator app found on device.")
                                }
                            },
                            modifier = Modifier.testTag("dictionary_translate_button")
                        ) {
                            Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Translate")
                        }
                    }
                }
            }
        }
    }
}
