package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class NovelBookSectionLoaderTest {

    private fun store(): NovelBookSectionStore {
        val disk = mutableMapOf<Long, String>()
        return NovelBookSectionStore(
            diskRead = { disk[it] },
            diskWrite = { chapterId, html -> disk[chapterId] = html },
        )
    }

    @Test
    fun `prepares a section once and serves it from the store afterwards`() = runTest {
        val fetchCount = AtomicInteger(0)
        val loader = NovelBookSectionLoader(store = store()) { chapterId ->
            fetchCount.incrementAndGet()
            "<p>chapter $chapterId</p>"
        }

        val first = loader.prepare(7L)
        val second = loader.prepare(7L)

        first shouldBe NovelBookSectionResult.Ready(7L, "<p>chapter 7</p>")
        second shouldBe first
        fetchCount.get() shouldBe 1
        loader.isPrepared(7L) shouldBe true
        loader.preparedHtml(7L) shouldBe "<p>chapter 7</p>"
    }

    @Test
    fun `concurrent requests for the same section share a single load`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fetchCount = AtomicInteger(0)
        val loader = NovelBookSectionLoader(store = store()) { chapterId ->
            fetchCount.incrementAndGet()
            gate.await()
            "<p>chapter $chapterId</p>"
        }

        val first = async { loader.prepare(3L) }
        val second = async { loader.prepare(3L) }
        gate.complete(Unit)

        first.await() shouldBe NovelBookSectionResult.Ready(3L, "<p>chapter 3</p>")
        second.await() shouldBe NovelBookSectionResult.Ready(3L, "<p>chapter 3</p>")
        fetchCount.get() shouldBe 1
        loader.inFlightSectionKeys shouldBe emptySet()
    }

    @Test
    fun `failures are reported and remembered without caching content`() = runTest {
        val loader = NovelBookSectionLoader(store = store()) { error("boom") }

        val result = loader.prepare(11L)

        result shouldBe NovelBookSectionResult.Failed(11L, "boom")
        loader.failedSectionKeys shouldBe setOf(11L)
        loader.isPrepared(11L) shouldBe false
        loader.inFlightSectionKeys shouldBe emptySet()
    }

    @Test
    fun `blank content is treated as a failure`() = runTest {
        val loader = NovelBookSectionLoader(store = store()) { "   " }

        val result = loader.prepare(5L)

        result shouldBe NovelBookSectionResult.Failed(5L, NovelBookSectionLoader.EMPTY_SECTION_MESSAGE)
        loader.isPrepared(5L) shouldBe false
    }

    @Test
    fun `retry clears a previous failure`() = runTest {
        val attempts = AtomicInteger(0)
        val loader = NovelBookSectionLoader(store = store()) { chapterId ->
            if (attempts.incrementAndGet() == 1) error("offline") else "<p>chapter $chapterId</p>"
        }

        loader.prepare(9L) shouldBe NovelBookSectionResult.Failed(9L, "offline")
        loader.retry(9L) shouldBe NovelBookSectionResult.Ready(9L, "<p>chapter 9</p>")
        loader.failedSectionKeys shouldBe emptySet()
        attempts.get() shouldBe 2
    }

    @Test
    fun `force reload refetches an already prepared section`() = runTest {
        val attempts = AtomicInteger(0)
        val loader = NovelBookSectionLoader(store = store()) { chapterId ->
            "<p>chapter $chapterId v${attempts.incrementAndGet()}</p>"
        }

        loader.prepare(2L) shouldBe NovelBookSectionResult.Ready(2L, "<p>chapter 2 v1</p>")
        loader.prepare(2L, forceReload = true) shouldBe NovelBookSectionResult.Ready(2L, "<p>chapter 2 v2</p>")
        loader.preparedHtml(2L) shouldBe "<p>chapter 2 v2</p>"
    }

    @Test
    fun `release drops a prepared section from memory`() = runTest {
        val loader = NovelBookSectionLoader(store = store()) { chapterId -> "<p>chapter $chapterId</p>" }

        loader.prepare(4L)
        loader.release(4L)

        loader.preparedHtml(4L) shouldBe "<p>chapter 4</p>"
    }

    @Test
    fun `clear releases resident sections and forgets failures`() = runTest {
        val loader = NovelBookSectionLoader(store = store()) { chapterId ->
            if (chapterId == 1L) "<p>chapter 1</p>" else error("boom")
        }

        loader.prepare(1L)
        loader.prepare(2L)
        loader.clear()

        loader.failedSectionKeys shouldBe emptySet()
        // Prepared sections stay on disk so an offline-prepared book survives a memory reset.
        loader.preparedHtml(1L) shouldBe "<p>chapter 1</p>"
        loader.preparedHtml(2L) shouldBe null
    }
}
