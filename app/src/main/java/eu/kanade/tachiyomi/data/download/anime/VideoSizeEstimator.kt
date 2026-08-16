package eu.kanade.tachiyomi.data.download.anime

import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.Request
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

/**
 * Best-effort file size lookup for a [Video] before it has been downloaded.
 *
 * Progressive files (mp4, mkv, ...) declare their length, so the value is exact. Segmented streams
 * (HLS/DASH) do not, so the size is derived from the declared duration and bitrate instead, which
 * is an approximation. When neither is available the caller gets null rather than a made-up number.
 */
object VideoSizeEstimator {

    /**
     * @param bytes the resolved size in bytes.
     * @param exact true when the server declared the length, false for a computed approximation.
     */
    data class Estimate(val bytes: Long, val exact: Boolean)

    private val network: NetworkHelper by lazy { Injekt.get() }

    /**
     * Resolves the size of [video], or null when it cannot be determined.
     *
     * Performs at most one network round trip, so it is safe to call when a quality is selected.
     */
    suspend fun estimate(video: Video, source: AnimeHttpSource?): Estimate? {
        val url = video.videoUrl
        if (url.isBlank() || !url.startsWith("http")) return null

        val requestHeaders = video.headers ?: source?.headers
        return try {
            if (isPlaylist(url)) {
                estimateFromMediaInfo(url, requestHeaders)
            } else {
                // Range first: it reuses the source's own client and works on CDNs that refuse HEAD.
                rangeLength(video, source)
                    ?: contentLength(url, requestHeaders)
                    ?: estimateFromMediaInfo(url, requestHeaders)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not determine the size of $url" }
            null
        }
    }

    private fun isPlaylist(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".m3u") || path.endsWith(".mpd")
    }

    /**
     * Exact size through the source's existing `Content-Range` probe. Only valid for progressive
     * files, since for a playlist it would describe the playlist text rather than the media.
     */
    private suspend fun rangeLength(video: Video, source: AnimeHttpSource?): Estimate? {
        if (source == null) return null
        return withIOContext {
            val size = source.getVideoSize(video, 1)
            if (size > 0L) Estimate(size, exact = true) else null
        }
    }

    /**
     * Exact size from a HEAD request, used when the Range probe is unavailable. Meaningless for
     * playlists, where Content-Length describes the playlist text instead of the media.
     */
    private suspend fun contentLength(url: String, headers: Headers?): Estimate? = withIOContext {
        val builder = Request.Builder().url(url).head()
        if (headers != null) builder.headers(headers)

        network.client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val length = response.header("Content-Length")?.toLongOrNull() ?: return@use null
            if (length > 0L) Estimate(length, exact = true) else null
        }
    }

    /** Approximates the size as duration * bitrate, both read from the container metadata. */
    private suspend fun estimateFromMediaInfo(url: String, headers: Headers?): Estimate? {
        val output = probe(url, headers) ?: return null

        val durationSeconds = readNumber(output, "duration")?.takeIf { it > 0.0 } ?: return null
        val bitsPerSecond = readNumber(output, "bit_rate")?.takeIf { it > 0.0 } ?: return null

        val bytes = (durationSeconds * bitsPerSecond / 8.0).toLong()
        return if (bytes > 0L) Estimate(bytes, exact = false) else null
    }

    /**
     * Reads a numeric field out of ffprobe JSON output by name, so the result does not depend on the
     * order in which ffprobe happens to print the fields.
     */
    private fun readNumber(output: String, field: String): Double? {
        val match = Regex("\"" + field + "\"\\s*:\\s*\"?([0-9.]+)\"?").find(output) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    private suspend fun probe(url: String, headers: Headers?): String? {
        val headerOptions = headers?.takeIf { it.size > 0 }
            ?.joinToString("", "-headers '", "' ") { "${it.first}: ${it.second}\r\n" }
            ?: ""

        val command = FFmpegKitConfig.parseArguments(
            headerOptions +
                "-v quiet -print_format json -show_entries format=duration,bit_rate \"" + url + "\"",
        )

        return suspendCancellableCoroutine { continuation ->
            val session = FFprobeKit.executeWithArgumentsAsync(command) { probeSession ->
                val output = probeSession.output?.takeIf { probeSession.returnCode.isValueSuccess }
                continuation.resume(output)
            }
            continuation.invokeOnCancellation { session.cancel() }
        }
    }
}
