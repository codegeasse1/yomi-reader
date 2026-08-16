package eu.kanade.tachiyomi.data.suggestions

import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.NovelCover

class SuggestionCoverModelTest {

    @Test
    fun `native manga suggestion uses MangaCover fetcher model`() {
        val model = suggestionCoverModel(
            item(
                mediaType = SuggestionMediaType.MANGA,
                providerId = "123:/title",
                thumbnailUrl = "https://img.example/cover.jpg",
            ),
        ).shouldBeInstanceOf<MangaCover>()

        model.sourceId shouldBe 123L
        model.url shouldBe "https://img.example/cover.jpg"
        model.isMangaFavorite shouldBe false
    }

    @Test
    fun `native anime suggestion uses AnimeCover fetcher model`() {
        val model = suggestionCoverModel(
            item(
                mediaType = SuggestionMediaType.ANIME,
                providerId = "456:/title",
                thumbnailUrl = "https://img.example/anime.jpg",
            ),
        ).shouldBeInstanceOf<AnimeCover>()

        model.sourceId shouldBe 456L
        model.url shouldBe "https://img.example/anime.jpg"
        model.isAnimeFavorite shouldBe false
    }

    @Test
    fun `native novel suggestion uses NovelCover fetcher model`() {
        val model = suggestionCoverModel(
            item(
                mediaType = SuggestionMediaType.NOVEL,
                providerId = "789:/title",
                thumbnailUrl = "https://img.example/novel.jpg",
            ),
        ).shouldBeInstanceOf<NovelCover>()

        model.sourceId shouldBe 789L
        model.url shouldBe "https://img.example/novel.jpg"
        model.isNovelFavorite shouldBe false
    }

    @Test
    fun `novel plugin image keeps specialized model`() {
        suggestionCoverModel(
            item(
                mediaType = SuggestionMediaType.NOVEL,
                providerId = "789:/title",
                thumbnailUrl = "novelimg://plugin?ref=image-1",
            ),
        ).shouldBeInstanceOf<NovelPluginImage>()
    }

    @Test
    fun `external suggestion without native source keeps raw url`() {
        suggestionCoverModel(
            item(
                mediaType = SuggestionMediaType.MANGA,
                providerId = null,
                thumbnailUrl = "https://cdn.example/external.jpg",
            ),
        ) shouldBe "https://cdn.example/external.jpg"
    }

    private fun item(
        mediaType: SuggestionMediaType,
        providerId: String?,
        thumbnailUrl: String?,
    ) = SuggestionItem(
        title = "Title",
        thumbnailUrl = thumbnailUrl,
        providerName = "Source",
        providerUrl = "/title",
        providerId = providerId,
        mediaType = mediaType,
    )
}
