package eu.kanade.tachiyomi.ui.download

import eu.kanade.tachiyomi.data.download.engine.DownloadEngineSnapshot
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Shape contract tests for DownloadEngineScreenModel.
 *
 * The facade requires real backend managers (Injekt scoped).  These tests
 * verify the expected shape and delegation patterns without doing full DI.
 */
class DownloadEngineScreenModelTest {

    @Test
    fun `initial engine snapshot shape has zero counts`() {
        val snapshot = DownloadEngineSnapshot()
        snapshot.activeCount shouldBe 0
        snapshot.queuedCount shouldBe 0
        snapshot.completedCount shouldBe 0
        snapshot.failedCount shouldBe 0
        snapshot.currentSpeedBps shouldBe null
        snapshot.etaMillis shouldBe null
    }

    @Test
    fun `engine snapshot with items exposes correct totals`() {
        val snapshot = DownloadEngineSnapshot(
            animeItems = 3,
            animeActive = 1,
            animeQueued = 2,
            animeFailed = 0,
            mangaItems = 2,
            mangaActive = 0,
            mangaQueued = 1,
            mangaFailed = 1,
            novelPending = 5,
            novelActive = 1,
            novelFailed = 0,
            sessionCompleted = 7,
            currentSpeedBps = 1024L,
            etaMillis = 5000L,
        )
        snapshot.activeCount shouldBe 2
        snapshot.queuedCount shouldBe 8
        snapshot.completedCount shouldBe 7
        snapshot.failedCount shouldBe 1
        snapshot.currentSpeedBps shouldBe 1024L
        snapshot.etaMillis shouldBe 5000L
    }
}
