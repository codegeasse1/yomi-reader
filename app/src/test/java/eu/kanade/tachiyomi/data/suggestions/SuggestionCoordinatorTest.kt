package eu.kanade.tachiyomi.data.suggestions

import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SuggestionCoordinatorTest {

    @Test
    fun `createSources should return correct sources for media type`() {
        val coordinator = SuggestionCoordinator()

        val animeSources = coordinator.createSources(SuggestionMediaType.ANIME)
        assertEquals(2, animeSources.size)
        assertTrue(animeSources.any { it.name == "AniList" })
        assertTrue(animeSources.any { it.name == "MyAnimeList" })

        val mangaSources = coordinator.createSources(SuggestionMediaType.MANGA)
        assertEquals(2, mangaSources.size)
        assertTrue(mangaSources.any { it.name == "AniList" })
        assertTrue(mangaSources.any { it.name == "MangaUpdates" })

        val novelSources = coordinator.createSources(SuggestionMediaType.NOVEL)
        // F1.1 + F1.2: MangaUpdates and NovelUpdates are now part of the
        // NOVEL source set. The shim SourcePreferences defaults to "true"
        // for all F3.2 toggles, so all three sources are present.
        assertEquals(3, novelSources.size)
        assertTrue(novelSources.any { it.name == "AniList" })
        assertTrue(novelSources.any { it.name == "MangaUpdates" })
        assertTrue(novelSources.any { it.name == "NovelUpdates" })
    }
}
