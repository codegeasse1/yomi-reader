package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Files

class NovelReaderChapterDiskCacheTest {

    @Test
    fun `stores and returns chapter html`() {
        val dir = Files.createTempDirectory("novel-reader-cache-test")
        try {
            val cache = NovelReaderChapterDiskCache(
                directory = dir.toFile(),
                maxEntries = 4,
                maxTotalBytes = 1024 * 1024,
                maxEntryBytes = 256 * 1024,
            )

            cache.put(chapterId = 10L, html = "<p>Hello cache</p>")

            cache.get(10L) shouldBe "<p>Hello cache</p>"
            cache.contains(10L) shouldBe true
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `evicts oldest entries when max entries exceeded`() {
        val dir = Files.createTempDirectory("novel-reader-cache-test")
        try {
            val cache = NovelReaderChapterDiskCache(
                directory = dir.toFile(),
                maxEntries = 2,
                maxTotalBytes = 1024 * 1024,
                maxEntryBytes = 256 * 1024,
            )

            cache.put(chapterId = 1L, html = "<p>one</p>")
            cache.put(chapterId = 2L, html = "<p>two</p>")
            cache.put(chapterId = 3L, html = "<p>three</p>")

            cache.contains(1L) shouldBe false
            cache.contains(2L) shouldBe true
            cache.contains(3L) shouldBe true
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `reports cache stats for current entries`() {
        val dir = Files.createTempDirectory("novel-reader-cache-test")
        try {
            val cache = NovelReaderChapterDiskCache(
                directory = dir.toFile(),
                maxEntries = 10,
                maxTotalBytes = 1024 * 1024,
                maxEntryBytes = 256 * 1024,
            )

            cache.put(chapterId = 1L, html = "<p>one</p>")
            cache.put(chapterId = 2L, html = "<p>two</p>")

            val stats = cache.stats()
            stats.entryCount shouldBe 2
            (stats.totalBytes > 0L) shouldBe true
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `does not evict entries when unlimited cache is enabled`() {
        val dir = Files.createTempDirectory("novel-reader-cache-test")
        try {
            val cache = NovelReaderChapterDiskCache(
                directory = dir.toFile(),
                maxEntries = 1,
                maxTotalBytes = 1,
                maxEntryBytes = 256 * 1024,
                unlimited = true,
            )

            cache.put(chapterId = 1L, html = "<p>one</p>")
            cache.put(chapterId = 2L, html = "<p>two</p>")

            cache.contains(1L) shouldBe true
            cache.contains(2L) shouldBe true
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
