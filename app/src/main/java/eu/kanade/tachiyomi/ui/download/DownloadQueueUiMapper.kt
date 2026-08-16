package eu.kanade.tachiyomi.ui.download

import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.engine.DownloadSection
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownload
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadStatus
import eu.kanade.tachiyomi.ui.download.anime.AnimeDownloadHeaderItem
import eu.kanade.tachiyomi.ui.download.anime.AnimeDownloadItem
import eu.kanade.tachiyomi.ui.download.manga.MangaDownloadHeaderItem
import eu.kanade.tachiyomi.ui.download.manga.MangaDownloadItem
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.entries.novel.model.asNovelCover

/**
 * Maps anime, manga, and novel domain items into the shared [DownloadQueueUiItem]
 * presentation model so all three tabs render through the same row composable.
 */
object DownloadQueueUiMapper {

    fun toSectionHeader(header: AnimeDownloadHeaderItem): DownloadQueueUiModel.SectionHeader {
        return DownloadQueueUiModel.SectionHeader(
            section = DownloadSection.ANIME,
            title = header.name,
            count = header.subItems.count { it.download.status != AnimeDownload.State.DOWNLOADED },
        )
    }

    fun toSectionHeader(header: MangaDownloadHeaderItem): DownloadQueueUiModel.SectionHeader {
        return DownloadQueueUiModel.SectionHeader(
            section = DownloadSection.MANGA,
            title = header.name,
            count = header.subItems.count { it.download.status != MangaDownload.State.DOWNLOADED },
        )
    }

    fun toUiItem(
        item: AnimeDownloadItem,
        progress: Int? = null,
        status: AnimeDownload.State? = null,
        downloadedBytes: Long? = null,
        currentSpeedBytesPerSecond: Long? = null,
        estimatedTotalBytes: Long? = null,
    ): DownloadQueueUiItem {
        val download = item.download
        val effectiveProgress = progress ?: download.progress
        val effectiveStatus = status ?: download.status
        val effectiveDownloadedBytes = downloadedBytes ?: download.downloadedBytes
        val effectiveCurrentSpeedBytes = currentSpeedBytesPerSecond ?: download.currentSpeedBytesPerSecond
        val effectiveTotalBytes = estimatedTotalBytes ?: download.estimatedTotalBytes
        val progressLabel = when (effectiveStatus) {
            AnimeDownload.State.DOWNLOADED -> "100%"
            AnimeDownload.State.ERROR -> ""
            else -> if (effectiveProgress >= 0) "$effectiveProgress%" else ""
        }
        val progressDetailText = buildList {
            if (effectiveDownloadedBytes > 0L) {
                // The total is an extrapolation for segmented sources, hence the "~" marker. It is
                // only shown once it is above the bytes already written, otherwise it would look
                // like the download shrank on the very first statistics callbacks.
                if (effectiveTotalBytes > effectiveDownloadedBytes) {
                    add("${formatBytes(effectiveDownloadedBytes)} / ~${formatBytes(effectiveTotalBytes)}")
                } else {
                    add(formatBytes(effectiveDownloadedBytes))
                }
            }
            if (effectiveStatus == AnimeDownload.State.DOWNLOADING && effectiveCurrentSpeedBytes > 0L) {
                val speedAndEta = buildList {
                    add("${formatBytes(effectiveCurrentSpeedBytes)}/s")
                    val remainingBytes = effectiveTotalBytes - effectiveDownloadedBytes
                    if (remainingBytes > 0L) {
                        add("~${formatEta(remainingBytes / effectiveCurrentSpeedBytes)}")
                    }
                }.joinToString(" | ")
                add(speedAndEta)
            }
        }.joinToString("\n")
        return DownloadQueueUiItem(
            section = DownloadSection.ANIME,
            itemId = download.episode.id.toString(),
            title = download.episode.name,
            subtitle = download.anime.title,
            coverData = download.anime.asAnimeCover(),
            progressFraction = (effectiveProgress / 100f).coerceIn(0f, 1f),
            progressText = progressLabel,
            progressDetailText = progressDetailText,
            status = when (effectiveStatus) {
                AnimeDownload.State.DOWNLOADING -> DownloadQueueUiModel.QueueStatus.DOWNLOADING
                AnimeDownload.State.QUEUE -> DownloadQueueUiModel.QueueStatus.QUEUED
                AnimeDownload.State.DOWNLOADED -> DownloadQueueUiModel.QueueStatus.DOWNLOADED
                AnimeDownload.State.ERROR -> DownloadQueueUiModel.QueueStatus.FAILED
                else -> DownloadQueueUiModel.QueueStatus.IDLE
            },
        )
    }

    fun toUiItem(
        item: MangaDownloadItem,
        progressOverride: Int? = null,
        status: MangaDownload.State? = null,
    ): DownloadQueueUiItem {
        val download = item.download
        val effectiveStatus = status ?: download.status
        val progressFraction = if (download.pages != null && download.pages!!.isNotEmpty()) {
            val downloaded = download.pages!!.count {
                it.status == eu.kanade.tachiyomi.source.model.Page.State.READY
            }
            downloaded.toFloat() / download.pages!!.size
        } else {
            progressOverride?.let { it / 100f } ?: 0f
        }
        val progressText = if (download.pages != null && download.pages!!.isNotEmpty()) {
            "${download.downloadedImages}/${download.pages!!.size}"
        } else {
            progressOverride?.let { "$it%" } ?: ""
        }
        return DownloadQueueUiItem(
            section = DownloadSection.MANGA,
            itemId = download.chapter.id.toString(),
            title = download.chapter.name,
            subtitle = download.manga.title,
            coverData = download.manga.asMangaCover(),
            progressFraction = progressFraction,
            progressText = progressText,
            status = when (effectiveStatus) {
                MangaDownload.State.DOWNLOADING -> DownloadQueueUiModel.QueueStatus.DOWNLOADING
                MangaDownload.State.QUEUE -> DownloadQueueUiModel.QueueStatus.QUEUED
                MangaDownload.State.DOWNLOADED -> DownloadQueueUiModel.QueueStatus.DOWNLOADED
                MangaDownload.State.ERROR -> DownloadQueueUiModel.QueueStatus.FAILED
                else -> DownloadQueueUiModel.QueueStatus.IDLE
            },
        )
    }

    fun toUiItem(task: NovelQueuedDownload, progressFraction: Float = 0f): DownloadQueueUiItem {
        return DownloadQueueUiItem(
            section = DownloadSection.NOVEL,
            itemId = task.taskId.toString(),
            title = task.chapter.name.ifBlank { task.chapter.chapterNumber.toString().removeSuffix(".0") },
            subtitle = task.novel.title,
            coverData = task.novel.asNovelCover(),
            progressFraction = progressFraction,
            description = when (task.status) {
                NovelQueuedDownloadStatus.FAILED -> task.errorMessage.orEmpty()
                else -> ""
            },
            status = when (task.status) {
                NovelQueuedDownloadStatus.QUEUED -> DownloadQueueUiModel.QueueStatus.QUEUED
                NovelQueuedDownloadStatus.DOWNLOADING -> DownloadQueueUiModel.QueueStatus.DOWNLOADING
                NovelQueuedDownloadStatus.FAILED -> DownloadQueueUiModel.QueueStatus.FAILED
            },
        )
    }

    /**
     * Formats a duration as a locale-neutral clock value (`4:12`, `1:02:33`) so no new translated
     * strings are needed for the remaining-time hint.
     */
    private fun formatEta(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0L)
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        val seconds = safeSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000L -> "${bytes / 1_000_000_000L}.${(bytes % 1_000_000_000L) / 100_000_000L} GB"
            bytes >= 1_000_000L -> "${bytes / 1_000_000L}.${(bytes % 1_000_000L) / 100_000L} MB"
            bytes >= 1_000L -> "${bytes / 1_000L} KB"
            else -> "$bytes B"
        }
    }
}
