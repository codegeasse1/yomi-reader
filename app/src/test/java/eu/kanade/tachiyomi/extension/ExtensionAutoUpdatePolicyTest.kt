package eu.kanade.tachiyomi.extension

import eu.kanade.domain.base.BasePreferences
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtensionAutoUpdatePolicyTest {

    @Test
    fun `a privately installed extension auto-updates when the toggle is on`() {
        canAutoUpdateExtension(
            autoUpdateEnabled = true,
            installer = BasePreferences.ExtensionInstaller.PRIVATE,
            isSharedInstall = false,
        ) shouldBe true
    }

    @Test
    fun `nothing auto-updates while the toggle is off`() {
        BasePreferences.ExtensionInstaller.entries.forEach { installer ->
            canAutoUpdateExtension(
                autoUpdateEnabled = false,
                installer = installer,
                isSharedInstall = false,
            ) shouldBe false
        }
    }

    @Test
    fun `installers that need a system dialog never auto-update`() {
        BasePreferences.ExtensionInstaller.entries
            .filterNot { it == BasePreferences.ExtensionInstaller.PRIVATE }
            .forEach { installer ->
                canAutoUpdateExtension(
                    autoUpdateEnabled = true,
                    installer = installer,
                    isSharedInstall = false,
                ) shouldBe false
            }
    }

    @Test
    fun `a system installed extension is left to a manual update`() {
        canAutoUpdateExtension(
            autoUpdateEnabled = true,
            installer = BasePreferences.ExtensionInstaller.PRIVATE,
            isSharedInstall = true,
        ) shouldBe false
    }
}
