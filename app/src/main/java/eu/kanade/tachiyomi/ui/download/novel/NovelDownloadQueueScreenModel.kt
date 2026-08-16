package eu.kanade.tachiyomi.ui.download.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueManager
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueState
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownload
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NovelDownloadQueueScreenModel(
    private val downloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val queueState: Flow<NovelDownloadQueueState> = NovelDownloadQueueManager.state,
) : StateScreenModel<NovelDownloadQueueScreenModel.State>(State()) {

    private var progressTickerJob: Job? = null
    private val startTimes = mutableMapOf<Long, Long>()

    init {
        screenModelScope.launch {
            queueState.collect { queueState ->
                mutableState.update { current ->
                    current.copy(
                        queueTasks = queueState.tasks,
                        isQueueRunning = queueState.isRunning,
                    )
                }
                startProgressTickerIfNeeded(queueState.tasks)
            }
        }
    }

    private fun startProgressTickerIfNeeded(tasks: List<NovelQueuedDownload>) {
        val activeTasks = tasks.filter { it.status == NovelQueuedDownloadStatus.DOWNLOADING }
        if (activeTasks.isEmpty()) {
            progressTickerJob?.cancel()
            progressTickerJob = null
            startTimes.clear()
            if (state.value.simulatedProgress.isNotEmpty()) {
                mutableState.update { it.copy(simulatedProgress = emptyMap()) }
            }
            return
        }

        if (progressTickerJob == null) {
            progressTickerJob = screenModelScope.launch {
                val duration = 8000f // Estimate 8 seconds for download
                while (true) {
                    val currentActiveTasks = state.value.queueTasks.filter {
                        it.status ==
                            NovelQueuedDownloadStatus.DOWNLOADING
                    }
                    if (currentActiveTasks.isEmpty()) break

                    val now = System.currentTimeMillis()
                    val nextProgress = mutableState.value.simulatedProgress.toMutableMap()

                    // Clean up untracked tasks
                    val activeIds = currentActiveTasks.map { it.taskId }.toSet()
                    nextProgress.keys.retainAll(activeIds)
                    startTimes.keys.retainAll(activeIds)

                    currentActiveTasks.forEach { task ->
                        val startTime = startTimes.getOrPut(task.taskId) { now }
                        val elapsed = now - startTime
                        val t = (elapsed / duration).coerceIn(0f, 0.95f)
                        // Use a beautiful ease-out curve so it starts fast and slows down towards 95%
                        val progress = 0.01f + (1f - (1f - t) * (1f - t)) * 0.94f
                        nextProgress[task.taskId] = progress
                    }

                    mutableState.update { it.copy(simulatedProgress = nextProgress) }
                    delay(100L) // Update every 100ms for smooth 10fps animation
                }
                progressTickerJob = null
            }
        }
    }

    fun startDownloads() {
        NovelDownloadQueueManager.startDownloads()
    }

    fun pauseDownloads() {
        NovelDownloadQueueManager.pauseDownloads()
    }

    fun retryFailed() {
        NovelDownloadQueueManager.retryFailed()
    }

    fun cancel(novelId: Long, chapterId: Long) {
        NovelDownloadQueueManager.cancelTask(novelId, chapterId)
    }

    @Immutable
    data class State(
        val isQueueRunning: Boolean = true,
        val queueTasks: List<NovelQueuedDownload> = emptyList(),
        val simulatedProgress: Map<Long, Float> = emptyMap(),
    ) {
        val pendingCount: Int
            get() = queueTasks.count { it.status == NovelQueuedDownloadStatus.QUEUED }
        val activeCount: Int
            get() = queueTasks.count { it.status == NovelQueuedDownloadStatus.DOWNLOADING }
        val failedCount: Int
            get() = queueTasks.count { it.status == NovelQueuedDownloadStatus.FAILED }
        val queueCount: Int
            get() = pendingCount + activeCount + failedCount
    }
}
