package com.example.data.dictionary

import kotlin.math.min

object EnglishLemmatizer {

    private val irregulars: Map<String, List<String>> = mapOf(
        "am" to listOf("be"),
        "is" to listOf("be"),
        "are" to listOf("be"),
        "was" to listOf("be"),
        "were" to listOf("be"),
        "been" to listOf("be"),
        "being" to listOf("be"),
        "has" to listOf("have"),
        "had" to listOf("have"),
        "having" to listOf("have"),
        "does" to listOf("do"),
        "did" to listOf("do"),
        "done" to listOf("do"),
        "doing" to listOf("do"),
        "goes" to listOf("go"),
        "went" to listOf("go"),
        "gone" to listOf("go"),
        "going" to listOf("go"),
        "ran" to listOf("run"),
        "running" to listOf("run"),
        "runs" to listOf("run"),
        "saw" to listOf("see"),
        "seen" to listOf("see"),
        "seeing" to listOf("see"),
        "came" to listOf("come"),
        "coming" to listOf("come"),
        "took" to listOf("take"),
        "taken" to listOf("take"),
        "taking" to listOf("take"),
        "gave" to listOf("give"),
        "given" to listOf("give"),
        "giving" to listOf("give"),
        "made" to listOf("make"),
        "making" to listOf("make"),
        "said" to listOf("say"),
        "saying" to listOf("say"),
        "told" to listOf("tell"),
        "telling" to listOf("tell"),
        "sold" to listOf("sell"),
        "selling" to listOf("sell"),
        "bought" to listOf("buy"),
        "buying" to listOf("buy"),
        "brought" to listOf("bring"),
        "bringing" to listOf("bring"),
        "thought" to listOf("think"),
        "thinking" to listOf("think"),
        "caught" to listOf("catch"),
        "catching" to listOf("catch"),
        "taught" to listOf("teach"),
        "teaching" to listOf("teach"),
        "sought" to listOf("seek"),
        "seeking" to listOf("seek"),
        "found" to listOf("find"),
        "finding" to listOf("find"),
        "held" to listOf("hold"),
        "holding" to listOf("hold"),
        "stood" to listOf("stand"),
        "standing" to listOf("stand"),
        "understood" to listOf("understand"),
        "understanding" to listOf("understand"),
        "wrote" to listOf("write"),
        "written" to listOf("write"),
        "writing" to listOf("write"),
        "spoke" to listOf("speak"),
        "spoken" to listOf("speak"),
        "speaking" to listOf("speak"),
        "broke" to listOf("break"),
        "broken" to listOf("break"),
        "breaking" to listOf("break"),
        "chose" to listOf("choose"),
        "chosen" to listOf("choose"),
        "choosing" to listOf("choose"),
        "fell" to listOf("fall"),
        "fallen" to listOf("fall"),
        "falling" to listOf("fall"),
        "felt" to listOf("feel"),
        "feeling" to listOf("feel"),
        "kept" to listOf("keep"),
        "keeping" to listOf("keep"),
        "slept" to listOf("sleep"),
        "sleeping" to listOf("sleep"),
        "built" to listOf("build"),
        "building" to listOf("build"),
        "knew" to listOf("know"),
        "known" to listOf("know"),
        "knowing" to listOf("know"),
        "grew" to listOf("grow"),
        "grown" to listOf("grow"),
        "growing" to listOf("grow"),
        "drew" to listOf("draw"),
        "drawn" to listOf("draw"),
        "drawing" to listOf("draw"),
        "laid" to listOf("lay"),
        "laying" to listOf("lay"),
        "led" to listOf("lead"),
        "leading" to listOf("lead"),
        "sat" to listOf("sit"),
        "sitting" to listOf("sit"),
        "met" to listOf("meet"),
        "meeting" to listOf("meet"),
        "lost" to listOf("lose"),
        "losing" to listOf("lose"),
        "heard" to listOf("hear"),
        "hearing" to listOf("hear"),
        "burnt" to listOf("burn"),
        "burned" to listOf("burn"),
        "burning" to listOf("burn"),
        "dreamt" to listOf("dream"),
        "dreamed" to listOf("dream"),
        "spelt" to listOf("spell"),
        "spelled" to listOf("spell"),
        "shone" to listOf("shine"),
        "shining" to listOf("shine"),
        "slipped" to listOf("slip"),
        "slipping" to listOf("slip"),
        "flopped" to listOf("flop"),
        "flopping" to listOf("flop"),
        "children" to listOf("child"),
        "mice" to listOf("mouse"),
        "men" to listOf("man"),
        "women" to listOf("woman"),
        "feet" to listOf("foot"),
        "teeth" to listOf("tooth"),
        "people" to listOf("people", "person"),
        "leaves" to listOf("leaf", "leave"),
        "pennies" to listOf("penny"),
        "stories" to listOf("story"),
        "better" to listOf("good", "well"),
        "best" to listOf("good", "well"),
        "worse" to listOf("bad"),
        "worst" to listOf("bad")
    )

    fun normalizeToken(raw: String): String {
        return raw.trim()
            .trim { ch ->
                !ch.isLetterOrDigit() && ch != '-' && ch != '\'' && ch != '’'
            }
            .replace('’', '\'')
            .lowercase()
    }

    /**
     * Returns an ordered, deduplicated list of lemma candidates starting with the normalized form itself.
     */
    fun candidateLemmas(rawWord: String): List<String> {
        val w = normalizeToken(rawWord)
        if (w.isBlank()) return emptyList()

        val out = LinkedHashSet<String>()
        out.add(w)

        // Strip trailing possessive 's / s'
        val base = when {
            w.endsWith("'s") && w.length > 3 -> w.dropLast(2)
            w.endsWith("s'") && w.length > 3 -> w.dropLast(1)
            else -> w
        }
        if (base != w) out.add(base)

        irregulars[base]?.forEach { out.add(it) }

        if ('-' in base) {
            base.split('-').filter { it.length >= 2 }.forEach { part ->
                out.add(part)
                irregulars[part]?.forEach { out.add(it) }
            }
        }

        // Common English morphological inflection rules
        if (base.endsWith("ies") && base.length > 4) {
            out.add(base.dropLast(3) + "y")
        }
        if (base.endsWith("ied") && base.length > 4) {
            out.add(base.dropLast(3) + "y")
        }
        if (base.endsWith("ves") && base.length > 4) {
            out.add(base.dropLast(3) + "f")
            out.add(base.dropLast(3) + "fe")
        }
        if (base.endsWith("ing") && base.length > 5) {
            val stem = base.dropLast(3)
            out.add(stem)
            out.add("${stem}e")
            if (stem.length >= 2 && stem.last() == stem[stem.length - 2]) {
                out.add(stem.dropLast(1))
            }
            if (stem.endsWith("y")) {
                out.add(stem.dropLast(1) + "ie")
            }
        }
        if (base.endsWith("ed") && base.length > 4) {
            val stem = base.dropLast(2)
            out.add(stem)
            out.add("${stem}e")
            if (stem.length >= 2 && stem.last() == stem[stem.length - 2]) {
                out.add(stem.dropLast(1))
            }
            out.add(base.dropLast(1))
        }
        if (base.endsWith("es") && base.length > 4) {
            out.add(base.dropLast(2))
            out.add(base.dropLast(1))
        }
        if (base.endsWith("s") && !base.endsWith("ss") && base.length > 3) {
            out.add(base.dropLast(1))
        }
        if (base.endsWith("ier") && base.length > 4) {
            out.add(base.dropLast(3) + "y")
        }
        if (base.endsWith("iest") && base.length > 5) {
            out.add(base.dropLast(4) + "y")
        }
        if (base.endsWith("er") && base.length > 4) {
            val stem = base.dropLast(2)
            out.add(stem)
            out.add("${stem}e")
            if (stem.length >= 2 && stem.last() == stem[stem.length - 2]) {
                out.add(stem.dropLast(1))
            }
        }
        if (base.endsWith("est") && base.length > 5) {
            val stem = base.dropLast(3)
            out.add(stem)
            out.add("${stem}e")
        }
        if (base.endsWith("ily") && base.length > 4) {
            out.add(base.dropLast(3) + "y")
        }
        if (base.endsWith("ly") && base.length > 4) {
            out.add(base.dropLast(2))
            out.add(base.dropLast(2) + "le")
        }
        if (base.endsWith("ness") && base.length > 6) {
            val stem = base.dropLast(4)
            out.add(stem)
            if (stem.endsWith("i")) out.add(stem.dropLast(1) + "y")
        }
        if (base.endsWith("ment") && base.length > 6) {
            out.add(base.dropLast(4))
        }

        return out.toList()
    }

    /**
     * Builds 2-word and 3-word phrase candidates centered on or adjacent to [queryWord] within [contextText].
     */
    fun extractContextPhrases(contextText: String, queryWord: String): List<String> {
        val target = normalizeToken(queryWord)
        if (target.isBlank() || contextText.isBlank()) return emptyList()
        val tokens = Regex("[\\p{L}\\p{N}'’-]+").findAll(contextText)
            .map { normalizeToken(it.value) }
            .filter { it.isNotBlank() }
            .toList()
        if (tokens.size < 2) return emptyList()

        val phrases = LinkedHashSet<String>()
        tokens.forEachIndexed { idx, tok ->
            if (tok == target || candidateLemmas(tok).contains(target)) {
                if (idx > 0) phrases.add("${tokens[idx - 1]} $tok")
                if (idx + 1 < tokens.size) phrases.add("$tok ${tokens[idx + 1]}")
                if (idx > 0 && idx + 1 < tokens.size) {
                    phrases.add("${tokens[idx - 1]} $tok ${tokens[idx + 1]}")
                }
                if (idx >= 2) phrases.add("${tokens[idx - 2]} ${tokens[idx - 1]} $tok")
                if (idx + 2 < tokens.size) phrases.add("$tok ${tokens[idx + 1]} ${tokens[idx + 2]}")
            }
        }
        return phrases.toList()
    }

    fun damerauLevenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val n = a.length
        val m = b.length
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    best = min(best, dp[i - 2][j - 2] + cost)
                }
                dp[i][j] = best
            }
        }
        return dp[n][m]
    }
}
