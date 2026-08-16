package tachiyomi.data.extension.novel

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class NetworkNovelPluginDownloader(
    private val client: OkHttpClient,
) : NovelPluginDownloader {
    override suspend fun download(url: String): ByteArray {
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(GET(url)).awaitSuccess().use { response ->
                    val bytes = response.body.bytes()
                    if (bytes.isEmpty()) {
                        throw NovelPluginInstallException.DownloadFailed(url, "empty body")
                    }
                    if (bytes.size > MAX_PLUGIN_SIZE_BYTES) {
                        throw NovelPluginInstallException.DownloadFailed(
                            url,
                            "too large: ${bytes.size} bytes (limit $MAX_PLUGIN_SIZE_BYTES)",
                        )
                    }
                    bytes
                }
            } catch (e: NovelPluginInstallException.DownloadFailed) {
                logcat(LogPriority.WARN) { "Novel plugin download failed reason=${e.reason} url=$url" }
                throw e
            } catch (e: HttpException) {
                val failure = NovelPluginInstallException.DownloadFailed(url, "http ${e.code}", e)
                logcat(LogPriority.WARN) { "Novel plugin download failed reason=http ${e.code} url=$url" }
                throw failure
            } catch (e: SSLException) {
                val failure = NovelPluginInstallException.DownloadFailed(url, "tls", e)
                logcat(LogPriority.WARN) { "Novel plugin download failed reason=tls url=$url" }
                throw failure
            } catch (e: SocketTimeoutException) {
                val failure = NovelPluginInstallException.DownloadFailed(url, "timeout", e)
                logcat(LogPriority.WARN) { "Novel plugin download failed reason=timeout url=$url" }
                throw failure
            } catch (e: UnknownHostException) {
                val failure = NovelPluginInstallException.DownloadFailed(url, "network unavailable", e)
                logcat(LogPriority.WARN) { "Novel plugin download failed reason=network unavailable url=$url" }
                throw failure
            } catch (e: IOException) {
                val failure = NovelPluginInstallException.DownloadFailed(url, "io", e)
                logcat(LogPriority.WARN) { "Novel plugin download failed reason=io url=$url" }
                throw failure
            }
        }
    }

    private companion object {
        /** Upper bound for a single plugin artifact (script or APK), ~64 MiB. */
        private const val MAX_PLUGIN_SIZE_BYTES = 64 * 1024 * 1024
    }
}
