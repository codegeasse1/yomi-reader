package eu.kanade.tachiyomi.ui.base.activity

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegateImpl
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegate
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegateImpl
import eu.kanade.tachiyomi.util.system.prepareTabletUiContext

open class BaseActivity :
    AppCompatActivity(),
    SecureActivityDelegate by SecureActivityDelegateImpl(),
    ThemingDelegate by ThemingDelegateImpl() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.prepareTabletUiContext())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(this)
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // A single oversized entry in Compose's SaveableStateRegistry (e.g. a WebView page
        // snapshot, which can be hundreds of kilobytes) pushes the instance-state parcel past
        // the ~1MB Binder transaction limit, crashing with TransactionTooLargeException when
        // the activity is stopped. Drop oversized entries so the parcel stays small; ordinary
        // small state is preserved.
        val lifecycleState = outState.getBundle(SAVED_STATE_LIFECYCLE_KEY) ?: return
        for (registryKey in lifecycleState.keySet().filter { it.startsWith(SAVED_STATE_REGISTRY_PREFIX) }) {
            val registry = lifecycleState.getBundle(registryKey) ?: continue
            for (entryKey in registry.keySet()) {
                val value = registry.get(entryKey)
                if (value is Bundle && value.sizeof() > MAX_SAVED_STATE_ENTRY_BYTES) {
                    registry.remove(entryKey)
                }
            }
            if (registry.sizeof() > MAX_SAVED_STATE_REGISTRY_BYTES) {
                lifecycleState.remove(registryKey)
            }
        }
    }

    private companion object {
        const val SAVED_STATE_LIFECYCLE_KEY = "androidx.lifecycle.BundlableSavedStateRegistry.key"
        const val SAVED_STATE_REGISTRY_PREFIX = "SaveableStateRegistry"
        const val MAX_SAVED_STATE_ENTRY_BYTES = 256 * 1024
        const val MAX_SAVED_STATE_REGISTRY_BYTES = 400 * 1024
    }
}
