package com.example.data.stats

import com.example.data.db.ReadingSessionEntity
import com.example.data.db.ReadingStatsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyReadingStat(
    val dateIso: String,
    val dayLabel: String,
    val durationSeconds: Long,
    val pagesTurned: Int
) {
    val minutes: Int get() = (durationSeconds / 60L).toInt()
}

data class BookReadingStat(
    val bookId: Long,
    val bookTitle: String,
    val durationSeconds: Long,
    val pagesTurned: Int
)

data class ReadingInsights(
    val totalSeconds: Long = 0L,
    val todaySeconds: Long = 0L,
    val totalPagesTurned: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val last7Days: List<DailyReadingStat> = emptyList(),
    val topBooks: List<BookReadingStat> = emptyList()
)

class ReadingStatsRepository(
    private val dao: ReadingStatsDao
) {
    fun observeInsights(): Flow<ReadingInsights> =
        dao.observeAllSessions().map { sessions ->
            computeInsights(sessions, todayIso())
        }

    suspend fun recordReadingDelta(
        bookId: Long,
        bookTitle: String,
        durationDeltaSeconds: Long,
        pagesTurnedDelta: Int,
        dateIso: String = todayIso()
    ) = withContext(Dispatchers.IO) {
        if (durationDeltaSeconds <= 0L && pagesTurnedDelta <= 0) return@withContext
        val now = System.currentTimeMillis()
        val existing = dao.getSessionForBookDate(bookId, dateIso)
        if (existing == null) {
            dao.upsertSession(
                ReadingSessionEntity(
                    bookId = bookId,
                    bookTitle = bookTitle,
                    dateIso = dateIso,
                    startedAt = now,
                    updatedAt = now,
                    durationSeconds = durationDeltaSeconds.coerceAtLeast(0L),
                    pagesTurned = pagesTurnedDelta.coerceAtLeast(0)
                )
            )
        } else {
            dao.upsertSession(
                existing.copy(
                    bookTitle = bookTitle,
                    updatedAt = now,
                    durationSeconds = existing.durationSeconds + durationDeltaSeconds.coerceAtLeast(0L),
                    pagesTurned = existing.pagesTurned + pagesTurnedDelta.coerceAtLeast(0)
                )
            )
        }
    }

    companion object {
        fun todayIso(nowMillis: Long = System.currentTimeMillis()): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(Date(nowMillis))
        }

        fun shiftIsoDays(baseIso: String, deltaDays: Int): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance()
            val parsed = runCatching { sdf.parse(baseIso) }.getOrNull()
            if (parsed != null) {
                cal.time = parsed
            }
            cal.add(Calendar.DAY_OF_YEAR, deltaDays)
            return sdf.format(cal.time)
        }

        fun shortDayLabel(iso: String): String {
            val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outFmt = SimpleDateFormat("EEE", Locale.US)
            val date = runCatching { inFmt.parse(iso) }.getOrNull() ?: return iso.takeLast(2)
            return outFmt.format(date).take(3)
        }

        fun computeInsights(
            sessions: List<ReadingSessionEntity>,
            todayIso: String = todayIso()
        ): ReadingInsights {
            if (sessions.isEmpty()) {
                val emptyDays = (6 downTo 0).map { offset ->
                    val iso = shiftIsoDays(todayIso, -offset)
                    DailyReadingStat(iso, shortDayLabel(iso), 0L, 0)
                }
                return ReadingInsights(last7Days = emptyDays)
            }

            val totalSec = sessions.sumOf { it.durationSeconds }
            val totalPages = sessions.sumOf { it.pagesTurned }
            val byDate = sessions.groupBy { it.dateIso }
            val todaySec = byDate[todayIso]?.sumOf { it.durationSeconds } ?: 0L

            val activeDates = byDate.entries
                .filter { (_, list) -> list.sumOf { it.durationSeconds } >= 15L || list.sumOf { it.pagesTurned } > 0 }
                .map { it.key }
                .toHashSet()

            // Current streak: consecutive days backwards from today (or yesterday if today not started yet)
            var currentStreak = 0
            var probe = if (todayIso in activeDates) todayIso else shiftIsoDays(todayIso, -1)
            while (probe in activeDates && currentStreak < 3650) {
                currentStreak++
                probe = shiftIsoDays(probe, -1)
            }

            // Longest streak across all active dates
            val sortedDates = activeDates.sorted()
            var longestStreak = 0
            var run = 0
            var prevDate: String? = null
            for (d in sortedDates) {
                run = if (prevDate != null && shiftIsoDays(prevDate, 1) == d) run + 1 else 1
                if (run > longestStreak) longestStreak = run
                prevDate = d
            }

            val last7 = (6 downTo 0).map { offset ->
                val iso = shiftIsoDays(todayIso, -offset)
                val daySessions = byDate[iso].orEmpty()
                DailyReadingStat(
                    dateIso = iso,
                    dayLabel = shortDayLabel(iso),
                    durationSeconds = daySessions.sumOf { it.durationSeconds },
                    pagesTurned = daySessions.sumOf { it.pagesTurned }
                )
            }

            val topBooks = sessions
                .groupBy { it.bookId }
                .values
                .map { group ->
                    BookReadingStat(
                        bookId = group.first().bookId,
                        bookTitle = group.last().bookTitle,
                        durationSeconds = group.sumOf { it.durationSeconds },
                        pagesTurned = group.sumOf { it.pagesTurned }
                    )
                }
                .sortedByDescending { it.durationSeconds }
                .take(8)

            return ReadingInsights(
                totalSeconds = totalSec,
                todaySeconds = todaySec,
                totalPagesTurned = totalPages,
                currentStreakDays = currentStreak,
                longestStreakDays = longestStreak,
                last7Days = last7,
                topBooks = topBooks
            )
        }
    }
}
