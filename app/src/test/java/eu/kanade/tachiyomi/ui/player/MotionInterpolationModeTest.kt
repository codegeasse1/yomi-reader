package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MotionInterpolationModeTest {

    @Test
    fun `off interpolation does not apply regardless of conditions`() {
        MotionInterpolationMode.Off.shouldApply(
            gpuNextEnabled = true,
            deviceSupportsInterpolation = true,
        ) shouldBe false
    }

    @Test
    fun `auto interpolation stays off when gpu next is disabled`() {
        MotionInterpolationMode.Auto.shouldApply(
            gpuNextEnabled = false,
            deviceSupportsInterpolation = true,
        ) shouldBe false
    }

    @Test
    fun `auto interpolation applies when gpu next and device support are enabled`() {
        MotionInterpolationMode.Auto.shouldApply(
            gpuNextEnabled = true,
            deviceSupportsInterpolation = true,
        ) shouldBe true
    }

    @Test
    fun `always interpolation applies when gpu next and device support are enabled`() {
        MotionInterpolationMode.Always.shouldApply(
            gpuNextEnabled = true,
            deviceSupportsInterpolation = true,
        ) shouldBe true
    }

    @Test
    fun `always interpolation stays off when device support is disabled`() {
        MotionInterpolationMode.Always.shouldApply(
            gpuNextEnabled = true,
            deviceSupportsInterpolation = false,
        ) shouldBe false
    }

    @Test
    fun `always interpolation stays off when gpu next disabled`() {
        MotionInterpolationMode.Always.shouldApply(
            gpuNextEnabled = false,
            deviceSupportsInterpolation = true,
        ) shouldBe false
    }

    @Test
    fun `off interpolation produces no mpv options`() {
        val result: Map<String, String>? = MotionInterpolationMode.Off.mpvOptions()
        result shouldBe null
    }

    @Test
    fun `interpolation produces mpv options for tscale and interpolation`() {
        val options = MotionInterpolationMode.Auto.mpvOptions()
        options shouldBe mapOf(
            "tscale" to "oversample",
            "interpolation" to "yes",
        )
    }
}
