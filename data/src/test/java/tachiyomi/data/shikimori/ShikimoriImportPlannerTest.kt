package tachiyomi.data.shikimori

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.data.anixart.AnixartMatcher

class ShikimoriImportPlannerTest {

    private val entry = ShikimoriImportEntry(
        mediaType = ShikimoriImportMediaType.ANIME,
        rateId = 1L,
        remoteId = 100L,
        name = "Naruto",
        russian = "Наруто",
        status = "watching",
        score = 8,
        progress = 10,
        totalCount = 220L,
        thumbnailUrl = null,
    )

    private val config = ShikimoriImportPlanner.Config(
        statusCategoryIds = mapOf(
            ShikimoriImportStatus.WATCHING to 10L,
            ShikimoriImportStatus.COMPLETED to 20L,
        ),
    )

    @Test
    fun `maps status to category`() {
        val plan = ShikimoriImportPlanner.plan(
            selections = listOf(
                ShikimoriImportPlanner.Selection(
                    entry = entry,
                    chosen = AnixartMatcher.SearchCandidate(1L, 1L, "Naruto", listOf("Naruto"), url = "u1"),
                    enabled = true,
                ),
            ),
            config = config,
        )
        plan.actions.size shouldBe 1
        plan.actions.first().categoryIds shouldContainExactly setOf(10L)
    }

    @Test
    fun `disabled and null candidate selections are skipped and counted`() {
        val plan = ShikimoriImportPlanner.plan(
            selections = listOf(
                ShikimoriImportPlanner.Selection(entry, null, enabled = true),
                ShikimoriImportPlanner.Selection(
                    entry = entry.copy(status = "completed"),
                    chosen = AnixartMatcher.SearchCandidate(2L, 1L, "Naruto", listOf("Naruto"), url = "u2"),
                    enabled = true,
                ),
                ShikimoriImportPlanner.Selection(
                    entry = entry,
                    chosen = AnixartMatcher.SearchCandidate(1L, 1L, "Naruto", listOf("Naruto"), url = "u1"),
                    enabled = false,
                ),
            ),
            config = config,
        )
        plan.actions.size shouldBe 1
        plan.skippedDisabled shouldBe 1
        plan.skippedNoMatch shouldBe 1
        plan.actions.first().categoryIds shouldContainExactly setOf(20L)
    }

    @Test
    fun `duplicate rows targeting the same entry are merged with union of categories`() {
        val plan = ShikimoriImportPlanner.plan(
            selections = listOf(
                ShikimoriImportPlanner.Selection(
                    entry = entry,
                    chosen = AnixartMatcher.SearchCandidate(1L, 1L, "Naruto", listOf("Naruto"), url = "same"),
                    enabled = true,
                ),
                ShikimoriImportPlanner.Selection(
                    entry = entry.copy(status = "completed"),
                    chosen = AnixartMatcher.SearchCandidate(1L, 1L, "Naruto", listOf("Naruto"), url = "same"),
                    enabled = true,
                ),
            ),
            config = config,
        )
        plan.actions.size shouldBe 1
        plan.mergedDuplicates shouldBe 1
        plan.actions.first().categoryIds shouldContainExactlyInAnyOrder setOf(10L, 20L)
    }
}
