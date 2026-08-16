package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Files

class NovelBookSectionDiskCacheTest {

    private fun withCache(
        config: NovelBookSectionDiskCacheConfig = NovelBookSectionDiskCacheConfig(),
        block: (NovelBookSectionDiskCache) -> Unit,
    ) {
        val dir = Files.createTempDirectory("novel-book-section-cache-test")
        try {
            block(
                NovelBookSectionDiskCache(
                    directory = dir.toFile(),
                    configProvider = { config },
                ),
            )
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun section(html: String, baseUrl: String? = null) =
        NovelBookPreparedSection(html = html, baseUrl = baseUrl)

    @Test
    fun `stores and returns a prepared section`() {
        withCache { cache ->
            cache.write(KEY, section("<p>five</p>", "https://example.org/5/"))

            cache.read(KEY) shouldBe section("<p>five</p>", "https://example.org/5/")
            cache.contains(KEY) shouldBe true
        }
    }

    @Test
    fun `a section without a base url round trips`() {
        withCache { cache ->
            cache.write(KEY, section("<p>five</p>"))

            cache.read(KEY) shouldBe section("<p>five</p>")
        }
    }

    @Test
    fun `blank html is never stored`() {
        withCache { cache ->
            cache.write(KEY, section("   "))

            cache.contains(KEY) shouldBe false
            cache.read(KEY) shouldBe null
        }
    }

    @Test
    fun `missing keys read as null`() {
        withCache { cache ->
            cache.read(KEY) shouldBe null
            cache.contains(KEY) shouldBe false
        }
    }

    @Test
    fun `keys of different variants do not share an entry`() {
        withCache { cache ->
            cache.write("novel1-chapters-h1-5", section("<p>with heading</p>"))
            cache.write("novel1-chapters-h0-5", section("<p>without heading</p>"))
            cache.write("novel1-artifact-h1-5", section("<p>artifact</p>"))

            cache.read("novel1-chapters-h1-5") shouldBe section("<p>with heading</p>")
            cache.read("novel1-chapters-h0-5") shouldBe section("<p>without heading</p>")
            cache.read("novel1-artifact-h1-5") shouldBe section("<p>artifact</p>")
            cache.stats().entryCount shouldBe 3
        }
    }

    @Test
    fun `an oversized section is not stored`() {
        withCache(NovelBookSectionDiskCacheConfig(maxEntryBytes = 1L)) { cache ->
            cache.write(KEY, section("<p>five</p>"))

            cache.contains(KEY) shouldBe false
        }
    }

    @Test
    fun `remove drops a single entry`() {
        withCache { cache ->
            cache.write(KEY, section("<p>five</p>"))
            cache.write(OTHER_KEY, section("<p>six</p>"))

            cache.remove(KEY)

            cache.contains(KEY) shouldBe false
            cache.contains(OTHER_KEY) shouldBe true
        }
    }

    @Test
    fun `removeScope drops only the entries of that scope`() {
        withCache { cache ->
            cache.write("novel1-chapters-h1-5", section("<p>one</p>"))
            cache.write("novel1-chapters-h1-6", section("<p>two</p>"))
            cache.write("novel2-chapters-h1-5", section("<p>three</p>"))

            cache.removeScope("novel1-")

            cache.contains("novel1-chapters-h1-5") shouldBe false
            cache.contains("novel1-chapters-h1-6") shouldBe false
            cache.contains("novel2-chapters-h1-5") shouldBe true
        }
    }

    @Test
    fun `removeScopeExcept keeps only the entries of the current transform signature`() {
        withCache { cache ->
            cache.write("novel1-artifact-p1-h1-raw-s0", section("<p>current</p>"))
            cache.write("novel1-artifact-p1-h1-gemini-s0", section("<p>stale translation</p>"))
            cache.write("novel1-artifact-p1-h0-raw-s0", section("<p>stale headings</p>"))
            cache.write("novel2-artifact-p1-h1-gemini-s0", section("<p>other novel</p>"))

            cache.removeScopeExcept(scopePrefix = "novel1-", keepPrefix = "novel1-artifact-p1-h1-raw-")

            cache.contains("novel1-artifact-p1-h1-raw-s0") shouldBe true
            cache.contains("novel1-artifact-p1-h1-gemini-s0") shouldBe false
            cache.contains("novel1-artifact-p1-h0-raw-s0") shouldBe false
            cache.contains("novel2-artifact-p1-h1-gemini-s0") shouldBe true
        }
    }

    @Test
    fun `a blank scope prefix removes nothing`() {
        withCache { cache ->
            cache.write(KEY, section("<p>five</p>"))

            cache.removeScope("  ")

            cache.contains(KEY) shouldBe true
        }
    }

    @Test
    fun `writing prunes down to the entry limit`() {
        withCache(NovelBookSectionDiskCacheConfig(maxEntries = 2)) { cache ->
            cache.write("novel1-chapters-h1-1", section("<p>one</p>"))
            cache.write("novel1-chapters-h1-2", section("<p>two</p>"))
            cache.write("novel1-chapters-h1-3", section("<p>three</p>"))

            cache.stats().entryCount shouldBe 2
        }
    }

    @Test
    fun `writing prunes down to the byte limit`() {
        withCache(NovelBookSectionDiskCacheConfig(maxTotalBytes = 1L)) { cache ->
            cache.write(KEY, section("<p>five</p>"))

            cache.stats().entryCount shouldBe 0
            cache.read(KEY) shouldBe null
        }
    }

    @Test
    fun `an unlimited cache keeps everything`() {
        val config = NovelBookSectionDiskCacheConfig(
            maxEntries = 1,
            maxTotalBytes = 1L,
            unlimited = true,
        )
        withCache(config) { cache ->
            cache.write("novel1-chapters-h1-1", section("<p>one</p>"))
            cache.write("novel1-chapters-h1-2", section("<p>two</p>"))

            cache.contains("novel1-chapters-h1-1") shouldBe true
            cache.contains("novel1-chapters-h1-2") shouldBe true
        }
    }

    @Test
    fun `stats and clear cover the whole cache`() {
        withCache { cache ->
            cache.write(KEY, section("<p>five</p>"))
            cache.write(OTHER_KEY, section("<p>six</p>"))

            val stats = cache.stats()
            stats.entryCount shouldBe 2
            (stats.totalBytes > 0L) shouldBe true

            cache.clear()

            cache.stats() shouldBe NovelBookSectionDiskCacheStats(entryCount = 0, totalBytes = 0L)
        }
    }

    @Test
    fun `trimToLimits applies a tighter limit later`() {
        withCache { cache ->
            cache.write(KEY, section("<p>five</p>"))
            cache.write(OTHER_KEY, section("<p>six</p>"))

            cache.trimToLimits(NovelBookSectionDiskCacheConfig(maxEntries = 1))

            cache.stats().entryCount shouldBe 1
        }
    }

    private companion object {
        const val KEY = "novel1-chapters-h1-5"
        const val OTHER_KEY = "novel1-chapters-h1-6"
    }
}
