package eu.kanade.tachiyomi.ui.player

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

enum class MotionInterpolationMode(
    val titleRes: StringResource,
) {
    Off(AYMR.strings.pref_motion_interpolation_off),
    Auto(AYMR.strings.pref_motion_interpolation_auto),
    Always(AYMR.strings.pref_motion_interpolation_always),
    ;

    fun shouldApply(gpuNextEnabled: Boolean, deviceSupportsInterpolation: Boolean): Boolean {
        if (this == Off) return false
        if (!gpuNextEnabled) return false
        if (!deviceSupportsInterpolation) return false
        return true
    }

    fun mpvOptions(): Map<String, String>? {
        return if (this == Off) {
            null
        } else {
            mapOf(
                "tscale" to "oversample",
                "interpolation" to "yes",
            )
        }
    }
}
