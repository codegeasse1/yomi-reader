package eu.kanade.tachiyomi.ui.download.anime

import eu.kanade.tachiyomi.data.download.engine.DownloadSection
import eu.kanade.tachiyomi.ui.download.DownloadQueueUiModel
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Shape/contract tests for the anime download queue UI layer
 * after migration to Compose.  Tests the mapper and UI model
 * patterns without needing a running RecyclerView or Injekt.
 */
class AnimeDownloadQueueScreenModelTest {

    @Test
    fun `queue status enum values are preserved`() {
        DownloadQueueUiModel.QueueStatus.IDLE.name shouldBe "IDLE"
        DownloadQueueUiModel.QueueStatus.QUEUED.name shouldBe "QUEUED"
        DownloadQueueUiModel.QueueStatus.DOWNLOADING.name shouldBe "DOWNLOADING"
        DownloadQueueUiModel.QueueStatus.DOWNLOADED.name shouldBe "DOWNLOADED"
        DownloadQueueUiModel.QueueStatus.FAILED.name shouldBe "FAILED"
    }

    @Test
    fun `anime section header maps correctly from id and name`() {
        val header = DownloadQueueUiModel.SectionHeader(
            section = DownloadSection.ANIME,
            title = "AnimeSource",
            count = 42,
        )
        header.section shouldBe DownloadSection.ANIME
        header.title shouldBe "AnimeSource"
        header.count shouldBe 42
    }

    @Test
    fun `anime section accent follows section not source`() {
        DownloadSection.ANIME.accentHex shouldBe "#FF5C62"
        DownloadSection.MANGA.accentHex shouldBe "#4A90D9"
        DownloadSection.NOVEL.accentHex shouldBe "#7C5CFC"

        // Accent is per-section, not per-source
        DownloadSection.ANIME.accentHex shouldNotBe DownloadSection.MANGA.accentHex
        DownloadSection.ANIME.accentHex shouldNotBe DownloadSection.NOVEL.accentHex
    }

    @Test
    fun `ui item for anime preserves metadata shape`() {
        val item = eu.kanade.tachiyomi.ui.download.DownloadQueueUiItem(
            section = DownloadSection.ANIME,
            itemId = "ep_99",
            title = "Episode 99",
            subtitle = "My Anime",
            progressFraction = 0.75f,
            progressText = "75%",
            status = DownloadQueueUiModel.QueueStatus.DOWNLOADING,
        )
        item.section shouldBe DownloadSection.ANIME
        item.itemId shouldBe "ep_99"
        item.progressFraction shouldBe 0.75f
        item.status shouldBe DownloadQueueUiModel.QueueStatus.DOWNLOADING
    }

    private infix fun String.shouldNotBe(other: String) {
        kotlin.test.assertNotEquals(other, this)
    }
}
