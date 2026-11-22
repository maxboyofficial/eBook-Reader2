package com.example.data.dictionary

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.abs

data class DictionaryRawEntry(
    val word: String,
    val lemma: String,
    val pos: String,
    val phonetic: String,
    val definition: String,
    val example: String
)

data class DictionaryStoredEntry(
    val id: Long,
    val dictId: Long,
    val word: String,
    val lemma: String,
    val pos: String,
    val phonetic: String,
    val definition: String,
    val example: String
)

class DictionarySqlStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                dict_id INTEGER NOT NULL,
                word TEXT NOT NULL,
                word_lower TEXT NOT NULL,
                lemma_lower TEXT NOT NULL,
                pos TEXT NOT NULL,
                phonetic TEXT NOT NULL,
                definition TEXT NOT NULL,
                example TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_dict ON entries(dict_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_word ON entries(word_lower)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_lemma ON entries(lemma_lower)")
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS entries_fts USING fts4(
                dict_id UNINDEXED,
                word,
                lemma,
                definition,
                tokenize=unicode61
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS entries_fts")
        db.execSQL("DROP TABLE IF EXISTS entries")
        onCreate(db)
    }

    fun countForDictionary(dictId: Long): Int {
        val db = readableDatabase
        db.rawQuery("SELECT COUNT(*) FROM entries WHERE dict_id = ?", arrayOf(dictId.toString())).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun replaceDictionaryEntries(dictId: Long, rawEntries: List<DictionaryRawEntry>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("entries", "dict_id = ?", arrayOf(dictId.toString()))
            db.delete("entries_fts", "dict_id = ?", arrayOf(dictId.toString()))

            val entryStmt = db.compileStatement(
                "INSERT INTO entries(dict_id, word, word_lower, lemma_lower, pos, phonetic, definition, example) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            )
            val ftsStmt = db.compileStatement(
                "INSERT INTO entries_fts(docid, dict_id, word, lemma, definition) VALUES (?, ?, ?, ?, ?)"
            )

            for (item in rawEntries) {
                val wordClean = item.word.trim()
                if (wordClean.isBlank() || item.definition.isBlank()) continue
                val wordLower = wordClean.lowercase()
                val lemmaLower = item.lemma.ifBlank { wordClean }.trim().lowercase()

                entryStmt.clearBindings()
                entryStmt.bindLong(1, dictId)
                entryStmt.bindString(2, wordClean)
                entryStmt.bindString(3, wordLower)
                entryStmt.bindString(4, lemmaLower)
                entryStmt.bindString(5, item.pos.trim())
                entryStmt.bindString(6, item.phonetic.trim())
                entryStmt.bindString(7, item.definition.trim())
                entryStmt.bindString(8, item.example.trim())
                val rowId = entryStmt.executeInsert()

                if (rowId > 0L) {
                    ftsStmt.clearBindings()
                    ftsStmt.bindLong(1, rowId)
                    ftsStmt.bindLong(2, dictId)
                    ftsStmt.bindString(3, wordLower)
                    ftsStmt.bindString(4, lemmaLower)
                    ftsStmt.bindString(5, item.definition.trim())
                    ftsStmt.executeInsert()
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteDictionary(dictId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("entries", "dict_id = ?", arrayOf(dictId.toString()))
            db.delete("entries_fts", "dict_id = ?", arrayOf(dictId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun findExactOrLemmaMatches(
        candidates: List<String>,
        enabledDictIds: List<Long>
    ): List<DictionaryStoredEntry> {
        if (candidates.isEmpty() || enabledDictIds.isEmpty()) return emptyList()
        val db = readableDatabase
        val candPlaceholders = candidates.joinToString(",") { "?" }
        val dictPlaceholders = enabledDictIds.joinToString(",") { "?" }
        val sql = """
            SELECT id, dict_id, word, lemma_lower, pos, phonetic, definition, example
            FROM entries
            WHERE dict_id IN ($dictPlaceholders)
              AND (word_lower IN ($candPlaceholders) OR lemma_lower IN ($candPlaceholders))
            LIMIT 48
        """.trimIndent()
        val args = buildList {
            enabledDictIds.forEach { add(it.toString()) }
            candidates.forEach { add(it.lowercase()) }
            candidates.forEach { add(it.lowercase()) }
        }.toTypedArray()

        val result = mutableListOf<DictionaryStoredEntry>()
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                result.add(
                    DictionaryStoredEntry(
                        id = c.getLong(0),
                        dictId = c.getLong(1),
                        word = c.getString(2),
                        lemma = c.getString(3),
                        pos = c.getString(4),
                        phonetic = c.getString(5),
                        definition = c.getString(6),
                        example = c.getString(7)
                    )
                )
            }
        }
        return result
    }

    fun searchFtsPrefix(prefix: String, enabledDictIds: List<Long>, limit: Int = 20): List<DictionaryStoredEntry> {
        val clean = prefix.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}\\s-]"), "")
        if (clean.length < 2 || enabledDictIds.isEmpty()) return emptyList()
        val db = readableDatabase
        val dictPlaceholders = enabledDictIds.joinToString(",") { "?" }
        val ftsQuery = clean.split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ") { "$it*" }
        val sql = """
            SELECT e.id, e.dict_id, e.word, e.lemma_lower, e.pos, e.phonetic, e.definition, e.example
            FROM entries e
            JOIN entries_fts f ON f.docid = e.id
            WHERE e.dict_id IN ($dictPlaceholders)
              AND entries_fts MATCH ?
            LIMIT ?
        """.trimIndent()
        val args = buildList {
            enabledDictIds.forEach { add(it.toString()) }
            add(ftsQuery)
            add(limit.toString())
        }.toTypedArray()

        val out = mutableListOf<DictionaryStoredEntry>()
        runCatching {
            db.rawQuery(sql, args).use { c ->
                while (c.moveToNext()) {
                    out.add(
                        DictionaryStoredEntry(
                            id = c.getLong(0),
                            dictId = c.getLong(1),
                            word = c.getString(2),
                            lemma = c.getString(3),
                            pos = c.getString(4),
                            phonetic = c.getString(5),
                            definition = c.getString(6),
                            example = c.getString(7)
                        )
                    )
                }
            }
        }
        return out
    }

    fun suggestDidYouMean(query: String, enabledDictIds: List<Long>, maxSuggestions: Int = 6): List<String> {
        val clean = EnglishLemmatizer.normalizeToken(query)
        if (clean.length < 2 || enabledDictIds.isEmpty()) return emptyList()
        val db = readableDatabase
        val dictPlaceholders = enabledDictIds.joinToString(",") { "?" }
        val firstChar = clean.take(1)
        val firstTwo = clean.take(2)
        val minLen = (clean.length - 2).coerceAtLeast(1)
        val maxLen = clean.length + 2
        val sql = """
            SELECT DISTINCT word
            FROM entries
            WHERE dict_id IN ($dictPlaceholders)
              AND (word_lower LIKE ? OR word_lower LIKE ?)
              AND LENGTH(word_lower) BETWEEN ? AND ?
            LIMIT 250
        """.trimIndent()
        val args = buildList {
            enabledDictIds.forEach { add(it.toString()) }
            add("$firstTwo%")
            add("$firstChar%")
            add(minLen.toString())
            add(maxLen.toString())
        }.toTypedArray()

        val pool = LinkedHashSet<String>()
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                pool.add(c.getString(0))
            }
        }
        // Fallback scan if first letter itself was mistyped (e.g., "qiet" / "syphon")
        if (pool.size < 20) {
            val fallbackSql = """
                SELECT DISTINCT word
                FROM entries
                WHERE dict_id IN ($dictPlaceholders)
                  AND ABS(LENGTH(word_lower) - ?) <= 2
                LIMIT 250
            """.trimIndent()
            val fallbackArgs = buildList {
                enabledDictIds.forEach { add(it.toString()) }
                add(clean.length.toString())
            }.toTypedArray()
            db.rawQuery(fallbackSql, fallbackArgs).use { c ->
                while (c.moveToNext()) {
                    pool.add(c.getString(0))
                }
            }
        }

        val maxDist = if (clean.length <= 4) 1 else 2
        return pool.mapNotNull { candidate ->
            val candLower = candidate.lowercase()
            if (candLower == clean) return@mapNotNull null
            if (abs(candLower.length - clean.length) > 2) return@mapNotNull null
            val dist = EnglishLemmatizer.damerauLevenshtein(clean, candLower)
            if (dist in 1..maxDist) candidate to dist else null
        }
            .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first.length }.thenBy { it.first })
            .map { it.first }
            .distinctBy { it.lowercase() }
            .take(maxSuggestions)
    }

    companion object {
        private const val DB_NAME = "mochi_dictionary_fts.db"
        private const val DB_VERSION = 1
    }
}
