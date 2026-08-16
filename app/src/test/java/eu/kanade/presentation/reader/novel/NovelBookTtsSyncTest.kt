package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBlockAnchor
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsSegment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import org.junit.Test

class NovelBookTtsSyncTest {

    @Test
    fun `the script addresses the block instead of searching for its text`() {
        val script = buildBookTtsAnchorSyncJavascript(anchorDomId = "12:5", chapterId = 12L, sectionIndex = 3)

        script shouldContain "document.querySelector('[data-an-b=\"' + anchor + '\"]')"
        script shouldContain "var anchor = '12:5';"
        script shouldContain "var chapterPrefix = '12:';"
        script shouldNotContain "textContent"
    }

    @Test
    fun `an empty anchor only clears the highlight`() {
        val script = buildBookTtsAnchorSyncJavascript(anchorDomId = null, chapterId = null, sectionIndex = null)

        script shouldContain "removeAttribute('data-an-tts-highlight')"
        script shouldContain "return 'cleared';"
    }

    @Test
    fun `the script answers whether it landed`() {
        isBookTtsSyncApplied("\"scrolled\"") shouldBe true
        isBookTtsSyncApplied("\"cleared\"") shouldBe true
        isBookTtsSyncApplied("\"not-found\"") shouldBe false
        isBookTtsSyncApplied(null) shouldBe false
    }

    @Test
    fun `the webview renderer receives the anchor of the spoken block`() {
        runBlocking {
            val surface = RecordingBookScrollSurface()
            val adapter = BookTtsNavigationAdapter(
                surface = { surface },
                sectionIndexForChapter = { 2 },
            )

            adapter.syncToSegment(segment(chapterId = 8L, blockIndex = 4))

            surface.lastScript.orEmpty() shouldContain "var anchor = '8:4';"
            adapter.unresolvedAnchor shouldBe null
        }
    }

    @Test
    fun `a webview section that is not in the document yet keeps the anchor pending`() {
        runBlocking {
            val surface = RecordingBookScrollSurface(result = "\"not-found\"")
            val adapter = BookTtsNavigationAdapter(
                surface = { surface },
                sectionIndexForChapter = { null },
            )

            adapter.syncToSegment(segment(chapterId = 8L, blockIndex = 4))

            adapter.unresolvedAnchor shouldBe NovelBlockAnchor(chapterId = 8L, blockIndex = 4)
        }
    }

    @Test
    fun `the pending anchor is replayed once its section arrives`() {
        runBlocking {
            val surface = RecordingBookScrollSurface(result = "\"not-found\"")
            var sectionIndex: Int? = null
            val adapter = BookTtsNavigationAdapter(
                surface = { surface },
                sectionIndexForChapter = { sectionIndex },
            )
            adapter.syncToSegment(segment(chapterId = 8L, blockIndex = 4))
            adapter.unresolvedAnchor shouldBe NovelBlockAnchor(chapterId = 8L, blockIndex = 4)

            // The section mounts and the document can answer now.
            sectionIndex = 5
            surface.result = "\"scrolled\""
            adapter.retryPendingAnchor()

            adapter.unresolvedAnchor shouldBe null
            surface.lastScript.orEmpty() shouldContain "var sectionSelector = '#"
            surface.scriptCount shouldBe 2
        }
    }

    @Test
    fun `a book document that is not mounted yet keeps the anchor pending`() {
        runBlocking {
            val adapter = BookTtsNavigationAdapter(
                surface = { null },
                sectionIndexForChapter = { 1 },
            )

            adapter.syncToSegment(segment(chapterId = 3L, blockIndex = 2))

            adapter.unresolvedAnchor shouldBe NovelBlockAnchor(chapterId = 3L, blockIndex = 2)
            adapter.requestedAnchor shouldBe NovelBlockAnchor(chapterId = 3L, blockIndex = 2)
        }
    }

    @Test
    fun `the native renderer is told the block and scrolled to its section`() {
        runBlocking {
            val surface = RecordingBookScrollSurface()
            var published: NovelBlockAnchor? = null
            var scrolledSection: Int? = null
            val adapter = BookTtsNavigationAdapter(
                surface = { surface },
                sectionIndexForChapter = { 5 },
                scrollNativeToSection = {
                    scrolledSection = it
                    true
                },
                onNativeAnchor = { published = it },
                isNativeRenderer = { true },
            )

            adapter.syncToSegment(segment(chapterId = 8L, blockIndex = 4))

            published shouldBe NovelBlockAnchor(chapterId = 8L, blockIndex = 4)
            scrolledSection shouldBe 5
            surface.lastScript shouldBe null
            adapter.unresolvedAnchor shouldBe null
        }
    }

    @Test
    fun `a native section that is not resident yet is retried after it mounts`() {
        runBlocking {
            var resident = false
            var scrolledSection: Int? = null
            val adapter = BookTtsNavigationAdapter(
                surface = { null },
                sectionIndexForChapter = { 5 },
                scrollNativeToSection = { index ->
                    if (resident) {
                        scrolledSection = index
                        true
                    } else {
                        false
                    }
                },
                isNativeRenderer = { true },
            )
            adapter.syncToSegment(segment(chapterId = 8L, blockIndex = 4))

            scrolledSection shouldBe null
            adapter.unresolvedAnchor shouldBe NovelBlockAnchor(chapterId = 8L, blockIndex = 4)

            resident = true
            adapter.retryPendingAnchor()

            scrolledSection shouldBe 5
            adapter.unresolvedAnchor shouldBe null
        }
    }

    @Test
    fun `a block that is already laid out needs no section scroll`() {
        runBlocking {
            val adapter = BookTtsNavigationAdapter(
                surface = { null },
                sectionIndexForChapter = { null },
                isNativeRenderer = { true },
                isNativeBlockLaidOut = { true },
            )

            adapter.syncToSegment(segment(chapterId = 8L, blockIndex = 4))

            adapter.unresolvedAnchor shouldBe null
        }
    }

    @Test
    fun `retrying without a pending anchor does nothing`() {
        runBlocking {
            val surface = RecordingBookScrollSurface()
            val adapter = BookTtsNavigationAdapter(
                surface = { surface },
                sectionIndexForChapter = { 1 },
            )
            adapter.syncToSegment(segment(chapterId = 8L, blockIndex = 4))

            adapter.retryPendingAnchor()

            surface.scriptCount shouldBe 1
        }
    }

    @Test
    fun `a manual anchor keeps the block the voice reached`() {
        runBlocking {
            val adapter = BookTtsNavigationAdapter(
                surface = { RecordingBookScrollSurface() },
                sectionIndexForChapter = { 0 },
            )
            adapter.syncToSegment(segment(chapterId = 3L, blockIndex = 9))

            val anchor = adapter.captureManualAnchor(scrollOffsetPx = 12)

            anchor.chapterId shouldBe 3L
            anchor.blockIndex shouldBe 9
            anchor.scrollOffsetPx shouldBe 12
        }
    }

    private fun segment(chapterId: Long, blockIndex: Int): NovelTtsSegment = NovelTtsSegment(
        id = "segment-1",
        chapterId = chapterId,
        text = "Spoken text",
        sourceBlockIndex = blockIndex,
        blockIndex = blockIndex,
        firstUtteranceIndex = 0,
        lastUtteranceIndex = 0,
        wordRangeCount = 1,
    )

    private class RecordingBookScrollSurface(
        var result: String? = "\"scrolled\"",
    ) : NovelBookScrollSurface {
        var lastScript: String? = null
        var scriptCount: Int = 0

        override fun isPaginated(): Boolean = false

        override fun canScrollForward(): Boolean = true

        override fun scrollBy(distancePx: Int): Int = 0

        override fun step(forward: Boolean) = Unit

        override fun evaluate(script: String, onResult: (String?) -> Unit) {
            lastScript = script
            scriptCount += 1
            onResult(result)
        }
    }
}
