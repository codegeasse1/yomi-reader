package eu.kanade.domain.source.novel.interactor

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.model.IncognitoPolicy
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginCapabilities
import eu.kanade.tachiyomi.novelsource.NovelSource
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.extension.novel.model.NovelPlugin

class GetNovelIncognitoStateTest {

    private val preferenceStore = FakePreferenceStore()
    private val basePreferences = BasePreferences(
        context = mockk<Context>(relaxed = true),
        preferenceStore = preferenceStore,
    )
    private val sourcePreferences = SourcePreferences(preferenceStore)

    @Test
    fun `global incognito overrides extension state`() {
        val extensionManager = FakeNovelExtensionManager(pluginId = "plugin-a", isNsfw = true)
        sourcePreferences.incognitoNovelExtensions().set(setOf("plugin-a"))
        sourcePreferences.incognitoPolicy().set(IncognitoPolicy.NSFW_AUTO)
        basePreferences.incognitoMode().set(true)

        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.await(SOURCE_ID) shouldBe true
    }

    @Test
    fun `nsfw auto enables incognito for flagged source`() {
        val extensionManager = FakeNovelExtensionManager(pluginId = "plugin-a", isNsfw = true)
        sourcePreferences.incognitoPolicy().set(IncognitoPolicy.NSFW_AUTO)

        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.await(SOURCE_ID) shouldBe true
    }

    @Test
    fun `per-extension incognito works in manual mode`() {
        val extensionManager = FakeNovelExtensionManager(pluginId = "plugin-a", isNsfw = true)
        sourcePreferences.incognitoNovelExtensions().set(setOf("plugin-a"))
        sourcePreferences.incognitoPolicy().set(IncognitoPolicy.MANUAL_ONLY)

        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.await(SOURCE_ID) shouldBe true
    }

    @Test
    fun `manual mode ignores nsfw without extension toggle`() {
        val extensionManager = FakeNovelExtensionManager(pluginId = "plugin-a", isNsfw = true)
        sourcePreferences.incognitoPolicy().set(IncognitoPolicy.MANUAL_ONLY)

        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.await(SOURCE_ID) shouldBe false
    }

    @Test
    fun `nsfw auto pauses history outside library`() {
        val extensionManager = FakeNovelExtensionManager(pluginId = "plugin-a", isNsfw = true)
        sourcePreferences.incognitoPolicy().set(IncognitoPolicy.NSFW_AUTO)

        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.shouldPauseHistory(SOURCE_ID, inLibrary = false) shouldBe true
    }

    @Test
    fun `library entry keeps history with nsfw auto`() {
        val extensionManager = FakeNovelExtensionManager(pluginId = "plugin-a", isNsfw = true)
        sourcePreferences.incognitoPolicy().set(IncognitoPolicy.NSFW_AUTO)

        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.shouldPauseHistory(SOURCE_ID, inLibrary = true) shouldBe false
    }

    @Test
    fun `global incognito pauses history even in library`() {
        val extensionManager = FakeNovelExtensionManager(pluginId = "plugin-a", isNsfw = false)
        basePreferences.incognitoMode().set(true)

        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.shouldPauseHistory(SOURCE_ID, inLibrary = true) shouldBe true
    }

    @Test
    fun `null source id only reflects global incognito`() {
        val extensionManager = FakeNovelExtensionManager(pluginId = "plugin-a", isNsfw = true)
        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.await(null) shouldBe false
        basePreferences.incognitoMode().set(true)
        state.await(null) shouldBe true
    }

    private class FakeNovelExtensionManager(
        private val pluginId: String,
        private val isNsfw: Boolean,
    ) : NovelExtensionManager {
        override val installedSourcesFlow: Flow<List<NovelSource>> = MutableStateFlow(emptyList())
        override val installedPluginsFlow: Flow<List<NovelPlugin.Installed>> = MutableStateFlow(emptyList())
        override val availablePluginsFlow: Flow<List<NovelPlugin.Available>> = MutableStateFlow(emptyList())
        override val untrustedPluginsFlow: Flow<List<NovelPlugin.Untrusted>> = MutableStateFlow(emptyList())
        override val updatesFlow: Flow<List<NovelPlugin.Installed>> = MutableStateFlow(emptyList())
        override val signatureMismatchEvents: SharedFlow<NovelExtensionManager.SignatureMismatchEvent> =
            MutableSharedFlow()
        override fun reportSignatureMismatch(pluginId: String) = Unit
        override val repoFetchErrors: Flow<Map<String, String>> = flowOf(emptyMap())

        override suspend fun refreshAvailablePlugins() = Unit
        override suspend fun installPlugin(plugin: NovelPlugin.Available): NovelPlugin.Installed {
            error("Not used")
        }
        override suspend fun uninstallPlugin(plugin: NovelPlugin.Installed) = Unit
        override suspend fun uninstallPlugin(plugin: NovelPlugin.Untrusted) = Unit
        override suspend fun replacePluginFromRepo(
            installed: NovelPlugin.Installed,
            replacement: NovelPlugin.Available,
        ): NovelPlugin.Installed = installed
        override suspend fun trustPlugin(plugin: NovelPlugin.Untrusted) = Unit
        override suspend fun getSourceData(id: Long) = null
        override fun getPluginIconUrlForSource(sourceId: Long): String? = null
        override fun getCapabilitiesForSource(sourceId: Long): NovelPluginCapabilities? = null
        override fun getPluginId(sourceId: Long): String? = pluginId.takeIf { sourceId == SOURCE_ID }
        override fun getPluginIdAsFlow(sourceId: Long): Flow<String?> = MutableStateFlow(getPluginId(sourceId))
        override fun isNsfwForSource(sourceId: Long): Boolean = sourceId == SOURCE_ID && isNsfw
        override fun isNsfwForSourceAsFlow(sourceId: Long): Flow<Boolean> = MutableStateFlow(isNsfwForSource(sourceId))
    }

    private class FakePreferenceStore : PreferenceStore {
        private val booleans = mutableMapOf<String, Preference<Boolean>>()
        private val stringSets = mutableMapOf<String, Preference<Set<String>>>()
        private val objects = mutableMapOf<String, Preference<Any>>()

        override fun getString(key: String, defaultValue: String): Preference<String> =
            error("Not used")

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            error("Not used")

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            error("Not used")

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            error("Not used")

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            booleans.getOrPut(key) { FakePreference(defaultValue) }

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            stringSets.getOrPut(key) { FakePreference(defaultValue) }

        @Suppress("UNCHECKED_CAST")
        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> =
            objects.getOrPut(key) { FakePreference(defaultValue as Any) as Preference<Any> } as Preference<T>

        override fun getAll(): Map<String, *> = emptyMap<String, Any>()
    }

    private class FakePreference<T>(initial: T) : Preference<T> {
        private val state = MutableStateFlow(initial)
        override fun key(): String = "fake"
        override fun get(): T = state.value
        override fun set(value: T) {
            state.value = value
        }
        override fun isSet(): Boolean = true
        override fun delete() = Unit
        override fun defaultValue(): T = state.value
        override fun changes(): Flow<T> = state
        override fun stateIn(scope: kotlinx.coroutines.CoroutineScope) = state
    }

    private companion object {
        const val SOURCE_ID = 42L
    }
}
