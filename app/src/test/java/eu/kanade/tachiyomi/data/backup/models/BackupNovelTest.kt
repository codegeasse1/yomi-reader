package eu.kanade.tachiyomi.data.backup.models

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BackupNovelTest {

    @Test
    fun `excluded scanlators can be stored in backup novel`() {
        val backup = BackupNovel(
            source = 1L,
            url = "/novel",
            excludedScanlators = listOf("Team A", "Team B"),
            notes = "Check chapter 12",
        )

        backup.excludedScanlators shouldBe listOf("Team A", "Team B")
        backup.notes shouldBe "Check chapter 12"
    }
}
