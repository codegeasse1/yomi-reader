package mihon.core.migration.migrations

import eu.kanade.tachiyomi.ui.player.DecoderPreset
import eu.kanade.tachiyomi.ui.player.MotionInterpolationMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class ResetPlayerDecoderPrefsMigrationTest {

    private fun pref(
        key: String,
        value: Boolean,
    ) = InMemoryPreferenceStore.InMemoryPreference(key, value, false)

    private fun pref(
        key: String,
        value: DecoderPreset,
    ) = InMemoryPreferenceStore.InMemoryPreference(key, value, DecoderPreset.Device)

    private fun pref(
        key: String,
        value: MotionInterpolationMode,
    ) = InMemoryPreferenceStore.InMemoryPreference(key, value, MotionInterpolationMode.Off)

    @Test
    fun `stored yuv420p true falls back to default`() {
        val yuv420p = pref("use_yuv420p", true)
        val preset = pref("pref_decoder_preset", DecoderPreset.Device)
        val interpolation = pref("pref_motion_interpolation_mode", MotionInterpolationMode.Off)

        resetPlayerDecoderPrefs(yuv420p, preset, interpolation)

        yuv420p.get() shouldBe false
    }

    @Test
    fun `device preset with auto interpolation resets interpolation to default`() {
        val yuv420p = pref("use_yuv420p", false)
        val preset = pref("pref_decoder_preset", DecoderPreset.Device)
        val interpolation = pref("pref_motion_interpolation_mode", MotionInterpolationMode.Auto)

        resetPlayerDecoderPrefs(yuv420p, preset, interpolation)

        interpolation.get() shouldBe MotionInterpolationMode.Off
    }

    @Test
    fun `device preset with always interpolation keeps interpolation`() {
        val yuv420p = pref("use_yuv420p", false)
        val preset = pref("pref_decoder_preset", DecoderPreset.Device)
        val interpolation = pref("pref_motion_interpolation_mode", MotionInterpolationMode.Always)

        resetPlayerDecoderPrefs(yuv420p, preset, interpolation)

        interpolation.get() shouldBe MotionInterpolationMode.Always
    }

    @Test
    fun `mid preset with auto interpolation keeps interpolation`() {
        val yuv420p = pref("use_yuv420p", false)
        val preset = pref("pref_decoder_preset", DecoderPreset.Mid)
        val interpolation = pref("pref_motion_interpolation_mode", MotionInterpolationMode.Auto)

        resetPlayerDecoderPrefs(yuv420p, preset, interpolation)

        interpolation.get() shouldBe MotionInterpolationMode.Auto
    }

    @Test
    fun `high preset keeps interpolation and yuv420p reset still applies`() {
        val yuv420p = pref("use_yuv420p", true)
        val preset = pref("pref_decoder_preset", DecoderPreset.High)
        val interpolation = pref("pref_motion_interpolation_mode", MotionInterpolationMode.Always)

        resetPlayerDecoderPrefs(yuv420p, preset, interpolation)

        yuv420p.get() shouldBe false
        interpolation.get() shouldBe MotionInterpolationMode.Always
    }
}
