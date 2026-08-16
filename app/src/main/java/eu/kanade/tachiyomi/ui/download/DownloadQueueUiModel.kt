package eu.kanade.tachiyomi.ui.download

import eu.kanade.tachiyomi.data.download.engine.DownloadSection

/**
 * Normalised queue row model shared by anime, manga, and novel screens.
 *
 * Each row is rendered by [DownloadQueueItem]; section headers by
 * [DownloadQueueSectionHeader]; and overflow actions by [DownloadQueueActionMenu].
 */
data class DownloadQueueUiItem(
    val section: DownloadSection,
    val itemId: String,
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val coverData: Any? = null,
    val progressFraction: Float = 0f,
    val progressText: String = "",
    val progressDetailText: String = "",
    val status: DownloadQueueUiModel.QueueStatus = DownloadQueueUiModel.QueueStatus.IDLE,
)

/**
 * Container that groups queue rows with optional section header info.
 */
object DownloadQueueUiModel {
    enum class QueueStatus {
        IDLE,
        QUEUED,
        DOWNLOADING,
        DOWNLOADED,
        FAILED,
    }

    data class SectionHeader(
        val section: DownloadSection,
        val title: String,
        val count: Int,
    )
}
