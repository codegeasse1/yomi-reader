package eu.kanade.tachiyomi.extension

import eu.kanade.domain.base.BasePreferences
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtensionInstallerSelectionTest {

    @Test
    fun `updating a privately installed extension stays private`() {
        BasePreferences.ExtensionInstaller.entries.forEach { preferred ->
            resolveExtensionInstaller(
                preferred = preferred,
                isUpdateForPrivatelyInstalled = true,
            ) shouldBe BasePreferences.ExtensionInstaller.PRIVATE
        }
    }

    @Test
    fun `a normal install follows the configured backend`() {
        BasePreferences.ExtensionInstaller.entries.forEach { preferred ->
            resolveExtensionInstaller(
                preferred = preferred,
                isUpdateForPrivatelyInstalled = false,
            ) shouldBe preferred
        }
    }
}
