package eu.kanade.tachiyomi.ui.browse

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSearchItemResult
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.MangaSearchItemResult
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.MangaSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.NovelSearchItemResult
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.NovelSearchScreenModel
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.jupiter.api.Test
import eu.kanade.tachiyomi.source.CatalogueSource as MangaCatalogueSource

class GlobalSearchLanguageFilterTest {

    @Test
    fun `novel search language filter hides source groups of other languages`() {
        val enSource = mockk<NovelCatalogueSource> { every { lang } returns "en" }
        val jaSource = mockk<NovelCatalogueSource> { every { lang } returns "ja" }
        val state = NovelSearchScreenModel.State(
            languageFilter = persistentSetOf("en"),
            items = persistentMapOf(
                enSource to NovelSearchItemResult.Success(emptyList()),
                jaSource to NovelSearchItemResult.Success(emptyList()),
            ),
        )
        state.filteredItems.keys shouldBe setOf(enSource)
    }

    @Test
    fun `novel search with empty language filter shows all sources`() {
        val enSource = mockk<NovelCatalogueSource> { every { lang } returns "en" }
        val jaSource = mockk<NovelCatalogueSource> { every { lang } returns "ja" }
        val state = NovelSearchScreenModel.State(
            items = persistentMapOf(
                enSource to NovelSearchItemResult.Success(emptyList()),
                jaSource to NovelSearchItemResult.Success(emptyList()),
            ),
        )
        state.filteredItems.keys shouldBe setOf(enSource, jaSource)
    }

    @Test
    fun `manga and anime search language filters behave identically`() {
        val enManga = mockk<MangaCatalogueSource> { every { lang } returns "en" }
        val jaManga = mockk<MangaCatalogueSource> { every { lang } returns "ja" }
        val mangaState = MangaSearchScreenModel.State(
            languageFilter = persistentSetOf("en"),
            items = persistentMapOf(
                enManga to MangaSearchItemResult.Success(emptyList()),
                jaManga to MangaSearchItemResult.Success(emptyList()),
            ),
        )
        mangaState.filteredItems.keys shouldBe setOf(enManga)

        val enAnime = mockk<AnimeCatalogueSource> { every { lang } returns "en" }
        val jaAnime = mockk<AnimeCatalogueSource> { every { lang } returns "ja" }
        val animeState = AnimeSearchScreenModel.State(
            languageFilter = persistentSetOf("en"),
            items = persistentMapOf(
                enAnime to AnimeSearchItemResult.Success(emptyList()),
                jaAnime to AnimeSearchItemResult.Success(emptyList()),
            ),
        )
        animeState.filteredItems.keys shouldBe setOf(enAnime)
    }
}
