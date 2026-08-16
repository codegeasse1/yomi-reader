package eu.kanade.domain.track.novel.interactor

import eu.kanade.domain.track.novel.model.toDbTrack
import eu.kanade.domain.track.novel.model.toNovelTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.MangaTracker
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.items.novelchapter.interactor.GetNovelChapters
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack

class AddNovelTracks(
    private val insertTrack: InsertNovelTrack,
    private val syncNovelChapterProgressWithTrack: SyncNovelChapterProgressWithTrack,
    private val getNovelChapters: GetNovelChapters,
) {

    suspend fun bind(tracker: MangaTracker, item: MangaTrack, novelId: Long) = withNonCancellableContext {
        withIOContext {
            val allChapters = getNovelChapters.await(novelId)
                .filter { it.isRecognizedNumber }
            val hasReadChapters = allChapters.any { it.read }
            val latestLocalReadChapterNumber = allChapters
                .sortedBy { it.chapterNumber }
                .takeWhile { it.read }
                .lastOrNull()
                ?.chapterNumber
                ?: 0.0

            item.manga_id = novelId
            tracker.bind(item, hasReadChapters)

            var track = item.toNovelTrack(idRequired = false) ?: return@withIOContext
            insertTrack.await(track)

            if (latestLocalReadChapterNumber > track.lastChapterRead) {
                track = track.copy(lastChapterRead = latestLocalReadChapterNumber)
                tracker.setRemoteLastChapterRead(
                    track.toDbTrack(),
                    latestLocalReadChapterNumber.toInt(),
                    isNovelEntry = true,
                )
            }

            syncNovelChapterProgressWithTrack.await(novelId, track, tracker)
        }
    }
}
