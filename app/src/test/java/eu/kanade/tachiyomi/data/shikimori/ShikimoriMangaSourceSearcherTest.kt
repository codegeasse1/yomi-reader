package eu.kanade.tachiyomi.data.shikimori

import eu.kanade.tachiyomi.data.suggestions.manga.FakeMangaCatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.manga.service.MangaSourceManager

class ShikimoriMangaSourceSearcherTest {

    @Test
    fun searchUsesSourceDefaultFilters() = runTest {
        val source = object : FakeMangaCatalogueSource(id = 42L) {
            override fun getFilterList(): FilterList =
                FilterList(eu.kanade.tachiyomi.source.model.Filter.Header("stub"))

            override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
                if (filters.isEmpty()) return MangasPage(emptyList(), false)
                val manga = SManga.create().apply {
                    title = query
                    url = "/$query"
                }
                return MangasPage(listOf(manga), false)
            }
        }
        val searcher = ShikimoriMangaSourceSearcher(
            sourceManager = FakeMangaSourceManager(listOf(source)),
            sourceIds = listOf(42L),
        )

        val results = searcher.search("One Piece")

        assertEquals(1, results.size)
        assertEquals("One Piece", results.first().displayTitle)
    }

    private class FakeMangaSourceManager(
        sources: List<eu.kanade.tachiyomi.source.CatalogueSource>,
    ) : MangaSourceManager {
        private val state = MutableStateFlow(sources)

        override val isInitialized = MutableStateFlow(true)
        override val catalogueSources = state

        override fun get(sourceKey: Long) = state.value.firstOrNull { it.id == sourceKey }

        override fun getOrStub(sourceKey: Long) =
            get(sourceKey) ?: error("Manga source $sourceKey not found")

        override fun getOnlineSources() =
            emptyList<eu.kanade.tachiyomi.source.online.HttpSource>()

        override fun getCatalogueSources() = state.value

        override fun getStubSources() =
            emptyList<tachiyomi.domain.source.manga.model.StubMangaSource>()
    }
}
