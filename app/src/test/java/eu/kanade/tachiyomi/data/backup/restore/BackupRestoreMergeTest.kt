package eu.kanade.tachiyomi.data.backup.restore

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BackupRestoreMergeTest {

    @Test
    fun `newer backup note is restored`() {
        resolveRestoredText(
            backupValue = "Keep this note",
            backupVersion = 5,
            currentValue = "Existing note",
            currentVersion = 4,
        ) shouldBe "Keep this note"
    }

    @Test
    fun `missing backup note keeps existing note`() {
        resolveRestoredText(
            backupValue = null,
            backupVersion = 5,
            currentValue = "Existing note",
            currentVersion = 4,
        ) shouldBe "Existing note"
    }

    @Test
    fun `older backup note does not replace current note`() {
        resolveRestoredText(
            backupValue = "Older note",
            backupVersion = 3,
            currentValue = "Existing note",
            currentVersion = 4,
        ) shouldBe "Existing note"
    }
}
