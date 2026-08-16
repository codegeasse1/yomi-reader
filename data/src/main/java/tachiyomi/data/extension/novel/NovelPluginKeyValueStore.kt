package tachiyomi.data.extension.novel

import android.content.Context
import android.content.SharedPreferences
import java.io.File

interface NovelPluginKeyValueStore {
    fun get(pluginId: String, key: String): String?
    fun set(pluginId: String, key: String, value: String)
    fun remove(pluginId: String, key: String)
    fun clear(pluginId: String)
    fun clearAll()
    fun keys(pluginId: String): Set<String>
}

class AndroidNovelPluginKeyValueStore(
    private val context: Context,
) : NovelPluginKeyValueStore {

    override fun get(pluginId: String, key: String): String? {
        return preferences(pluginId).getString(key, null)
    }

    override fun set(pluginId: String, key: String, value: String) {
        preferences(pluginId).edit().putString(key, value).apply()
    }

    override fun remove(pluginId: String, key: String) {
        preferences(pluginId).edit().remove(key).apply()
    }

    override fun clear(pluginId: String) {
        preferences(pluginId).edit().clear().apply()
    }

    override fun clearAll() {
        val dataDir = context.applicationInfo?.dataDir ?: return
        val sharedPrefsDir = File(dataDir, "shared_prefs")
        if (!sharedPrefsDir.exists()) return

        sharedPrefsDir.listFiles()
            ?.asSequence()
            ?.filter { file ->
                file.name.startsWith(PREFS_PREFIX) && file.name.endsWith(".xml")
            }
            ?.forEach { file ->
                val prefsName = file.name.removeSuffix(".xml")
                context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
                file.delete()
            }
    }

    override fun keys(pluginId: String): Set<String> {
        return preferences(pluginId).all.keys
    }

    private fun preferences(pluginId: String): SharedPreferences {
        // The plugin id comes from a remote index and can contain path separators or other
        // characters that are invalid in a SharedPreferences file name — a malicious index would
        // otherwise crash getSharedPreferences. Keep only safe characters.
        val safeId = pluginId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return context.getSharedPreferences(PREFS_PREFIX + safeId, Context.MODE_PRIVATE)
    }

    private companion object {
        private const val PREFS_PREFIX = "novel_plugin_storage_"
    }
}
