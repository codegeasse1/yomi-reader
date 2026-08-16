package eu.kanade.tachiyomi.data.backup.models

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BackupAnimeTest {

    @Test
    fun `notes can be stored in backup anime`() {
        val backup = BackupAnime(
            source = 1L,
            url = "/anime",
            notes = "Watch after episode 4",
        )

        backup.notes shouldBe "Watch after episode 4"
    }
}
