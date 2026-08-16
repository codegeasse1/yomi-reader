package eu.kanade.tachiyomi.data.download

import eu.kanade.tachiyomi.util.system.NetworkState

sealed interface DownloadNetworkStatus {
    data object Available : DownloadNetworkStatus
    data object NoNetwork : DownloadNetworkStatus
    data object NoWifi : DownloadNetworkStatus
}

fun NetworkState.toDownloadNetworkStatus(requireWifi: Boolean): DownloadNetworkStatus {
    return when {
        !isOnline -> DownloadNetworkStatus.NoNetwork
        requireWifi && !isWifi -> DownloadNetworkStatus.NoWifi
        else -> DownloadNetworkStatus.Available
    }
}

fun isNetworkRelatedFailure(throwable: Throwable?): Boolean {
    var cause = throwable
    while (cause != null) {
        when (cause) {
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.SocketTimeoutException,
            is javax.net.ssl.SSLException,
            -> return true
        }
        cause = cause.cause
    }
    return false
}

internal fun shouldRequeueNovelTaskAfterFailure(
    waitingForNetwork: Boolean,
    networkAvailable: Boolean,
    exception: Throwable?,
): Boolean {
    if (waitingForNetwork) return true
    if (!networkAvailable) return true
    return isNetworkRelatedFailure(exception)
}
