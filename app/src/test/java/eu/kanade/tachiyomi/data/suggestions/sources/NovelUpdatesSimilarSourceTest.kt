package eu.kanade.tachiyomi.data.suggestions.sources

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelUpdatesSimilarSourceTest {

    @Test
    fun `name should be NovelUpdates`() {
        val source = NovelUpdatesSimilarSource(SuggestionMediaType.NOVEL)
        assertEquals("NovelUpdates", source.name)
    }

    @Test
    fun `mediaType is exposed`() {
        val source = NovelUpdatesSimilarSource(SuggestionMediaType.NOVEL)
        assertEquals(SuggestionMediaType.NOVEL, source.mediaType)
    }

    @Test
    fun `mediaType MATCHES regardless of NOVEL or MANGA at construction`() {
        // The source constructor should never throw and should always expose
        // whatever media type was requested. The actual guard happens at
        // fetch time, but we make sure construction is safe.
        val noelSource = NovelUpdatesSimilarSource(SuggestionMediaType.NOVEL)
        val mangaSource = NovelUpdatesSimilarSource(SuggestionMediaType.MANGA)
        assertEquals(SuggestionMediaType.NOVEL, noelSource.mediaType)
        assertEquals(SuggestionMediaType.MANGA, mangaSource.mediaType)
    }

    @Test
    fun `name and providerName are stable identifiers`() {
        val source = NovelUpdatesSimilarSource(SuggestionMediaType.NOVEL)
        // We rely on this string in tests and the UI badge, so lock it in.
        assertTrue(source.name.isNotBlank())
        assertEquals("NovelUpdates", source.name)
    }
}
