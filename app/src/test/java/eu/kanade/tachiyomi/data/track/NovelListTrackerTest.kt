package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.novellist.NovelList
import eu.kanade.tachiyomi.data.track.novellist.buildNovelListTrackingUrl
import eu.kanade.tachiyomi.data.track.novellist.extractNovelListUuid
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class NovelListTrackerTest {

    @Test
    fun `status list stays aligned with novel list states`() {
        val tracker = NovelList(11L)

        tracker.getStatusListManga() shouldBe listOf(
            NovelList.READING,
            NovelList.COMPLETED,
            NovelList.ON_HOLD,
            NovelList.DROPPED,
            NovelList.PLAN_TO_READ,
        )
    }

    @Test
    fun `login rejects blank access token`() = runTest {
        val tracker = NovelList(11L)

        var failed = false
        try {
            tracker.login("NovelList User", "")
        } catch (_: Exception) {
            failed = true
        }

        failed shouldBe true
    }

    @Test
    fun `tracking url uses public novels route and stores uuid fragment`() {
        val url = buildNovelListTrackingUrl("sample-novel", "uuid-123")

        url shouldBe "https://www.novellist.co/novels/sample-novel#uuid-123"
        extractNovelListUuid(url) shouldBe "uuid-123"
    }
}
