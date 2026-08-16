package mihon.feature.upcoming.novel

import tachiyomi.domain.entries.novel.model.Novel
import java.time.LocalDate

sealed interface UpcomingNovelUIModel {
    data class Header(val date: LocalDate, val novelCount: Int) : UpcomingNovelUIModel
    data class Item(val novel: Novel) : UpcomingNovelUIModel
}
