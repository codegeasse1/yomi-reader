package eu.kanade.tachiyomi.data.library

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.workManager

object LibraryUpdateCoordinator {

    const val CHAIN_TAG = "LibraryUpdate-chain"

    fun startAll(
        context: Context,
        updateAnime: Boolean,
        updateManga: Boolean,
        updateNovel: Boolean,
        workManager: WorkManager = context.workManager,
    ): Boolean {
        if (workManager.isRunning("AnimeLibraryUpdate") ||
            workManager.isRunning("LibraryUpdate") ||
            workManager.isRunning("NovelLibraryUpdate")
        ) {
            return false
        }

        val requests = mutableListOf<OneTimeWorkRequest>()
        if (updateAnime) {
            requests.add(
                OneTimeWorkRequestBuilder<AnimeLibraryUpdateJob>()
                    .addTag("AnimeLibraryUpdate")
                    .addTag("AnimeLibraryUpdate-manual")
                    .build(),
            )
        }
        if (updateManga) {
            requests.add(
                OneTimeWorkRequestBuilder<MangaLibraryUpdateJob>()
                    .addTag("LibraryUpdate")
                    .addTag("LibraryUpdate-manual")
                    .build(),
            )
        }
        if (updateNovel) {
            requests.add(
                OneTimeWorkRequestBuilder<NovelLibraryUpdateJob>()
                    .addTag("NovelLibraryUpdate")
                    .addTag("NovelLibraryUpdate-manual")
                    .build(),
            )
        }

        if (requests.isEmpty()) return false

        var continuation = workManager.beginUniqueWork(
            CHAIN_TAG,
            ExistingWorkPolicy.KEEP,
            requests[0],
        )
        for (i in 1 until requests.size) {
            continuation = continuation.then(requests[i])
        }
        continuation.enqueue()
        return true
    }

    fun stop(
        context: Context,
        workManager: WorkManager = context.workManager,
    ) {
        workManager.cancelAllWorkByTag(CHAIN_TAG)
        AnimeLibraryUpdateJob.stop(context)
        MangaLibraryUpdateJob.stop(context)
        NovelLibraryUpdateJob.stop(context)
    }
}
