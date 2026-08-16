package eu.kanade.tachiyomi.data.download.engine

/**
 * Backend-facing contract for emitting byte-level download telemetry.
 *
 * Implementations should call [record] at stable byte checkpoints that the
 * backend already knows about.  Backends that cannot provide truthful byte
 * signals should leave the emitter as a no-op.
 */
interface DownloadTelemetryEmitter {

    /**
     * Record a single byte-level progress sample.
     *
     * @param section the content section (ANIME / MANGA / NOVEL)
     * @param downloadKey a stable identifier for the active download item
     * @param bytesDownloaded total bytes downloaded so far for this item
     * @param bytesTotal expected total bytes for the item (may be 0 if unknown)
     * @param timestampMs monotonic clock time when the sample was recorded
     */
    fun record(
        section: DownloadSection,
        downloadKey: String,
        bytesDownloaded: Long,
        bytesTotal: Long,
        timestampMs: Long,
    )

    companion object {
        /** A no-op emitter for backends that cannot provide byte signals. */
        val NOOP: DownloadTelemetryEmitter = object : DownloadTelemetryEmitter {
            override fun record(
                section: DownloadSection,
                downloadKey: String,
                bytesDownloaded: Long,
                bytesTotal: Long,
                timestampMs: Long,
            ) = Unit
        }
    }
}
