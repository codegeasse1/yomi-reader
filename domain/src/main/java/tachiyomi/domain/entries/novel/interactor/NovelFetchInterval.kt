package tachiyomi.domain.entries.novel.interactor

import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.items.novelchapter.interactor.GetNovelChapters
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

/**
 * Novel counterpart of [tachiyomi.domain.entries.manga.interactor.MangaFetchInterval]:
 * estimates the expected next release date from the recent chapter release rhythm and
 * persists it into [Novel.nextUpdate] (consumed by the Upcoming calendar).
 */
class NovelFetchInterval(
    private val getNovelChapters: GetNovelChapters,
) {

    suspend fun toNovelUpdate(
        novel: Novel,
        dateTime: ZonedDateTime,
        window: Pair<Long, Long>,
    ): NovelUpdate {
        val interval = novel.fetchInterval.takeIf { it < 0 } ?: calculateInterval(
            chapters = getNovelChapters.await(novel.id, applyScanlatorFilter = true),
            zone = dateTime.zone,
        )
        val currentWindow = if (window.first == 0L && window.second == 0L) {
            getWindow(ZonedDateTime.now())
        } else {
            window
        }
        val nextUpdate = calculateNextUpdate(novel, interval, dateTime, currentWindow)

        return NovelUpdate(id = novel.id, nextUpdate = nextUpdate, fetchInterval = interval)
    }

    fun getWindow(dateTime: ZonedDateTime): Pair<Long, Long> {
        val today = dateTime.toLocalDate().atStartOfDay(dateTime.zone)
        val lowerBound = today.minusDays(GRACE_PERIOD)
        val upperBound = today.plusDays(GRACE_PERIOD)
        return Pair(lowerBound.toEpochSecond() * 1000, upperBound.toEpochSecond() * 1000 - 1)
    }

    internal fun calculateInterval(chapters: List<NovelChapter>, zone: ZoneId): Int {
        val chapterWindow = if (chapters.size <= 8) 3 else 10

        val uploadDates = chapters.asSequence()
            .filter { it.dateUpload > 0L }
            .sortedByDescending { it.dateUpload }
            .map {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.dateUpload), zone)
                    .toLocalDate()
                    .atStartOfDay()
            }
            .distinct()
            .take(chapterWindow)
            .toList()

        val fetchDates = chapters.asSequence()
            .sortedByDescending { it.dateFetch }
            .map {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.dateFetch), zone)
                    .toLocalDate()
                    .atStartOfDay()
            }
            .distinct()
            .take(chapterWindow)
            .toList()

        val interval = when {
            // Enough upload date from source
            uploadDates.size >= 3 -> {
                val uploadDelta = uploadDates.last().until(uploadDates.first(), ChronoUnit.DAYS)
                val uploadPeriod = uploadDates.indexOf(uploadDates.last())
                uploadDelta.floorDiv(uploadPeriod).toInt()
            }
            // Enough fetch date from client
            fetchDates.size >= 3 -> {
                val fetchDelta = fetchDates.last().until(fetchDates.first(), ChronoUnit.DAYS)
                val fetchPeriod = fetchDates.indexOf(fetchDates.last())
                fetchDelta.floorDiv(fetchPeriod).toInt()
            }
            // Default to 7 days
            else -> 7
        }

        return interval.coerceIn(1, MAX_INTERVAL)
    }

    private fun calculateNextUpdate(
        novel: Novel,
        interval: Int,
        dateTime: ZonedDateTime,
        window: Pair<Long, Long>,
    ): Long {
        if (novel.nextUpdate in window.first.rangeTo(window.second + 1)) {
            return novel.nextUpdate
        }

        val latestDate = ZonedDateTime.ofInstant(
            if (novel.lastUpdate > 0) Instant.ofEpochMilli(novel.lastUpdate) else Instant.now(),
            dateTime.zone,
        )
            .toLocalDate()
            .atStartOfDay()
        val timeSinceLatest = ChronoUnit.DAYS.between(latestDate, dateTime).toInt()
        val cycle = timeSinceLatest.floorDiv(
            interval.absoluteValue.takeIf { interval < 0 }
                ?: increaseInterval(interval, timeSinceLatest, increaseWhenOver = 10),
        )
        return latestDate.plusDays((cycle + 1) * interval.absoluteValue.toLong()).toEpochSecond(dateTime.offset) * 1000
    }

    private fun increaseInterval(delta: Int, timeSinceLatest: Int, increaseWhenOver: Int): Int {
        if (delta >= MAX_INTERVAL) return MAX_INTERVAL

        // double delta again if missed more than 9 check in new delta
        val cycle = timeSinceLatest.floorDiv(delta) + 1
        return if (cycle > increaseWhenOver) {
            increaseInterval(delta * 2, timeSinceLatest, increaseWhenOver)
        } else {
            delta
        }
    }

    companion object {
        const val MAX_INTERVAL = 28

        private const val GRACE_PERIOD = 1L
    }
}
