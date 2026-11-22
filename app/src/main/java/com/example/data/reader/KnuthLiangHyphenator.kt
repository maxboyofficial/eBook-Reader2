package com.example.data.reader

/**
 * Knuth-Liang pattern-based hyphenation engine with a curated offline English pattern table.
 * Inserts Unicode soft hyphens (\u00AD) at valid syllable boundaries so Compose Text wraps
 * justified or narrow-column prose cleanly without harsh river gaps.
 */
object KnuthLiangHyphenator {

    private const val SOFT_HYPHEN = '\u00AD'

    // Curated subset of standard TeX English Liang patterns (format: digits before characters)
    private val rawPatterns = listOf(
        ".ach4", ".ad4der", ".af1t", ".al3t", ".am5at", ".an5c", ".ang4", ".ani5m", ".ant4",
        ".an3te", ".anti5s", ".ar5s", ".ar4ti", ".as3c", ".as1p", ".at5h", ".be5ra", ".bi5d",
        ".bi2t", ".co4n", ".de4c", ".de5s", ".di5s", ".dis5c", ".en5c", ".ex5p", ".for5e",
        ".in5s", ".inter5", ".mis5", ".non5", ".over5", ".per5", ".pre5", ".pro5", ".re5c",
        ".re5s", ".sub5", ".super5", ".trans5", ".un5", ".under5",
        "4tion.", "3tion", "4sion.", "3sion", "4ment.", "3ment", "4ness.", "3ness",
        "4able.", "3able", "4ible.", "3ible", "4ing.", "3ing", "4ity.", "3ity",
        "4ous.", "3ous", "4ful.", "3ful", "4less.", "3less", "4ly.", "3ize", "3ise",
        "3age", "3ate", "3ent", "3ant", "3ance", "3ence", "3ery", "3ory", "3ary",
        "1ba", "1be", "1bi", "1bo", "1bu", "1by",
        "1ca", "1ce", "1ci", "1co", "1cu", "1cy",
        "1da", "1de", "1di", "1do", "1du", "1dy",
        "1fa", "1fe", "1fi", "1fo", "1fu", "1fy",
        "1ga", "1ge", "1gi", "1go", "1gu",
        "1ha", "1he", "1hi", "1ho", "1hu",
        "1ja", "1je", "1ji", "1jo", "1ju",
        "1ka", "1ke", "1ki", "1ko", "1ku",
        "1la", "1le", "1li", "1lo", "1lu", "1ly",
        "1ma", "1me", "1mi", "1mo", "1mu", "1my",
        "1na", "1ne", "1ni", "1no", "1nu",
        "1pa", "1pe", "1pi", "1po", "1pu",
        "1ra", "1re", "1ri", "1ro", "1ru",
        "1sa", "1se", "1si", "1so", "1su",
        "1ta", "1te", "1ti", "1to", "1tu", "1ty",
        "1va", "1ve", "1vi", "1vo", "1vu",
        "1wa", "1we", "1wi", "1wo", "1za", "1ze", "1zi", "1zo",
        "2ch", "2sh", "2th", "2ph", "2wh", "2gh", "2ck", "2ng", "2qu",
        "2st", "2tr", "2pr", "2br", "2cr", "2dr", "2fr", "2gr", "2bl", "2cl", "2fl", "2gl", "2pl",
        "a1e", "a1i", "a1o", "a1u", "e1a", "e1o", "i1a", "i1o", "o1a", "o1e", "u1a", "u1e",
        "2b1l", "2c1t", "2d1m", "2f1t", "2g1n", "2l1d", "2l1f", "2l1k", "2l1m", "2l1p",
        "2l1t", "2m1b", "2m1p", "2n1d", "2n1k", "2n1t", "2p1t", "2r1b", "2r1c", "2r1d",
        "2r1f", "2r1g", "2r1k", "2r1l", "2r1m", "2r1n", "2r1p", "2r1s", "2r1t", "2s1k",
        "2s1p", "2s1t", "3c2i", "3t2i", "3s2i", "4ed.", "4es.", "4er."
    )

    private val patternMap: Map<String, IntArray> by lazy {
        buildMap {
            for (pat in rawPatterns) {
                val letters = StringBuilder()
                val values = mutableListOf<Int>()
                var pendingDigit = 0
                for (ch in pat) {
                    if (ch.isDigit()) {
                        pendingDigit = ch - '0'
                    } else {
                        values.add(pendingDigit)
                        pendingDigit = 0
                        letters.append(ch)
                    }
                }
                values.add(pendingDigit)
                put(letters.toString(), values.toIntArray())
            }
        }
    }

    fun hyphenateWord(word: String): String {
        if (word.length < 5 || !word.all { it.isLetter() }) return word
        val padded = ".${word.lowercase()}."
        val scores = IntArray(padded.length + 1)

        for (i in padded.indices) {
            for (j in i + 1..padded.length) {
                val sub = padded.substring(i, j)
                val weights = patternMap[sub] ?: continue
                for (k in weights.indices) {
                    if (weights[k] > scores[i + k]) {
                        scores[i + k] = weights[k]
                    }
                }
            }
        }

        // Valid break points have odd scores, keeping at least 2 chars before and 3 chars after
        val out = StringBuilder(word.length + 4)
        for (idx in word.indices) {
            out.append(word[idx])
            val scoreIdx = idx + 2 // offset for leading '.' and next char boundary
            val remaining = word.length - (idx + 1)
            if (idx >= 1 && remaining >= 3 && scoreIdx < scores.size && scores[scoreIdx] % 2 == 1) {
                out.append(SOFT_HYPHEN)
            }
        }
        return out.toString()
    }

    fun hyphenateText(text: String): String {
        if (text.length < 5) return text
        val result = StringBuilder(text.length + 16)
        val wordBuf = StringBuilder()
        for (ch in text) {
            if (ch.isLetter()) {
                wordBuf.append(ch)
            } else {
                if (wordBuf.isNotEmpty()) {
                    result.append(hyphenateWord(wordBuf.toString()))
                    wordBuf.clear()
                }
                result.append(ch)
            }
        }
        if (wordBuf.isNotEmpty()) {
            result.append(hyphenateWord(wordBuf.toString()))
        }
        return result.toString()
    }
}
