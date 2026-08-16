package eu.kanade.tachiyomi.data.download.anime.model

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.ProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class AnimeDownload(
    val source: AnimeHttpSource,
    val anime: Anime,
    val episode: Episode,
    val changeDownloader: Boolean = false,
    var video: Video? = null,
) : ProgressListener {

    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    @Transient
    private val progressStateFlow = MutableStateFlow(0)

    @Transient
    val progressFlow = progressStateFlow.asStateFlow()
    var progress: Int
        get() = progressStateFlow.value
        set(value) {
            progressStateFlow.value = value
        }

    @Transient
    private val downloadedBytesStateFlow = MutableStateFlow(0L)

    @Transient
    val downloadedBytesFlow = downloadedBytesStateFlow.asStateFlow()
    var downloadedBytes: Long
        get() = downloadedBytesStateFlow.value
        set(value) {
            downloadedBytesStateFlow.value = value
        }

    @Transient
    private val currentSpeedBytesStateFlow = MutableStateFlow(0L)

    @Transient
    val currentSpeedBytesFlow = currentSpeedBytesStateFlow.asStateFlow()
    var currentSpeedBytesPerSecond: Long
        get() = currentSpeedBytesStateFlow.value
        set(value) {
            currentSpeedBytesStateFlow.value = value
        }

    @Transient
    private val estimatedTotalBytesStateFlow = MutableStateFlow(0L)

    @Transient
    val estimatedTotalBytesFlow = estimatedTotalBytesStateFlow.asStateFlow()

    /**
     * Best-effort estimate of the final file size in bytes, or 0 when still unknown.
     *
     * Segmented (HLS) sources never declare a total length up front, so this is extrapolated from
     * how much of the timeline ffmpeg has already muxed. It self-corrects as the download advances.
     */
    var estimatedTotalBytes: Long
        get() = estimatedTotalBytesStateFlow.value
        set(value) {
            estimatedTotalBytesStateFlow.value = value
        }

    /**
     * Updates the status of the download
     *
     * @param bytesRead the updated TOTAL number of bytes read (not a partial increment)
     * @param contentLength the updated content length
     * @param done whether progress has completed or not
     */
    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
        val newProgress = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
        if (progress != newProgress) progress = newProgress
    }

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }

    companion object {
        suspend fun fromEpisodeId(
            episodeId: Long,
            getEpisode: GetEpisode = Injekt.get(),
            getAnimeById: GetAnime = Injekt.get(),
            sourceManager: AnimeSourceManager = Injekt.get(),
        ): AnimeDownload? {
            val episode = getEpisode.await(episodeId) ?: return null
            val anime = getAnimeById.await(episode.animeId) ?: return null
            val source = sourceManager.get(anime.source) as? AnimeHttpSource ?: return null

            return AnimeDownload(source, anime, episode)
        }
    }
}
