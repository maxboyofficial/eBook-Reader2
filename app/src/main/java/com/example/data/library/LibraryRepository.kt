package com.example.data.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.db.BookCollectionCrossRef
import com.example.data.db.BookDao
import com.example.data.db.BookEntity
import com.example.data.db.BookTagCrossRef
import com.example.data.db.CollectionDao
import com.example.data.db.CollectionEntity
import com.example.data.db.FolderDao
import com.example.data.db.FolderEntity
import com.example.data.db.ReadingStatus
import com.example.data.db.TagDao
import com.example.data.db.TagEntity
import com.example.data.db.ThemeDao
import com.example.data.db.ThemeEntity
import com.example.theme.BundledThemes
import com.example.theme.MochiThemePalette
import com.example.theme.ThemePackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class LibraryRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val folderDao: FolderDao,
    private val collectionDao: CollectionDao,
    private val tagDao: TagDao,
    private val themeDao: ThemeDao
) {
    val visibleBooks: Flow<List<BookEntity>> = bookDao.observeVisibleBooks()
    val allBooksIncludingHidden: Flow<List<BookEntity>> = bookDao.observeAllBooks()
    val folders: Flow<List<FolderEntity>> = folderDao.observeFolders()
    val collections: Flow<List<CollectionEntity>> = collectionDao.observeCollections()
    val bookCollectionRefs: Flow<List<BookCollectionCrossRef>> = collectionDao.observeBookCollectionRefs()
    val tags: Flow<List<TagEntity>> = tagDao.observeTags()
    val bookTagRefs: Flow<List<BookTagCrossRef>> = tagDao.observeBookTagRefs()

    val themes: Flow<List<MochiThemePalette>> = themeDao.observeThemes().map { entities ->
        if (entities.isEmpty()) {
            BundledThemes.all
        } else {
            entities.mapNotNull { entity ->
                ThemePackManager.fromJson(entity.paletteJson)?.copy(
                    id = entity.id,
                    name = entity.name,
                    isDark = entity.isDark,
                    builtIn = entity.builtIn
                )
            }
        }
    }

    suspend fun initializeDefaults() = withContext(Dispatchers.IO) {
        // Seed or refresh bundled themes
        val themeEntities = BundledThemes.all.map { palette ->
            ThemeEntity(
                id = palette.id,
                name = palette.name,
                isDark = palette.isDark,
                paletteJson = ThemePackManager.toJson(palette),
                builtIn = true
            )
        }
        themeDao.insertAll(themeEntities)

        // Seed sample books if library is empty
        if (bookDao.getAllBooksOnce().isEmpty()) {
            seedSampleLibrary()
        }
    }

    suspend fun seedSampleLibrary() = withContext(Dispatchers.IO) {
        val samples = SampleBookSeeder.seedIfNeeded(context)
        val existingByUri = bookDao.getAllBooksOnce().associateBy { it.uri }
        val books = samples.mapIndexed { idx, sample ->
            SafBookScanner.scanSeededSample(
                context = context,
                sample = sample,
                existing = existingByUri[Uri.fromFile(sample.file).toString()],
                shelfOrder = idx
            )
        }
        books.forEach { bookDao.upsertBook(it) }
        folderDao.upsertFolder(
            FolderEntity(
                treeUri = "samples://local",
                displayName = "Mochi Samples",
                addedAt = System.currentTimeMillis(),
                lastScannedAt = System.currentTimeMillis(),
                bookCount = books.size
            )
        )
    }

    suspend fun addSafFolder(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }
        val doc = DocumentFile.fromTreeUri(context, treeUri)
        val folderName = doc?.name ?: "Books Folder"
        val existingMap = bookDao.getAllBooksOnce().associateBy { it.uri }
        val nextOrder = (existingMap.values.maxOfOrNull { it.shelfOrder } ?: -1) + 1
        val scanned = SafBookScanner.scanSafTree(
            context = context,
            treeUri = treeUri,
            existingBooksByUri = existingMap,
            nextShelfOrderStart = nextOrder
        )
        scanned.forEach { bookDao.upsertBook(it) }
        folderDao.upsertFolder(
            FolderEntity(
                treeUri = treeUri.toString(),
                displayName = folderName,
                addedAt = System.currentTimeMillis(),
                lastScannedAt = System.currentTimeMillis(),
                bookCount = scanned.size
            )
        )
        enqueueBackgroundScan()
        scanned.size
    }

    suspend fun importSingleFile(uri: Uri): BookEntity? = withContext(Dispatchers.IO) {
        val persistable = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        }.getOrDefault(false)

        val targetUri = if (!persistable && uri.scheme == "content") {
            val (name, _) = queryDisplayNameAndSize(uri)
            val importedDir = File(context.filesDir, "imported_books").apply { mkdirs() }
            val cleanName = name.ifBlank { "book_${System.currentTimeMillis()}" }.replace('/', '_')
            val destFile = File(importedDir, "${System.currentTimeMillis()}_$cleanName")
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }.isSuccess
            if (copied && destFile.exists() && destFile.length() > 0) {
                Uri.fromFile(destFile)
            } else {
                uri
            }
        } else {
            uri
        }

        val (name, size) = queryDisplayNameAndSize(targetUri)
        val existing = bookDao.getBookByUri(targetUri.toString())
        val nextOrder = (bookDao.getAllBooksOnce().maxOfOrNull { it.shelfOrder } ?: -1) + 1
        val scanned = SafBookScanner.scanSingleDocument(
            context = context,
            uri = targetUri,
            displayName = name,
            fileSize = size,
            existing = existing,
            shelfOrder = nextOrder
        ) ?: return@withContext null
        val id = bookDao.upsertBook(scanned)
        scanned.copy(id = if (id > 0) id else scanned.id)
    }

    suspend fun rescanAllFolders() = withContext(Dispatchers.IO) {
        val allFolders = folderDao.getAllFoldersOnce()
        val existingMap = bookDao.getAllBooksOnce().associateBy { it.uri }
        var nextOrder = (existingMap.values.maxOfOrNull { it.shelfOrder } ?: -1) + 1

        for (folder in allFolders) {
            if (folder.treeUri == "samples://local") {
                seedSampleLibrary()
            } else {
                val treeUri = Uri.parse(folder.treeUri)
                val scanned = SafBookScanner.scanSafTree(
                    context = context,
                    treeUri = treeUri,
                    existingBooksByUri = existingMap,
                    nextShelfOrderStart = nextOrder
                )
                nextOrder += scanned.size
                scanned.forEach { bookDao.upsertBook(it) }
                if (scanned.isNotEmpty()) {
                    bookDao.deleteRemovedInFolder(folder.treeUri, scanned.map { it.uri })
                }
                folderDao.upsertFolder(
                    folder.copy(
                        lastScannedAt = System.currentTimeMillis(),
                        bookCount = scanned.size
                    )
                )
            }
        }
    }

    fun enqueueBackgroundScan() {
        val request = OneTimeWorkRequestBuilder<LibraryScanWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "mochi_saf_rescan",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun removeFolder(treeUri: String) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(treeUri)
        val booksInFolder = bookDao.getAllBooksOnce().filter { it.folderTreeUri == treeUri }.map { it.id }
        if (booksInFolder.isNotEmpty()) {
            bookDao.deleteBooksByIds(booksInFolder)
        }
    }

    suspend fun swapBookOrder(firstId: Long, secondId: Long) = withContext(Dispatchers.IO) {
        val first = bookDao.getBookById(firstId) ?: return@withContext
        val second = bookDao.getBookById(secondId) ?: return@withContext
        bookDao.updateShelfOrder(first.id, second.shelfOrder)
        bookDao.updateShelfOrder(second.id, first.shelfOrder)
    }

    suspend fun stackBookOnto(sourceId: Long, targetId: Long) = withContext(Dispatchers.IO) {
        if (sourceId == targetId) return@withContext
        val source = bookDao.getBookById(sourceId) ?: return@withContext
        val target = bookDao.getBookById(targetId) ?: return@withContext
        val stackId = target.stackId ?: "stack_${UUID.randomUUID().toString().take(8)}"
        val stackTitle = target.stackTitle ?: target.series ?: "${target.title.take(18)} Stack"
        bookDao.updateStack(target.id, stackId, stackTitle)
        bookDao.updateStack(source.id, stackId, stackTitle)
    }

    suspend fun unstackBook(bookId: Long) = withContext(Dispatchers.IO) {
        bookDao.updateStack(bookId, null, null)
    }

    suspend fun updateStatus(ids: List<Long>, status: ReadingStatus) = withContext(Dispatchers.IO) {
        bookDao.updateStatus(ids, status)
    }

    suspend fun hideBooks(ids: List<Long>, hidden: Boolean) = withContext(Dispatchers.IO) {
        bookDao.updateHidden(ids, hidden)
    }

    suspend fun removeFromLibrary(ids: List<Long>) = withContext(Dispatchers.IO) {
        bookDao.deleteBooksByIds(ids)
    }

    suspend fun deleteBookFilesFromDisk(ids: List<Long>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (id in ids) {
            val book = bookDao.getBookById(id) ?: continue
            val uri = Uri.parse(book.uri)
            val ok = when (uri.scheme) {
                "file" -> uri.path?.let { File(it).delete() } ?: false
                "content" -> runCatching { DocumentFile.fromSingleUri(context, uri)?.delete() == true }.getOrDefault(false)
                else -> false
            }
            if (ok) deleted++
        }
        bookDao.deleteBooksByIds(ids)
        deleted
    }

    suspend fun createCollection(name: String): Long = withContext(Dispatchers.IO) {
        collectionDao.insertCollection(CollectionEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
    }

    suspend fun renameCollection(id: Long, newName: String) = withContext(Dispatchers.IO) {
        collectionDao.renameCollection(id, newName.trim())
    }

    suspend fun deleteCollection(id: Long) = withContext(Dispatchers.IO) {
        collectionDao.deleteCollection(id)
    }

    suspend fun addBooksToCollection(bookIds: List<Long>, collectionId: Long) = withContext(Dispatchers.IO) {
        bookIds.forEach { bookId ->
            collectionDao.addBookToCollection(BookCollectionCrossRef(bookId, collectionId))
        }
    }

    suspend fun removeBookFromCollection(bookId: Long, collectionId: Long) = withContext(Dispatchers.IO) {
        collectionDao.removeBookFromCollection(bookId, collectionId)
    }

    suspend fun addTagToBook(bookId: Long, tagName: String) = withContext(Dispatchers.IO) {
        val clean = tagName.trim()
        if (clean.isEmpty()) return@withContext
        val existing = tagDao.findByName(clean)
        val tagId = existing?.id ?: tagDao.insertTag(TagEntity(name = clean))
        tagDao.addBookTag(BookTagCrossRef(bookId, tagId))
    }

    suspend fun removeTagFromBook(bookId: Long, tagId: Long) = withContext(Dispatchers.IO) {
        tagDao.removeBookTag(bookId, tagId)
    }

    suspend fun saveCustomTheme(palette: MochiThemePalette) = withContext(Dispatchers.IO) {
        themeDao.upsertTheme(
            ThemeEntity(
                id = palette.id,
                name = palette.name,
                isDark = palette.isDark,
                paletteJson = ThemePackManager.toJson(palette),
                builtIn = false
            )
        )
    }

    suspend fun duplicateTheme(source: MochiThemePalette): MochiThemePalette = withContext(Dispatchers.IO) {
        val copy = source.copy(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = "${source.name} Copy",
            builtIn = false
        )
        saveCustomTheme(copy)
        copy
    }

    suspend fun deleteCustomTheme(id: String) = withContext(Dispatchers.IO) {
        themeDao.deleteCustomTheme(id)
    }

    private fun queryDisplayNameAndSize(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported Book"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }
}
