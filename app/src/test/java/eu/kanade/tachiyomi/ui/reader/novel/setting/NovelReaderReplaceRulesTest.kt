package eu.kanade.tachiyomi.ui.reader.novel.setting

import eu.kanade.tachiyomi.ui.reader.novel.replace.ReplaceRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class NovelReaderReplaceRulesTest {

    private fun createPrefs(store: FakePreferenceStore = FakePreferenceStore()): NovelReaderPreferences {
        return NovelReaderPreferences(
            preferenceStore = store,
            json = Json { encodeDefaults = true },
        )
    }

    @Test
    fun `replace rules default to empty`() {
        createPrefs().replaceRules() shouldBe emptyList()
    }

    @Test
    fun `set and get roundtrips all rule fields`() {
        val prefs = createPrefs()
        val rules = listOf(
            ReplaceRule(
                id = 1L,
                name = "Имя",
                group = "Группа",
                pattern = "p1",
                replacement = "r1",
                scopeTitle = true,
                scopeContent = false,
                isEnabled = false,
                isRegex = false,
                timeoutMillisecond = 1234L,
                order = 3,
            ),
            ReplaceRule(pattern = "p2", replacement = "r2"),
        )

        prefs.setReplaceRules(rules)
        prefs.replaceRules() shouldBe rules
    }

    @Test
    fun `setting empty rules clears storage`() {
        val prefs = createPrefs()
        prefs.setReplaceRules(listOf(ReplaceRule(pattern = "p", replacement = "r")))

        prefs.setReplaceRules(emptyList())

        prefs.replaceRules() shouldBe emptyList()
    }

    @Test
    fun `enabledReplaceRules filters disabled and invalid`() {
        val prefs = createPrefs()
        prefs.setReplaceRules(
            listOf(
                ReplaceRule(pattern = "ok", replacement = "x", isRegex = false),
                ReplaceRule(pattern = "выкл", replacement = "x", isRegex = false, isEnabled = false),
                ReplaceRule(pattern = "", replacement = "x", isRegex = false),
                ReplaceRule(pattern = "([x", replacement = "y", isRegex = true),
            ),
        )

        prefs.enabledReplaceRules() shouldBe listOf(
            ReplaceRule(pattern = "ok", replacement = "x", isRegex = false),
        )
    }

    @Test
    fun `export produces legado-compatible json with all fields`() {
        val prefs = createPrefs()
        prefs.setReplaceRules(
            listOf(
                ReplaceRule(
                    id = 7L,
                    name = "Имя",
                    pattern = "p",
                    replacement = "r",
                    isRegex = false,
                ),
            ),
        )

        val exported = prefs.exportReplaceRules()

        exported.contains("\"id\":7") shouldBe true
        exported.contains("\"name\":\"Имя\"") shouldBe true
        exported.contains("\"pattern\":\"p\"") shouldBe true
        exported.contains("\"replacement\":\"r\"") shouldBe true
        exported.contains("\"scopeTitle\":false") shouldBe true
        exported.contains("\"scopeContent\":true") shouldBe true
        exported.contains("\"isEnabled\":true") shouldBe true
        exported.contains("\"isRegex\":false") shouldBe true
        exported.contains("\"timeoutMillisecond\":3000") shouldBe true
        exported.contains("\"order\":0") shouldBe true
    }

    @Test
    fun `import merges by id and tolerates unknown keys`() {
        val prefs = createPrefs()
        prefs.setReplaceRules(listOf(ReplaceRule(id = 1L, pattern = "old", replacement = "1")))

        val imported = prefs.importReplaceRules(
            """
            [
              {"id": 1, "pattern": "new", "replacement": "2", "extraField": "ignored"},
              {"id": 2, "pattern": "два", "replacement": "3"}
            ]
            """.trimIndent(),
        ).getOrThrow()

        imported.map { it.id } shouldBe listOf(1L, 2L)
        prefs.replaceRules().first { it.id == 1L }.pattern shouldBe "new"
        prefs.replaceRules().first { it.id == 2L }.replacement shouldBe "3"
    }

    @Test
    fun `import of invalid json keeps existing rules and reports failure`() {
        val prefs = createPrefs()
        prefs.setReplaceRules(listOf(ReplaceRule(id = 1L, pattern = "old", replacement = "1")))

        val result = prefs.importReplaceRules("not json at all")

        result.isFailure shouldBe true
        prefs.replaceRules().map { it.id } shouldBe listOf(1L)
    }

    @Test
    fun `duplicate ids in hand-edited json are normalized`() {
        val store = FakePreferenceStore()
        val prefs = createPrefs(store)
        store.getString("novel_reader_replace_rules", "").set(
            """[{"id":1,"pattern":"a","replacement":"1"},{"id":1,"pattern":"b","replacement":"2"}]""",
        )

        val rules = prefs.replaceRules()

        rules.size shouldBe 2
        rules.first().id shouldBe 1L
        rules.map { it.id }.toSet().size shouldBe 2
    }

    private class FakePreferenceStore : PreferenceStore {
        private val strings = mutableMapOf<String, Preference<String>>()
        private val longs = mutableMapOf<String, Preference<Long>>()
        private val ints = mutableMapOf<String, Preference<Int>>()
        private val floats = mutableMapOf<String, Preference<Float>>()
        private val booleans = mutableMapOf<String, Preference<Boolean>>()
        private val stringSets = mutableMapOf<String, Preference<Set<String>>>()
        private val objects = mutableMapOf<String, Preference<Any>>()

        override fun getString(key: String, defaultValue: String): Preference<String> =
            strings.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            longs.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            ints.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            floats.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            booleans.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            stringSets.getOrPut(key) { FakePreference(key, defaultValue) }

        @Suppress("UNCHECKED_CAST")
        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> {
            return objects.getOrPut(key) { FakePreference(key, defaultValue as Any) } as Preference<T>
        }

        override fun getAll(): Map<String, *> {
            return emptyMap<String, Any>()
        }
    }

    private class FakePreference<T>(
        private val preferenceKey: String,
        defaultValue: T,
    ) : Preference<T> {
        private val state = MutableStateFlow(defaultValue)

        override fun key(): String = preferenceKey

        override fun get(): T = state.value

        override fun set(value: T) {
            state.value = value
        }

        override fun isSet(): Boolean = true

        override fun delete() = Unit

        override fun defaultValue(): T = state.value

        override fun changes(): Flow<T> = state

        override fun stateIn(scope: CoroutineScope) = state
    }
}
