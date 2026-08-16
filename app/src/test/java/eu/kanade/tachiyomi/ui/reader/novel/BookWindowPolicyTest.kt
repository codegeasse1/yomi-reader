package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.Test

class BookWindowPolicyTest {

    @Test
    fun `the resident window is centered on the reading position`() {
        val policy = BookWindowPolicy(residentRadius = 2)

        policy.residentSections(center = 5, sectionCount = 20) shouldBe listOf(3, 4, 5, 6, 7)
    }

    @Test
    fun `the resident window is clamped at the book edges`() {
        val policy = BookWindowPolicy(residentRadius = 2)

        policy.residentSections(center = 0, sectionCount = 20) shouldBe listOf(0, 1, 2)
        policy.residentSections(center = 19, sectionCount = 20) shouldBe listOf(17, 18, 19)
        policy.residentSections(center = 0, sectionCount = 0) shouldBe emptyList()
    }

    @Test
    fun `the engine keeps exactly as many sections as the policy declares`() {
        // The engine used to prune with a hardcoded `> 5`; the default policy has to describe the
        // same window, otherwise moving the knob here would silently change reading behaviour.
        BookWindowPolicy.DEFAULT.residentSectionCount shouldBe 5
    }

    @Test
    fun `prefetching serves the resident window before looking ahead`() {
        val policy = BookWindowPolicy(residentRadius = 1, prefetchAhead = 2, prefetchBehind = 1)

        policy.prefetchOrder(center = 5, sectionCount = 20) shouldBe listOf(5, 4, 6, 7, 8, 3)
    }

    @Test
    fun `sections that are prepared or already loading are not requested again`() {
        val policy = BookWindowPolicy(residentRadius = 1, prefetchAhead = 2, prefetchBehind = 0)

        val order = policy.prefetchOrder(
            center = 5,
            sectionCount = 20,
            prepared = setOf(5, 6),
            inFlight = setOf(7),
        )

        order shouldBe listOf(4, 8)
    }

    @Test
    fun `the loader is never handed more work than it can run at once`() {
        val policy = BookWindowPolicy(maxConcurrentPrefetch = 2)
        val order = listOf(5, 6, 4, 7)

        policy.nextPrefetchBatch(order, inFlightCount = 0) shouldBe listOf(5, 6)
        policy.nextPrefetchBatch(order, inFlightCount = 1) shouldBe listOf(5)
        policy.nextPrefetchBatch(order, inFlightCount = 2) shouldBe emptyList()
    }

    @Test
    fun `only sections outside the cache window are released, furthest away first`() {
        val policy = BookWindowPolicy(residentRadius = 1, prefetchAhead = 1, prefetchBehind = 1)

        val released = policy.releasableSections(
            center = 5,
            sectionCount = 20,
            prepared = setOf(0, 3, 5, 7, 12),
        )

        released shouldBe listOf(12, 0)
    }

    @Test
    fun `the window is sized by text, not by how the book happens to be sliced`() {
        // 40k-char blocks: seven sections hold about 240k characters around the reader.
        val blocks = BookWindowPolicy.forBlockChars(40_000)
        // Whole chapters are much longer, so fewer of them are kept resident for the same text.
        val chapters = BookWindowPolicy.forBlockChars(120_000)

        blocks.residentRadius shouldBe 3
        chapters.residentRadius shouldBe 2
        BookWindowPolicy.forBlockChars(0) shouldBe BookWindowPolicy.DEFAULT
    }

    @Test
    fun `a tiny section size cannot blow the window up without bound`() {
        BookWindowPolicy.forBlockChars(1).residentRadius shouldBe 8
    }
}
