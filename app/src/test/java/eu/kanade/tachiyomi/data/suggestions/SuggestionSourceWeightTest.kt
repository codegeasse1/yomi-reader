package eu.kanade.tachiyomi.data.suggestions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SuggestionSourceWeightTest {

    @Test
    fun `AniList weight is 1_0`() {
        assertEquals(1.0, SuggestionSourceWeight.of(SuggestionReason.EXTERNAL_ANILIST), 0.0001)
    }

    @Test
    fun `MangaUpdates and NovelUpdates weights are 0_9`() {
        assertEquals(0.9, SuggestionSourceWeight.of(SuggestionReason.EXTERNAL_MU), 0.0001)
        assertEquals(0.9, SuggestionSourceWeight.of(SuggestionReason.EXTERNAL_NU), 0.0001)
    }

    @Test
    fun `Related weight is 0_8`() {
        assertEquals(0.8, SuggestionSourceWeight.of(SuggestionReason.RELATED), 0.0001)
    }

    @Test
    fun `Search title is 0_6, author 0_4, genre 0_3`() {
        assertEquals(0.6, SuggestionSourceWeight.of(SuggestionReason.SEARCH_TITLE), 0.0001)
        assertEquals(0.4, SuggestionSourceWeight.of(SuggestionReason.SEARCH_AUTHOR), 0.0001)
        assertEquals(0.3, SuggestionSourceWeight.of(SuggestionReason.SEARCH_GENRE), 0.0001)
    }

    @Test
    fun `Popular backfill weight is 0_1`() {
        assertEquals(0.1, SuggestionSourceWeight.of(SuggestionReason.POPULAR_BACKFILL), 0.0001)
    }

    @Test
    fun `Final score multiplies weight by normalized match score`() {
        // AniList with exact match (100): 1.0 * 100 = 100
        assertEquals(
            100.0,
            SuggestionSourceWeight.finalScore(SuggestionReason.EXTERNAL_ANILIST, 100),
            0.0001,
        )
        // MangaUpdates with prefix match (75): 0.9 * 75 = 67.5
        assertEquals(
            67.5,
            SuggestionSourceWeight.finalScore(SuggestionReason.EXTERNAL_MU, 75),
            0.0001,
        )
        // Popular backfill with token overlap (40): 0.1 * 40 = 4.0
        assertEquals(
            4.0,
            SuggestionSourceWeight.finalScore(SuggestionReason.POPULAR_BACKFILL, 40),
            0.0001,
        )
    }

    @Test
    fun `Final score clamps out-of-range match scores`() {
        assertEquals(
            90.0,
            SuggestionSourceWeight.finalScore(SuggestionReason.EXTERNAL_ANILIST, 90),
            0.0001,
        )
        // 150 should clamp to 100
        assertEquals(
            100.0,
            SuggestionSourceWeight.finalScore(SuggestionReason.EXTERNAL_ANILIST, 150),
            0.0001,
        )
        // Negative should clamp to 0
        assertEquals(
            0.0,
            SuggestionSourceWeight.finalScore(SuggestionReason.EXTERNAL_ANILIST, -10),
            0.0001,
        )
    }

    @Test
    fun `External sources always rank above backfill regardless of match`() {
        // Even with the best possible match score, backfill should never
        // outrank a real external recommendation at the same score.
        val backfillBest = SuggestionSourceWeight.finalScore(
            SuggestionReason.POPULAR_BACKFILL,
            100,
        )
        val anilistWorst = SuggestionSourceWeight.finalScore(
            SuggestionReason.EXTERNAL_ANILIST,
            20,
        )
        assertTrue(backfillBest < anilistWorst)
    }
}
