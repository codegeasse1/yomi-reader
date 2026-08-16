package eu.kanade.tachiyomi.ui.player

import android.os.Build
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import tachiyomi.i18n.aniyomi.AYMR

enum class DecoderPreset(
    val titleRes: StringResource,
) {
    Device(AYMR.strings.pref_decoder_preset_device),
    Low(AYMR.strings.pref_decoder_preset_low),
    Mid(AYMR.strings.pref_decoder_preset_mid),
    High(AYMR.strings.pref_decoder_preset_high),
    ;

    fun applyTo(decoderPreferences: DecoderPreferences) {
        val supportsGpuNext = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        decoderPreferences.tryHWDecoding().set(true)
        decoderPreferences.gpuNext().set(
            when (this) {
                Device -> supportsGpuNext
                Low -> false
                Mid, High -> true
            },
        )
        decoderPreferences.videoDebanding().set(
            when (this) {
                Device -> if (supportsGpuNext) Debanding.GPU else Debanding.None
                Low -> Debanding.None
                Mid, High -> Debanding.GPU
            },
        )
        decoderPreferences.anime4kShaderPreset().set(
            when (this) {
                Device, Low -> Anime4KShaderPreset.Off
                Mid -> Anime4KShaderPreset.Balanced
                High -> Anime4KShaderPreset.Quality
            },
        )
        decoderPreferences.motionInterpolationMode().set(
            when (this) {
                Device, Low -> MotionInterpolationMode.Off
                Mid -> MotionInterpolationMode.Auto
                High -> MotionInterpolationMode.Always
            },
        )
        decoderPreferences.useYUV420P().set(false)
    }
}
