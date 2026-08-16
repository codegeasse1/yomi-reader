package tachiyomi.data.anixart

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ImportCollisionResolverTest {

    private fun candidate(id: Long, url: String, sourceId: Long = 1L) =
        AnixartMatcher.SearchCandidate(
            id = id,
            sourceId = sourceId,
            displayTitle = "T$id",
            titles = listOf("T$id"),
            url = url,
        )

    private fun scored(id: Long, url: String, score: Int) =
        AnixartMatcher.ScoredCandidate(candidate(id, url), score)

    @Test
    fun `loser of a contested entry falls back to its next free candidate`() {
        val first = scored(1L, "/a", 95)
        val second = scored(2L, "/b", 60)
        val (resolutions, report) = ImportCollisionResolver.resolve(
            listOf(
                ImportCollisionResolver.Row(0, "one", listOf(first, second), 1L, true),
                ImportCollisionResolver.Row(1, "two", listOf(first, second), 1L, true),
            ),
        )
        resolutions[0].selectedId shouldBe 1L
        resolutions[0].reassigned shouldBe false
        resolutions[1].selectedId shouldBe 2L
        resolutions[1].reassigned shouldBe true
        report.reassigned shouldBe 1
        report.released shouldBe 0
        report.duplicates shouldBe 0
    }

    @Test
    fun `row with no free candidate left is released instead of vanishing`() {
        val only = scored(1L, "/a", 95)
        val (resolutions, report) = ImportCollisionResolver.resolve(
            listOf(
                ImportCollisionResolver.Row(0, "one", listOf(only), 1L, true),
                ImportCollisionResolver.Row(1, "two", listOf(only), 1L, true),
            ),
        )
        resolutions[0].selectedId shouldBe 1L
        resolutions[1].selectedId shouldBe null
        resolutions[1].enabled shouldBe false
        resolutions[1].released shouldBe true
        report.released shouldBe 1
    }

    @Test
    fun `higher scoring row keeps the entry regardless of file order`() {
        val weak = listOf(scored(1L, "/a", 70))
        val strong = listOf(scored(1L, "/a", 99), scored(2L, "/b", 50))
        val (resolutions, _) = ImportCollisionResolver.resolve(
            listOf(
                ImportCollisionResolver.Row(0, "weak", weak, 1L, true),
                ImportCollisionResolver.Row(1, "strong", strong, 1L, true),
            ),
        )
        resolutions[1].selectedId shouldBe 1L
        resolutions[0].selectedId shouldBe null
    }

    @Test
    fun `a genuine duplicate row keeps the shared entry and is only counted`() {
        val ranked = listOf(scored(1L, "/a", 95), scored(2L, "/b", 60))
        val (resolutions, report) = ImportCollisionResolver.resolve(
            listOf(
                ImportCollisionResolver.Row(0, "same title", ranked, 1L, true),
                ImportCollisionResolver.Row(1, "same title", ranked, 1L, true),
            ),
        )
        resolutions[0].selectedId shouldBe 1L
        resolutions[1].selectedId shouldBe 1L
        resolutions[1].duplicateOf shouldBe 0
        report.duplicates shouldBe 1
        report.reassigned shouldBe 0
        report.released shouldBe 0
    }

    @Test
    fun `same url on different sources is not a collision`() {
        val a = AnixartMatcher.ScoredCandidate(candidate(1L, "/a", sourceId = 1L), 95)
        val b = AnixartMatcher.ScoredCandidate(candidate(2L, "/a", sourceId = 2L), 95)
        val (resolutions, report) = ImportCollisionResolver.resolve(
            listOf(
                ImportCollisionResolver.Row(0, "one", listOf(a), 1L, true),
                ImportCollisionResolver.Row(1, "two", listOf(b), 2L, true),
            ),
        )
        resolutions[0].selectedId shouldBe 1L
        resolutions[1].selectedId shouldBe 2L
        report.reassigned shouldBe 0
        report.released shouldBe 0
    }

    @Test
    fun `skipped and unmatched rows pass through untouched`() {
        val ranked = listOf(scored(1L, "/a", 95))
        val (resolutions, report) = ImportCollisionResolver.resolve(
            listOf(
                ImportCollisionResolver.Row(0, "one", ranked, 1L, false),
                ImportCollisionResolver.Row(1, "two", emptyList(), null, true),
                ImportCollisionResolver.Row(2, "three", ranked, 1L, true),
            ),
        )
        resolutions[0].enabled shouldBe false
        resolutions[0].selectedId shouldBe 1L
        resolutions[1].selectedId shouldBe null
        resolutions[2].selectedId shouldBe 1L
        report.released shouldBe 0
    }
}
