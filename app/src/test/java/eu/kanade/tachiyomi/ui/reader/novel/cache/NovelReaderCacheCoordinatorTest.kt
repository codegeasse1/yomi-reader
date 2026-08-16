package eu.kanade.tachiyomi.ui.reader.novel.cache

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelReaderCacheCoordinatorTest {

    private val globalBudgetBytes = 32L * 1024 * 1024 // 32 MB

    @Test
    fun `tracks total bytes across registered caches`() {
        val coordinator = NovelReaderCacheCoordinator(maxTotalBytes = globalBudgetBytes)
        val cacheA = FakeSizeReportingCache("a", 1_000_000L)
        val cacheB = FakeSizeReportingCache("b", 2_000_000L)

        coordinator.register(cacheA)
        coordinator.register(cacheB)

        (coordinator.totalBytes() == 3_000_000L) shouldBe true
    }

    @Test
    fun `trims oldest caches when global budget exceeded`() {
        val coordinator = NovelReaderCacheCoordinator(maxTotalBytes = 5_000_000L)
        val cacheA = FakeSizeReportingCache("a", 3_000_000L) // older
        val cacheB = FakeSizeReportingCache("b", 3_000_000L) // newer

        coordinator.register(cacheA)
        Thread.sleep(5) // ensure distinct registration times
        coordinator.register(cacheB)

        // total 6MB > 5MB budget => should trim oldest (cacheA)
        (coordinator.totalBytes() <= 5_000_000L) shouldBe true
        cacheA.wasTrimmed shouldBe true
    }

    @Test
    fun `does not trim when under budget`() {
        val coordinator = NovelReaderCacheCoordinator(maxTotalBytes = 10_000_000L)
        val cacheA = FakeSizeReportingCache("a", 3_000_000L)
        val cacheB = FakeSizeReportingCache("b", 2_000_000L)

        coordinator.register(cacheA)
        coordinator.register(cacheB)

        cacheA.wasTrimmed shouldBe false
        cacheB.wasTrimmed shouldBe false
    }

    @Test
    fun `unregister removes cache from tracking`() {
        val coordinator = NovelReaderCacheCoordinator(maxTotalBytes = globalBudgetBytes)
        val cacheA = FakeSizeReportingCache("a", 5_000_000L)
        val cacheB = FakeSizeReportingCache("b", 2_000_000L)

        coordinator.register(cacheA)
        coordinator.register(cacheB)
        coordinator.unregister("a")

        coordinator.totalBytes() shouldBe 2_000_000L
    }

    @Test
    fun `dispose trims all registered caches`() {
        val coordinator = NovelReaderCacheCoordinator(maxTotalBytes = globalBudgetBytes)
        val cacheA = FakeSizeReportingCache("a", 3_000_000L)
        val cacheB = FakeSizeReportingCache("b", 2_000_000L)
        coordinator.register(cacheA)
        coordinator.register(cacheB)

        coordinator.dispose()

        cacheA.wasDisposed shouldBe true
        cacheB.wasDisposed shouldBe true
    }
}

private class FakeSizeReportingCache(
    val name: String,
    initialBytes: Long,
) : NovelReaderCacheReporter {
    var wasTrimmed = false
        private set
    var wasDisposed = false
        private set
    private var currentBytesValue = initialBytes

    override fun cacheId(): String = name
    override fun currentBytes(): Long = currentBytesValue
    override fun trimToTargetBytes(targetBytes: Long) {
        wasTrimmed = true
        if (targetBytes < currentBytesValue) {
            currentBytesValue = targetBytes
        }
    }
    override fun dispose() {
        wasDisposed = true
    }
}
