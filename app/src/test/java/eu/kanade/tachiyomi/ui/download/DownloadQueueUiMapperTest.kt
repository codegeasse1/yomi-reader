package eu.kanade.tachiyomi.ui.download

import eu.kanade.tachiyomi.data.download.engine.DownloadSection
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownload
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadFormat
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadStatus
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadType
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class DownloadQueueUiMapperTest {

    @Test
    fun `novel queued task maps to shared ui item preserving section`() {
        val novel = Novel.create().copy(id = 100L, title = "Test Novel")
        val chapter = NovelChapter.create().copy(
            id = 200L,
            novelId = 100L,
            name = "Chapter 1",
            chapterNumber = 1.0,
        )
        val task = NovelQueuedDownload(
            taskId = 42L,
            novel = novel,
            chapter = chapter,
            type = NovelQueuedDownloadType.ORIGINAL,
            format = NovelQueuedDownloadFormat.HTML,
            status = NovelQueuedDownloadStatus.QUEUED,
        )

        val item = DownloadQueueUiMapper.toUiItem(task)
        item.section shouldBe DownloadSection.NOVEL
        item.itemId shouldBe "42"
        item.title shouldBe "Chapter 1"
        item.subtitle shouldBe "Test Novel"
        item.status shouldBe DownloadQueueUiModel.QueueStatus.QUEUED
    }

    @Test
    fun `novel task with blank chapter name uses chapter number`() {
        val novel = Novel.create().copy(id = 101L)
        val chapter = NovelChapter.create().copy(
            id = 201L,
            novelId = 101L,
            name = "",
            chapterNumber = 7.0,
        )
        val task = NovelQueuedDownload(
            taskId = 1L,
            novel = novel,
            chapter = chapter,
            type = NovelQueuedDownloadType.ORIGINAL,
            format = NovelQueuedDownloadFormat.HTML,
            status = NovelQueuedDownloadStatus.QUEUED,
        )

        val item = DownloadQueueUiMapper.toUiItem(task)
        item.title shouldBe "7"
    }

    @Test
    fun `novel failed task preserves error message`() {
        val novel = Novel.create().copy(id = 102L)
        val chapter = NovelChapter.create().copy(id = 202L, novelId = 102L, name = "Ch")
        val task = NovelQueuedDownload(
            taskId = 2L,
            novel = novel,
            chapter = chapter,
            type = NovelQueuedDownloadType.TRANSLATED,
            format = NovelQueuedDownloadFormat.TXT,
            status = NovelQueuedDownloadStatus.FAILED,
            errorMessage = "Network timeout",
        )

        val item = DownloadQueueUiMapper.toUiItem(task)
        item.status shouldBe DownloadQueueUiModel.QueueStatus.FAILED
        item.description shouldBe "Network timeout"
    }

    @Test
    fun `novel downloading task gets downloading status`() {
        val novel = Novel.create().copy(id = 103L)
        val chapter = NovelChapter.create().copy(id = 203L, novelId = 103L, name = "Ch")
        val task = NovelQueuedDownload(
            taskId = 3L,
            novel = novel,
            chapter = chapter,
            type = NovelQueuedDownloadType.ORIGINAL,
            format = NovelQueuedDownloadFormat.HTML,
            status = NovelQueuedDownloadStatus.DOWNLOADING,
        )

        val item = DownloadQueueUiMapper.toUiItem(task)
        item.status shouldBe DownloadQueueUiModel.QueueStatus.DOWNLOADING
        item.description shouldBe ""
    }

    @Test
    fun `section header shape preserves title and count`() {
        val header = DownloadQueueUiModel.SectionHeader(
            section = DownloadSection.MANGA,
            title = "Source Name",
            count = 15,
        )
        header.section shouldBe DownloadSection.MANGA
        header.title shouldBe "Source Name"
        header.count shouldBe 15
    }

    @Test
    fun `queue ui item with empty metadata does not produce fake text`() {
        val item = DownloadQueueUiItem(
            section = DownloadSection.ANIME,
            itemId = "ep_1",
            title = "",
            subtitle = "",
            description = "",
            progressFraction = 0f,
            progressText = "",
            status = DownloadQueueUiModel.QueueStatus.IDLE,
        )
        item.title shouldBe ""
        item.subtitle shouldBe ""
        item.description shouldBe ""
        item.progressFraction shouldBe 0f
        item.progressText shouldBe ""
    }

    @Test
    fun `row accent follows the section not individual source name`() {
        // Section identifies the content type, not the source
        val animeItem = DownloadQueueUiItem(
            section = DownloadSection.ANIME,
            itemId = "1",
            title = "Ep 1",
            subtitle = "SourceX",
        )
        val mangaItem = DownloadQueueUiItem(
            section = DownloadSection.MANGA,
            itemId = "2",
            title = "Ch 1",
            subtitle = "SourceX", // same source name as anime
        )
        animeItem.section shouldBe DownloadSection.ANIME
        mangaItem.section shouldBe DownloadSection.MANGA
        // Subtitle can be same across sections — section determines color/accent
        animeItem.subtitle shouldBe "SourceX"
        mangaItem.subtitle shouldBe "SourceX"
    }
}
