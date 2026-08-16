package tachiyomi.source.local.entries.manga

import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LocalMangaSourceCoverTest {

    @Test
    fun `existing local cover is preferred without generating a replacement`() {
        val manga = createManga()
        var generated = false

        applyLocalMangaCover(manga, LOCAL_COVER_URL) {
            generated = true
        }

        generated shouldBe false
        manga.thumbnail_url shouldBe LOCAL_COVER_URL
    }

    @Test
    fun `cover is generated when local cover and thumbnail are absent`() {
        val manga = createManga()

        applyLocalMangaCover(manga, null) {
            manga.thumbnail_url = GENERATED_COVER_URL
        }

        manga.thumbnail_url shouldBe GENERATED_COVER_URL
    }

    @Test
    fun `cover is not generated when thumbnail already exists`() {
        val manga = createManga().apply {
            thumbnail_url = EXISTING_THUMBNAIL_URL
        }
        var generated = false

        applyLocalMangaCover(manga, null) {
            generated = true
        }

        generated shouldBe false
        manga.thumbnail_url shouldBe EXISTING_THUMBNAIL_URL
    }

    private fun createManga(): SManga {
        return SManga.create().apply {
            title = MANGA_TITLE
            url = MANGA_TITLE
            thumbnail_url = null
        }
    }

    private companion object {
        const val MANGA_TITLE = "Title"
        const val LOCAL_COVER_URL = "local-cover"
        const val GENERATED_COVER_URL = "generated-cover"
        const val EXISTING_THUMBNAIL_URL = "existing-thumbnail"
    }
}
