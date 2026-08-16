package eu.kanade.tachiyomi.data.library

import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob
import eu.kanade.tachiyomi.util.system.workManager
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

internal data class LibraryAutoUpdateConstraintPolicy(
    val requireWifi: Boolean,
    val requireNotMetered: Boolean,
    val requireCharging: Boolean,
)

internal fun resolveLibraryAutoUpdateConstraintPolicy(
    restrictions: Set<String>,
    forceWifiAndCharging: Boolean,
): LibraryAutoUpdateConstraintPolicy {
    val requireWifi = forceWifiAndCharging || (DEVICE_ONLY_ON_WIFI in restrictions)
    val requireCharging = forceWifiAndCharging || (DEVICE_CHARGING in restrictions)
    val requireNotMetered = DEVICE_NETWORK_NOT_METERED in restrictions
    return LibraryAutoUpdateConstraintPolicy(
        requireWifi = requireWifi,
        requireNotMetered = requireNotMetered,
        requireCharging = requireCharging,
    )
}

internal fun shouldRetryLegacyAutoUpdateRun(
    restrictions: Set<String>,
    isConnectedToWifi: Boolean,
    isCharging: Boolean,
): Boolean {
    if ((DEVICE_ONLY_ON_WIFI in restrictions) && !isConnectedToWifi) {
        return true
    }
    if ((DEVICE_NETWORK_NOT_METERED in restrictions) && !isConnectedToWifi) {
        return true
    }
    if ((DEVICE_CHARGING in restrictions) && !isCharging) {
        return true
    }
    return false
}

class LibraryAutoUpdateSchedulerJob(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val wm = context.workManager
        val preferences = Injekt.get<LibraryPreferences>()
        val now = System.currentTimeMillis()
        val bufferMs = 5 * 60 * 1000L // 5 minutes buffer

        val generalInterval = preferences.autoUpdateInterval().get()
        val animePref = preferences.animeUpdateInterval().get()
        val mangaPref = preferences.mangaUpdateInterval().get()
        val novelPref = preferences.novelUpdateInterval().get()

        val animeInterval = if (animePref == -2) generalInterval else animePref
        val mangaInterval = if (mangaPref == -2) generalInterval else mangaPref
        val novelInterval = if (novelPref == -2) generalInterval else novelPref

        var scheduledAny = false

        if (animeInterval > 0) {
            val lastUpdate = preferences.lastAnimeUpdateTimestamp().get()
            val intervalMs = animeInterval * 3600000L
            if (lastUpdate == 0L || (now - lastUpdate) >= intervalMs - bufferMs) {
                wm.enqueueUniqueWork(
                    ANIME_AUTO_TRIGGER_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<AnimeLibraryUpdateJob>()
                        .addTag(ANIME_TAG)
                        .addTag(ANIME_AUTO_TAG)
                        .build(),
                )
                preferences.lastAnimeUpdateTimestamp().set(now)
                scheduledAny = true
            }
        }

        if (mangaInterval > 0) {
            val lastUpdate = preferences.lastMangaUpdateTimestamp().get()
            val intervalMs = mangaInterval * 3600000L
            if (lastUpdate == 0L || (now - lastUpdate) >= intervalMs - bufferMs) {
                wm.enqueueUniqueWork(
                    MANGA_AUTO_TRIGGER_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<MangaLibraryUpdateJob>()
                        .addTag(MANGA_TAG)
                        .addTag(MANGA_AUTO_TAG)
                        .build(),
                )
                preferences.lastMangaUpdateTimestamp().set(now)
                scheduledAny = true
            }
        }

        if (novelInterval > 0) {
            val lastUpdate = preferences.lastNovelUpdateTimestamp().get()
            val intervalMs = novelInterval * 3600000L
            if (lastUpdate == 0L || (now - lastUpdate) >= intervalMs - bufferMs) {
                wm.enqueueUniqueWork(
                    NOVEL_AUTO_TRIGGER_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<NovelLibraryUpdateJob>()
                        .addTag(NOVEL_TAG)
                        .addTag(NOVEL_AUTO_TAG)
                        .build(),
                )
                preferences.lastNovelUpdateTimestamp().set(now)
                scheduledAny = true
            }
        }

        if (scheduledAny) {
            preferences.lastUpdatedTimestamp().set(now)
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "LibraryAutoUpdateScheduler"
        private const val WORK_NAME_AUTO = "LibraryAutoUpdateScheduler-auto"

        // Legacy periodic work names to cancel after coalescing.
        private const val LEGACY_MANGA_PERIODIC_WORK_NAME = "LibraryUpdate-auto"
        private const val LEGACY_ANIME_PERIODIC_WORK_NAME = "AnimeLibraryUpdate-auto"
        private const val LEGACY_NOVEL_PERIODIC_WORK_NAME = "NovelLibraryUpdate-auto"

        // Child worker tags used by existing worker logic.
        private const val MANGA_TAG = "LibraryUpdate"
        private const val MANGA_AUTO_TAG = "LibraryUpdate-auto"
        private const val ANIME_TAG = "AnimeLibraryUpdate"
        private const val ANIME_AUTO_TAG = "AnimeLibraryUpdate-auto"
        private const val NOVEL_TAG = "NovelLibraryUpdate"
        private const val NOVEL_AUTO_TAG = "NovelLibraryUpdate-auto"

        private const val MANGA_AUTO_TRIGGER_WORK_NAME = "LibraryUpdate-auto-trigger"
        private const val ANIME_AUTO_TRIGGER_WORK_NAME = "AnimeLibraryUpdate-auto-trigger"
        private const val NOVEL_AUTO_TRIGGER_WORK_NAME = "NovelLibraryUpdate-auto-trigger"

        fun setupTask(
            context: Context,
            prefInterval: Int? = null,
        ) {
            val preferences = Injekt.get<LibraryPreferences>()
            val generalInterval = prefInterval ?: preferences.autoUpdateInterval().get()

            val animePref = preferences.animeUpdateInterval().get()
            val mangaPref = preferences.mangaUpdateInterval().get()
            val novelPref = preferences.novelUpdateInterval().get()

            val animeInterval = if (animePref == -2) generalInterval else animePref
            val mangaInterval = if (mangaPref == -2) generalInterval else mangaPref
            val novelInterval = if (novelPref == -2) generalInterval else novelPref

            val activeIntervals = listOf(animeInterval, mangaInterval, novelInterval).filter { it > 0 }

            cancelLegacyPeriodicWorks(context)

            if (activeIntervals.isNotEmpty()) {
                val minInterval = activeIntervals.minOrNull() ?: generalInterval
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                val forceWifiAndCharging = preferences.autoUpdateWifiAndChargingOnly().get()
                val constraints = buildConstraints(restrictions, forceWifiAndCharging)
                val request = PeriodicWorkRequestBuilder<LibraryAutoUpdateSchedulerJob>(
                    minInterval.toLong(),
                    TimeUnit.HOURS,
                    15,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()
                context.workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
        }

        private fun buildConstraints(
            restrictions: Set<String>,
            forceWifiAndCharging: Boolean,
        ): Constraints {
            val policy = resolveLibraryAutoUpdateConstraintPolicy(
                restrictions = restrictions,
                forceWifiAndCharging = forceWifiAndCharging,
            )
            val networkType = if (policy.requireNotMetered) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }
            val networkRequestBuilder = NetworkRequest.Builder()
            if (policy.requireWifi) {
                networkRequestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            }
            if (policy.requireNotMetered) {
                networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
            return Constraints.Builder()
                // 'networkRequest' only applies to Android 9+, otherwise 'networkType' is used
                .setRequiredNetworkRequest(networkRequestBuilder.build(), networkType)
                .setRequiresCharging(policy.requireCharging)
                .setRequiresBatteryNotLow(true)
                .build()
        }

        private fun cancelLegacyPeriodicWorks(context: Context) {
            val wm = context.workManager
            wm.cancelUniqueWork(LEGACY_MANGA_PERIODIC_WORK_NAME)
            wm.cancelUniqueWork(LEGACY_ANIME_PERIODIC_WORK_NAME)
            wm.cancelUniqueWork(LEGACY_NOVEL_PERIODIC_WORK_NAME)
        }
    }
}
