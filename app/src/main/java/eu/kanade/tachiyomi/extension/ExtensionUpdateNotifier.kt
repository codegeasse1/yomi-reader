package eu.kanade.tachiyomi.extension

import android.content.Context
import androidx.core.app.NotificationCompat
import com.yomi.reader.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notify
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.i18n.R as I18nR

class ExtensionUpdateNotifier(
    private val context: Context,
    private val securityPreferences: SecurityPreferences = Injekt.get(),
) {

    fun promptUpdates(names: List<String>, anime: Boolean = false) {
        context.notify(
            notificationId(anime),
            Notifications.CHANNEL_EXTENSIONS_UPDATE,
        ) {
            setContentTitle(
                context.resources.getQuantityString(
                    I18nR.plurals.update_check_notification_ext_updates,
                    names.size,
                    names.size,
                ),
            )
            if (!securityPreferences.hideNotificationContent().get()) {
                val extNames = names.joinToString(", ")
                setContentText(extNames)
                setStyle(NotificationCompat.BigTextStyle().bigText(extNames))
            }
            setSmallIcon(R.drawable.ic_extension_24dp)
            if (!anime) {
                setContentIntent(NotificationReceiver.openExtensionsPendingActivity(context))
            } else {
                setContentIntent(NotificationReceiver.openAnimeExtensionsPendingActivity(context))
            }
            setAutoCancel(true)
        }
    }

    fun dismiss(anime: Boolean = false) {
        context.cancelNotification(notificationId(anime))
    }

    /**
     * Auto-updates happen without any user interaction, so the result is reported instead of a
     * prompt. The pending-update prompt shares the id and is replaced by this notification.
     */
    fun notifyAutoUpdated(names: List<String>, anime: Boolean = false) {
        if (names.isEmpty()) return
        context.notify(
            notificationId(anime),
            Notifications.CHANNEL_EXTENSIONS_UPDATE,
        ) {
            setContentTitle(context.getString(I18nR.string.ext_auto_update_notif_title))
            if (!securityPreferences.hideNotificationContent().get()) {
                val extNames = names.joinToString(", ")
                setContentText(extNames)
                setStyle(NotificationCompat.BigTextStyle().bigText(extNames))
            }
            setSmallIcon(R.drawable.ic_extension_24dp)
            if (!anime) {
                setContentIntent(NotificationReceiver.openExtensionsPendingActivity(context))
            } else {
                setContentIntent(NotificationReceiver.openAnimeExtensionsPendingActivity(context))
            }
            setAutoCancel(true)
        }
    }

    /**
     * Auto-update is enabled, but updates were skipped because they are shared system installs
     * (only privately installed extensions can be auto-updated). Shown once per run so the user
     * understands why the count did not go down.
     */
    fun notifySharedAutoUpdateSkipped(names: List<String>, anime: Boolean = false) {
        if (names.isEmpty()) return
        context.notify(
            notificationId(anime),
            Notifications.CHANNEL_EXTENSIONS_UPDATE,
        ) {
            setContentTitle(context.getString(I18nR.string.ext_auto_update_shared_skipped_title))
            if (!securityPreferences.hideNotificationContent().get()) {
                val extNames = names.joinToString(", ")
                setContentText(extNames)
                setStyle(NotificationCompat.BigTextStyle().bigText(extNames))
            }
            setSmallIcon(R.drawable.ic_extension_24dp)
            if (!anime) {
                setContentIntent(NotificationReceiver.openExtensionsPendingActivity(context))
            } else {
                setContentIntent(NotificationReceiver.openAnimeExtensionsPendingActivity(context))
            }
            setAutoCancel(true)
        }
    }

    /**
     * Anime and manga post their own notification: a shared id meant each media type dismissed the
     * other's pending update prompt.
     */
    private fun notificationId(anime: Boolean): Int {
        return if (anime) Notifications.ID_UPDATES_TO_ANIME_EXTS else Notifications.ID_UPDATES_TO_EXTS
    }
}
