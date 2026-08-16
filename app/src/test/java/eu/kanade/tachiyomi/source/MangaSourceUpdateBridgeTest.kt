package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The host now asks sources for updates through the combined `getMangaUpdate` entry point.
 *
 * Extensions published today implement only the split 1.4/1.5 methods, so the default bridge has to
 * keep serving them unchanged; a future extension that implements only the combined method has to
 * work as well. Both directions are pinned here.
 */
class MangaSourceUpdateBridgeTest {

    private val manga = SManga.create().apply {
        url = "/manga/1"
        title = "Title"
    }

    @Test
    fun `a legacy source is served by the default bridge`() = runTest {
        val source = LegacySource()

        val update = source.getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true)

        update.manga.description shouldBe "from getMangaDetails"
        update.chapters.map { it.url } shouldBe listOf("/chapter/1")
        source.detailCalls shouldBe 1
        source.chapterCalls shouldBe 1
    }

    @Test
    fun `the bridge only calls what was asked for`() = runTest {
        val source = LegacySource()

        val update = source.getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)

        update.chapters.isEmpty() shouldBe true
        source.detailCalls shouldBe 1
        source.chapterCalls shouldBe 0
    }

    @Test
    fun `a source that only implements the combined api is used as is`() = runTest {
        val source = CombinedOnlySource()

        val update = source.getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true)

        update.manga.description shouldBe "from getMangaUpdate"
        update.chapters.map { it.url } shouldBe listOf("/combined/1")
    }

    private open class LegacySource : MangaSource {
        var detailCalls = 0
        var chapterCalls = 0

        override val id: Long = 1L
        override val name: String = "Legacy"
        override val lang: String = "en"

        override suspend fun getMangaDetails(manga: SManga): SManga {
            detailCalls++
            return SManga.create().apply {
                url = manga.url
                title = manga.title
                description = "from getMangaDetails"
            }
        }

        override suspend fun getChapterList(manga: SManga): List<SChapter> {
            chapterCalls++
            return listOf(
                SChapter.create().apply {
                    url = "/chapter/1"
                    name = "Chapter 1"
                },
            )
        }

        override suspend fun getPageList(chapter: SChapter): List<eu.kanade.tachiyomi.source.model.Page> =
            emptyList()
    }

    /** Stands in for an extension built against the combined 1.6 API only. */
    private class CombinedOnlySource : LegacySource() {
        override suspend fun getMangaDetails(manga: SManga): SManga =
            throw UnsupportedOperationException("1.6 extension does not implement the split api")

        override suspend fun getChapterList(manga: SManga): List<SChapter> =
            throw UnsupportedOperationException("1.6 extension does not implement the split api")

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            return SMangaUpdate(
                manga = SManga.create().apply {
                    url = manga.url
                    title = manga.title
                    description = "from getMangaUpdate"
                },
                chapters = listOf(
                    SChapter.create().apply {
                        url = "/combined/1"
                        name = "Combined"
                    },
                ),
            )
        }
    }

    @Test
    fun `a source may reject concurrent updates for the same entry`() = runTest {
        // KeiSource (the 1.6 base class) does exactly this, so the host must ask for both halves in
        // one call rather than firing details and chapters in parallel.
        val source = SingleFlightSource()

        val update = source.getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true)

        update.chapters.size shouldBe 1
        source.inFlightViolations shouldBe 0
    }

    /** Mirrors KeiSource's guard: overlapping calls for one manga are a programming error. */
    private class SingleFlightSource : LegacySource() {
        var inFlightViolations = 0
        private val inFlight = mutableSetOf<String>()

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            if (!inFlight.add(manga.url)) {
                inFlightViolations++
                error("getMangaUpdate must not be called concurrently for same manga")
            }
            try {
                return SMangaUpdate(
                    manga = manga,
                    chapters = listOf(
                        SChapter.create().apply {
                            url = "/single/1"
                            name = "Single"
                        },
                    ),
                )
            } finally {
                inFlight.remove(manga.url)
            }
        }
    }
}
