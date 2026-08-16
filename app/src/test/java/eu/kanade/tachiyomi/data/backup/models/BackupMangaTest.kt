package eu.kanade.tachiyomi.data.backup.models

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BackupMangaTest {

    @Test
    fun `notes can be stored in backup manga`() {
        val backup = BackupManga(
            source = 1L,
            url = "/manga",
            notes = "Important detail",
        )

        backup.notes shouldBe "Important detail"
    }
}
