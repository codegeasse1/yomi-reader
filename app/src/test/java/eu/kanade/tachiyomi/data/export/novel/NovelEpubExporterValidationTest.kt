package eu.kanade.tachiyomi.data.export.novel

import android.app.Application
import com.adobe.epubcheck.api.EpubCheck
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import java.io.File
import java.nio.file.Path

class NovelEpubExporterValidationTest {

    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun `exported basic epub passes EPUBCheck`() {
        runBlocking {
            val epub = exportEpub(
                chapterHtml = "<p>Hello EPUBCheck</p>",
                sourceLang = "en",
            )

            EpubCheck(epub).doValidate() shouldBe 0
        }
    }

    private suspend fun exportEpub(
        chapterHtml: String,
        sourceLang: String?,
    ): File {
        val cacheDir = tempDir.resolve("cache").toFile().apply { mkdirs() }
        val application = mockk<Application>()
        every { application.cacheDir } returns cacheDir

        val downloadManager = mockk<eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager>()
        every { downloadManager.getDownloadedChapterText(any(), any()) } returns chapterHtml

        val sourceManager = sourceLang?.let { lang ->
            mockk<NovelSourceManager>().also { manager ->
                every { manager.get(any()) } returns FakeNovelSource(lang)
            }
        }

        val exporter = NovelEpubExporter(
            application = application,
            sourceManager = sourceManager,
            downloadManager = downloadManager,
        )

        val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
        val chapter = NovelChapter.create().copy(
            id = 11L,
            novelId = novel.id,
            sourceOrder = 1L,
            url = "/chapter-1",
            name = "Chapter 1",
        )
        return exporter.export(
            novel = novel,
            chapters = listOf(chapter),
        ).shouldNotBeNull()
    }

    private class FakeNovelSource(
        override val lang: String,
    ) : eu.kanade.tachiyomi.novelsource.NovelSource {
        override val id: Long = 10L
        override val name: String = "Test Source"

        override suspend fun getNovelDetails(
            novel: eu.kanade.tachiyomi.novelsource.model.SNovel,
        ) = novel

        override suspend fun getChapterList(
            novel: eu.kanade.tachiyomi.novelsource.model.SNovel,
        ) = emptyList<eu.kanade.tachiyomi.novelsource.model.SNovelChapter>()

        override suspend fun getChapterText(
            chapter: eu.kanade.tachiyomi.novelsource.model.SNovelChapter,
        ) = ""
    }
}
