package tachiyomi.data.anixart

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnixartImportPlannerTest {

    private val config = AnixartImportPlanner.Config(
        statusCategoryIds = mapOf(
            AnixartStatus.WATCHING to 10L,
            AnixartStatus.COMPLETED to 20L,
            AnixartStatus.PLAN_TO_WATCH to 30L,
            AnixartStatus.DROPPED to 40L,
        ),
        favoriteCategoryId = 99L,
    )

    @Test
    fun `maps status to category and adds favourite category`() {
        val plan = AnixartImportPlanner.plan(
            selections = listOf(
                AnixartImportPlanner.Selection(
                    row = AnixartRow(1, "ru", "orig", "", "Добавлено", "Смотрю", "4 из 5"),
                    chosen = AnixartMatcher.SearchCandidate(1L, 1L, "T1", listOf("T1"), url = "u1"),
                    enabled = true,
                ),
            ),
            config = config,
        )
        plan.actions.size shouldBe 1
        val a = plan.actions.first()
        a.categoryIds shouldContainExactlyInAnyOrder setOf(10L, 99L)
        a.status shouldBe AnixartStatus.WATCHING
        a.rating shouldBe 8
        a.isFavorite shouldBe true
    }

    @Test
    fun `disabled and null candidate selections are skipped and counted`() {
        val plan = AnixartImportPlanner.plan(
            selections = listOf(
                AnixartImportPlanner.Selection(
                    AnixartRow(1, "ru", "orig", "", "", "Смотрю", ""),
                    AnixartMatcher.SearchCandidate(1L, 1L, "T1", listOf("T1"), url = "u1"),
                    enabled = false,
                ),
                AnixartImportPlanner.Selection(AnixartRow(1, "ru", "orig", "", "", "Смотрю", ""), null, enabled = true),
                AnixartImportPlanner.Selection(
                    AnixartRow(1, "ru", "orig", "", "", "Просмотрено", ""),
                    AnixartMatcher.SearchCandidate(2L, 1L, "T2", listOf("T2"), url = "u2"),
                    enabled = true,
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
    fun `duplicate rows targeting the same anime are merged with union of categories`() {
        val plan = AnixartImportPlanner.plan(
            selections = listOf(
                AnixartImportPlanner.Selection(
                    AnixartRow(1, "ru", "orig", "", "", "Смотрю", ""),
                    AnixartMatcher.SearchCandidate(1L, 1L, "T1", listOf("T1"), url = "same"),
                    enabled = true,
                ),
                AnixartImportPlanner.Selection(
                    AnixartRow(1, "ru", "orig", "", "Добавлено", "Просмотрено", ""),
                    AnixartMatcher.SearchCandidate(1L, 1L, "T1", listOf("T1"), url = "same"),
                    enabled = true,
                ),
            ),
            config = config,
        )
        plan.actions.size shouldBe 1
        plan.mergedDuplicates shouldBe 1
        plan.actions.first().categoryIds shouldContainExactlyInAnyOrder setOf(10L, 20L, 99L)
        plan.actions.first().isFavorite shouldBe true
    }

    @Test
    fun `no category config yields empty category set but still imports`() {
        val plan = AnixartImportPlanner.plan(
            selections = listOf(
                AnixartImportPlanner.Selection(
                    AnixartRow(1, "ru", "orig", "", "Добавлено", "Смотрю", ""),
                    AnixartMatcher.SearchCandidate(1L, 1L, "T1", listOf("T1"), url = "u1"),
                    enabled = true,
                ),
            ),
            config = AnixartImportPlanner.Config(),
        )
        plan.actions.size shouldBe 1
        plan.actions.first().categoryIds shouldBe emptySet()
    }

    @Test
    fun `first-seen order is preserved`() {
        val plan = AnixartImportPlanner.plan(
            selections = listOf(
                AnixartImportPlanner.Selection(
                    AnixartRow(1, "ru", "orig", "", "", "Смотрю", ""),
                    AnixartMatcher.SearchCandidate(3L, 1L, "T3", listOf("T3"), url = "u3"),
                    enabled = true,
                ),
                AnixartImportPlanner.Selection(
                    AnixartRow(1, "ru", "orig", "", "", "Смотрю", ""),
                    AnixartMatcher.SearchCandidate(1L, 1L, "T1", listOf("T1"), url = "u1"),
                    enabled = true,
                ),
                AnixartImportPlanner.Selection(
                    AnixartRow(1, "ru", "orig", "", "", "Смотрю", ""),
                    AnixartMatcher.SearchCandidate(2L, 1L, "T2", listOf("T2"), url = "u2"),
                    enabled = true,
                ),
            ),
            config = config,
        )
        plan.actions.map { it.candidate.id } shouldContainExactly listOf(3L, 1L, 2L)
    }
}
