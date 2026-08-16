package eu.kanade.tachiyomi.ui.player.subtitle.translation

import android.content.ContentResolver
import android.net.Uri
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

class SubtitleTranslationPlayerBridge(
    private val networkHelper: NetworkHelper,
    private val contentResolver: ContentResolver,
    private val cacheDir: File,
    private val coordinator: SubtitleTranslationCoordinator,
    private val maxOutputFiles: Int = 40,
    private val maxDownloadAttempts: Int = 3,
) {
    suspend fun translateExternalTrack(
        track: Track,
        sourceLanguage: String,
        targetLanguage: String,
        providerId: SubtitleTranslationProviderId = SubtitleTranslationProviderId.Google,
        sourceIdentity: String = track.url,
        useCache: Boolean = true,
        bilingual: Boolean = false,
        title: String? = null,
        genreHint: String? = null,
        onProgress: (SubtitleTranslationProgress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        onProgress(SubtitleTranslationProgress(0, 0, SubtitleTranslationStage.Downloading))
        translateRawSubtitle(
            raw = readTrackText(track.url),
            formatHint = track.url,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            providerId = providerId,
            sourceIdentity = sourceIdentity,
            useCache = useCache,
            bilingual = bilingual,
            title = title,
            genreHint = genreHint,
            onProgress = onProgress,
        )
    }

    suspend fun translateRawSubtitle(
        raw: String,
        formatHint: String,
        sourceLanguage: String,
        targetLanguage: String,
        providerId: SubtitleTranslationProviderId = SubtitleTranslationProviderId.Google,
        sourceIdentity: String,
        useCache: Boolean = true,
        bilingual: Boolean = false,
        title: String? = null,
        genreHint: String? = null,
        onProgress: (SubtitleTranslationProgress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        onProgress(SubtitleTranslationProgress(0, 0, SubtitleTranslationStage.Parsing))
        val document = SubtitleParser.parse(raw, formatHint)
        require(document.cues.isNotEmpty()) { "No subtitle cues found" }

        val normalizedTarget = targetLanguage.ifBlank { Locale.getDefault().language }.ifBlank { "en" }
        val result = coordinator.translate(
            SubtitleTranslationRequest(
                document = document,
                sourceLanguage = sourceLanguage.ifBlank { "auto" },
                targetLanguage = normalizedTarget,
                providerId = providerId,
                sourceIdentity = sourceIdentity,
                useCache = useCache,
                bilingual = bilingual,
                title = title,
                genreHint = genreHint,
            ),
            onProgress = onProgress,
        )

        onProgress(SubtitleTranslationProgress(0, 0, SubtitleTranslationStage.Applying))
        val outputDir = File(cacheDir, "subtitle_translations/player").also { it.mkdirs() }
        val extension = result.document.format.takeIf { it != SubtitleFormat.Unknown }?.extension ?: "vtt"
        val suffix = if (bilingual) "-bi" else ""
        val output = File(outputDir, "${result.cacheKey}$suffix.$extension")
        val tmp = File(outputDir, output.name + ".tmp")
        tmp.writeText(SubtitleWriter.write(result.document), Charsets.UTF_8)
        if (output.exists()) output.delete()
        if (!tmp.renameTo(output)) {
            tmp.copyTo(output, overwrite = true)
            tmp.delete()
        }
        pruneOutputDir(outputDir)
        output
    }

    private fun pruneOutputDir(outputDir: File) {
        val files = outputDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(maxOutputFiles).forEach { runCatching { it.delete() } }
    }

    private suspend fun readTrackText(url: String): String {
        return when {
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> {
                downloadWithRetry(url)
            }
            url.startsWith("content://", ignoreCase = true) -> {
                contentResolver.openInputStream(Uri.parse(url))?.use { input ->
                    input.reader(Charsets.UTF_8).readText()
                } ?: error("Unable to open subtitle content URI")
            }
            url.startsWith("file://", ignoreCase = true) -> {
                File(Uri.parse(url).path.orEmpty()).readText(Charsets.UTF_8)
            }
            else -> File(url).readText(Charsets.UTF_8)
        }
    }

    private suspend fun downloadWithRetry(url: String): String {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < maxDownloadAttempts.coerceAtLeast(1)) {
            try {
                return networkHelper.client.newCall(GET(url)).execute().use { response ->
                    val code = response.code
                    if (!response.isSuccessful) {
                        // 4xx (except 408/429) is not worth retrying.
                        val retryable = code == 408 || code == 429 || code >= 500
                        throw IOException(
                            "Subtitle download failed: HTTP $code${if (retryable) " (retryable)" else ""}",
                        )
                    }
                    response.body.string()
                }
            } catch (error: IOException) {
                lastError = error
                val retryable = (error.message ?: "").let { it.contains("retryable") || !it.contains("HTTP") }
                if (!retryable) throw error
                attempt++
                if (attempt < maxDownloadAttempts) delay(1_000L * attempt)
            }
        }
        throw lastError ?: IOException("Subtitle download failed")
    }
}
