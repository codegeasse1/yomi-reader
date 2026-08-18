package eu.kanade.tachiyomi.ui.base.activity

import android.content.Context
import android.os.Bundle
import android.os.Parcel
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
        // the Binder transaction limit, crashing with TransactionTooLargeException when the
        // activity is stopped. Drop oversized entries so the parcel stays small; ordinary
        // small state is preserved.
        val lifecycleState = outState.getBundle(SAVED_STATE_LIFECYCLE_KEY) ?: return
        for (registryKey in lifecycleState.keySet().filter { it.startsWith(SAVED_STATE_REGISTRY_PREFIX) }) {
            val registry = lifecycleState.getBundle(registryKey) ?: continue
            for (entryKey in registry.keySet()) {
                val value = registry.get(entryKey)
                if (value is Bundle && parcelSize(value) > MAX_SAVED_STATE_ENTRY_BYTES) {
                    registry.remove(entryKey)
                }
            }
            if (parcelSize(registry) > MAX_SAVED_STATE_REGISTRY_BYTES) {
                lifecycleState.remove(registryKey)
            }
        }
    }

    private fun parcelSize(bundle: Bundle): Int {
        // Bundle.sizeof() is a hidden API, so measure by writing to a Parcel instead.
        // If measuring fails for any reason, report the maximum size so the entry is dropped
        // rather than risking a TransactionTooLargeException.
        return runCatching {
            val parcel = Parcel.obtain()
            try {
                bundle.writeToParcel(parcel, 0)
                parcel.dataSize()
            } finally {
                parcel.recycle()
            }
        }.getOrDefault(Int.MAX_VALUE)
    }

    private companion object {
        const val SAVED_STATE_LIFECYCLE_KEY = "androidx.lifecycle.BundlableSavedStateRegistry.key"
        const val SAVED_STATE_REGISTRY_PREFIX = "SaveableStateRegistry"
        const val MAX_SAVED_STATE_ENTRY_BYTES = 256 * 1024
        const val MAX_SAVED_STATE_REGISTRY_BYTES = 400 * 1024
    }
}
