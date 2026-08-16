package eu.kanade.presentation.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPowerPolicyBannerLogicTest {

    @Test
    fun `dismissed banner stays hidden even on restricted xiaomi devices`() {
        assertFalse(
            shouldShowDownloadPowerPolicyBanner(
                isXiaomiFamily = true,
                ignoringBatteryOptimizations = false,
                dismissed = true,
            ),
        )
    }

    @Test
    fun `xiaomi devices see the banner until dismissed`() {
        assertTrue(
            shouldShowDownloadPowerPolicyBanner(
                isXiaomiFamily = true,
                ignoringBatteryOptimizations = true,
                dismissed = false,
            ),
        )
    }

    @Test
    fun `banner shows while battery optimizations are still enabled`() {
        assertTrue(
            shouldShowDownloadPowerPolicyBanner(
                isXiaomiFamily = false,
                ignoringBatteryOptimizations = false,
                dismissed = false,
            ),
        )
    }

    @Test
    fun `banner hides automatically on unrestricted non-xiaomi devices`() {
        assertFalse(
            shouldShowDownloadPowerPolicyBanner(
                isXiaomiFamily = false,
                ignoringBatteryOptimizations = true,
                dismissed = false,
            ),
        )
    }
}
