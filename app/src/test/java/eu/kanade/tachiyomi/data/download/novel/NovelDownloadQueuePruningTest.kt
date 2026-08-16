package eu.kanade.tachiyomi.data.download.novel

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter

/**
 * TDD tests for OOM-related unbounded growth of failed download tasks in
 * [NovelDownloadQueueManager.processLoop].
 *
 * Root cause: when a download fails, [NovelDownloadQueueManager.markTaskFailed] keeps
 * the task in [NovelDownloadQueueState.tasks] with FAILED status, but the processLoop
 * only processes QUEUED tasks. Failed tasks accumulate in the StateFlow forever,
 * consuming memory until OOM.
 *
 * These tests define a [pruneFailedTasks] function that bounds failed task accumulation.
 */
class NovelDownloadQueuePruningTest {

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun novel(id: Long = 1L): Novel = Novel.create().copy(id = id)

    private fun chapter(id: Long = 1L, novelId: Long = 1L): NovelChapter =
        NovelChapter.create().copy(id = id, novelId = novelId)

    private fun failedTask(
        taskId: Long,
        novelId: Long = 1L,
        chapterId: Long = 1L,
    ): NovelQueuedDownload = NovelQueuedDownload(
        taskId = taskId,
        novel = novel(novelId),
        chapter = chapter(chapterId, novelId),
        type = NovelQueuedDownloadType.ORIGINAL,
        format = NovelQueuedDownloadFormat.HTML,
        status = NovelQueuedDownloadStatus.FAILED,
        errorMessage = "error-$taskId",
    )

    private fun queuedTask(taskId: Long): NovelQueuedDownload = NovelQueuedDownload(
        taskId = taskId,
        novel = novel(),
        chapter = chapter(),
        type = NovelQueuedDownloadType.ORIGINAL,
        format = NovelQueuedDownloadFormat.HTML,
        status = NovelQueuedDownloadStatus.QUEUED,
    )

    private fun downloadingTask(taskId: Long): NovelQueuedDownload = NovelQueuedDownload(
        taskId = taskId,
        novel = novel(),
        chapter = chapter(),
        type = NovelQueuedDownloadType.ORIGINAL,
        format = NovelQueuedDownloadFormat.HTML,
        status = NovelQueuedDownloadStatus.DOWNLOADING,
    )

    // ── Tests: pruneFailedTasks pure function ────────────────────────────

    // RED phase: these tests define the expected contract.
    // The function pruneFailedTasks does NOT exist yet.
    // The test will fail to compile → we write the function → tests pass.

    @Test
    fun `prune with under-limit failed tasks keeps all tasks unchanged`() {
        val tasks = listOf(
            failedTask(taskId = 1L),
            failedTask(taskId = 2L),
            queuedTask(taskId = 3L),
        )

        val pruned = pruneFailedTasks(tasks, maxFailed = 5)

        pruned.size shouldBe 3
        pruned.map { it.taskId } shouldContainExactly listOf(1L, 2L, 3L)
    }

    @Test
    fun `prune removes oldest failed tasks when count exceeds limit`() {
        val tasks = listOf(
            failedTask(taskId = 1L),
            failedTask(taskId = 2L),
            failedTask(taskId = 3L),
            failedTask(taskId = 4L),
        )

        val pruned = pruneFailedTasks(tasks, maxFailed = 2)

        // Oldest 2 failed tasks removed, newest 2 kept
        pruned.size shouldBe 2
        pruned.map { it.taskId } shouldContainExactly listOf(3L, 4L)
    }

    @Test
    fun `prune preserves queued and downloading tasks regardless of failed limit`() {
        val tasks = listOf(
            failedTask(taskId = 1L),
            queuedTask(taskId = 2L),
            failedTask(taskId = 3L),
            downloadingTask(taskId = 4L),
            failedTask(taskId = 5L),
        )

        val pruned = pruneFailedTasks(tasks, maxFailed = 1)

        // 3 failed → limit 1 → remove 2 oldest (1L, 3L), keep 5L + ALL non-failed
        pruned.size shouldBe 3
        pruned.map { it.taskId } shouldContainExactly listOf(2L, 4L, 5L)
    }

    @Test
    fun `prune with zero max failed removes all failed tasks`() {
        val tasks = listOf(
            failedTask(taskId = 1L),
            failedTask(taskId = 2L),
            queuedTask(taskId = 3L),
        )

        val pruned = pruneFailedTasks(tasks, maxFailed = 0)

        pruned.size shouldBe 1
        pruned.single().taskId shouldBe 3L
    }

    @Test
    fun `prune with no failed tasks returns same list`() {
        val tasks = listOf(
            queuedTask(taskId = 1L),
            downloadingTask(taskId = 2L),
        )

        val pruned = pruneFailedTasks(tasks, maxFailed = 5)

        pruned shouldBe tasks
    }

    @Test
    fun `prune empty list returns empty list`() {
        val pruned = pruneFailedTasks(emptyList(), maxFailed = 10)

        pruned.shouldBeEmpty()
    }

    @Test
    fun `prune preserves task order for non-failed tasks`() {
        val tasks = listOf(
            failedTask(taskId = 1L),
            queuedTask(taskId = 2L),
            failedTask(taskId = 3L),
            downloadingTask(taskId = 4L),
            failedTask(taskId = 5L),
            queuedTask(taskId = 6L),
        )

        val pruned = pruneFailedTasks(tasks, maxFailed = 1)

        // Failed: 1, 3, 5 → limit 1 → remove 1, 3 → keep 5
        // Queue order: 2, 4, 5, 6
        pruned.map { it.taskId } shouldContainExactly listOf(2L, 4L, 5L, 6L)
    }

    // ── Tests: mergeNovelQueuedTasks with large failure scenario ──────────

    @Test
    fun `merge does not deduplicate across different novels`() {
        val task = failedTask(taskId = 1L, novelId = 1L)

        val merged = mergeNovelQueuedTasks(
            currentTasks = listOf(task),
            novel = novel(id = 2L),
            chapters = listOf(chapter(id = 1L, novelId = 2L)),
            type = NovelQueuedDownloadType.ORIGINAL,
            format = NovelQueuedDownloadFormat.HTML,
            runtimeState = NovelDownloadQueueRuntimeState(),
        )

        merged.tasks.size shouldBe 2
    }

    @Test
    fun `many failures without pruning causes unbounded queue growth`() {
        // This test documents the BUG: failed tasks accumulate without limit.
        val failCount = 500
        val initialTasks = (1L..failCount).map { failedTask(taskId = it) }

        // Without pruning, ALL 500 failed tasks stay
        initialTasks.size shouldBe failCount
        initialTasks.count { it.status == NovelQueuedDownloadStatus.FAILED } shouldBe failCount

        // With pruning at maxFailed=100, only the newest 100 survive
        val pruned = pruneFailedTasks(initialTasks, maxFailed = 100)
        pruned.size shouldBe 100
        pruned.map { it.taskId } shouldContainExactly (401L..500L).toList()
    }
}
