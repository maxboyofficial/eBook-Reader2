package com.example.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.db.BookCollectionCrossRef
import com.example.data.db.BookEntity
import com.example.data.db.BookTagCrossRef
import com.example.data.db.CollectionEntity
import com.example.data.db.ReadingStatus
import com.example.data.db.TagEntity
import com.example.theme.ContrastChecker
import com.example.ui.designsystem.LiquidGlassSurface
import com.example.ui.designsystem.MochiBookCover
import com.example.ui.designsystem.MochiWavyProgressBar
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMochiPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailSheet(
    book: BookEntity,
    allBooks: List<BookEntity>,
    collections: List<CollectionEntity>,
    collectionRefs: List<BookCollectionCrossRef>,
    tags: List<TagEntity>,
    tagRefs: List<BookTagCrossRef>,
    isDuplicate: Boolean,
    onDismiss: () -> Unit,
    onOpenReader: (() -> Unit)? = null,
    onUpdateStatus: (ReadingStatus) -> Unit,
    onToggleCollection: (Long, Boolean) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (Long) -> Unit,
    onStackOnto: (Long) -> Unit,
    onUnstack: () -> Unit,
    onHideBook: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onDeleteFileFromDisk: () -> Unit
) {
    val palette = LocalMochiPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface)
    ) {
        BookDetailContent(
            book = book,
            allBooks = allBooks,
            collections = collections,
            collectionRefs = collectionRefs,
            tags = tags,
            tagRefs = tagRefs,
            isDuplicate = isDuplicate,
            onOpenReader = onOpenReader?.let { open ->
                {
                    onDismiss()
                    open()
                }
            },
            onUpdateStatus = onUpdateStatus,
            onToggleCollection = onToggleCollection,
            onAddTag = onAddTag,
            onRemoveTag = onRemoveTag,
            onStackOnto = onStackOnto,
            onUnstack = onUnstack,
            onHideBook = onHideBook,
            onRemoveFromLibrary = onRemoveFromLibrary,
            onDeleteFileFromDisk = {
                onDeleteFileFromDisk()
                onDismiss()
            },
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        )
    }
}

/**
 * Persistent Tablet Supporting Pane for EXPANDED screens (tablets & landscape foldables).
 */
@Composable
fun BookInspectorPane(
    book: BookEntity?,
    allBooks: List<BookEntity>,
    collections: List<CollectionEntity>,
    collectionRefs: List<BookCollectionCrossRef>,
    tags: List<TagEntity>,
    tagRefs: List<BookTagCrossRef>,
    duplicateHashes: Set<String>,
    onOpenReader: (BookEntity) -> Unit,
    onUpdateStatus: (Long, ReadingStatus) -> Unit,
    onToggleCollection: (Long, Long, Boolean) -> Unit,
    onAddTag: (Long, String) -> Unit,
    onRemoveTag: (Long, Long) -> Unit,
    onStackOnto: (Long, Long) -> Unit,
    onUnstack: (Long) -> Unit,
    onHideBook: (BookEntity) -> Unit,
    onRemoveFromLibrary: (Long) -> Unit,
    onDeleteFileFromDisk: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMochiPalette.current
    LiquidGlassSurface(
        shape = RoundedCornerShape(28.dp),
        elevation = 8.dp,
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 12.dp, bottom = 14.dp, end = 14.dp)
            .fillMaxHeight()
    ) {
        if (book == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = Color(palette.accent),
                    modifier = Modifier.size(40.dp)
                )
                Text("Book Inspector", style = MaterialTheme.typography.titleLarge, color = Color(palette.text))
                Text(
                    text = "Tap any book to start reading, or tap Details to inspect metadata, tags, and collections side by side.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(palette.textSecondary)
                )
            }
        } else {
            BookDetailContent(
                book = book,
                allBooks = allBooks,
                collections = collections,
                collectionRefs = collectionRefs,
                tags = tags,
                tagRefs = tagRefs,
                isDuplicate = book.contentHash in duplicateHashes,
                onOpenReader = { onOpenReader(book) },
                onUpdateStatus = { onUpdateStatus(book.id, it) },
                onToggleCollection = { colId, add -> onToggleCollection(book.id, colId, add) },
                onAddTag = { onAddTag(book.id, it) },
                onRemoveTag = { onRemoveTag(book.id, it) },
                onStackOnto = { targetId -> onStackOnto(book.id, targetId) },
                onUnstack = { onUnstack(book.id) },
                onHideBook = { onHideBook(book) },
                onRemoveFromLibrary = { onRemoveFromLibrary(book.id) },
                onDeleteFileFromDisk = { onDeleteFileFromDisk(book.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            )
        }
    }
}

@Composable
fun BookDetailContent(
    book: BookEntity,
    allBooks: List<BookEntity>,
    collections: List<CollectionEntity>,
    collectionRefs: List<BookCollectionCrossRef>,
    tags: List<TagEntity>,
    tagRefs: List<BookTagCrossRef>,
    isDuplicate: Boolean,
    onOpenReader: (() -> Unit)?,
    onUpdateStatus: (ReadingStatus) -> Unit,
    onToggleCollection: (Long, Boolean) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (Long) -> Unit,
    onStackOnto: (Long) -> Unit,
    onUnstack: () -> Unit,
    onHideBook: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onDeleteFileFromDisk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMochiPalette.current
    var newTagText by remember { mutableStateOf("") }
    var showStackPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val assignedCollectionIds = remember(collectionRefs, book.id) {
        collectionRefs.filter { it.bookId == book.id }.map { it.collectionId }.toSet()
    }
    val assignedTagIds = remember(tagRefs, book.id) {
        tagRefs.filter { it.bookId == book.id }.map { it.tagId }.toSet()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            MochiBookCover(
                book = book,
                cornerRadius = 14.dp,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(0.68f)
            )
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = CircleShape,
                    color = Color(palette.accentContainer)
                ) {
                    Text(
                        text = "${book.format.displayName} · ~${book.pageCountEstimate} pages",
                        style = MaterialTheme.typography.labelSmall,
                        color = ContrastChecker.bestOnColor(Color(palette.accentContainer)),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(palette.text)
                )
                Text(
                    text = book.authors,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(palette.textSecondary)
                )
                if (!book.series.isNullOrBlank()) {
                    Text(
                        text = "Series: ${book.series}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(palette.link)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                MochiWavyProgressBar(progress = book.progress)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(book.progress * 100).roundToInt()}% · ${formatFileSize(book.fileSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(palette.textSecondary)
                )
            }
        }

        if (onOpenReader != null) {
            Button(
                onClick = onOpenReader,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(palette.accent),
                    contentColor = ContrastChecker.bestOnColor(Color(palette.accent))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("detail_read_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (book.progress > 0.01f) "Continue Reading" else "Open in Reader")
            }
        }

        if (isDuplicate) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(palette.accentContainer).copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Duplicate detected by content hash. You can hide or remove extra copies without touching your original file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ContrastChecker.bestOnColor(Color(palette.accentContainer)),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Integrity badge (non-destructive file guarantee)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(palette.surfaceVariant).copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    tint = Color(palette.link),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Non-destructive · Original file is never modified",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(palette.text)
                    )
                    Text(
                        text = "SHA-256: ${book.contentHash.take(16)}…",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = Color(palette.textSecondary)
                    )
                }
            }
        }

        // Reading Status
        Text("Reading Status", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReadingStatus.entries.forEach { status ->
                FilterChip(
                    selected = book.status == status,
                    onClick = { onUpdateStatus(status) },
                    label = { Text(status.label) }
                )
            }
        }

        // Collections
        if (collections.isNotEmpty()) {
            Text("Collections", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                collections.forEach { col ->
                    val assigned = col.id in assignedCollectionIds
                    FilterChip(
                        selected = assigned,
                        onClick = { onToggleCollection(col.id, !assigned) },
                        label = { Text(col.name) }
                    )
                }
            }
        }

        // Tags
        Text("Tags", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
        if (assignedTagIds.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tags.filter { it.id in assignedTagIds }.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemoveTag(tag.id) },
                        label = { Text("#${tag.name} ×") }
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newTagText,
                onValueChange = { newTagText = it },
                placeholder = { Text("Add tag") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    if (newTagText.isNotBlank()) {
                        onAddTag(newTagText)
                        newTagText = ""
                    }
                }
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add tag")
            }
        }

        // Stack & library actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (book.stackId != null) {
                OutlinedButton(onClick = onUnstack, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unstack")
                }
            } else {
                OutlinedButton(onClick = { showStackPicker = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stack")
                }
            }
            OutlinedButton(onClick = onHideBook, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (book.hidden) "Unhide" else "Hide")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onRemoveFromLibrary, modifier = Modifier.weight(1f)) {
                Text("Remove from Library", color = Color(palette.textSecondary))
            }
            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("delete_file_button")
            ) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete File", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showStackPicker) {
        val candidates = allBooks.filter { it.id != book.id && !it.hidden }
        AlertDialog(
            onDismissRequest = { showStackPicker = false },
            title = { Text("Stack onto book") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    candidates.take(8).forEach { target ->
                        TextButton(
                            onClick = {
                                onStackOnto(target.id)
                                showStackPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(target.title, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStackPicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete file permanently?") },
            text = {
                Text("This permanently deletes \"${book.title}\" from storage and cannot be undone. To keep the file on disk, choose Remove from Library instead.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteFileFromDisk()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

