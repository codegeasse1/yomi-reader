package eu.kanade.tachiyomi.data.suggestions.sources

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AniListRecommendationSourceTest {

    @Test
    fun `name should be AniList`() {
        val source = AniListRecommendationSource(SuggestionMediaType.ANIME)
        assertEquals("AniList", source.name)
    }
}
