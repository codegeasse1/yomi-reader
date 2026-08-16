package eu.kanade.tachiyomi.ui.player

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

enum class Anime4KShaderPreset(
    val titleRes: StringResource,
    private val shaderPaths: List<String>,
) {
    Off(
        AYMR.strings.pref_anime4k_off,
        emptyList(),
    ),
    Balanced(
        AYMR.strings.pref_anime4k_balanced,
        listOf(
            "Restore/Anime4K_Clamp_Highlights.glsl",
            "Restore/Anime4K_Restore_CNN_Soft_M.glsl",
            "Upscale/Anime4K_AutoDownscalePre_x2.glsl",
            "Upscale/Anime4K_AutoDownscalePre_x4.glsl",
            "Upscale/Anime4K_Upscale_CNN_x2_M.glsl",
        ),
    ),
    Quality(
        AYMR.strings.pref_anime4k_quality,
        listOf(
            "Restore/Anime4K_Clamp_Highlights.glsl",
            "Restore/Anime4K_Restore_CNN_M.glsl",
            "Upscale/Anime4K_AutoDownscalePre_x2.glsl",
            "Upscale/Anime4K_AutoDownscalePre_x4.glsl",
            "Upscale/Anime4K_Upscale_CNN_x2_M.glsl",
        ),
    ),
    ;

    fun shaderPaths(): List<String> = shaderPaths

    fun mpvShaderOption(): String? = shaderPaths.takeIf { it.isNotEmpty() }?.joinToString(":") {
        "~~/shaders/anime4k/$it"
    }
}
