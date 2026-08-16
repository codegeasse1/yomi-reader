package eu.kanade.tachiyomi.data.backup.create

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BackupExtensionStoreOptionTest {

    @Test
    fun `stores are exported only when the extension repo option is on`() {
        shouldBackupExtensionStores(
            options = BackupOptions(extensionRepoSettings = true),
            includeType = true,
        ) shouldBe true

        shouldBackupExtensionStores(
            options = BackupOptions(extensionRepoSettings = false),
            includeType = true,
        ) shouldBe false
    }

    @Test
    fun `stores of a media type excluded from the backup are skipped`() {
        shouldBackupExtensionStores(
            options = BackupOptions(extensionRepoSettings = true),
            includeType = false,
        ) shouldBe false
    }
}
