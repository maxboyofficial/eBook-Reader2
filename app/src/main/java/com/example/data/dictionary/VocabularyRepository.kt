package com.example.data.dictionary

import android.content.Context
import android.net.Uri
import com.example.data.db.VocabularyDao
import com.example.data.db.VocabularyWordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VocabularyRepository(
    private val vocabularyDao: VocabularyDao
) {
    fun observeAll(): Flow<List<VocabularyWordEntity>> = vocabularyDao.observeAll()

    suspend fun getAllOnce(): List<VocabularyWordEntity> = vocabularyDao.getAllOnce()

    suspend fun findByWordOrLemma(term: String): VocabularyWordEntity? =
        vocabularyDao.findByWordOrLemma(EnglishLemmatizer.normalizeToken(term))

    suspend fun saveWord(
        word: String,
        lemma: String,
        partOfSpeech: String,
        definition: String,
        exampleSentence: String,
        contextSentence: String,
        bookId: Long?,
        bookTitle: String
    ): VocabularyWordEntity {
        val cleanWord = EnglishLemmatizer.normalizeToken(word).ifBlank { word.trim() }
        val cleanLemma = lemma.ifBlank { cleanWord }.trim().lowercase()
        val existing = vocabularyDao.findByWordOrLemma(cleanWord)
            ?: vocabularyDao.findByWordOrLemma(cleanLemma)
        if (existing != null) {
            val updated = existing.copy(
                definition = definition.ifBlank { existing.definition },
                partOfSpeech = partOfSpeech.ifBlank { existing.partOfSpeech },
                exampleSentence = exampleSentence.ifBlank { existing.exampleSentence },
                contextSentence = contextSentence.ifBlank { existing.contextSentence }
            )
            vocabularyDao.upsert(updated)
            return updated
        }
        val entity = VocabularyWordEntity(
            word = cleanWord,
            lemma = cleanLemma,
            partOfSpeech = partOfSpeech.trim(),
            definition = definition.trim(),
            exampleSentence = exampleSentence.trim(),
            contextSentence = contextSentence.trim(),
            bookId = bookId,
            bookTitle = bookTitle.trim(),
            nextReviewAt = System.currentTimeMillis(),
            masteryLevel = "NEW"
        )
        val id = vocabularyDao.upsert(entity)
        return entity.copy(id = id)
    }

    suspend fun recordReview(
        word: VocabularyWordEntity,
        grade: SrsGrade,
        nowMillis: Long = System.currentTimeMillis()
    ): VocabularyWordEntity {
        val update = SrsSm2Calculator.compute(word, grade, nowMillis)
        val updated = word.copy(
            srsRepetitions = update.repetitions,
            srsIntervalDays = update.intervalDays,
            srsEaseFactor = update.easeFactor,
            nextReviewAt = update.nextReviewAt,
            lastReviewedAt = nowMillis,
            masteryLevel = update.masteryLevel
        )
        vocabularyDao.upsert(updated)
        return updated
    }

    suspend fun deleteWord(id: Long) {
        vocabularyDao.deleteById(id)
    }
}

object VocabularyExporter {

    /**
     * Generates an Anki-ready TSV deck file (`#separator:tab`) with Front, Back, Context, Source Book, Tags.
     */
    fun exportAnkiTsv(words: List<VocabularyWordEntity>): String = buildString {
        appendLine("#separator:tab")
        appendLine("#html:false")
        appendLine("#tags column:6")
        words.forEach { w ->
            val front = listOfNotNull(
                w.word,
                w.partOfSpeech.takeIf { it.isNotBlank() }?.let { "($it)" }
            ).joinToString(" ")
            val back = buildString {
                append(w.definition.replace('\t', ' ').replace('\n', ' '))
                if (w.exampleSentence.isNotBlank()) {
                    append(" | Ex: ${w.exampleSentence.replace('\t', ' ').replace('\n', ' ')}")
                }
            }
            val context = w.contextSentence.replace('\t', ' ').replace('\n', ' ')
            val book = w.bookTitle.replace('\t', ' ').replace('\n', ' ')
            val tags = listOf("mochi", w.masteryLevel.lowercase(), w.partOfSpeech.lowercase().filter { it.isLetter() })
                .filter { it.isNotBlank() }
                .joinToString(" ")
            appendLine(
                listOf(
                    front.replace('\t', ' '),
                    w.lemma.replace('\t', ' '),
                    back,
                    context,
                    book,
                    tags
                ).joinToString("\t")
            )
        }
    }

    fun exportAnkiCsv(words: List<VocabularyWordEntity>): String = buildString {
        appendLine("word,lemma,part_of_speech,definition,example,context_sentence,book,mastery,repetitions,interval_days")
        words.forEach { w ->
            appendLine(
                listOf(
                    csvCell(w.word),
                    csvCell(w.lemma),
                    csvCell(w.partOfSpeech),
                    csvCell(w.definition),
                    csvCell(w.exampleSentence),
                    csvCell(w.contextSentence),
                    csvCell(w.bookTitle),
                    csvCell(w.masteryLevel),
                    w.srsRepetitions.toString(),
                    w.srsIntervalDays.toString()
                ).joinToString(",")
            )
        }
    }

    fun exportMarkdown(words: List<VocabularyWordEntity>): String = buildString {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        appendLine("---")
        appendLine("title: Mochi Vocabulary Deck")
        appendLine("exported: ${dateFmt.format(Date())}")
        appendLine("cards: ${words.size}")
        appendLine("---")
        appendLine()
        appendLine("# Vocabulary Deck")
        appendLine()
        words.forEach { w ->
            append("## ${w.word}")
            if (w.partOfSpeech.isNotBlank()) append(" *(${w.partOfSpeech})*")
            appendLine()
            if (w.lemma.isNotBlank() && w.lemma != w.word.lowercase()) {
                appendLine("- **Base form:** ${w.lemma}")
            }
            appendLine("- **Meaning:** ${w.definition}")
            if (w.exampleSentence.isNotBlank()) appendLine("- **Example:** *${w.exampleSentence}*")
            if (w.contextSentence.isNotBlank()) appendLine("> ${w.contextSentence}")
            if (w.bookTitle.isNotBlank()) appendLine("- **Source:** ${w.bookTitle}")
            appendLine("- **SRS:** ${w.masteryLevel} (${w.srsIntervalDays}d interval)")
            appendLine()
        }
    }

    suspend fun writeToSafUri(context: Context, uri: Uri, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                } ?: error("Could not open export file")
                Unit
            }
        }

    private fun csvCell(raw: String): String {
        val escaped = raw.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
