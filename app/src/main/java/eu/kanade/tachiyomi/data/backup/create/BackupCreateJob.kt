package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.backup.BackupDiagnosticLog
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class BackupCreateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)
    private val backupPreferences = Injekt.get<BackupPreferences>()

    override suspend fun doWork(): Result {
        val isAutoBackup = inputData.getBoolean(IS_AUTO_BACKUP_KEY, true)

        return try {
            BackupDiagnosticLog.log(context, "foreground_start")
            setForegroundSafely()
            BackupDiagnosticLog.log(context, "foreground_ready")

            if (isAutoBackup && BackupRestoreJob.isRunning(context)) {
                BackupDiagnosticLog.log(context, "deferred", "restore_in_progress")
                return Result.retry()
            }

            val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
                ?: getAutomaticBackupLocation()

            BackupDiagnosticLog.beginSession(context, isAutoBackup, uri)

            if (uri == null) {
                BackupDiagnosticLog.endSession(context, success = false, details = "no_backup_location")
                if (!isAutoBackup) {
                    notifier.showBackupError(context.stringResource(MR.strings.create_backup_file_error))
                }
                return Result.failure()
            }

            val options = inputData.getBooleanArray(OPTIONS_KEY)?.let { BackupOptions.fromBooleanArray(it) }
                ?: BackupOptions()

            BackupDiagnosticLog.log(
                context,
                "options",
                "library=${options.libraryEntries} stats=${options.stats} achievements=${options.achievements}",
            )

            val location = BackupCreator(context, isAutoBackup).backup(uri, options)
            if (!isAutoBackup) {
                notifier.showBackupComplete(location)
            }
            BackupDiagnosticLog.endSession(context, success = true, details = "bytes_written=ok")
            Result.success()
        } catch (e: CancellationException) {
            BackupDiagnosticLog.log(context, "job_cancelled")
            throw e
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            BackupDiagnosticLog.logError(context, "job_failed", e)
            BackupDiagnosticLog.endSession(context, success = false, details = formatBackupError(e))
            if (!isAutoBackup) {
                notifier.showBackupError(formatBackupError(e))
            }
            Result.failure()
        } finally {
            context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_BACKUP_PROGRESS,
            notifier.showBackupProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun getAutomaticBackupLocation(): Uri? {
        val cloudUri = backupPreferences.cloudBackupUri().get()
        if (cloudUri.isNotBlank()) {
            BackupDiagnosticLog.log(context, "auto_location", "source=cloud")
            return cloudUri.toUri()
        }
        val storageManager = Injekt.get<StorageManager>()
        BackupDiagnosticLog.log(context, "auto_location", "source=local_autobackup")
        return storageManager.getAutomaticBackupsDirectory()?.uri
    }

    companion object {
        fun isManualJobRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_MANUAL)
        }

        fun isAnyJobRunning(context: Context): Boolean {
            val workManager = context.workManager
            return workManager.isRunning(TAG_MANUAL) || workManager.isRunning(TAG_AUTO)
        }

        /**
         * Clears a stale progress notification left after a process kill (e.g. OOM during backup).
         */
        fun clearStaleProgressNotification(context: Context) {
            if (!isAnyJobRunning(context)) {
                context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)
            }
        }

        fun setupTask(context: Context, prefInterval: Int? = null) {
            val backupPreferences = Injekt.get<BackupPreferences>()
            val interval = prefInterval ?: backupPreferences.backupInterval().get()
            if (interval > 0) {
                val constraints = Constraints(
                    requiresBatteryNotLow = true,
                )

                val request = PeriodicWorkRequestBuilder<BackupCreateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                    .addTag(TAG_AUTO)
                    .setConstraints(constraints)
                    .setInputData(workDataOf(IS_AUTO_BACKUP_KEY to true))
                    .build()

                context.workManager.enqueueUniquePeriodicWork(TAG_AUTO, ExistingPeriodicWorkPolicy.UPDATE, request)
            } else {
                context.workManager.cancelUniqueWork(TAG_AUTO)
            }
        }

        fun startNow(context: Context, uri: Uri, options: BackupOptions) {
            clearStaleProgressNotification(context)
            val inputData = workDataOf(
                IS_AUTO_BACKUP_KEY to false,
                LOCATION_URI_KEY to uri.toString(),
                OPTIONS_KEY to options.asBooleanArray(),
            )
            val request = OneTimeWorkRequestBuilder<BackupCreateJob>()
                .addTag(TAG_MANUAL)
                .setInputData(inputData)
                .build()
            context.workManager.enqueueUniqueWork(TAG_MANUAL, ExistingWorkPolicy.KEEP, request)
        }
    }
}

private fun formatBackupError(e: Throwable): String {
    return when (e) {
        is OutOfMemoryError -> "Out of memory during backup"
        else -> e.message?.takeIf { it.isNotBlank() }
            ?: e::class.simpleName
            ?: "Unknown error"
    }
}

private const val TAG_AUTO = "BackupCreator"
private const val TAG_MANUAL = "$TAG_AUTO:manual"

private const val IS_AUTO_BACKUP_KEY = "is_auto_backup" // Boolean
private const val LOCATION_URI_KEY = "location_uri" // String
private const val OPTIONS_KEY = "options" // BooleanArray
