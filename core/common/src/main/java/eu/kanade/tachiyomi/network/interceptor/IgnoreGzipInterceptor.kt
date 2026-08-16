package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Legacy OkHttp 4 helper for pairing with network-level [okhttp3.brotli.BrotliInterceptor].
 *
 * OkHttp 5 + KeiSource extensions expect compression via application-level
 * [okhttp3.CompressionInterceptor] instead. Do **not** register this as a network
 * interceptor on the host default client — KeiSource fails with
 * "IgnoreGzipInterceptor must not be present in default client".
 *
 * Kept for reference / any out-of-tree callers; [eu.kanade.tachiyomi.network.NetworkHelper]
 * no longer installs it.
 */
class IgnoreGzipInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (request.header("Accept-Encoding") == "gzip") {
            request = request.newBuilder().removeHeader("Accept-Encoding").build()
        }
        return chain.proceed(request)
    }
}
