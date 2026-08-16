package mihon.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Runs the legacy `extension_repos` -> `extension_store` port at most once per install.
 *
 * The port used to be gated on "the store table is empty", which re-imported - and therefore
 * re-trusted - every legacy repo as soon as the user deleted their last store. The decision is now
 * persisted, so it survives a process restart; an already populated store table counts as ported so
 * existing rows are never overwritten with their legacy values.
 */
internal class LegacyExtensionStorePortGuard(
    preferenceStore: PreferenceStore,
    key: String,
) {
    private val ported: Preference<Boolean> = preferenceStore.getBoolean(Preference.appStateKey(key), false)
    private val mutex = Mutex()

    @Volatile
    private var checked = false

    /**
     * Invokes [port] only when the legacy rows still have to be copied, using [storeCount] to detect
     * an install that was already ported by an earlier build.
     */
    suspend fun runOnce(storeCount: suspend () -> Long, port: suspend () -> Unit) {
        if (checked) return
        mutex.withLock {
            if (checked) return
            if (ported.get()) {
                checked = true
                return
            }
            if (storeCount() > 0L) {
                markPorted()
                return
            }
            port()
            markPorted()
        }
    }

    private fun markPorted() {
        ported.set(true)
        checked = true
    }
}
