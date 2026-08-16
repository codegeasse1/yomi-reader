package eu.kanade.tachiyomi.data.suggestions.sources

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MyAnimeListRecommendationSourceTest {

    @Test
    fun `name should be MyAnimeList`() {
        val source = MyAnimeListRecommendationSource(SuggestionMediaType.ANIME)
        assertEquals("MyAnimeList", source.name)
    }
}
