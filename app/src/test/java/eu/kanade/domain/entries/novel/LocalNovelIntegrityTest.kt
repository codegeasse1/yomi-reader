package eu.kanade.domain.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LocalNovelIntegrityTest {

    @Test
    fun `should force chapter resync only for local sources`() {
        LocalNovelIntegrity.shouldForceLocalChapterResync(isLocalSource = true) shouldBe true
        LocalNovelIntegrity.shouldForceLocalChapterResync(isLocalSource = false) shouldBe false
    }

    @Test
    fun `detects manga local storage cover urls including SAF encoding`() {
        LocalNovelIntegrity.isMangaLocalStorageCoverUrl(
            "content://com.android.externalstorage.documents/tree/primary%3ADownload%2F123/" +
                "document/primary%3ADownload%2F123%2Flocal%2Fprocachka%2Fcover.jpg",
        ) shouldBe true

        LocalNovelIntegrity.isMangaLocalStorageCoverUrl(
            "content://.../document/primary%3ADownload%2F123%2Flocalnovel%2Fbook%2Fcover.jpg",
        ) shouldBe false

        LocalNovelIntegrity.isMangaLocalStorageCoverUrl(
            "/storage/emulated/0/Download/123/local/procachka/cover.jpg",
        ) shouldBe true

        LocalNovelIntegrity.isMangaLocalStorageCoverUrl(
            "/storage/emulated/0/Download/123/localnovel/procachka/cover.jpg",
        ) shouldBe false

        LocalNovelIntegrity.isMangaLocalStorageCoverUrl(
            "https://cdn.example/local/cover.jpg",
        ) shouldBe false

        LocalNovelIntegrity.isMangaLocalStorageCoverUrl(null) shouldBe false
        LocalNovelIntegrity.isMangaLocalStorageCoverUrl("") shouldBe false
    }

    @Test
    fun `does not treat localanime as manga local cover`() {
        LocalNovelIntegrity.isMangaLocalStorageCoverUrl(
            "content://.../document/primary%3ADownload%2F123%2Flocalanime%2Fshow%2Fcover.jpg",
        ) shouldBe false
    }

    @Test
    fun `empty chapter html detection`() {
        LocalNovelIntegrity.isEmptyChapterHtml(null) shouldBe true
        LocalNovelIntegrity.isEmptyChapterHtml("") shouldBe true
        LocalNovelIntegrity.isEmptyChapterHtml("<html><body></body></html>") shouldBe true
        LocalNovelIntegrity.isEmptyChapterHtml("<html><body>   </body></html>") shouldBe true
        LocalNovelIntegrity.isEmptyChapterHtml("<html><body><p>Chapter text</p></body></html>") shouldBe false
        LocalNovelIntegrity.shouldRecordHistoryForChapterHtml("<html><body></body></html>") shouldBe false
        LocalNovelIntegrity.shouldRecordHistoryForChapterHtml("<p>Hi</p>") shouldBe true
    }

    @Test
    fun `purge decision for empty local source`() {
        LocalNovelIntegrity.shouldPurgeChaptersOnEmptyLocalSource(
            isLocalSource = true,
            sourceChapterCount = 0,
            cachedChapterCount = 1,
        ) shouldBe true

        LocalNovelIntegrity.shouldPurgeChaptersOnEmptyLocalSource(
            isLocalSource = true,
            sourceChapterCount = 0,
            cachedChapterCount = 0,
        ) shouldBe false

        LocalNovelIntegrity.shouldPurgeChaptersOnEmptyLocalSource(
            isLocalSource = false,
            sourceChapterCount = 0,
            cachedChapterCount = 5,
        ) shouldBe false

        LocalNovelIntegrity.shouldPurgeChaptersOnEmptyLocalSource(
            isLocalSource = true,
            sourceChapterCount = 3,
            cachedChapterCount = 3,
        ) shouldBe false
    }

    @Test
    fun `sanitize local novel thumbnail clears manga local covers`() {
        LocalNovelIntegrity.sanitizeLocalNovelThumbnailUrl(
            "content://x/document/primary%3ADownload%2Flocal%2Fprocachka%2Fcover.jpg",
        ) shouldBe ""

        LocalNovelIntegrity.sanitizeLocalNovelThumbnailUrl(
            "content://x/document/primary%3ADownload%2Flocalnovel%2Fbook.jpg",
        ) shouldBe "content://x/document/primary%3ADownload%2Flocalnovel%2Fbook.jpg"

        LocalNovelIntegrity.sanitizeLocalNovelThumbnailUrl(null) shouldBe null
    }
}
