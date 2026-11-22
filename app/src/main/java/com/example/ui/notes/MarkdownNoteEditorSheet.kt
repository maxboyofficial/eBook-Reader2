package com.example.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.example.data.annotations.CozySticker
import com.example.data.annotations.HighlightColorHelper
import com.example.data.annotations.HighlightStyle
import com.example.data.db.BookEntity
import com.example.data.db.BookFormat
import com.example.data.reader.ContentBlock
import com.example.data.reader.DocumentParsers
import com.example.data.reader.ReaderPaginator
import com.example.theme.ContrastChecker
import com.example.ui.designsystem.MochiConnectedSegments
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMochiPalette

private enum class NoteEditorTab(val label: String) {
    WRITE("Write"),
    PREVIEW("Preview")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownNoteEditorSheet(
    title: String,
    quoteText: String,
    initialNote: String,
    initialTags: String,
    initialColorRole: Int,
    initialStyle: HighlightStyle,
    initialSticker: CozySticker,
    colorBlindSafe: Boolean,
    showStyleAndColor: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (
        noteMarkdown: String,
        tags: String,
        colorRole: Int,
        style: HighlightStyle,
        sticker: CozySticker
    ) -> Unit
) {
    val palette = LocalMochiPalette.current
    var activeTab by remember { mutableStateOf(NoteEditorTab.WRITE) }
    var noteText by remember(initialNote) { mutableStateOf(initialNote) }
    var tagsText by remember(initialTags) { mutableStateOf(initialTags) }
    var selectedColor by remember(initialColorRole) { mutableIntStateOf(initialColorRole) }
    var selectedStyle by remember(initialStyle) { mutableStateOf(initialStyle) }
    var selectedSticker by remember(initialSticker) { mutableStateOf(initialSticker) }

    val highlightColors = remember(palette, colorBlindSafe) {
        HighlightColorHelper.resolveColors(palette, colorBlindSafe)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface),
        modifier = Modifier.testTag("markdown_note_editor_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 660.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color(palette.text))
                MochiConnectedSegments(
                    items = NoteEditorTab.entries,
                    selected = activeTab,
                    labelProvider = { it.label },
                    testTagProvider = { "note_tab_${it.name.lowercase()}" },
                    onSelect = { activeTab = it },
                    modifier = Modifier.widthIn(max = 210.dp)
                )
            }

            if (quoteText.isNotBlank()) {
                val roleColor = Color(highlightColors[selectedColor.coerceIn(0, highlightColors.lastIndex)])
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = roleColor.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "“${quoteText.trim()}”",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = Color(palette.text),
                        modifier = Modifier.padding(12.dp),
                        maxLines = 4
                    )
                }
            }

            if (showStyleAndColor) {
                // 8 Highlight color circles
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    highlightColors.forEachIndexed { idx, argb ->
                        val picked = idx == selectedColor
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (picked) 2.5.dp else 1.dp,
                                    color = if (picked) Color(palette.accent) else Color(palette.textSecondary).copy(alpha = 0.25f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = idx }
                                .testTag("note_color_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (picked) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = HighlightColorHelper.nameForRole(idx),
                                    tint = ContrastChecker.bestOnColor(Color(argb)),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // 5 Style chips
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HighlightStyle.entries.forEach { st ->
                        FilterChip(
                            selected = selectedStyle == st,
                            onClick = { selectedStyle = st },
                            label = { Text(st.label) }
                        )
                    }
                }
            }

            if (activeTab == NoteEditorTab.WRITE) {
                // Formatting toolbar
                val snippets = listOf(
                    "B" to "**bold**",
                    "I" to "*italic*",
                    "H2" to "\n## Heading\n",
                    "List" to "\n- ",
                    "Task" to "\n- [ ] ",
                    "Quote" to "\n> ",
                    "Code" to "`code`",
                    "[[Link]]" to "[[Book Title]]",
                    "#tag" to " #idea"
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    snippets.forEach { (label, insert) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(palette.surfaceVariant),
                            onClick = { noteText += insert }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(palette.text),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Markdown Note") },
                    placeholder = { Text("Write thoughts in Markdown, add [[wikilinks]] or - [ ] tasks…") },
                    minLines = 4,
                    maxLines = 9,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("markdown_note_input")
                )
            } else {
                val previewDoc = remember(noteText) {
                    val dummyBook = BookEntity(
                        uri = "memory://preview.md",
                        contentHash = "preview",
                        title = "Note Preview",
                        authors = "",
                        format = BookFormat.MD,
                        addedAt = 0L
                    )
                    DocumentParsers.parseMarkdown(dummyBook, noteText.ifBlank { "_Nothing written yet._" }, emptyList())
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(palette.background),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 280.dp)
                        .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        previewDoc.chapters.flatMap { it.blocks }.forEach { block ->
                            when (block) {
                                is ContentBlock.Heading -> Text(
                                    text = ReaderPaginator.toAnnotatedString(block.content, palette, false),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(palette.text)
                                )
                                is ContentBlock.Paragraph -> Text(
                                    text = ReaderPaginator.toAnnotatedString(block.content, palette, false),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(palette.text)
                                )
                                is ContentBlock.Blockquote -> Text(
                                    text = "“${block.content.text}”",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                    color = Color(palette.textSecondary)
                                )
                                is ContentBlock.CodeBlock -> Text(
                                    text = block.code,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = Color(palette.text),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(palette.surfaceVariant), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                )
                                is ContentBlock.ListBlock -> block.items.forEach { item ->
                                    val prefix = when (item.checked) {
                                        true -> "☑ "
                                        false -> "☐ "
                                        null -> "• "
                                    }
                                    Text(
                                        text = prefix + ReaderPaginator.toAnnotatedString(item.text, palette, false),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(palette.text)
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = { Text("Tags (comma or space separated)") },
                placeholder = { Text("quote, philosophy, chapter1") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("markdown_note_tags_input")
            )

            // Cozy Stickers
            Text("Cozy Sticker", style = MaterialTheme.typography.labelLarge, color = Color(palette.text))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CozySticker.entries.forEach { st ->
                    val label = if (st.badgeText.isNotEmpty()) "${st.badgeText} ${st.label}" else st.label
                    FilterChip(
                        selected = selectedSticker == st,
                        onClick = { selectedSticker = st },
                        label = { Text(label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(palette.textSecondary))
                }
                Button(
                    onClick = {
                        onSave(noteText, tagsText, selectedColor, selectedStyle, selectedSticker)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(palette.accent),
                        contentColor = ContrastChecker.bestOnColor(Color(palette.accent))
                    ),
                    modifier = Modifier.testTag("save_note_button")
                ) {
                    Text("Save Note")
                }
            }
        }
    }
}
