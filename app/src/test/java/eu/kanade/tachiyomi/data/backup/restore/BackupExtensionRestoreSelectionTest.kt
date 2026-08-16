package eu.kanade.tachiyomi.data.backup.restore

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BackupExtensionRestoreSelectionTest {

    private val repoBackup = BackupExtensionRepos(
        baseUrl = "https://example.org/repo",
        name = "Example",
        shortName = null,
        website = "https://example.org",
        signingKeyFingerprint = "ABC",
    )

    private val storeBackup = BackupExtensionStore(
        indexUrl = "https://example.org/repo/index.min.json",
        name = "Example",
        badgeLabel = "Example",
        signingKey = "ABC",
        contactWebsite = "https://example.org",
        contactDiscord = null,
        isLegacy = true,
        extensionListUrl = null,
    )

    @Test
    fun `legacy repo entries are skipped when the backup already carries stores`() {
        legacyExtensionRepoBackupsToRestore(
            repoBackups = listOf(repoBackup),
            storeBackups = listOf(storeBackup),
        ) shouldBe emptyList()
    }

    @Test
    fun `legacy repo entries are restored when the backup has no stores`() {
        legacyExtensionRepoBackupsToRestore(
            repoBackups = listOf(repoBackup),
            storeBackups = emptyList(),
        ) shouldBe listOf(repoBackup)
    }
}
