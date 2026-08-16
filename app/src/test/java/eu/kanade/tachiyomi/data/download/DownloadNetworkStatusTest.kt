package eu.kanade.tachiyomi.data.download

import eu.kanade.tachiyomi.util.system.NetworkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class DownloadNetworkStatusTest {

    @Test
    fun offlineMapsToNoNetwork() {
        val status = NetworkState(isConnected = false, isValidated = false, isWifi = false)
            .toDownloadNetworkStatus(requireWifi = false)
        assertEquals(DownloadNetworkStatus.NoNetwork, status)
    }

    @Test
    fun onlineWithoutWifiRequirementMapsToAvailable() {
        val status = NetworkState(isConnected = true, isValidated = true, isWifi = false)
            .toDownloadNetworkStatus(requireWifi = false)
        assertEquals(DownloadNetworkStatus.Available, status)
    }

    @Test
    fun onlineOnCellularWithWifiRequirementMapsToNoWifi() {
        val status = NetworkState(isConnected = true, isValidated = true, isWifi = false)
            .toDownloadNetworkStatus(requireWifi = true)
        assertEquals(DownloadNetworkStatus.NoWifi, status)
    }

    @Test
    fun onlineOnWifiWithWifiRequirementMapsToAvailable() {
        val status = NetworkState(isConnected = true, isValidated = true, isWifi = true)
            .toDownloadNetworkStatus(requireWifi = true)
        assertEquals(DownloadNetworkStatus.Available, status)
    }

    @Test
    fun networkRelatedFailureDetectsUnknownHost() {
        assertTrue(isNetworkRelatedFailure(UnknownHostException("offline")))
    }

    @Test
    fun shouldRequeueNovelTaskWhenWaitingForNetwork() {
        assertTrue(
            shouldRequeueNovelTaskAfterFailure(
                waitingForNetwork = true,
                networkAvailable = true,
                exception = null,
            ),
        )
    }

    @Test
    fun shouldNotRequeueNovelTaskForUnrelatedFailureWhenNetworkAvailable() {
        assertFalse(
            shouldRequeueNovelTaskAfterFailure(
                waitingForNetwork = false,
                networkAvailable = true,
                exception = IllegalStateException("parse error"),
            ),
        )
    }
}
