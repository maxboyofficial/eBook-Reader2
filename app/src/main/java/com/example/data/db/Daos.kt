package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE hidden = 0 ORDER BY shelfOrder ASC, addedAt DESC")
    fun observeVisibleBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY shelfOrder ASC, addedAt DESC")
    fun observeAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    fun observeBookById(bookId: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: Long): BookEntity?

    @Query("SELECT * FROM books WHERE uri = :uri LIMIT 1")
    suspend fun getBookByUri(uri: String): BookEntity?

    @Query("SELECT * FROM books")
    suspend fun getAllBooksOnce(): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: BookEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(books: List<BookEntity>)

    @Update
    suspend fun updateBooks(books: List<BookEntity>)

    @Query("UPDATE books SET status = :status WHERE id IN (:ids)")
    suspend fun updateStatus(ids: List<Long>, status: ReadingStatus)

    @Query("UPDATE books SET hidden = :hidden WHERE id IN (:ids)")
    suspend fun updateHidden(ids: List<Long>, hidden: Boolean)

    @Query("UPDATE books SET shelfOrder = :order WHERE id = :bookId")
    suspend fun updateShelfOrder(bookId: Long, order: Int)

    @Query("UPDATE books SET stackId = :stackId, stackTitle = :stackTitle WHERE id = :bookId")
    suspend fun updateStack(bookId: Long, stackId: String?, stackTitle: String?)

    @Query("DELETE FROM books WHERE id IN (:ids)")
    suspend fun deleteBooksByIds(ids: List<Long>)

    @Query("DELETE FROM books WHERE folderTreeUri = :treeUri AND uri NOT IN (:activeUris)")
    suspend fun deleteRemovedInFolder(treeUri: String, activeUris: List<String>)

    @Query("UPDATE books SET progress = :progress, lastOpenedAt = :lastOpenedAt, status = :status WHERE id = :bookId")
    suspend fun updateReadingProgress(bookId: Long, progress: Float, lastOpenedAt: Long, status: ReadingStatus)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY addedAt DESC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersOnce(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE treeUri = :treeUri")
    suspend fun deleteFolder(treeUri: String)
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM book_collections")
    fun observeBookCollectionRefs(): Flow<List<BookCollectionCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun renameCollection(id: Long, name: String)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBookToCollection(ref: BookCollectionCrossRef)

    @Query("DELETE FROM book_collections WHERE bookId = :bookId AND collectionId = :collectionId")
    suspend fun removeBookFromCollection(bookId: Long, collectionId: Long)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM book_tags")
    fun observeBookTagRefs(): Flow<List<BookTagCrossRef>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBookTag(ref: BookTagCrossRef)

    @Query("DELETE FROM book_tags WHERE bookId = :bookId AND tagId = :tagId")
    suspend fun removeBookTag(bookId: Long, tagId: Long)
}

@Dao
interface ThemeDao {
    @Query("SELECT * FROM themes ORDER BY builtIn DESC, createdAt ASC")
    fun observeThemes(): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM themes WHERE id = :id LIMIT 1")
    suspend fun getThemeById(id: String): ThemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTheme(theme: ThemeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(themes: List<ThemeEntity>)

    @Query("DELETE FROM themes WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustomTheme(id: String)
}

@Dao
interface PositionDao {
    @Query("SELECT * FROM positions WHERE bookId = :bookId LIMIT 1")
    suspend fun getPosition(bookId: Long): PositionEntity?

    @Query("SELECT * FROM positions WHERE bookId = :bookId LIMIT 1")
    fun observePosition(bookId: Long): Flow<PositionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosition(position: PositionEntity)
}

@Dao
interface BookSettingsDao {
    @Query("SELECT * FROM book_settings WHERE bookId = :bookId LIMIT 1")
    fun observeBookSettings(bookId: Long): Flow<BookSettingsEntity?>

    @Query("SELECT * FROM book_settings WHERE bookId = :bookId LIMIT 1")
    suspend fun getBookSettings(bookId: Long): BookSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookSettings(settings: BookSettingsEntity)

    @Query("DELETE FROM book_settings WHERE bookId = :bookId")
    suspend fun clearBookSettings(bookId: Long)
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: Long): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY createdAt ASC")
    suspend fun getForBookOnce(bookId: Long): List<AnnotationEntity>

    @Query("SELECT * FROM annotations ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<AnnotationEntity>

    @Query("SELECT * FROM annotations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AnnotationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(annotation: AnnotationEntity): Long

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ExportTemplateDao {
    @Query("SELECT * FROM export_templates ORDER BY id ASC")
    fun observeTemplates(): Flow<List<ExportTemplateEntity>>

    @Query("SELECT * FROM export_templates ORDER BY id ASC")
    suspend fun getAllOnce(): List<ExportTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: ExportTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(templates: List<ExportTemplateEntity>)

    @Query("DELETE FROM export_templates WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionaries ORDER BY sortOrder ASC, addedAt ASC")
    fun observeDictionaries(): Flow<List<DictionaryEntity>>

    @Query("SELECT * FROM dictionaries ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun getAllOnce(): List<DictionaryEntity>

    @Query("SELECT * FROM dictionaries WHERE enabled = 1 ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun getEnabledOnce(): List<DictionaryEntity>

    @Query("SELECT * FROM dictionaries WHERE isBundled = 1 LIMIT 1")
    suspend fun getBundledOnce(): DictionaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dictionary: DictionaryEntity): Long

    @Update
    suspend fun updateAll(dictionaries: List<DictionaryEntity>)

    @Query("DELETE FROM dictionaries WHERE id = :id AND isBundled = 0")
    suspend fun deleteCustomById(id: Long)
}

@Dao
interface VocabularyDao {
    @Query("SELECT * FROM vocabulary_words ORDER BY nextReviewAt ASC, createdAt DESC")
    fun observeAll(): Flow<List<VocabularyWordEntity>>

    @Query("SELECT * FROM vocabulary_words ORDER BY nextReviewAt ASC, createdAt DESC")
    suspend fun getAllOnce(): List<VocabularyWordEntity>

    @Query("SELECT * FROM vocabulary_words WHERE nextReviewAt <= :nowMillis ORDER BY nextReviewAt ASC")
    fun observeDue(nowMillis: Long): Flow<List<VocabularyWordEntity>>

    @Query("SELECT * FROM vocabulary_words WHERE LOWER(word) = LOWER(:term) OR LOWER(lemma) = LOWER(:term) LIMIT 1")
    suspend fun findByWordOrLemma(term: String): VocabularyWordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(word: VocabularyWordEntity): Long

    @Query("DELETE FROM vocabulary_words WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AnnotationStudioDao {
    @Query("SELECT * FROM annotation_layers WHERE bookId = :bookId ORDER BY sortOrder ASC, id ASC")
    fun observeLayers(bookId: Long): Flow<List<AnnotationLayerEntity>>

    @Query("SELECT * FROM annotation_layers WHERE bookId = :bookId ORDER BY sortOrder ASC, id ASC")
    suspend fun getLayersOnce(bookId: Long): List<AnnotationLayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLayer(layer: AnnotationLayerEntity): Long

    @Query("DELETE FROM annotation_layers WHERE id = :layerId")
    suspend fun deleteLayer(layerId: Long)

    @Query("SELECT * FROM markup_objects WHERE bookId = :bookId ORDER BY pageIndex ASC, zOrder ASC, id ASC")
    fun observeAllBookMarkups(bookId: Long): Flow<List<MarkupObjectEntity>>

    @Query("SELECT * FROM markup_objects WHERE bookId = :bookId AND pageIndex = :pageIndex ORDER BY zOrder ASC, id ASC")
    fun observePageMarkups(bookId: Long, pageIndex: Int): Flow<List<MarkupObjectEntity>>

    @Query("SELECT * FROM markup_objects WHERE bookId = :bookId ORDER BY pageIndex ASC, zOrder ASC, id ASC")
    suspend fun getAllBookMarkupsOnce(bookId: Long): List<MarkupObjectEntity>

    @Query("SELECT * FROM markup_objects WHERE bookId = :bookId AND pageIndex = :pageIndex ORDER BY zOrder ASC, id ASC")
    suspend fun getPageMarkupsOnce(bookId: Long, pageIndex: Int): List<MarkupObjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMarkup(markup: MarkupObjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMarkups(markups: List<MarkupObjectEntity>)

    @Query("DELETE FROM markup_objects WHERE id = :id")
    suspend fun deleteMarkup(id: Long)

    @Query("DELETE FROM markup_objects WHERE id IN (:ids)")
    suspend fun deleteMarkups(ids: List<Long>)

    @Query("DELETE FROM markup_objects WHERE layerId = :layerId")
    suspend fun deleteMarkupsByLayer(layerId: Long)

    @Query("SELECT * FROM page_scales WHERE bookId = :bookId")
    fun observeScales(bookId: Long): Flow<List<PageScaleEntity>>

    @Query("SELECT * FROM page_scales WHERE bookId = :bookId AND pageIndex = :pageIndex LIMIT 1")
    suspend fun getScale(bookId: Long, pageIndex: Int): PageScaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScale(scale: PageScaleEntity)
}

@Dao
interface ReadingStatsDao {
    @Query("SELECT * FROM reading_sessions ORDER BY dateIso DESC, updatedAt DESC")
    fun observeAllSessions(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY dateIso DESC, updatedAt DESC")
    suspend fun getAllSessionsOnce(): List<ReadingSessionEntity>

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId AND dateIso = :dateIso LIMIT 1")
    suspend fun getSessionForBookDate(bookId: Long, dateIso: String): ReadingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ReadingSessionEntity): Long
}




