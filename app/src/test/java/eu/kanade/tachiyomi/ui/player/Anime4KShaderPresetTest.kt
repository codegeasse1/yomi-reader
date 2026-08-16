package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.i18n.aniyomi.AYMR

class Anime4KShaderPresetTest {

    @Test
    fun `balanced preset exposes the localized title`() {
        Anime4KShaderPreset.Balanced.titleRes shouldBe AYMR.strings.pref_anime4k_balanced
    }

    @Test
    fun `quality preset exposes the localized title`() {
        Anime4KShaderPreset.Quality.titleRes shouldBe AYMR.strings.pref_anime4k_quality
    }

    @Test
    fun `off preset does not produce shader options`() {
        Anime4KShaderPreset.Off.mpvShaderOption() shouldBe null as String?
    }

    @Test
    fun `balanced preset produces mpv shader option`() {
        val option = Anime4KShaderPreset.Balanced.mpvShaderOption()
        option shouldBe (
            "~~/shaders/anime4k/Restore/Anime4K_Clamp_Highlights.glsl:" +
                "~~/shaders/anime4k/Restore/Anime4K_Restore_CNN_Soft_M.glsl:" +
                "~~/shaders/anime4k/Upscale/Anime4K_AutoDownscalePre_x2.glsl:" +
                "~~/shaders/anime4k/Upscale/Anime4K_AutoDownscalePre_x4.glsl:" +
                "~~/shaders/anime4k/Upscale/Anime4K_Upscale_CNN_x2_M.glsl"
            )
    }

    @Test
    fun `quality preset produces mpv shader option`() {
        val option = Anime4KShaderPreset.Quality.mpvShaderOption()
        option shouldBe (
            "~~/shaders/anime4k/Restore/Anime4K_Clamp_Highlights.glsl:" +
                "~~/shaders/anime4k/Restore/Anime4K_Restore_CNN_M.glsl:" +
                "~~/shaders/anime4k/Upscale/Anime4K_AutoDownscalePre_x2.glsl:" +
                "~~/shaders/anime4k/Upscale/Anime4K_AutoDownscalePre_x4.glsl:" +
                "~~/shaders/anime4k/Upscale/Anime4K_Upscale_CNN_x2_M.glsl"
            )
    }
}
