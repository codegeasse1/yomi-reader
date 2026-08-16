package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelBookSpineTest {

    private fun chapters(count: Int): List<NovelChapter> = (1..count).map { index ->
        NovelChapter.create().copy(id = index.toLong(), name = "Chapter $index")
    }

    @Test
    fun `empty chapter list produces an empty spine`() {
        val spine = testSpineOf(emptyList())

        spine.isEmpty shouldBe true
        spine.totalCharCount shouldBe 0
        spine.progressOf(NovelBookLocation.START) shouldBe 0f
        spine.locationOf(1_000) shouldBe NovelBookLocation.START
    }

    @Test
    fun `sections without an explicit length use the fixture default`() {
        val spine = testSpineOf(chapters(3))

        spine.sections.size shouldBe 3
        spine.measuredSectionCount shouldBe 3
        spine.totalCharCount shouldBe TEST_SECTION_CHAR_COUNT * 3
        spine.sectionAt(1)?.name shouldBe "Chapter 2"
    }

    @Test
    fun `explicit lengths win over the fixture default`() {
        val spine = testSpineOf(
            chapters = chapters(4),
            charCounts = mapOf(1L to 1_000, 2L to 3_000),
        )

        spine.sectionAt(0)?.charCount shouldBe 1_000
        spine.sectionAt(1)?.charCount shouldBe 3_000
        spine.sectionAt(2)?.charCount shouldBe TEST_SECTION_CHAR_COUNT
        spine.sectionAt(2)?.isMeasured shouldBe true
        spine.measuredSectionCount shouldBe 4
        spine.totalCharCount shouldBe 1_000 + 3_000 + TEST_SECTION_CHAR_COUNT * 2
    }

    @Test
    fun `location and global offset round trip`() {
        val spine = testSpineOf(
            chapters = chapters(3),
            charCounts = mapOf(1L to 100, 2L to 200, 3L to 300),
        )

        spine.startOffsetOf(0) shouldBe 0
        spine.startOffsetOf(1) shouldBe 100
        spine.startOffsetOf(2) shouldBe 300

        spine.globalOffsetOf(NovelBookLocation(1, 50)) shouldBe 150
        spine.locationOf(150) shouldBe NovelBookLocation(1, 50)
        spine.locationOf(0) shouldBe NovelBookLocation(0, 0)
        spine.locationOf(100) shouldBe NovelBookLocation(1, 0)
        spine.locationOf(299) shouldBe NovelBookLocation(1, 199)
        spine.locationOf(300) shouldBe NovelBookLocation(2, 0)
    }

    @Test
    fun `out of range values are clamped instead of throwing`() {
        val spine = testSpineOf(
            chapters = chapters(2),
            charCounts = mapOf(1L to 100, 2L to 100),
        )

        spine.clampLocation(NovelBookLocation(9, 9_999)) shouldBe NovelBookLocation(1, 99)
        spine.clampLocation(NovelBookLocation(-5, -5)) shouldBe NovelBookLocation(0, 0)
        spine.locationOf(-10) shouldBe NovelBookLocation(0, 0)
        spine.locationOf(10_000) shouldBe NovelBookLocation(1, 99)
    }

    @Test
    fun `progress is measured against the whole book`() {
        val spine = testSpineOf(
            chapters = chapters(4),
            charCounts = mapOf(1L to 100, 2L to 100, 3L to 100, 4L to 100),
        )

        spine.progressOf(NovelBookLocation(0, 0)) shouldBe 0f
        spine.progressOf(NovelBookLocation(2, 0)) shouldBe 0.5f
        spine.progressOf(NovelBookLocation(3, 99)) shouldBe 0.9975f
        spine.sectionProgressOf(NovelBookLocation(2, 50)) shouldBe 0.5f
    }

    @Test
    fun `artifact sections carry exact measured lengths`() {
        val spine = testSpineOf(
            chapters = chapters(3),
            charCounts = mapOf(1L to 1_000, 2L to 3_000, 3L to 2_000),
        )

        spine.sectionAt(0)?.charCount shouldBe 1_000
        spine.sectionAt(0)?.isMeasured shouldBe true
        spine.startOffsetOf(1) shouldBe 1_000
        spine.measuredSectionCount shouldBe 3
        spine.totalCharCount shouldBe 6_000
    }

    @Test
    fun `legacy chapter progress maps onto a book location`() {
        val spine = testSpineOf(
            chapters = chapters(3),
            charCounts = mapOf(1L to 100, 2L to 200, 3L to 300),
        )

        spine.locationFor(chapterId = 2L) shouldBe NovelBookLocation(1, 0)
        spine.locationFor(chapterId = 42L) shouldBe null
        spine.locationForChapterFraction(chapterId = 2L, fraction = 0.5f) shouldBe NovelBookLocation(1, 100)
        spine.locationForChapterFraction(chapterId = 2L, fraction = 5f) shouldBe NovelBookLocation(1, 199)
        spine.locationForChapterFraction(chapterId = 2L, fraction = -1f) shouldBe NovelBookLocation(1, 0)
        spine.locationForChapterFraction(chapterId = 42L, fraction = 0.5f) shouldBe null
    }

    @Test
    fun `resident window is centered and clamped to the spine`() {
        val spine = testSpineOf(chapters(6))

        spine.windowAround(sectionIndex = 3, radius = 2) shouldBe listOf(1, 2, 3, 4, 5)
        spine.windowAround(sectionIndex = 0, radius = 2) shouldBe listOf(0, 1, 2)
        spine.windowAround(sectionIndex = 5, radius = 2) shouldBe listOf(3, 4, 5)
        spine.windowAround(sectionIndex = 99, radius = 1) shouldBe listOf(4, 5)
        spine.windowAround(sectionIndex = 2, radius = 0) shouldBe listOf(2)
        NovelBookSpine.EMPTY.windowAround(sectionIndex = 0, radius = 3) shouldBe emptyList()
    }

    @Test
    fun `index lookups resolve chapter ids`() {
        val spine = testSpineOf(chapters(3))

        spine.indexOf(2L) shouldBe 1
        spine.indexOf(404L) shouldBe -1
        spine.sectionOf(3L)?.index shouldBe 2
        spine.sectionOf(404L) shouldBe null
    }

    @Test
    fun `section fractions describe the whole book in one size domain`() {
        val spine = testSpineOf(
            chapters = chapters(4),
            charCounts = mapOf(1L to 100, 2L to 100, 3L to 100, 4L to 100),
        )

        spine.sectionFractions shouldBe listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        spine.sectionStartFraction(2) shouldBe 0.5f
        spine.sectionStartFraction(99) shouldBe 0f
        NovelBookSpine.EMPTY.sectionFractions shouldBe listOf(0f)
    }
}
