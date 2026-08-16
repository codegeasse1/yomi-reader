package eu.kanade.tachiyomi.data.translation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val queueManager = Injekt.get<TranslationQueueManager>()
        val notificationManager = Injekt.get<TranslationNotificationManager>()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_CANCEL_ALL -> {
                        val hadWork = queueManager.cancelAll()
                        if (hadWork) {
                            TranslationJob.stop(context)
                        }
                        notificationManager.cancelQueueProgress()
                    }
                    ACTION_PAUSE_BATCH -> {
                        val batchToken = intent.getStringExtra(EXTRA_BATCH_TOKEN).orEmpty()
                        if (batchToken.isBlank()) return@launch
                        if (queueManager.pauseBatch(batchToken)) {
                            queueManager.getBatchState(batchToken)?.let(notificationManager::showBatchPaused)
                        }
                    }
                    ACTION_RESUME_BATCH -> {
                        val batchToken = intent.getStringExtra(EXTRA_BATCH_TOKEN).orEmpty()
                        if (batchToken.isBlank()) return@launch
                        if (queueManager.resumeBatch(batchToken)) {
                            TranslationJob.runImmediately(context.applicationContext)
                        }
                    }
                    ACTION_CANCEL_BATCH -> {
                        val batchToken = intent.getStringExtra(EXTRA_BATCH_TOKEN).orEmpty()
                        if (batchToken.isBlank()) return@launch
                        val wasActive = queueManager.cancelBatch(batchToken)
                        if (wasActive) {
                            TranslationJob.stop(context)
                        }
                        if (queueManager.hasNext()) {
                            TranslationJob.runImmediately(context.applicationContext)
                        }
                        notificationManager.cancelQueueProgress()
                    }
                    else -> {
                        val chapterId = intent.getLongExtra(EXTRA_CHAPTER_ID, -1)
                        if (chapterId == -1L) return@launch
                        val wasActive = queueManager.cancelChapter(chapterId)
                        if (wasActive) {
                            TranslationJob.stop(context)
                            if (queueManager.hasNext()) {
                                TranslationJob.runImmediately(context.applicationContext)
                            }
                        }
                        context.cancelNotification(Notifications.ID_TRANSLATION_PROGRESS + chapterId.toInt())
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CANCEL_CHAPTER = "eu.kanade.tachiyomi.action.CANCEL_TRANSLATION_CHAPTER"
        const val ACTION_CANCEL_ALL = "eu.kanade.tachiyomi.action.CANCEL_TRANSLATION_ALL"
        const val ACTION_PAUSE_BATCH = "eu.kanade.tachiyomi.action.PAUSE_TRANSLATION_BATCH"
        const val ACTION_RESUME_BATCH = "eu.kanade.tachiyomi.action.RESUME_TRANSLATION_BATCH"
        const val ACTION_CANCEL_BATCH = "eu.kanade.tachiyomi.action.CANCEL_TRANSLATION_BATCH"
        const val EXTRA_CHAPTER_ID = "chapterId"
        const val EXTRA_BATCH_TOKEN = "batchToken"
    }
}
