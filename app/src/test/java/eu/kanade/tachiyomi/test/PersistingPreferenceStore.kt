package eu.kanade.tachiyomi.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * In-memory [PreferenceStore] whose writes are visible to later lookups.
 *
 * `InMemoryPreferenceStore` builds a fresh preference object per lookup and never writes back, so a
 * test that does `preferences.something().set(true)` and production code that later calls
 * `preferences.something().get()` see different objects - the write silently disappears. Tests that
 * register shared singletons in Injekt must use this one instead, because whichever test class runs
 * first decides what every other class in the same JVM gets.
 */
class PersistingPreferenceStore : PreferenceStore {

    private val flows = mutableMapOf<String, MutableStateFlow<Any?>>()

    override fun getString(key: String, defaultValue: String): Preference<String> = pref(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> = pref(key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> = pref(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> = pref(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = pref(key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
        pref(key, defaultValue)

    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = pref(key, defaultValue)

    override fun getAll(): Map<String, *> = flows.mapValues { it.value.value }

    @Suppress("UNCHECKED_CAST")
    private fun <T> pref(key: String, defaultValue: T): Preference<T> {
        val state = flows.getOrPut(key) { MutableStateFlow(defaultValue as Any?) }
        return object : Preference<T> {
            override fun key(): String = key
            override fun get(): T = state.value as T
            override fun set(value: T) {
                state.value = value
            }
            override fun isSet(): Boolean = state.value != defaultValue
            override fun delete() {
                state.value = defaultValue
            }
            override fun defaultValue(): T = defaultValue
            override fun changes(): Flow<T> = state.map { it as T }
            override fun stateIn(scope: CoroutineScope): StateFlow<T> = MutableStateFlow(get())
        }
    }
}
