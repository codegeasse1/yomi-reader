package eu.kanade.presentation.reader.novel

import android.view.KeyEvent
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelReaderVolumeKeyTest {

    @Test
    fun `volume buttons disabled lets the key fall through`() {
        resolveVolumeKeyAction(
            keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
            action = KeyEvent.ACTION_UP,
            useVolumeButtons = false,
            showReaderUi = { false },
        ) shouldBe VolumeKeyAction.NONE
    }

    @Test
    fun `non volume key falls through`() {
        resolveVolumeKeyAction(
            keyCode = KeyEvent.KEYCODE_A,
            action = KeyEvent.ACTION_UP,
            useVolumeButtons = true,
            showReaderUi = { false },
        ) shouldBe VolumeKeyAction.NONE
    }

    @Test
    fun `action down is consumed without acting so the system slider stays hidden`() {
        resolveVolumeKeyAction(
            keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
            action = KeyEvent.ACTION_DOWN,
            useVolumeButtons = true,
            showReaderUi = { false },
        ) shouldBe VolumeKeyAction.CONSUME
    }

    @Test
    fun `non up action other than down falls through`() {
        resolveVolumeKeyAction(
            keyCode = KeyEvent.KEYCODE_VOLUME_UP,
            action = KeyEvent.ACTION_MULTIPLE,
            useVolumeButtons = true,
            showReaderUi = { false },
        ) shouldBe VolumeKeyAction.NONE
    }

    @Test
    fun `volume up with reader ui visible is consumed but does not navigate`() {
        resolveVolumeKeyAction(
            keyCode = KeyEvent.KEYCODE_VOLUME_UP,
            action = KeyEvent.ACTION_UP,
            useVolumeButtons = true,
            showReaderUi = { true },
        ) shouldBe VolumeKeyAction.CONSUME
    }

    @Test
    fun `volume up moves backwards`() {
        resolveVolumeKeyAction(
            keyCode = KeyEvent.KEYCODE_VOLUME_UP,
            action = KeyEvent.ACTION_UP,
            useVolumeButtons = true,
            showReaderUi = { false },
        ) shouldBe VolumeKeyAction.BACKWARD
    }

    @Test
    fun `volume down moves forwards`() {
        resolveVolumeKeyAction(
            keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
            action = KeyEvent.ACTION_UP,
            useVolumeButtons = true,
            showReaderUi = { false },
        ) shouldBe VolumeKeyAction.FORWARD
    }
}
