package com.example.data.dictionary

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.db.DictionaryDao
import com.example.data.db.DictionaryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class DictionaryLookupGroup(
    val dictionary: DictionaryEntity,
    val entries: List<DictionaryStoredEntry>
)

data class DictionaryLookupResult(
    val query: String,
    val resolvedLemma: String?,
    val groups: List<DictionaryLookupGroup>,
    val phraseMatches: List<DictionaryLookupGroup>,
    val suggestions: List<String>
) {
    val primaryEntry: DictionaryStoredEntry?
        get() = groups.firstOrNull()?.entries?.firstOrNull()
            ?: phraseMatches.firstOrNull()?.entries?.firstOrNull()
}

class DictionaryRepository(
    private val context: Context,
    private val dictionaryDao: DictionaryDao,
    val sqlStore: DictionarySqlStore = DictionarySqlStore(context)
) {
    fun observeDictionaries(): Flow<List<DictionaryEntity>> = dictionaryDao.observeDictionaries()

    suspend fun ensureBundledDictionary() = withContext(Dispatchers.IO) {
        val existing = dictionaryDao.getBundledOnce()
        if (existing != null && sqlStore.countForDictionary(existing.id) > 0) {
            return@withContext
        }
        val rawEntries = context.assets.open(BUNDLED_ASSET_PATH).use { stream ->
            DictionaryImporters.parseTsvAsset(stream)
        }
        val dictEntity = if (existing != null) {
            existing.copy(entryCount = rawEntries.size)
        } else {
            DictionaryEntity(
                name = "Mochi Core English (WordNet-style)",
                language = "en",
                format = "BUNDLED",
                storagePath = BUNDLED_ASSET_PATH,
                entryCount = rawEntries.size,
                enabled = true,
                sortOrder = 0,
                isBundled = true
            )
        }
        val id = dictionaryDao.upsert(dictEntity).let { rowId ->
            if (dictEntity.id != 0L) dictEntity.id else rowId
        }
        sqlStore.replaceDictionaryEntries(id, rawEntries)
    }

    suspend fun lookup(
        rawQuery: String,
        contextSentence: String = "",
        bookLanguage: String = "en",
        perLanguageDefaultDict: Map<String, Long> = emptyMap()
    ): DictionaryLookupResult = withContext(Dispatchers.IO) {
        ensureBundledDictionary()
        val cleanQuery = EnglishLemmatizer.normalizeToken(rawQuery)
        if (cleanQuery.isBlank()) {
            return@withContext DictionaryLookupResult(
                query = rawQuery,
                resolvedLemma = null,
                groups = emptyList(),
                phraseMatches = emptyList(),
                suggestions = emptyList()
            )
        }

        val enabledDicts = prioritizeDictionaries(
            dicts = dictionaryDao.getEnabledOnce(),
            bookLanguage = bookLanguage,
            perLanguageDefaults = perLanguageDefaultDict
        )
        if (enabledDicts.isEmpty()) {
            return@withContext DictionaryLookupResult(
                query = rawQuery,
                resolvedLemma = null,
                groups = emptyList(),
                phraseMatches = emptyList(),
                suggestions = emptyList()
            )
        }
        val enabledIds = enabledDicts.map { it.id }
        val dictById = enabledDicts.associateBy { it.id }

        val candidates = EnglishLemmatizer.candidateLemmas(cleanQuery)
        val rawMatches = sqlStore.findExactOrLemmaMatches(candidates, enabledIds)
        val sortedMatches = rawMatches.sortedWith(
            compareBy<DictionaryStoredEntry> { entry ->
                val idx = candidates.indexOfFirst {
                    it == entry.word.lowercase() || it == entry.lemma.lowercase()
                }
                if (idx < 0) Int.MAX_VALUE else idx
            }.thenBy { entry ->
                enabledIds.indexOf(entry.dictId).let { if (it < 0) Int.MAX_VALUE else it }
            }
        )

        val resolvedLemma = sortedMatches.firstOrNull()?.let { hit ->
            val hWord = hit.word.lowercase()
            if (hWord != cleanQuery) hit.word else hit.lemma.takeIf { it != cleanQuery }
        }

        val groups = enabledDicts.mapNotNull { dict ->
            val list = sortedMatches.filter { it.dictId == dict.id }
            if (list.isEmpty()) null else DictionaryLookupGroup(dict, list)
        }

        // Phrase & idiom lookup from surrounding context
        val phrases = EnglishLemmatizer.extractContextPhrases(contextSentence, cleanQuery)
        val phraseMatchesRaw = if (phrases.isNotEmpty()) {
            sqlStore.findExactOrLemmaMatches(phrases, enabledIds)
                .filter { it.word.contains(' ') }
        } else emptyList()
        val phraseGroups = enabledDicts.mapNotNull { dict ->
            val list = phraseMatchesRaw.filter { it.dictId == dict.id }
            if (list.isEmpty()) null else DictionaryLookupGroup(dict, list)
        }

        val suggestions = if (groups.isEmpty()) {
            val ftsHits = sqlStore.searchFtsPrefix(cleanQuery, enabledIds, limit = 6).map { it.word }
            val fuzzyHits = sqlStore.suggestDidYouMean(cleanQuery, enabledIds, maxSuggestions = 6)
            (fuzzyHits + ftsHits).distinctBy { it.lowercase() }.take(6)
        } else emptyList()

        DictionaryLookupResult(
            query = rawQuery.trim(),
            resolvedLemma = resolvedLemma,
            groups = groups,
            phraseMatches = phraseGroups,
            suggestions = suggestions
        )
    }

    suspend fun toggleEnabled(dict: DictionaryEntity, enabled: Boolean) = withContext(Dispatchers.IO) {
        dictionaryDao.upsert(dict.copy(enabled = enabled))
    }

    suspend fun moveOrder(dictId: Long, delta: Int) = withContext(Dispatchers.IO) {
        val list = dictionaryDao.getAllOnce().toMutableList()
        val index = list.indexOfFirst { it.id == dictId }
        if (index < 0) return@withContext
        val target = (index + delta).coerceIn(0, list.lastIndex)
        if (target == index) return@withContext
        val item = list.removeAt(index)
        list.add(target, item)
        val reindexed = list.mapIndexed { idx, d -> d.copy(sortOrder = idx) }
        dictionaryDao.updateAll(reindexed)
    }

    suspend fun updateLanguage(dict: DictionaryEntity, language: String) = withContext(Dispatchers.IO) {
        dictionaryDao.upsert(dict.copy(language = language.trim().lowercase().ifBlank { "en" }))
    }

    suspend fun deleteCustomDictionary(dictId: Long) = withContext(Dispatchers.IO) {
        sqlStore.deleteDictionary(dictId)
        dictionaryDao.deleteCustomById(dictId)
    }

    suspend fun importFromSafTree(treeUri: Uri): Result<DictionaryEntity> = withContext(Dispatchers.IO) {
        DictionaryImporters.importFromSafTree(context, treeUri).mapCatching { pack ->
            saveParsedPack(pack)
        }
    }

    suspend fun importFromSafFile(fileUri: Uri): Result<DictionaryEntity> = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(fileUri) ?: "dictionary_pack.tsv"
        DictionaryImporters.importFromSafFile(context, fileUri, displayName).mapCatching { pack ->
            saveParsedPack(pack)
        }
    }

    suspend fun importParsedPack(pack: ParsedDictionaryPack): DictionaryEntity = withContext(Dispatchers.IO) {
        saveParsedPack(pack)
    }

    private suspend fun saveParsedPack(pack: ParsedDictionaryPack): DictionaryEntity {
        require(pack.entries.isNotEmpty()) { "Dictionary contains 0 readable entries." }
        val existing = dictionaryDao.getAllOnce()
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: 0) + 1
        val entity = DictionaryEntity(
            name = pack.name,
            language = pack.language,
            format = pack.format,
            storagePath = "sql://dict_${System.currentTimeMillis()}",
            entryCount = pack.entries.size,
            enabled = true,
            sortOrder = nextOrder,
            isBundled = false
        )
        val id = dictionaryDao.upsert(entity)
        val saved = entity.copy(id = id)
        sqlStore.replaceDictionaryEntries(id, pack.entries)
        return saved
    }

    private fun prioritizeDictionaries(
        dicts: List<DictionaryEntity>,
        bookLanguage: String,
        perLanguageDefaults: Map<String, Long>
    ): List<DictionaryEntity> {
        val normLang = bookLanguage.trim().lowercase().substringBefore('-').ifBlank { "en" }
        val preferredId = perLanguageDefaults[normLang]
        return dicts.sortedWith(
            compareByDescending<DictionaryEntity> { it.id == preferredId }
                .thenByDescending { it.language.lowercase().substringBefore('-') == normLang }
                .thenBy { it.sortOrder }
                .thenBy { it.id }
        )
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    companion object {
        const val BUNDLED_ASSET_PATH = "dictionaries/mochi_wordnet_en.tsv"
    }
}
