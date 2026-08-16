package eu.kanade.domain.extension.manga.interactor

import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extensionstore.model.ExtensionStore
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustMangaExtensionTest {

    /**
     * SharedPreferences-like in-memory store: every getter wraps the same underlying map, so
     * writes made through one instance are visible to later lookups (InMemoryPreferenceStore
     * copies values per lookup, which breaks string-set prefs).
     */
    private class MapPreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, Any?>()

        private inner class MapPreference<T>(
            private val key: String,
            private val default: T,
        ) : Preference<T> {
            @Suppress("UNCHECKED_CAST")
            override fun get(): T = (values[key] as? T) ?: default
            override fun key() = key
            override fun isSet() = values.containsKey(key)
            override fun defaultValue() = default
            override fun set(value: T) {
                values[key] = value
            }
            override fun delete() {
                values.remove(key)
            }
            override fun changes(): Flow<T> = flow { emit(get()) }
            override fun stateIn(scope: CoroutineScope): StateFlow<T> =
                changes().stateIn(scope, SharingStarted.Eagerly, get())
        }

        override fun getString(key: String, defaultValue: String) = MapPreference(key, defaultValue)
        override fun getLong(key: String, defaultValue: Long) = MapPreference(key, defaultValue)
        override fun getInt(key: String, defaultValue: Int) = MapPreference(key, defaultValue)
        override fun getFloat(key: String, defaultValue: Float) = MapPreference(key, defaultValue)
        override fun getBoolean(key: String, defaultValue: Boolean) = MapPreference(key, defaultValue)
        override fun getStringSet(key: String, defaultValue: Set<String>) = MapPreference(key, defaultValue)
        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ) = MapPreference(key, defaultValue)
        override fun getAll(): Map<String, *> = values
    }

    private class FakeStoreRepository : MangaExtensionStoreRepository {
        override suspend fun insert(indexUrl: String): Result<Unit> = Result.success(Unit)
        override suspend fun insertFromPreference(indexUrl: String, name: String) = Unit
        override suspend fun refreshAll() = Unit
        override suspend fun upsertStore(store: ExtensionStore) = Unit
        override suspend fun setCustomName(indexUrl: String, customName: String?) = Unit
        override suspend fun getAll(): List<ExtensionStore> = emptyList()
        override fun getAllAsFlow(): Flow<List<ExtensionStore>> = flowOf(emptyList())
        override fun getCountAsFlow(): Flow<Long> = flowOf(0L)
        override suspend fun remove(indexUrl: String) = Unit
        override suspend fun ensureLegacyMigrated() = Unit
    }

    private fun prefs(): SourcePreferences = SourcePreferences(MapPreferenceStore())

    private fun interactor(prefs: SourcePreferences) = TrustMangaExtension(
        mangaExtensionStoreRepository = FakeStoreRepository(),
        preferences = prefs,
    )

    @Test
    fun `trustIfSameSigner carries trust to the new version when the key matches`() {
        val prefs = prefs()
        val interactor = interactor(prefs)

        interactor.trust("eu.kanade.tachiyomi.extension.en.demo", 1, "sig-a")
        interactor.trustIfSameSigner("eu.kanade.tachiyomi.extension.en.demo", 2, "sig-a")

        val trusted = prefs.trustedExtensions().get()
        assertTrue("eu.kanade.tachiyomi.extension.en.demo:2:sig-a" in trusted)
        assertFalse(trusted.any { it.startsWith("eu.kanade.tachiyomi.extension.en.demo:1") })
    }

    @Test
    fun `trustIfSameSigner does nothing when the key changed`() {
        val prefs = prefs()
        val interactor = interactor(prefs)

        interactor.trust("eu.kanade.tachiyomi.extension.en.demo", 1, "sig-a")
        interactor.trustIfSameSigner("eu.kanade.tachiyomi.extension.en.demo", 2, "sig-b")

        val trusted = prefs.trustedExtensions().get()
        assertEquals(
            setOf("eu.kanade.tachiyomi.extension.en.demo:1:sig-a"),
            trusted,
        )
    }

    @Test
    fun `trustIfSameSigner does nothing when the package was never trusted`() {
        val prefs = prefs()
        val interactor = interactor(prefs)

        interactor.trustIfSameSigner("eu.kanade.tachiyomi.extension.en.other", 5, "sig-x")

        assertTrue(prefs.trustedExtensions().get().isEmpty())
    }
}
