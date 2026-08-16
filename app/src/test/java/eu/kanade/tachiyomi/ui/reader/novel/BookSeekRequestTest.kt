package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BookSeekRequestTest {

    private fun seekRequest(id: Long, reason: BookSeekReason = BookSeekReason.Seekbar) =
        BookSeekRequest(
            id = id,
            locator = BookLocator(chapterId = 7L, blockIndex = 3, charOffset = 120),
            location = NovelBookLocation(sectionIndex = 3, charOffset = 12_000),
            reason = reason,
        )

    @Test
    fun `no request means nothing to apply`() {
        shouldApplyBookSeek(request = null, lastAppliedSeekId = 0L) shouldBe false
    }

    @Test
    fun `a fresh request is applied`() {
        shouldApplyBookSeek(request = seekRequest(id = 1L), lastAppliedSeekId = 0L) shouldBe true
    }

    @Test
    fun `the same request is never applied twice`() {
        val request = seekRequest(id = 4L)
        shouldApplyBookSeek(request = request, lastAppliedSeekId = 4L) shouldBe false
    }

    @Test
    fun `a recomposition with an older request is ignored`() {
        // Requests are monotonic, so an id that is not ahead of the applied one can only be a
        // repeat of something the renderer already did.
        shouldApplyBookSeek(request = seekRequest(id = 2L), lastAppliedSeekId = 5L) shouldBe false
    }

    @Test
    fun `the reason does not change whether a request is applied`() {
        BookSeekReason.entries.forEach { reason ->
            shouldApplyBookSeek(
                request = seekRequest(id = 9L, reason = reason),
                lastAppliedSeekId = 8L,
            ) shouldBe true
        }
    }
}
