package eu.kanade.tachiyomi.data.download.engine

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DownloadSpeedTrackerTest {

    @Test
    fun `rolling window keeps only the newest 60 samples`() {
        val tracker = DownloadSpeedTracker(windowSize = 10)
        repeat(15) { i ->
            tracker.pushSample(
                DownloadSpeedSample(
                    section = DownloadSection.ANIME,
                    bytesDelta = 1000L,
                    timestampMs = i * 100L,
                ),
            )
        }

        val snapshot = tracker.snapshot()
        snapshot.sampleCount shouldBe 10
    }

    @Test
    fun `zero byte deltas are ignored`() {
        val tracker = DownloadSpeedTracker(windowSize = 10)

        tracker.pushSample(
            DownloadSpeedSample(
                section = DownloadSection.MANGA,
                bytesDelta = 0L,
                timestampMs = 100L,
            ),
        )

        val snapshot = tracker.snapshot()
        snapshot.sampleCount shouldBe 0
    }

    @Test
    fun `negative byte deltas are ignored`() {
        val tracker = DownloadSpeedTracker(windowSize = 10)

        tracker.pushSample(
            DownloadSpeedSample(
                section = DownloadSection.ANIME,
                bytesDelta = -500L,
                timestampMs = 100L,
            ),
        )

        val snapshot = tracker.snapshot()
        snapshot.sampleCount shouldBe 0
    }

    @Test
    fun `current speed is computed from window total bytes and time`() {
        val tracker = DownloadSpeedTracker(windowSize = 5)

        // 5000 bytes / 400ms = 12_500 Bps
        repeat(5) { i ->
            tracker.pushSample(
                DownloadSpeedSample(
                    section = DownloadSection.MANGA,
                    bytesDelta = 1000L,
                    timestampMs = i * 100L,
                ),
            )
        }

        val snapshot = tracker.snapshot()
        val speed = snapshot.currentSpeedBps
        requireNotNull(speed) { "currentSpeedBps should not be null with valid samples" }
        speed shouldBe 12_500L
    }

    @Test
    fun `current speed window averages out outlier spikes`() {
        val tracker = DownloadSpeedTracker(windowSize = 5)

        // 4 normal + 1 spike: total bytes = 54_000, time = 400ms
        repeat(4) { i ->
            tracker.pushSample(
                DownloadSpeedSample(
                    section = DownloadSection.NOVEL,
                    bytesDelta = 1000L,
                    timestampMs = i * 100L,
                ),
            )
        }

        tracker.pushSample(
            DownloadSpeedSample(
                section = DownloadSection.NOVEL,
                bytesDelta = 50_000L,
                timestampMs = 400L,
            ),
        )

        val snapshot = tracker.snapshot()
        // 54_000 bytes / 400 ms = 135_000 Bps (window average, not 500_000 spike)
        val speed = snapshot.currentSpeedBps
        requireNotNull(speed)
        speed shouldBe 135_000L
    }

    @Test
    fun `average speed is cumulative across all samples`() {
        val tracker = DownloadSpeedTracker(windowSize = 10)

        repeat(10) { i ->
            tracker.pushSample(
                DownloadSpeedSample(
                    section = DownloadSection.ANIME,
                    bytesDelta = 1000L,
                    timestampMs = i * 100L,
                ),
            )
        }

        val snapshot = tracker.snapshot()
        // 10_000 bytes / 900ms = ~11_111 Bps
        val avg = snapshot.averageSpeedBps
        requireNotNull(avg)
        avg shouldBe 11_111L
    }

    @Test
    fun `tracker returns idle snapshot when no samples have been recorded`() {
        val tracker = DownloadSpeedTracker()

        val snapshot = tracker.snapshot()
        snapshot.sampleCount shouldBe 0
        snapshot.currentSpeedBps shouldBe null
        snapshot.averageSpeedBps shouldBe null
        snapshot.etaMillis shouldBe null
    }

    @Test
    fun `eta is null when remaining bytes are zero`() {
        val tracker = DownloadSpeedTracker(windowSize = 5)

        repeat(3) { i ->
            tracker.pushSample(
                DownloadSpeedSample(
                    section = DownloadSection.MANGA,
                    bytesDelta = 1000L,
                    timestampMs = i * 100L,
                ),
            )
        }

        val snapshot = tracker.snapshot(remainingBytes = 0L)
        snapshot.etaMillis shouldBe null
    }

    @Test
    fun `eta is computed from remaining bytes and average speed`() {
        val tracker = DownloadSpeedTracker(windowSize = 5)

        // 5_000 bytes / 400ms = 12_500 Bps average
        repeat(5) { i ->
            tracker.pushSample(
                DownloadSpeedSample(
                    section = DownloadSection.MANGA,
                    bytesDelta = 1000L,
                    timestampMs = i * 100L,
                ),
            )
        }

        val snapshot = tracker.snapshot(remainingBytes = 50_000L)
        // 50_000 * 1000 / 12_500 = 4_000 ms
        val eta = snapshot.etaMillis
        requireNotNull(eta)
        eta shouldBe 4_000L
    }

    @Test
    fun `per-section speed is computed separately`() {
        val tracker = DownloadSpeedTracker(windowSize = 5)

        // Anime: 3 samples, 1500 bytes / 200ms = 7_500 Bps
        repeat(3) { i ->
            tracker.pushSample(
                DownloadSpeedSample(
                    section = DownloadSection.ANIME,
                    bytesDelta = 500L,
                    timestampMs = i * 100L,
                ),
            )
        }

        // Manga: 3 samples, 6000 bytes / 200ms = 30_000 Bps
        repeat(3) { i ->
            tracker.pushSample(
                DownloadSpeedSample(
                    section = DownloadSection.MANGA,
                    bytesDelta = 2000L,
                    timestampMs = i * 100L + 300L,
                ),
            )
        }

        val snapshot = tracker.snapshot()
        snapshot.perSectionSpeed[DownloadSection.ANIME] shouldBe 7_500L
        snapshot.perSectionSpeed[DownloadSection.MANGA] shouldBe 30_000L
    }
}
