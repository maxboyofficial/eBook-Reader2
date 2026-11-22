package com.example.data.dictionary

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

data class ParsedDictionaryPack(
    val name: String,
    val language: String,
    val format: String,
    val entries: List<DictionaryRawEntry>
)

object DictionaryImporters {

    private const val MAX_IMPORT_ENTRIES = 60_000
    private val DICTD_B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun parseTsvAsset(input: InputStream): List<DictionaryRawEntry> {
        val entries = mutableListOf<DictionaryRawEntry>()
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEachIndexed { idx, rawLine ->
                val line = rawLine.trimEnd('\r')
                if (line.isBlank()) return@forEachIndexed
                if (idx == 0 && line.lowercase().startsWith("word\t")) return@forEachIndexed
                val cols = line.split('\t')
                if (cols.size >= 6) {
                    entries.add(
                        DictionaryRawEntry(
                            word = cols[0],
                            lemma = cols[1],
                            pos = cols[2],
                            phonetic = cols[3],
                            definition = cols[4],
                            example = cols[5]
                        )
                    )
                } else if (cols.size >= 2) {
                    entries.add(
                        DictionaryRawEntry(
                            word = cols[0],
                            lemma = cols[0],
                            pos = cols.getOrNull(2).orEmpty(),
                            phonetic = "",
                            definition = cols[1],
                            example = cols.getOrNull(3).orEmpty()
                        )
                    )
                }
            }
        }
        return entries
    }

    /**
     * Parses a StarDict (.ifo + .idx + .dict/.dict.dz) dictionary directly from byte payloads.
     */
    fun parseStarDict(
        ifoBytes: ByteArray,
        idxBytes: ByteArray,
        dictPayloadBytes: ByteArray,
        isDictGzipped: Boolean,
        fallbackName: String = "StarDict Dictionary",
        languageHint: String = "en"
    ): ParsedDictionaryPack {
        val ifoMeta = parseStarDictIfo(ifoBytes.toString(Charsets.UTF_8))
        val name = ifoMeta["bookname"]?.takeIf { it.isNotBlank() } ?: fallbackName
        val idxOffsetBits = ifoMeta["idxoffsetbits"]?.toIntOrNull() ?: 32
        val sameTypeSeq = ifoMeta["sametypesequence"].orEmpty()

        val rawDict = if (isDictGzipped || isGzipBytes(dictPayloadBytes)) {
            decompressGzip(dictPayloadBytes)
        } else {
            dictPayloadBytes
        }

        val entries = mutableListOf<DictionaryRawEntry>()
        var cursor = 0
        while (cursor < idxBytes.size && entries.size < MAX_IMPORT_ENTRIES) {
            var nullPos = cursor
            while (nullPos < idxBytes.size && idxBytes[nullPos] != 0.toByte()) {
                nullPos++
            }
            if (nullPos >= idxBytes.size) break
            val headword = String(idxBytes, cursor, nullPos - cursor, Charsets.UTF_8).trim()
            cursor = nullPos + 1

            val offsetByteLen = if (idxOffsetBits == 64) 8 else 4
            if (cursor + offsetByteLen + 4 > idxBytes.size) break

            val buf = ByteBuffer.wrap(idxBytes, cursor, offsetByteLen + 4).order(ByteOrder.BIG_ENDIAN)
            val dataOffset = if (offsetByteLen == 8) buf.long else (buf.int.toLong() and 0xFFFFFFFFL)
            val dataSize = (buf.int.toLong() and 0xFFFFFFFFL).toInt()
            cursor += offsetByteLen + 4

            if (headword.isBlank() || dataOffset < 0 || dataSize <= 0) continue
            val start = dataOffset.toInt()
            val end = (start + dataSize).coerceAtMost(rawDict.size)
            if (start >= rawDict.size || start >= end) continue

            val defRaw = String(rawDict, start, end - start, Charsets.UTF_8)
            val cleanedDef = cleanStarDictBody(defRaw, sameTypeSeq)
            if (cleanedDef.isNotBlank()) {
                entries.add(
                    DictionaryRawEntry(
                        word = headword,
                        lemma = headword.lowercase(),
                        pos = "",
                        phonetic = "",
                        definition = cleanedDef,
                        example = ""
                    )
                )
            }
        }

        return ParsedDictionaryPack(
            name = name,
            language = detectLanguageFromName(name, languageHint),
            format = "STARDICT",
            entries = entries
        )
    }

    /**
     * Parses a dictd (.index + .dict/.dict.dz) dictionary from byte payloads.
     */
    fun parseDictd(
        indexBytes: ByteArray,
        dictPayloadBytes: ByteArray,
        isDictGzipped: Boolean,
        fallbackName: String = "dictd Dictionary",
        languageHint: String = "en"
    ): ParsedDictionaryPack {
        val rawDict = if (isDictGzipped || isGzipBytes(dictPayloadBytes)) {
            decompressGzip(dictPayloadBytes)
        } else {
            dictPayloadBytes
        }

        val entries = mutableListOf<DictionaryRawEntry>()
        val lines = indexBytes.toString(Charsets.UTF_8).lineSequence()
        for (line in lines) {
            if (entries.size >= MAX_IMPORT_ENTRIES) break
            if (line.isBlank()) continue
            val parts = line.split('\t')
            if (parts.size < 3) continue
            val headword = parts[0].trim()
            if (headword.isBlank() || headword.startsWith("00-database-")) continue
            val offset = decodeDictdBase64(parts[1].trim()).toInt()
            val len = decodeDictdBase64(parts[2].trim()).toInt()
            if (offset < 0 || len <= 0 || offset >= rawDict.size) continue
            val end = (offset + len).coerceAtMost(rawDict.size)
            val defText = String(rawDict, offset, end - offset, Charsets.UTF_8)
                .replace("\r\n", "\n")
                .trim()
            if (defText.isNotBlank()) {
                entries.add(
                    DictionaryRawEntry(
                        word = headword,
                        lemma = headword.lowercase(),
                        pos = "",
                        phonetic = "",
                        definition = defText,
                        example = ""
                    )
                )
            }
        }

        return ParsedDictionaryPack(
            name = fallbackName,
            language = detectLanguageFromName(fallbackName, languageHint),
            format = "DICTD",
            entries = entries
        )
    }

    /**
     * Parses Wiktionary/Kaikki JSONL or generic TSV/CSV word packs.
     */
    fun parseWiktionaryOrTsv(
        textStream: InputStream,
        fileName: String,
        languageHint: String = "en"
    ): ParsedDictionaryPack {
        val lower = fileName.lowercase()
        val rawStream: InputStream = if (lower.endsWith(".gz")) {
            GZIPInputStream(BufferedInputStream(textStream))
        } else {
            BufferedInputStream(textStream)
        }
        val cleanName = fileName
            .removeSuffix(".gz")
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .ifBlank { "Wiktionary Pack" }

        val entries = mutableListOf<DictionaryRawEntry>()
        var detectedLang = languageHint

        rawStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (rawLine in lines) {
                if (entries.size >= MAX_IMPORT_ENTRIES) break
                val line = rawLine.trim()
                if (line.isBlank()) continue

                if (line.startsWith("{")) {
                    // Wiktionary / Kaikki JSONL entry
                    runCatching {
                        val obj = JSONObject(line)
                        val word = obj.optString("word").trim()
                        val pos = obj.optString("pos").trim()
                        val langCode = obj.optString("lang_code").trim()
                        if (langCode.isNotBlank()) detectedLang = langCode
                        val phonetic = buildString {
                            val sounds = obj.optJSONArray("sounds")
                            if (sounds != null) {
                                for (i in 0 until sounds.length()) {
                                    val ipa = sounds.optJSONObject(i)?.optString("ipa").orEmpty()
                                    if (ipa.isNotBlank()) {
                                        append(ipa)
                                        break
                                    }
                                }
                            }
                        }
                        val senses = obj.optJSONArray("senses")
                        if (word.isNotBlank() && senses != null) {
                            for (i in 0 until senses.length()) {
                                val sense = senses.optJSONObject(i) ?: continue
                                val glosses = sense.optJSONArray("glosses")
                                val def = if (glosses != null && glosses.length() > 0) {
                                    (0 until glosses.length()).joinToString("; ") { glosses.optString(it) }
                                } else {
                                    sense.optString("raw_glosses").trim()
                                }
                                if (def.isBlank()) continue
                                val examples = sense.optJSONArray("examples")
                                val exText = if (examples != null && examples.length() > 0) {
                                    examples.optJSONObject(0)?.optString("text").orEmpty()
                                } else ""
                                entries.add(
                                    DictionaryRawEntry(
                                        word = word,
                                        lemma = word.lowercase(),
                                        pos = pos,
                                        phonetic = phonetic,
                                        definition = def,
                                        example = exText
                                    )
                                )
                            }
                        } else if (word.isNotBlank() && obj.has("definition")) {
                            entries.add(
                                DictionaryRawEntry(
                                    word = word,
                                    lemma = obj.optString("lemma", word).lowercase(),
                                    pos = pos,
                                    phonetic = obj.optString("phonetic"),
                                    definition = obj.optString("definition"),
                                    example = obj.optString("example")
                                )
                            )
                        }
                    }
                } else {
                    // TSV or CSV row
                    val delimiter = if ('\t' in line) '\t' else ','
                    val cols = line.split(delimiter)
                    if (cols.size >= 2) {
                        val first = cols[0].trim().trim('"')
                        if (entries.isEmpty() && first.equals("word", ignoreCase = true)) continue
                        val second = cols[1].trim().trim('"')
                        if (cols.size >= 5) {
                            entries.add(
                                DictionaryRawEntry(
                                    word = first,
                                    lemma = cols[1].trim().ifBlank { first }.lowercase(),
                                    pos = cols[2].trim(),
                                    phonetic = cols[3].trim(),
                                    definition = cols[4].trim(),
                                    example = cols.getOrNull(5)?.trim().orEmpty()
                                )
                            )
                        } else {
                            entries.add(
                                DictionaryRawEntry(
                                    word = first,
                                    lemma = first.lowercase(),
                                    pos = cols.getOrNull(2)?.trim().orEmpty(),
                                    phonetic = "",
                                    definition = second,
                                    example = cols.getOrNull(3)?.trim().orEmpty()
                                )
                            )
                        }
                    }
                }
            }
        }

        return ParsedDictionaryPack(
            name = cleanName,
            language = detectLanguageFromName(cleanName, detectedLang),
            format = "WIKTIONARY",
            entries = entries
        )
    }

    fun importFromSafTree(context: Context, treeUri: Uri): Result<ParsedDictionaryPack> = runCatching {
        val dir = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Could not access selected dictionary folder")
        val files = dir.listFiles().filter { it.isFile }
        val namedBytes = mutableMapOf<String, ByteArray>()
        files.forEach { df ->
            val name = df.name ?: return@forEach
            val lower = name.lowercase()
            if (lower.endsWith(".ifo") || lower.endsWith(".idx") ||
                lower.endsWith(".index") || lower.endsWith(".dict") ||
                lower.endsWith(".dict.dz") || lower.endsWith(".jsonl") ||
                lower.endsWith(".jsonl.gz") || lower.endsWith(".tsv") ||
                lower.endsWith(".tsv.gz") || lower.endsWith(".csv")
            ) {
                context.contentResolver.openInputStream(df.uri)?.use { stream ->
                    namedBytes[name] = stream.readBytes()
                }
            }
        }
        detectAndParseFileMap(namedBytes, fallbackName = dir.name ?: "Imported Dictionary")
    }

    fun importFromSafFile(context: Context, fileUri: Uri, displayName: String): Result<ParsedDictionaryPack> = runCatching {
        val lower = displayName.lowercase()
        if (lower.endsWith(".zip")) {
            val fileMap = mutableMapOf<String, ByteArray>()
            context.contentResolver.openInputStream(fileUri)?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val baseName = entry.name.substringAfterLast('/')
                            fileMap[baseName] = zip.readBytes()
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Unable to open ZIP dictionary archive")
            return@runCatching detectAndParseFileMap(fileMap, displayName.substringBeforeLast('.'))
        }

        context.contentResolver.openInputStream(fileUri)?.use { input ->
            return@runCatching parseWiktionaryOrTsv(input, displayName)
        } ?: error("Unable to read selected dictionary file")
    }

    fun detectAndParseFileMap(
        files: Map<String, ByteArray>,
        fallbackName: String
    ): ParsedDictionaryPack {
        val ifoEntry = files.entries.firstOrNull { it.key.lowercase().endsWith(".ifo") }
        val idxEntry = files.entries.firstOrNull { it.key.lowercase().endsWith(".idx") }
        val indexEntry = files.entries.firstOrNull { it.key.lowercase().endsWith(".index") }
        val dictDzEntry = files.entries.firstOrNull { it.key.lowercase().endsWith(".dict.dz") }
        val dictRawEntry = files.entries.firstOrNull { it.key.lowercase().endsWith(".dict") }
        val dictEntry = dictDzEntry ?: dictRawEntry

        if (ifoEntry != null && idxEntry != null && dictEntry != null) {
            return parseStarDict(
                ifoBytes = ifoEntry.value,
                idxBytes = idxEntry.value,
                dictPayloadBytes = dictEntry.value,
                isDictGzipped = dictEntry.key.lowercase().endsWith(".dz"),
                fallbackName = fallbackName
            )
        }
        if (indexEntry != null && dictEntry != null) {
            val dictName = indexEntry.key.substringBeforeLast('.').ifBlank { fallbackName }
            return parseDictd(
                indexBytes = indexEntry.value,
                dictPayloadBytes = dictEntry.value,
                isDictGzipped = dictEntry.key.lowercase().endsWith(".dz"),
                fallbackName = dictName
            )
        }
        val textPack = files.entries.firstOrNull {
            val l = it.key.lowercase()
            l.endsWith(".jsonl") || l.endsWith(".jsonl.gz") || l.endsWith(".tsv") ||
                l.endsWith(".tsv.gz") || l.endsWith(".csv")
        }
        if (textPack != null) {
            return parseWiktionaryOrTsv(ByteArrayInputStream(textPack.value), textPack.key)
        }
        error("No supported StarDict (.ifo/.idx/.dict), dictd (.index/.dict), or Wiktionary (.jsonl/.tsv) dictionary found.")
    }

    internal fun decodeDictdBase64(token: String): Long {
        var result = 0L
        for (ch in token) {
            val idx = DICTD_B64.indexOf(ch)
            if (idx < 0) break
            result = (result shl 6) or idx.toLong()
        }
        return result
    }

    private fun parseStarDictIfo(ifoText: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        ifoText.lineSequence().forEach { line ->
            val eq = line.indexOf('=')
            if (eq > 0) {
                val key = line.substring(0, eq).trim().lowercase()
                val value = line.substring(eq + 1).trim()
                map[key] = value
            }
        }
        return map
    }

    private fun cleanStarDictBody(raw: String, sameTypeSequence: String): String {
        val noTags = if ('h' in sameTypeSequence || 'x' in sameTypeSequence || "<" in raw) {
            raw.replace(Regex("<[^>]+>"), " ")
        } else raw
        return noTags.replace('\u0000', '\n')
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun isGzipBytes(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()

    private fun decompressGzip(bytes: ByteArray): ByteArray {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { gz ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var n = gz.read(buf)
            while (n > 0) {
                out.write(buf, 0, n)
                n = gz.read(buf)
            }
            return out.toByteArray()
        }
    }

    private fun detectLanguageFromName(name: String, fallback: String): String {
        val lower = name.lowercase()
        return when {
            "japanese" in lower || "jmdict" in lower -> "ja"
            "spanish" in lower || "español" in lower -> "es"
            "french" in lower || "français" in lower -> "fr"
            "german" in lower || "deutsch" in lower -> "de"
            "italian" in lower -> "it"
            "chinese" in lower -> "zh"
            else -> fallback.ifBlank { "en" }
        }
    }
}
