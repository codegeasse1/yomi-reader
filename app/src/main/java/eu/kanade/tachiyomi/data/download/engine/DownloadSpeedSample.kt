package eu.kanade.tachiyomi.data.download.engine

/**
 * A single throughput sample for the live speed tracker.
 *
 * @param section the content section this sample belongs to
 * @param bytesDelta bytes transferred in this sample window
 * @param timestampMs monotonic time when the sample was recorded
 */
data class DownloadSpeedSample(
    val section: DownloadSection,
    val bytesDelta: Long,
    val timestampMs: Long,
)
