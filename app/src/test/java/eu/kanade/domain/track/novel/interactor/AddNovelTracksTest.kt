package eu.kanade.domain.track.novel.interactor

import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.MangaTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.interactor.GetNovelChapters
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack

class AddNovelTracksTest {

    @Test
    fun `bind uses planned status when no local chapters are read`() = runTest {
        val novelId = 42L
        val tracker = mockk<MangaTracker>(relaxed = true)
        val insertTrack = mockk<InsertNovelTrack>(relaxed = true)
        val sync = mockk<SyncNovelChapterProgressWithTrack>(relaxed = true)
        val getNovelChapters = mockk<GetNovelChapters>()
        val item = track(trackerId = 11L)

        coEvery { getNovelChapters.await(novelId) } returns listOf(
            chapter(id = 1, novelId = novelId, chapterNumber = 1.0, read = false),
        )
        coEvery { tracker.bind(item, false) } returns item

        AddNovelTracks(insertTrack, sync, getNovelChapters).bind(tracker, item, novelId)

        coVerify(exactly = 1) { tracker.bind(item, false) }
        coVerify(exactly = 1) { insertTrack.await(match { it.novelId == novelId && it.trackerId == 11L }) }
        coVerify(exactly = 0) { tracker.setRemoteLastChapterRead(any(), any()) }
    }

    @Test
    fun `bind pushes latest local read chapter when local progress is ahead`() = runTest {
        val novelId = 42L
        val tracker = mockk<MangaTracker>(relaxed = true)
        val insertTrack = mockk<InsertNovelTrack>(relaxed = true)
        val sync = mockk<SyncNovelChapterProgressWithTrack>(relaxed = true)
        val getNovelChapters = mockk<GetNovelChapters>()
        val item = track(trackerId = 11L, lastChapterRead = 0.0)

        coEvery { getNovelChapters.await(novelId) } returns listOf(
            chapter(id = 1, novelId = novelId, chapterNumber = 1.0, read = true),
            chapter(id = 2, novelId = novelId, chapterNumber = 2.0, read = true),
            chapter(id = 3, novelId = novelId, chapterNumber = 3.0, read = false),
        )
        coEvery { tracker.bind(item, true) } returns item

        AddNovelTracks(insertTrack, sync, getNovelChapters).bind(tracker, item, novelId)

        coVerify(exactly = 1) { tracker.bind(item, true) }
        // Novel entries pass isNovelEntry = true since novel tracking landed.
        coVerify(exactly = 1) { tracker.setRemoteLastChapterRead(match { it.manga_id == novelId }, 2, true) }
        coVerify(atLeast = 1) { insertTrack.await(match { it.novelId == novelId && it.trackerId == 11L }) }
    }

    private fun track(
        trackerId: Long,
        lastChapterRead: Double = 0.0,
    ): MangaTrack {
        return MangaTrack.create(trackerId).also {
            it.id = -1
            it.remote_id = 123L
            it.title = "Novel"
            it.tracking_url = "https://www.novellist.co/novels/novel#uuid"
            it.last_chapter_read = lastChapterRead
        }
    }

    private fun chapter(
        id: Long,
        novelId: Long,
        chapterNumber: Double,
        read: Boolean,
    ): NovelChapter {
        return NovelChapter.create().copy(
            id = id,
            novelId = novelId,
            chapterNumber = chapterNumber,
            read = read,
        )
    }
}
