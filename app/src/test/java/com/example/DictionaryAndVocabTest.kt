package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.MochiDatabase
import com.example.data.dictionary.DictionaryImporters
import com.example.data.dictionary.DictionaryRawEntry
import com.example.data.dictionary.DictionaryRepository
import com.example.data.dictionary.DictionarySqlStore
import com.example.data.dictionary.ParsedDictionaryPack
import com.example.data.dictionary.SrsGrade
import com.example.data.dictionary.SrsSm2Calculator
import com.example.data.dictionary.VocabularyExporter
import com.example.data.dictionary.VocabularyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DictionaryAndVocabTest {

    private lateinit var context: Context
    private lateinit var db: MochiDatabase
    private lateinit var sqlStore: DictionarySqlStore
    private lateinit var dictionaryRepo: DictionaryRepository
    private lateinit var vocabularyRepo: VocabularyRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("mochi_dictionary_fts.db")
        db = Room.inMemoryDatabaseBuilder(context, MochiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sqlStore = DictionarySqlStore(context)
        dictionaryRepo = DictionaryRepository(
            context = context,
            dictionaryDao = db.dictionaryDao(),
            sqlStore = sqlStore
        )
        vocabularyRepo = VocabularyRepository(db.vocabularyDao())
    }

    @After
    fun tearDown() {
        sqlStore.close()
        db.close()
        context.deleteDatabase("mochi_dictionary_fts.db")
    }

    @Test
    fun bundledDictionaryResolvesLemmasPhrasesAndDidYouMeanSuggestions() = runTest {
        dictionaryRepo.ensureBundledDictionary()
        val dicts = dictionaryRepo.observeDictionaries().first()
        assertTrue(dicts.isNotEmpty())
        assertTrue(dicts.first().isBundled)
        assertTrue(dicts.first().entryCount >= 400)

        // Inflected words -> lemmas
        val running = dictionaryRepo.lookup("running")
        assertEquals("run", running.primaryEntry?.word?.lowercase())
        assertEquals("run", running.resolvedLemma?.lowercase())

        val burned = dictionaryRepo.lookup("burned")
        assertEquals("burn", burned.primaryEntry?.word?.lowercase())

        val pennies = dictionaryRepo.lookup("pennies")
        assertEquals("penny", pennies.primaryEntry?.word?.lowercase())

        val softened = dictionaryRepo.lookup("softened")
        assertEquals("soften", softened.primaryEntry?.word?.lowercase())

        // Phrase & idiom lookup from surrounding sentence
        val inBloom = dictionaryRepo.lookup(
            rawQuery = "bloom",
            contextSentence = "A single camellia tree stood in bloom beside the basin."
        )
        assertTrue(inBloom.phraseMatches.flatMap { it.entries }.any { it.word.equals("in bloom", ignoreCase = true) })

        val makeUp = dictionaryRepo.lookup(
            rawQuery = "make",
            contextSentence = "Sobs, sniffles, and smiles make up life."
        )
        assertTrue(makeUp.phraseMatches.flatMap { it.entries }.any { it.word.equals("make up", ignoreCase = true) })

        // Fuzzy "did you mean" suggestion
        val typo = dictionaryRepo.lookup("parsmony")
        assertTrue(typo.groups.isEmpty())
        assertTrue("Expected parsimony in ${typo.suggestions}", typo.suggestions.any { it.equals("parsimony", ignoreCase = true) })
    }

    @Test
    fun starDictDictdAndWiktionaryImportersParsePayloadsAccurately() {
        // 1. StarDict (.ifo + binary .idx + gzipped .dict.dz)
        val body1 = "An offline cozy book reader."
        val body2 = "A soft rice confection."
        val rawDictBytes = (body1 + body2).toByteArray(Charsets.UTF_8)
        val body1Len = body1.toByteArray(Charsets.UTF_8).size
        val body2Len = body2.toByteArray(Charsets.UTF_8).size

        val idxOut = ByteArrayOutputStream()
        idxOut.write("reader".toByteArray(Charsets.UTF_8))
        idxOut.write(0)
        idxOut.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(0).putInt(body1Len).array())
        idxOut.write("mochi".toByteArray(Charsets.UTF_8))
        idxOut.write(0)
        idxOut.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(body1Len).putInt(body2Len).array())

        val gzOut = ByteArrayOutputStream()
        GZIPOutputStream(gzOut).use { it.write(rawDictBytes) }

        val ifoBytes = """
            StarDict's dict ifo file
            version=2.4.2
            bookname=Cozy StarDict Pack
            wordcount=2
            idxoffsetbits=32
            sametypesequence=m
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val starPack = DictionaryImporters.parseStarDict(
            ifoBytes = ifoBytes,
            idxBytes = idxOut.toByteArray(),
            dictPayloadBytes = gzOut.toByteArray(),
            isDictGzipped = true
        )
        assertEquals("Cozy StarDict Pack", starPack.name)
        assertEquals("STARDICT", starPack.format)
        assertEquals(2, starPack.entries.size)
        assertEquals("An offline cozy book reader.", starPack.entries[0].definition)
        assertEquals("A soft rice confection.", starPack.entries[1].definition)

        // 2. dictd (.index base64 + gzipped .dict.dz)
        // Base64 in dictd: 'A' = 0, 'b' = 27
        val dictdBody = "To read calmly at night."
        val dictdBytes = dictdBody.toByteArray(Charsets.UTF_8)
        val dictdGz = ByteArrayOutputStream()
        GZIPOutputStream(dictdGz).use { it.write(dictdBytes) }
        val encodedLen = encodeDictdBase64ForTest(dictdBytes.size.toLong())
        val indexBytes = "nightread\tA\t$encodedLen\n".toByteArray(Charsets.UTF_8)

        val dictdPack = DictionaryImporters.parseDictd(
            indexBytes = indexBytes,
            dictPayloadBytes = dictdGz.toByteArray(),
            isDictGzipped = true,
            fallbackName = "Night dictd"
        )
        assertEquals("DICTD", dictdPack.format)
        assertEquals(1, dictdPack.entries.size)
        assertEquals("nightread", dictdPack.entries[0].word)
        assertEquals("To read calmly at night.", dictdPack.entries[0].definition)

        // 3. Wiktionary / Kaikki JSONL
        val jsonl = """
            {"word":"sakura","pos":"noun","lang_code":"ja","sounds":[{"ipa":"/sa.kɯ.ɾa/"}],"senses":[{"glosses":["Cherry blossom tree."],"examples":[{"text":"Sakura petals drifted over the path."}]}]}
        """.trimIndent()
        val wiktPack = DictionaryImporters.parseWiktionaryOrTsv(
            textStream = ByteArrayInputStream(jsonl.toByteArray(Charsets.UTF_8)),
            fileName = "japanese_wiktionary.jsonl"
        )
        assertEquals("WIKTIONARY", wiktPack.format)
        assertEquals("ja", wiktPack.language)
        assertEquals(1, wiktPack.entries.size)
        assertEquals("sakura", wiktPack.entries[0].word)
        assertEquals("/sa.kɯ.ɾa/", wiktPack.entries[0].phonetic)
        assertEquals("Cherry blossom tree.", wiktPack.entries[0].definition)
    }

    @Test
    fun multiDictionaryPriorityAndPerLanguageDefaultOrderResults() = runTest {
        dictionaryRepo.ensureBundledDictionary()
        val customEn = dictionaryRepo.importParsedPack(
            ParsedDictionaryPack(
                name = "Literary Companion",
                language = "en",
                format = "TSV",
                entries = listOf(
                    DictionaryRawEntry(
                        word = "parsimony",
                        lemma = "parsimony",
                        pos = "n.",
                        phonetic = "",
                        definition = "Literary frugality (custom priority entry).",
                        example = ""
                    )
                )
            )
        )
        val customJa = dictionaryRepo.importParsedPack(
            ParsedDictionaryPack(
                name = "Japanese Glossary",
                language = "ja",
                format = "WIKTIONARY",
                entries = listOf(
                    DictionaryRawEntry(
                        word = "komorebi",
                        lemma = "komorebi",
                        pos = "n.",
                        phonetic = "",
                        definition = "Sunlight filtering through leaves.",
                        example = ""
                    )
                )
            )
        )

        // Move customEn above bundled
        dictionaryRepo.moveOrder(customEn.id, -1)
        val afterMove = dictionaryRepo.lookup("parsimony", bookLanguage = "en")
        assertEquals(customEn.id, afterMove.groups.first().dictionary.id)

        // Per-language default overrides sort order
        val bundled = dictionaryRepo.observeDictionaries().first().first { it.isBundled }
        val overridden = dictionaryRepo.lookup(
            rawQuery = "parsimony",
            bookLanguage = "en",
            perLanguageDefaultDict = mapOf("en" to bundled.id)
        )
        assertEquals(bundled.id, overridden.groups.first().dictionary.id)

        val jaHit = dictionaryRepo.lookup(
            rawQuery = "komorebi",
            bookLanguage = "ja",
            perLanguageDefaultDict = mapOf("ja" to customJa.id)
        )
        assertEquals(customJa.id, jaHit.groups.first().dictionary.id)
    }

    @Test
    fun sm2SchedulerAndAnkiExportWorkEndToEnd() = runTest {
        val saved = vocabularyRepo.saveWord(
            word = "parsimony",
            lemma = "parsimony",
            partOfSpeech = "n.",
            definition = "Extreme unwillingness to spend money.",
            exampleSentence = "Her cheeks burned with the silent imputation of parsimony.",
            contextSentence = "Her cheeks burned with the silent imputation of parsimony.",
            bookId = null,
            bookTitle = "The Gift of the Magi"
        )
        assertEquals("NEW", saved.masteryLevel)

        // 1st Good -> 1d, rep 1
        val r1 = vocabularyRepo.recordReview(saved, SrsGrade.GOOD, nowMillis = 1_000_000L)
        assertEquals(1, r1.srsRepetitions)
        assertEquals(1, r1.srsIntervalDays)
        assertEquals("LEARNING", r1.masteryLevel)

        // 2nd Good -> 6d, rep 2, REVIEWING
        val r2 = vocabularyRepo.recordReview(r1, SrsGrade.GOOD, nowMillis = 2_000_000L)
        assertEquals(2, r2.srsRepetitions)
        assertEquals(6, r2.srsIntervalDays)
        assertEquals("REVIEWING", r2.masteryLevel)

        // 3rd Easy -> >= 21d -> MASTERED
        val r3 = vocabularyRepo.recordReview(r2, SrsGrade.EASY, nowMillis = 3_000_000L)
        assertEquals(3, r3.srsRepetitions)
        assertTrue(r3.srsIntervalDays >= 18)

        // Ensure 4th Good pushes >= 21 days -> MASTERED
        val r4 = vocabularyRepo.recordReview(r3, SrsGrade.GOOD, nowMillis = 4_000_000L)
        assertTrue(r4.srsIntervalDays >= 21)
        assertEquals("MASTERED", r4.masteryLevel)

        // Again resets repetitions to 0
        val lapsed = vocabularyRepo.recordReview(r4, SrsGrade.AGAIN, nowMillis = 5_000_000L)
        assertEquals(0, lapsed.srsRepetitions)
        assertEquals(0, lapsed.srsIntervalDays)
        assertEquals("LEARNING", lapsed.masteryLevel)
        assertEquals("10m", SrsSm2Calculator.previewLabel(r4, SrsGrade.AGAIN))

        // Anki TSV, CSV & Markdown export formatting
        val tsv = VocabularyExporter.exportAnkiTsv(listOf(r4))
        assertTrue(tsv.contains("#separator:tab"))
        assertTrue(tsv.contains("parsimony (n.)\tparsimony\t"))
        assertTrue(tsv.contains("The Gift of the Magi"))

        val csv = VocabularyExporter.exportAnkiCsv(listOf(r4))
        assertTrue(csv.contains("word,lemma,part_of_speech"))
        assertTrue(csv.contains("\"parsimony\",\"parsimony\",\"n.\""))

        val md = VocabularyExporter.exportMarkdown(listOf(r4))
        assertTrue(md.contains("## parsimony *(n.)*"))
        assertTrue(md.contains("- **Meaning:** Extreme unwillingness to spend money."))
    }

    private fun encodeDictdBase64ForTest(value: Long): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        if (value == 0L) return "A"
        var v = value
        val chars = StringBuilder()
        while (v > 0L) {
            chars.append(alphabet[(v and 63L).toInt()])
            v = v ushr 6
        }
        return chars.reverse().toString()
    }
}
