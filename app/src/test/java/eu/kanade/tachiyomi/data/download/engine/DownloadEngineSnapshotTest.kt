package eu.kanade.tachiyomi.data.download.engine

import eu.kanade.tachiyomi.ui.download.DownloadQueueUiItem
import eu.kanade.tachiyomi.ui.download.DownloadQueueUiModel
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DownloadEngineSnapshotTest {

    @Test
    fun `each section resolves to expected label and accent color`() {
        DownloadSection.ANIME.label shouldBe "Anime"
        DownloadSection.MANGA.label shouldBe "Manga"
        DownloadSection.NOVEL.label shouldBe "Novels"

        // Accent tokens should be distinct, non-null hex colors
        DownloadSection.ANIME.accentHex shouldBe "#FF5C62"
        DownloadSection.MANGA.accentHex shouldBe "#4A90D9"
        DownloadSection.NOVEL.accentHex shouldBe "#7C5CFC"
    }

    @Test
    fun `snapshot derives counts correctly from zero state`() {
        val snapshot = DownloadEngineSnapshot()
        snapshot.activeCount shouldBe 0
        snapshot.queuedCount shouldBe 0
        snapshot.completedCount shouldBe 0
        snapshot.failedCount shouldBe 0
    }

    @Test
    fun `snapshot derives counts from per-section items`() {
        val snapshot = DownloadEngineSnapshot(
            animeItems = 5,
            animeActive = 1,
            animeQueued = 3,
            animeFailed = 1,
            mangaItems = 3,
            mangaActive = 1,
            mangaQueued = 2,
            mangaFailed = 0,
            novelPending = 4,
            novelActive = 1,
            novelFailed = 2,
            sessionCompleted = 10,
        )
        snapshot.activeCount shouldBe 3 // 1 anime + 1 manga + 1 novel
        snapshot.queuedCount shouldBe 9 // 3 anime + 2 manga + 4 novel
        snapshot.completedCount shouldBe 10
        snapshot.failedCount shouldBe 3 // 1 anime + 0 manga + 2 novel
    }

    @Test
    fun `snapshot leaves speed and eta empty when no samples`() {
        val snapshot = DownloadEngineSnapshot()
        snapshot.currentSpeedBps shouldBe null
        snapshot.averageSpeedBps shouldBe null
        snapshot.etaMillis shouldBe null
    }

    @Test
    fun `snapshot exposes speed and eta when samples are provided`() {
        val snapshot = DownloadEngineSnapshot(
            currentSpeedBps = 1_048_576L,
            averageSpeedBps = 524_288L,
            etaMillis = 30_000L,
        )
        snapshot.currentSpeedBps shouldBe 1_048_576L
        snapshot.averageSpeedBps shouldBe 524_288L
        snapshot.etaMillis shouldBe 30_000L
    }

    @Test
    fun `snapshot preserves per-section storage info`() {
        val snapshot = DownloadEngineSnapshot(
            animeStorageBytes = 1024 * 1024 * 100L,
            mangaStorageBytes = 1024 * 1024 * 200L,
            novelStorageBytes = 1024 * 1024 * 50L,
        )
        snapshot.animeStorageBytes shouldBe 104_857_600L
        snapshot.mangaStorageBytes shouldBe 209_715_200L
        snapshot.novelStorageBytes shouldBe 52_428_800L
    }

    @Test
    fun `queue ui item preserves section and metadata`() {
        val item = DownloadQueueUiItem(
            section = DownloadSection.MANGA,
            itemId = "ch_42",
            title = "Chapter 42",
            subtitle = "Source Name",
            description = "12 pages",
            progressFraction = 0.5f,
            progressText = "6/12",
            status = DownloadQueueUiModel.QueueStatus.DOWNLOADING,
        )
        item.section shouldBe DownloadSection.MANGA
        item.itemId shouldBe "ch_42"
        item.title shouldBe "Chapter 42"
        item.subtitle shouldBe "Source Name"
        item.description shouldBe "12 pages"
        item.progressFraction shouldBe 0.5f
        item.progressText shouldBe "6/12"
        item.status shouldBe DownloadQueueUiModel.QueueStatus.DOWNLOADING
    }
}
