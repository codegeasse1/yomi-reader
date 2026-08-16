package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.tachiyomi.extension.novel.runtime.resolveUrl
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException

data class NovelReaderRefererImage(
    val url: String,
    val referer: String,
)

class NovelReaderRefererImageKeyer : Keyer<NovelReaderRefererImage> {
    override fun key(data: NovelReaderRefererImage, options: Options): String {
        return "novel-reader-img;${data.url};${data.referer}"
    }
}

class NovelReaderRefererImageFetcher(
    private val data: NovelReaderRefererImage,
    private val options: Options,
    private val callFactory: Lazy<Call.Factory>,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // Sources frequently reference images by relative paths. The referer is the page the image
        // was read from, so resolving against it is exactly what a browser does; without this the
        // relative URL used to hit HttpUrl.get and fail the whole load.
        val resolvedUrl = resolveUrl(data.url, data.referer)
        val httpUrl = resolvedUrl.toHttpUrlOrNull()
            ?: throw IOException("Unsupported image URL: ${data.url}")
        val request = Request.Builder()
            .url(httpUrl)
            .header("Referer", refererHeaderValue(data.referer))
            .build()

        val response = callFactory.value.newCall(request).execute()
        val body = response.body
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

        return SourceFetchResult(
            source = ImageSource(
                source = body.source(),
                fileSystem = options.fileSystem,
            ),
            mimeType = body.contentType()?.toString() ?: "image/*",
            dataSource = DataSource.NETWORK,
        )
    }

    /**
     * HTTP header values must be ASCII: a referer with a non-ASCII (IDN) host — e.g. a cyrillic
     * domain — has to be sent in punycode, otherwise okhttp rejects the header outright and the
     * whole image load fails. Parsing through [HttpUrl] canonicalizes the host exactly like the
     * URL above.
     */
    private fun refererHeaderValue(referer: String): String {
        val trimmed = referer.trim().trimEnd('/')
        val ascii = trimmed.toHttpUrlOrNull()?.toString()?.trimEnd('/') ?: trimmed
        return "$ascii/"
    }

    class Factory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<NovelReaderRefererImage> {
        override fun create(
            data: NovelReaderRefererImage,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return NovelReaderRefererImageFetcher(
                data = data,
                options = options,
                callFactory = callFactoryLazy,
            )
        }
    }
}
