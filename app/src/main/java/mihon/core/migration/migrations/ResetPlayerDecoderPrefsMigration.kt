package mihon.core.migration.migrations

import eu.kanade.tachiyomi.ui.player.DecoderPreset
import eu.kanade.tachiyomi.ui.player.MotionInterpolationMode
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/**
 * One-time reset of decoder preferences that shipped with battery-hungry defaults
 * (see the player battery optimization batch):
 * - `use_yuv420p` stored `true` forces the CPU `format=yuv420p` filter, which defeats
 *   hardware decoding on 10-bit HEVC content. Reset to the new default (false) so hwdec
 *   stays intact; users who need the fallback can re-enable it in player settings.
 * - `pref_motion_interpolation_mode` stored `Auto` together with preset `Device` is the old
 *   footgun default (frame interpolation is the most power-hungry mode). Deliberate choices
 *   (presets `Mid`/`High`, `Always`) are preserved.
 */
class ResetPlayerDecoderPrefsMigration : Migration {
    override val version: Float = 189f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false

        resetPlayerDecoderPrefs(
            useYuv420pPref = preferenceStore.getBoolean("use_yuv420p", false),
            presetPref = preferenceStore.getEnum("pref_decoder_preset", DecoderPreset.Device),
            interpolationPref = preferenceStore.getEnum(
                "pref_motion_interpolation_mode",
                MotionInterpolationMode.Off,
            ),
        )

        return true
    }
}

fun resetPlayerDecoderPrefs(
    useYuv420pPref: Preference<Boolean>,
    presetPref: Preference<DecoderPreset>,
    interpolationPref: Preference<MotionInterpolationMode>,
) {
    useYuv420pPref.delete()

    if (presetPref.get() == DecoderPreset.Device && interpolationPref.get() == MotionInterpolationMode.Auto) {
        interpolationPref.delete()
    }
}
