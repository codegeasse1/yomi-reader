package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.service.LibraryPreferences

class LibraryPreferencesGlobalGroupingTest {

    @Test
    fun `per-media values are preserved when global toggle is on`() {
        val store = InMemoryPreferenceStore()
        val prefs = LibraryPreferences(store)

        prefs.mangaGroupLibraryBy().set(LibraryGroup.BY_SOURCE)
        prefs.animeGroupLibraryBy().set(LibraryGroup.BY_STATUS)
        prefs.novelGroupLibraryBy().set(LibraryGroup.UNGROUPED)
        prefs.globalGroupLibrary().set(false)

        prefs.globalGroupLibrary().set(true)
        prefs.globalGroupLibraryBy().set(LibraryGroup.BY_TRACK_STATUS)

        prefs.mangaGroupLibraryBy().get() shouldBe LibraryGroup.BY_SOURCE
        prefs.animeGroupLibraryBy().get() shouldBe LibraryGroup.BY_STATUS
        prefs.novelGroupLibraryBy().get() shouldBe LibraryGroup.UNGROUPED

        prefs.globalGroupLibraryBy().get() shouldBe LibraryGroup.BY_TRACK_STATUS
    }

    @Test
    fun `global toggle defaults to false`() {
        val store = InMemoryPreferenceStore()
        val prefs = LibraryPreferences(store)
        prefs.globalGroupLibrary().get() shouldBe false
    }

    @Test
    fun `global group by defaults to BY_DEFAULT`() {
        val store = InMemoryPreferenceStore()
        val prefs = LibraryPreferences(store)
        prefs.globalGroupLibraryBy().get() shouldBe LibraryGroup.BY_DEFAULT
    }
}

private class InMemoryPreferenceStore : PreferenceStore {
    private val values = mutableMapOf<String, MutableStateFlow<Any?>>()

    override fun getString(key: String, defaultValue: String): Preference<String> =
        getOrCreate(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
        getOrCreate(key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
        getOrCreate(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
        getOrCreate(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
        getOrCreate(key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
        getOrCreate(key, defaultValue)

    @Suppress("UNCHECKED_CAST")
    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = getOrCreate(key, defaultValue) as Preference<T>

    override fun getAll(): Map<String, *> = emptyMap<String, Any>()

    private fun <T> getOrCreate(key: String, defaultValue: T): Preference<T> {
        val flow = values.getOrPut(key) { MutableStateFlow<Any?>(defaultValue) }
        return object : Preference<T> {
            override fun key(): String = key

            @Suppress("UNCHECKED_CAST")
            override fun get(): T = flow.value as T

            @Suppress("UNCHECKED_CAST")
            override fun set(value: T) {
                flow.value = value
            }

            override fun isSet(): Boolean = true

            override fun delete() {
                flow.value = null
            }

            @Suppress("UNCHECKED_CAST")
            override fun defaultValue(): T = defaultValue

            @Suppress("UNCHECKED_CAST")
            override fun changes(): Flow<T> = flow as Flow<T>

            @Suppress("UNCHECKED_CAST")
            override fun stateIn(scope: CoroutineScope): StateFlow<T> =
                flow.asStateFlow() as StateFlow<T>
        }
    }
}
