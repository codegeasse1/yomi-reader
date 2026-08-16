package eu.kanade.tachiyomi.ui.download.manga

import eu.kanade.tachiyomi.data.download.engine.DownloadSection
import eu.kanade.tachiyomi.ui.download.DownloadQueueUiModel
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Shape/contract tests for the manga download queue UI layer
 * after migration to Compose.
 */
class MangaDownloadQueueScreenModelTest {

    @Test
    fun `queue status enum values are preserved after migration`() {
        DownloadQueueUiModel.QueueStatus.IDLE.name shouldBe "IDLE"
        DownloadQueueUiModel.QueueStatus.QUEUED.name shouldBe "QUEUED"
        DownloadQueueUiModel.QueueStatus.DOWNLOADING.name shouldBe "DOWNLOADING"
        DownloadQueueUiModel.QueueStatus.DOWNLOADED.name shouldBe "DOWNLOADED"
        DownloadQueueUiModel.QueueStatus.FAILED.name shouldBe "FAILED"
    }

    @Test
    fun `manga section header maps correctly`() {
        val header = DownloadQueueUiModel.SectionHeader(
            section = DownloadSection.MANGA,
            title = "MangaSource",
            count = 15,
        )
        header.section shouldBe DownloadSection.MANGA
        header.title shouldBe "MangaSource"
        header.count shouldBe 15
    }

    @Test
    fun `manga ui item preserves chapter metadata`() {
        val item = eu.kanade.tachiyomi.ui.download.DownloadQueueUiItem(
            section = DownloadSection.MANGA,
            itemId = "ch_42",
            title = "Chapter 42",
            subtitle = "My Manga",
            description = "12 pages",
            progressFraction = 0.5f,
            progressText = "6/12",
            status = DownloadQueueUiModel.QueueStatus.DOWNLOADING,
        )
        item.section shouldBe DownloadSection.MANGA
        item.itemId shouldBe "ch_42"
        item.title shouldBe "Chapter 42"
        item.description shouldBe "12 pages"
        item.progressFraction shouldBe 0.5f
        item.progressText shouldBe "6/12"
        item.status shouldBe DownloadQueueUiModel.QueueStatus.DOWNLOADING
    }

    @Test
    fun `manga section accent distinct from other sections`() {
        DownloadSection.MANGA.accentHex shouldNotBe DownloadSection.ANIME.accentHex
        DownloadSection.MANGA.accentHex shouldNotBe DownloadSection.NOVEL.accentHex
    }

    private infix fun String.shouldNotBe(other: String) {
        kotlin.test.assertNotEquals(other, this)
    }
}
