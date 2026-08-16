package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.util.system.sanitizeCloudflareRequestHeaders
import java.util.Locale

internal fun sanitizeCloudflareReplayHeaders(
    requestHeaders: Map<String, String>,
    contextPackageName: String,
    spoofedPackageName: String,
): Map<String, String> {
    val safeHeaders = requestHeaders.filterNot { (name, _) ->
        name.lowercase(Locale.ROOT) in cloudflareUnsafeReplayHeaderNames
    }
    return sanitizeCloudflareRequestHeaders(
        requestHeaders = safeHeaders,
        contextPackageName = contextPackageName,
        spoofedPackageName = spoofedPackageName,
    )
}

private val cloudflareUnsafeReplayHeaderNames = setOf(
    "connection",
    "content-length",
    "host",
    "keep-alive",
    "set-cookie",
    "te",
    "trailer",
    "transfer-encoding",
    "upgrade",
)
