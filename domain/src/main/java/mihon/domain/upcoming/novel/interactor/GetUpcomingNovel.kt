package mihon.domain.upcoming.novel.interactor

import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository

class GetUpcomingNovel(
    private val novelRepository: NovelRepository,
) {

    private val includedStatuses = setOf(
        SNovel.ONGOING.toLong(),
        SNovel.PUBLISHING_FINISHED.toLong(),
    )

    fun subscribe(): Flow<List<Novel>> {
        return novelRepository.getUpcomingNovels(includedStatuses)
    }
}
