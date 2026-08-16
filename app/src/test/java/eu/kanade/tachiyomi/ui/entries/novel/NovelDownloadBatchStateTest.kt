package eu.kanade.tachiyomi.ui.entries.novel

import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCacheEvent
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelDownloadBatchStateTest {

    @Test
    fun `merge downloaded ids batch applies multiple chapter changes at once`() {
        // Before fix: 500 sequential handleDownloadCacheEvent calls
        // each calls updateSuccessState → 500 recompositions
        // After fix: batch events → single updateSuccessState call
        val events = listOf(
            NovelDownloadCacheEvent.ChaptersChanged(1L, setOf(10L, 11L), downloaded = true),
            NovelDownloadCacheEvent.ChaptersChanged(1L, setOf(12L, 13L), downloaded = true),
            NovelDownloadCacheEvent.ChaptersChanged(1L, setOf(14L), downloaded = true),
        )
        val currentIds = setOf<Long>()
        val result = mergeDownloadBatchEvents(novelId = 1L, currentIds = currentIds, events = events)

        result shouldBe setOf(10L, 11L, 12L, 13L, 14L)
    }

    @Test
    fun `merge downloaded ids batch handles delete events correctly`() {
        val events = listOf(
            NovelDownloadCacheEvent.ChaptersChanged(1L, setOf(10L, 11L), downloaded = true),
            NovelDownloadCacheEvent.ChaptersChanged(1L, setOf(10L), downloaded = false),
            NovelDownloadCacheEvent.ChaptersChanged(1L, setOf(12L), downloaded = true),
        )
        val currentIds = setOf<Long>()

        val result = mergeDownloadBatchEvents(novelId = 1L, currentIds = currentIds, events = events)

        result shouldBe setOf(11L, 12L)
    }

    @Test
    fun `merge downloaded ids batch ignores events for different novels`() {
        val events = listOf(
            NovelDownloadCacheEvent.ChaptersChanged(1L, setOf(10L), downloaded = true),
            NovelDownloadCacheEvent.ChaptersChanged(2L, setOf(20L), downloaded = true),
            NovelDownloadCacheEvent.ChaptersChanged(1L, setOf(11L), downloaded = true),
        )
        val currentIds = setOf<Long>()

        val result = mergeDownloadBatchEvents(novelId = 1L, currentIds = currentIds, events = events)

        result shouldBe setOf(10L, 11L)
    }

    @Test
    fun `merge downloaded ids batch returns currentIds when no events match novel`() {
        val events = listOf(NovelDownloadCacheEvent.ChaptersChanged(2L, setOf(20L), downloaded = true))
        val currentIds = setOf(5L)

        val result = mergeDownloadBatchEvents(novelId = 1L, currentIds = currentIds, events = events)

        result shouldBe setOf(5L)
    }

    @Test
    fun `merge downloaded ids batch returns currentIds for empty events`() {
        val currentIds = setOf(5L, 6L)
        val result = mergeDownloadBatchEvents(novelId = 1L, currentIds = currentIds, events = emptyList())

        result shouldBe setOf(5L, 6L)
    }

    @Test
    fun `merge downloaded ids batch handles 500 chapters without quadratic behavior`() {
        val currentIds = setOf<Long>()
        val events = (1L..500L).map { chapterId ->
            NovelDownloadCacheEvent.ChaptersChanged(1L, setOf(chapterId), downloaded = true)
        }

        val result = mergeDownloadBatchEvents(novelId = 1L, currentIds = currentIds, events = events)

        result.size shouldBe 500
        result.min() shouldBe 1L
        result.max() shouldBe 500L
    }
}
