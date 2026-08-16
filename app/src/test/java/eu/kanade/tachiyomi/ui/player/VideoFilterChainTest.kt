package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class VideoFilterChainTest {

    @Test
    fun `no filters produce null chain`() {
        buildVideoFilterChain(debanding = Debanding.None, useYuv420p = false) shouldBe null
    }

    @Test
    fun `cpu debanding alone produces gradfun filter`() {
        buildVideoFilterChain(debanding = Debanding.CPU, useYuv420p = false) shouldBe
            "gradfun=radius=12"
    }

    @Test
    fun `yuv420p alone produces format filter`() {
        buildVideoFilterChain(debanding = Debanding.None, useYuv420p = true) shouldBe
            "format=yuv420p"
    }

    @Test
    fun `cpu debanding combined with yuv420p keeps both filters`() {
        buildVideoFilterChain(debanding = Debanding.CPU, useYuv420p = true) shouldBe
            "gradfun=radius=12,format=yuv420p"
    }

    @Test
    fun `gpu debanding never produces a vf filter`() {
        buildVideoFilterChain(debanding = Debanding.GPU, useYuv420p = false) shouldBe null
    }
}
