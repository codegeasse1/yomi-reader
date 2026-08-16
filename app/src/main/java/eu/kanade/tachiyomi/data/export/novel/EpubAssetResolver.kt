package eu.kanade.tachiyomi.data.export.novel

import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URLConnection

internal class EpubAssetResolver(
    private val networkHelper: NetworkHelper?,
    private val maxAssetBytes: Long = DEFAULT_MAX_ASSET_BYTES,
    private val maxTotalAssetBytes: Long = DEFAULT_MAX_TOTAL_ASSET_BYTES,
) {
    private var totalAssetBytes: Long = 0L

    fun resetSession() {
        totalAssetBytes = 0L
    }

    fun resolveBinaryAsset(
        src: String,
        baseUrls: List<String>,
    ): EpubBinaryAsset? {
        return resolveBinaryAssetWithReport(src, baseUrls).asset
    }

    fun resolveBinaryAssetWithReport(
        src: String,
        baseUrls: List<String>,
    ): EpubAssetResolutionReport {
        val directUri = runCatching { URI(src) }.getOrNull()
        val isAbsolute = directUri?.isAbsolute == true
        val candidates = buildList {
            if (isAbsolute) {
                add(src)
            } else {
                baseUrls.forEach { base ->
                    runCatching {
                        val baseUri = URI(base)
                        if (baseUri.isAbsolute) {
                            add(baseUri.resolve(src).toString())
                        }
                    }
                }
            }
        }.ifEmpty { listOf(src) }

        var warning: String? = null
        candidates.forEach { candidate ->
            when (val result = resolveAbsoluteBinaryAsset(candidate)) {
                is AssetResolveResult.Success -> return EpubAssetResolutionReport(result.asset)
                is AssetResolveResult.Failure -> {
                    warning = result.reason.toWarning(src, candidate)
                }
            }
        }
        return EpubAssetResolutionReport(
            asset = null,
            warning = warning ?: "Image $src skipped: unsupported or unresolved image URI.",
        )
    }

    private fun resolveAbsoluteBinaryAsset(src: String): AssetResolveResult {
        val uri = runCatching { URI(src) }.getOrNull()
        return when {
            src.startsWith("http://", ignoreCase = true) || src.startsWith("https://", ignoreCase = true) -> {
                resolveHttpAsset(src)
            }
            uri?.scheme.equals("file", ignoreCase = true) -> {
                resolveFileAsset(uri)
            }
            else -> AssetResolveResult.Failure(AssetSkipReason.UnsupportedUri)
        }
    }

    private fun resolveHttpAsset(src: String): AssetResolveResult {
        val client = networkHelper?.client ?: return AssetResolveResult.Failure(AssetSkipReason.NetworkUnavailable)
        return runCatching {
            val request = Request.Builder().url(src).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use AssetResolveResult.Failure(
                        AssetSkipReason.HttpError(response.code),
                    )
                }
                val body = response.body
                val contentLength = body.contentLength()
                if (contentLength >
                    maxAssetBytes
                ) {
                    return@use AssetResolveResult.Failure(AssetSkipReason.AssetTooLarge(maxAssetBytes))
                }
                if (contentLength > 0 && totalAssetBytes + contentLength > maxTotalAssetBytes) {
                    return@use AssetResolveResult.Failure(AssetSkipReason.TotalLimitExceeded(maxTotalAssetBytes))
                }
                val bytes = body.byteStream().use { stream ->
                    stream.readBytesWithLimit(maxAssetBytes)
                } ?: return@use AssetResolveResult.Failure(AssetSkipReason.AssetTooLarge(maxAssetBytes))
                if (bytes.isEmpty()) return@use AssetResolveResult.Failure(AssetSkipReason.EmptyAsset)
                if (totalAssetBytes + bytes.size > maxTotalAssetBytes) {
                    return@use AssetResolveResult.Failure(AssetSkipReason.TotalLimitExceeded(maxTotalAssetBytes))
                }
                val mediaType = body.contentType()?.toString()
                    ?: URLConnection.guessContentTypeFromStream(bytes.inputStream())
                    ?: mediaTypeFromName(src)
                    ?: "image/jpeg"
                if (!isAllowedImageMediaType(mediaType)) {
                    return@use AssetResolveResult.Failure(
                        AssetSkipReason.UnsupportedMediaType(normalizeMediaType(mediaType)),
                    )
                }
                totalAssetBytes += bytes.size
                AssetResolveResult.Success(
                    EpubBinaryAsset(
                        bytes = bytes,
                        mediaType = normalizeMediaType(mediaType),
                        extension = extensionFromMediaType(mediaType),
                    ),
                )
            }
        }.getOrElse { AssetResolveResult.Failure(AssetSkipReason.LoadFailed) }
    }

    private fun resolveFileAsset(uri: URI?): AssetResolveResult {
        return runCatching {
            val fileUri = uri ?: return@runCatching AssetResolveResult.Failure(AssetSkipReason.UnsupportedUri)
            val file = File(fileUri)
            if (!file.exists()) return@runCatching AssetResolveResult.Failure(AssetSkipReason.LoadFailed)
            if (file.length() >
                maxAssetBytes
            ) {
                return@runCatching AssetResolveResult.Failure(AssetSkipReason.AssetTooLarge(maxAssetBytes))
            }
            if (totalAssetBytes + file.length() > maxTotalAssetBytes) {
                return@runCatching AssetResolveResult.Failure(AssetSkipReason.TotalLimitExceeded(maxTotalAssetBytes))
            }
            val bytes = file.inputStream().use { stream ->
                stream.readBytesWithLimit(maxAssetBytes)
            } ?: return@runCatching AssetResolveResult.Failure(AssetSkipReason.AssetTooLarge(maxAssetBytes))
            if (bytes.isEmpty()) return@runCatching AssetResolveResult.Failure(AssetSkipReason.EmptyAsset)
            if (totalAssetBytes + bytes.size > maxTotalAssetBytes) {
                return@runCatching AssetResolveResult.Failure(AssetSkipReason.TotalLimitExceeded(maxTotalAssetBytes))
            }
            val mediaType = mediaTypeFromName(file.name) ?: "image/jpeg"
            if (!isAllowedImageMediaType(mediaType)) {
                return@runCatching AssetResolveResult.Failure(
                    AssetSkipReason.UnsupportedMediaType(normalizeMediaType(mediaType)),
                )
            }
            totalAssetBytes += bytes.size
            AssetResolveResult.Success(
                EpubBinaryAsset(
                    bytes = bytes,
                    mediaType = normalizeMediaType(mediaType),
                    extension = extensionFromMediaType(mediaType, file.extension),
                ),
            )
        }.getOrElse { AssetResolveResult.Failure(AssetSkipReason.LoadFailed) }
    }

    private fun InputStream.readBytesWithLimit(limit: Long): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            if (total > limit) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun isAllowedImageMediaType(mediaType: String): Boolean {
        return when (normalizeMediaType(mediaType)) {
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/svg+xml", "image/webp" -> true
            else -> false
        }
    }

    private fun extensionFromMediaType(
        mediaType: String,
        fallback: String? = null,
    ): String {
        return when (normalizeMediaType(mediaType)) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/svg+xml" -> "svg"
            "image/webp" -> "webp"
            else -> fallback?.ifBlank { null } ?: "jpg"
        }
    }

    private fun mediaTypeFromName(name: String): String? {
        return when (name.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            else -> URLConnection.guessContentTypeFromName(name)
        }
    }

    private fun normalizeMediaType(mediaType: String): String {
        return mediaType.substringBefore(';').trim().lowercase()
    }

    private sealed interface AssetResolveResult {
        data class Success(val asset: EpubBinaryAsset) : AssetResolveResult
        data class Failure(val reason: AssetSkipReason) : AssetResolveResult
    }

    private sealed interface AssetSkipReason {
        data object UnsupportedUri : AssetSkipReason
        data object NetworkUnavailable : AssetSkipReason
        data object LoadFailed : AssetSkipReason
        data object EmptyAsset : AssetSkipReason
        data class HttpError(val code: Int) : AssetSkipReason
        data class AssetTooLarge(val limitBytes: Long) : AssetSkipReason
        data class TotalLimitExceeded(val limitBytes: Long) : AssetSkipReason
        data class UnsupportedMediaType(val mediaType: String) : AssetSkipReason

        fun toWarning(src: String, candidate: String): String {
            val target = if (candidate == src) src else "$src ($candidate)"
            val reason = when (this) {
                UnsupportedUri -> "unsupported or unresolved image URI"
                NetworkUnavailable -> "network client is unavailable"
                LoadFailed -> "asset could not be loaded"
                EmptyAsset -> "asset is empty"
                is HttpError -> "network request failed with HTTP $code"
                is AssetTooLarge -> "asset exceeds ${limitBytes.toDisplaySize()} limit"
                is TotalLimitExceeded -> "total embedded image size exceeds ${limitBytes.toDisplaySize()} limit"
                is UnsupportedMediaType -> "unsupported format $mediaType"
            }
            return "Image $target skipped: $reason."
        }

        private fun Long.toDisplaySize(): String {
            val mib = this / (1024L * 1024L)
            return if (mib > 0) "${mib}MB" else "${this}B"
        }
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 8 * 1024
        const val DEFAULT_MAX_ASSET_BYTES = 5L * 1024L * 1024L
        const val DEFAULT_MAX_TOTAL_ASSET_BYTES = 25L * 1024L * 1024L
    }
}

internal data class EpubAssetResolutionReport(
    val asset: EpubBinaryAsset?,
    val warning: String? = null,
)

internal data class EpubBinaryAsset(
    val bytes: ByteArray,
    val mediaType: String,
    val extension: String,
)
