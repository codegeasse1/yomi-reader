package tachiyomi.data.anixart

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnixartMatcherTest {

    @Test
    fun `normalize folds case diacritics and unifies separators`() {
        AnixartMatcher.normalize("Re:Zero — kara Hajimeru!") shouldBe "re zero kara hajimeru"
        // Cyrillic lowercases correctly (the SQLite ASCII-LOWER class of bug).
        AnixartMatcher.normalize("ГАРЕМ") shouldBe "гарем"
        AnixartMatcher.normalize("Pokémon") shouldBe "pokemon"
    }

    @Test
    fun `exact normalized match scores 100`() {
        AnixartMatcher.pairScore("Steins;Gate", "steins gate") shouldBe 100
    }

    @Test
    fun `containment is scaled by length ratio so short queries do not auto-win`() {
        val short = AnixartMatcher.pairScore("Bleach", "Bleach: Thousand-Year Blood War Arc Part 2")
        val close = AnixartMatcher.pairScore("Naruto Shippuuden", "Naruto Shippuuden")
        short shouldBeGreaterThan 0
        short shouldBeLessThan 90
        close shouldBe 100
    }

    @Test
    fun `token overlap never beats containment`() {
        val overlap = AnixartMatcher.pairScore("magic high school", "the irregular at magic high school")
        overlap shouldBeGreaterThan 0
        // containment path yields >=70; pure token overlap is capped below it
        val pureOverlap = AnixartMatcher.pairScore("red dragon emperor", "white dragon spirit")
        pureOverlap shouldBeLessThan 66
    }

    @Test
    fun `clear single winner is AUTO`() {
        val result = AnixartMatcher.match(
            queryTitles = listOf("Re:Zero kara Hajimeru Isekai Seikatsu"),
            candidates = listOf(
                AnixartMatcher.SearchCandidate(
                    1L,
                    1L,
                    "Re:Zero kara Hajimeru Isekai Seikatsu",
                    listOf("Re:Zero kara Hajimeru Isekai Seikatsu"),
                ),
                AnixartMatcher.SearchCandidate(2L, 1L, "Konosuba", listOf("Konosuba")),
            ),
        )
        result.confidence shouldBe AnixartMatcher.Confidence.AUTO
        result.best!!.candidate.id shouldBe 1L
    }

    @Test
    fun `two near-equal candidates force NEEDS_REVIEW`() {
        val result = AnixartMatcher.match(
            queryTitles = listOf("Fate Stay Night"),
            candidates = listOf(
                AnixartMatcher.SearchCandidate(1L, 1L, "Fate Stay Night", listOf("Fate Stay Night")),
                AnixartMatcher.SearchCandidate(2L, 1L, "Fate Stay Night", listOf("Fate Stay Night")),
            ),
        )
        result.confidence shouldBe AnixartMatcher.Confidence.NEEDS_REVIEW
    }

    @Test
    fun `weak similarity below floor is NO_MATCH`() {
        val result = AnixartMatcher.match(
            queryTitles = listOf("Totally Unrelated Title"),
            candidates = listOf(AnixartMatcher.SearchCandidate(1L, 1L, "Some Other Anime", listOf("Some Other Anime"))),
        )
        result.confidence shouldBe AnixartMatcher.Confidence.NO_MATCH
        result.best shouldBe null
    }

    @Test
    fun `empty inputs yield NO_MATCH`() {
        AnixartMatcher.match(
            emptyList(),
            listOf(AnixartMatcher.SearchCandidate(1L, 1L, "X", listOf("X"))),
        ).confidence shouldBe
            AnixartMatcher.Confidence.NO_MATCH
        AnixartMatcher.match(listOf("X"), emptyList()).confidence shouldBe
            AnixartMatcher.Confidence.NO_MATCH
    }

    @Test
    fun `best score is taken across all query and candidate titles`() {
        val result = AnixartMatcher.match(
            queryTitles = listOf("неточное русское", "Cowboy Bebop"),
            candidates = listOf(
                AnixartMatcher.SearchCandidate(
                    9L,
                    1L,
                    "Ковбой Бибоп",
                    listOf("Ковбой Бибоп", "Cowboy Bebop"),
                ),
            ),
        )
        result.confidence shouldBe AnixartMatcher.Confidence.AUTO
        result.best!!.score shouldBe 100
    }

    @Test
    fun `ranked list is sorted and capped`() {
        val many = (1..10).map {
            AnixartMatcher.SearchCandidate(it.toLong(), 1L, "Title $it", listOf("Title $it"))
        }
        val result = AnixartMatcher.match(listOf("Title 1"), many)
        (result.ranked.size <= 5) shouldBe true
        result.ranked.zipWithNext().all { (a, b) -> a.score >= b.score } shouldBe true
    }
}
