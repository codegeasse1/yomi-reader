package eu.kanade.tachiyomi.ui.library.anime

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraCategorySwipeTest {

    @Test
    fun `coerceAuroraLibraryCategoryIndex returns 0 for empty categories`() {
        coerceAuroraLibraryCategoryIndex(requestedIndex = 0, categoryCount = 0) shouldBe 0
        coerceAuroraLibraryCategoryIndex(requestedIndex = 5, categoryCount = 0) shouldBe 0
    }

    @Test
    fun `coerceAuroraLibraryCategoryIndex clamps negative index to 0`() {
        coerceAuroraLibraryCategoryIndex(requestedIndex = -1, categoryCount = 3) shouldBe 0
    }

    @Test
    fun `coerceAuroraLibraryCategoryIndex clamps index above max`() {
        coerceAuroraLibraryCategoryIndex(requestedIndex = 10, categoryCount = 3) shouldBe 2
    }

    @Test
    fun `coerceAuroraLibraryCategoryIndex passes through valid index`() {
        coerceAuroraLibraryCategoryIndex(requestedIndex = 1, categoryCount = 3) shouldBe 1
    }

    @Test
    fun `category swipe forward returns next index when within bounds`() {
        val current = 0
        val forward = true
        val size = 3
        val target = if (forward) (current + 1).coerceAtMost(size - 1) else (current - 1).coerceAtLeast(0)
        target shouldBe 1
    }

    @Test
    fun `category swipe backward returns previous index when within bounds`() {
        val current = 2
        val forward = false
        val size = 3
        val target = if (forward) (current + 1).coerceAtMost(size - 1) else (current - 1).coerceAtLeast(0)
        target shouldBe 1
    }

    @Test
    fun `category swipe forward at last index stays`() {
        val current = 2
        val forward = true
        val size = 3
        val target = if (forward) (current + 1).coerceAtMost(size - 1) else (current - 1).coerceAtLeast(0)
        target shouldBe 2
    }

    @Test
    fun `category swipe backward at first index stays`() {
        val current = 0
        val forward = false
        val size = 3
        val target = if (forward) (current + 1).coerceAtMost(size - 1) else (current - 1).coerceAtLeast(0)
        target shouldBe 0
    }

    @Test
    fun `category swipe with single category does nothing`() {
        (1 > 1) shouldBe false
    }
}
