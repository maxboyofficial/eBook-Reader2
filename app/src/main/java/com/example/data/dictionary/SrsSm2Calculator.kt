package com.example.data.dictionary

import com.example.data.db.VocabularyWordEntity
import kotlin.math.max
import kotlin.math.roundToInt

enum class SrsGrade(val quality: Int, val label: String) {
    AGAIN(1, "Again"),
    HARD(3, "Hard"),
    GOOD(4, "Good"),
    EASY(5, "Easy")
}

object SrsSm2Calculator {

    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val TEN_MIN_MS = 10L * 60L * 1000L

    data class SrsUpdate(
        val repetitions: Int,
        val intervalDays: Int,
        val easeFactor: Float,
        val nextReviewAt: Long,
        val masteryLevel: String,
        val previewIntervalLabel: String
    )

    fun compute(
        word: VocabularyWordEntity,
        grade: SrsGrade,
        nowMillis: Long = System.currentTimeMillis()
    ): SrsUpdate {
        val q = grade.quality
        val oldEf = word.srsEaseFactor.coerceAtLeast(1.3f)
        val delta = 0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f)
        val newEf = max(1.3f, oldEf + delta)

        val newReps: Int
        val newIntervalDays: Int
        val nextDueMillis: Long
        val preview: String

        if (q < 3) {
            // Again: lapse, re-queue in 10 minutes
            newReps = 0
            newIntervalDays = 0
            nextDueMillis = nowMillis + TEN_MIN_MS
            preview = "10m"
        } else {
            newReps = word.srsRepetitions + 1
            newIntervalDays = when (newReps) {
                1 -> if (grade == SrsGrade.EASY) 4 else 1
                2 -> if (grade == SrsGrade.EASY) 8 else 6
                else -> {
                    val base = (word.srsIntervalDays.coerceAtLeast(1) * newEf).roundToInt().coerceAtLeast(1)
                    when (grade) {
                        SrsGrade.HARD -> (base * 0.8f).roundToInt().coerceAtLeast(word.srsIntervalDays + 1)
                        SrsGrade.EASY -> (base * 1.3f).roundToInt().coerceAtLeast(base + 1)
                        else -> base
                    }
                }
            }
            nextDueMillis = nowMillis + newIntervalDays * DAY_MS
            preview = "${newIntervalDays}d"
        }

        val mastery = when {
            newReps == 0 -> "LEARNING"
            newIntervalDays >= 21 && newReps >= 3 -> "MASTERED"
            newReps >= 2 -> "REVIEWING"
            else -> "LEARNING"
        }

        return SrsUpdate(
            repetitions = newReps,
            intervalDays = newIntervalDays,
            easeFactor = newEf,
            nextReviewAt = nextDueMillis,
            masteryLevel = mastery,
            previewIntervalLabel = preview
        )
    }

    fun previewLabel(word: VocabularyWordEntity, grade: SrsGrade): String =
        compute(word, grade, nowMillis = 0L).previewIntervalLabel
}
