package eu.kanade.tachiyomi.ui.download.novel

import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueState
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownload
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadFormat
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadStatus
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelDownloadQueueScreenModelTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupMainDispatcher() {
            Dispatchers.setMain(Dispatchers.Unconfined)
        }

        @JvmStatic
        @AfterAll
        fun resetMainDispatcher() {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `queue state changes are reflected in screen model state`() {
        runBlocking {
            val queueState = MutableStateFlow(NovelDownloadQueueState())
            val screenModel = NovelDownloadQueueScreenModel(queueState = queueState)

            try {
                queueState.value = NovelDownloadQueueState(
                    tasks = listOf(queuedTask(1L)),
                    isRunning = true,
                )

                withTimeout(1_000) {
                    while (screenModel.state.value.queueCount != 1 || !screenModel.state.value.isQueueRunning) {
                        yield()
                    }
                }

                screenModel.state.value.queueCount shouldBe 1
                screenModel.state.value.isQueueRunning shouldBe true
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `empty queue results in zero queueCount`() {
        runBlocking {
            val queueState = MutableStateFlow(NovelDownloadQueueState())
            val screenModel = NovelDownloadQueueScreenModel(queueState = queueState)

            try {
                withTimeout(1_000) {
                    while (screenModel.state.value.queueCount != 0) {
                        yield()
                    }
                }

                screenModel.state.value.queueCount shouldBe 0
                screenModel.state.value.isQueueRunning shouldBe true
            } finally {
                screenModel.onDispose()
            }
        }
    }

    private fun queuedTask(taskId: Long): NovelQueuedDownload {
        return NovelQueuedDownload(
            taskId = taskId,
            novel = Novel.create().copy(id = 100L + taskId),
            chapter = NovelChapter.create().copy(id = 200L + taskId, novelId = 100L + taskId),
            type = NovelQueuedDownloadType.ORIGINAL,
            format = NovelQueuedDownloadFormat.HTML,
            status = NovelQueuedDownloadStatus.QUEUED,
        )
    }
}
