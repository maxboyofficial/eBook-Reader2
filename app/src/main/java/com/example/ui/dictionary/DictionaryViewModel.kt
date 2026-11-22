package com.example.ui.dictionary

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MochiApplication
import com.example.data.db.DictionaryEntity
import com.example.data.db.VocabularyWordEntity
import com.example.data.dictionary.DictionaryLookupResult
import com.example.data.dictionary.SrsGrade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DictionaryHubState(
    val dictionaries: List<DictionaryEntity> = emptyList(),
    val vocabulary: List<VocabularyWordEntity> = emptyList(),
    val dueVocabulary: List<VocabularyWordEntity> = emptyList(),
    val isImporting: Boolean = false
)

class DictionaryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MochiApplication).container
    private val dictionaryRepo = container.dictionaryRepository
    private val vocabularyRepo = container.vocabularyRepository

    private val isImportingFlow = MutableStateFlow(false)

    val uiState: StateFlow<DictionaryHubState> = combine(
        dictionaryRepo.observeDictionaries(),
        vocabularyRepo.observeAll(),
        isImportingFlow
    ) { dicts, vocab, importing ->
        val now = System.currentTimeMillis()
        DictionaryHubState(
            dictionaries = dicts,
            vocabulary = vocab,
            dueVocabulary = vocab.filter { it.nextReviewAt <= now },
            isImporting = importing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DictionaryHubState()
    )

    private val _activeLookup = MutableStateFlow<DictionaryLookupResult?>(null)
    val activeLookup: StateFlow<DictionaryLookupResult?> = _activeLookup.asStateFlow()

    suspend fun lookupWord(
        query: String,
        contextSentence: String = "",
        bookLanguage: String = "en",
        perLanguageDefaults: Map<String, Long> = emptyMap()
    ): DictionaryLookupResult {
        val result = dictionaryRepo.lookup(
            rawQuery = query,
            contextSentence = contextSentence,
            bookLanguage = bookLanguage,
            perLanguageDefaultDict = perLanguageDefaults
        )
        _activeLookup.value = result
        return result
    }

    fun clearLookup() {
        _activeLookup.value = null
    }

    fun toggleDictionaryEnabled(dict: DictionaryEntity, enabled: Boolean) = viewModelScope.launch {
        dictionaryRepo.toggleEnabled(dict, enabled)
    }

    fun moveDictionaryOrder(dictId: Long, delta: Int) = viewModelScope.launch {
        dictionaryRepo.moveOrder(dictId, delta)
    }

    fun updateDictionaryLanguage(dict: DictionaryEntity, language: String) = viewModelScope.launch {
        dictionaryRepo.updateLanguage(dict, language)
    }

    fun deleteCustomDictionary(dictId: Long) = viewModelScope.launch {
        dictionaryRepo.deleteCustomDictionary(dictId)
    }

    fun importFromSafTree(uri: Uri, onDone: (String) -> Unit) = viewModelScope.launch {
        isImportingFlow.value = true
        val res = dictionaryRepo.importFromSafTree(uri)
        isImportingFlow.value = false
        res.fold(
            onSuccess = { d -> onDone("Imported \"${d.name}\" (${d.entryCount} entries)") },
            onFailure = { e -> onDone(e.localizedMessage ?: "Dictionary import failed") }
        )
    }

    fun importFromSafFile(uri: Uri, onDone: (String) -> Unit) = viewModelScope.launch {
        isImportingFlow.value = true
        val res = dictionaryRepo.importFromSafFile(uri)
        isImportingFlow.value = false
        res.fold(
            onSuccess = { d -> onDone("Imported \"${d.name}\" (${d.entryCount} entries)") },
            onFailure = { e -> onDone(e.localizedMessage ?: "Dictionary import failed") }
        )
    }

    fun saveVocabularyWord(
        word: String,
        lemma: String,
        partOfSpeech: String,
        definition: String,
        exampleSentence: String,
        contextSentence: String,
        bookId: Long?,
        bookTitle: String,
        onSaved: (VocabularyWordEntity) -> Unit = {}
    ) = viewModelScope.launch {
        val saved = vocabularyRepo.saveWord(
            word = word,
            lemma = lemma,
            partOfSpeech = partOfSpeech,
            definition = definition,
            exampleSentence = exampleSentence,
            contextSentence = contextSentence,
            bookId = bookId,
            bookTitle = bookTitle
        )
        onSaved(saved)
    }

    fun recordSrsReview(word: VocabularyWordEntity, grade: SrsGrade) = viewModelScope.launch {
        vocabularyRepo.recordReview(word, grade)
    }

    fun deleteVocabularyWord(id: Long) = viewModelScope.launch {
        vocabularyRepo.deleteWord(id)
    }
}
