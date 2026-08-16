package mihon.data.extension.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable
import mihon.domain.extensionstore.model.ExtensionStore

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class NetworkLegacyExtension(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int = 0,
    val sources: List<Source>? = null,
) {
    @Serializable
    data class Source(
        val id: Long,
        val lang: String,
        val name: String,
        val baseUrl: String,
    )

    fun toAvailableExtensionData(
        store: ExtensionStore,
        storeBaseUrl: String,
    ): AvailableExtensionData? {
        // One malformed entry must not fail the whole store index (see service mapNotNull).
        val libVersion = version.substringBeforeLast('.').toDoubleOrNull() ?: return null
        return AvailableExtensionData(
            name = name.stripLegacyExtensionNamePrefix(),
            pkgName = pkg,
            apkUrl = "$storeBaseUrl/apk/$apk",
            iconUrl = "$storeBaseUrl/icon/$pkg.png",
            libVersion = libVersion,
            versionCode = code,
            versionName = version,
            lang = lang,
            isNsfw = nsfw == 1,
            sources = if (sources.isNullOrEmpty()) {
                listOf(
                    AvailableExtensionData.Source(
                        id = 0,
                        name = name,
                        lang = lang,
                        baseUrl = "",
                    ),
                )
            } else {
                sources.map { source ->
                    AvailableExtensionData.Source(
                        id = source.id,
                        name = source.name,
                        lang = source.lang,
                        baseUrl = source.baseUrl,
                    )
                }
            },
            store = store,
        )
    }
}

/**
 * Legacy `index.min.json` entries carry a host prefix in their name: `Tachiyomi: ` for manga repos
 * and `Aniyomi: ` for anime ones. Both have to go, otherwise the whole anime catalogue sorts under
 * the same letter and each extension renames itself once installed (the installed side strips it).
 */
internal fun String.stripLegacyExtensionNamePrefix(): String {
    return substringAfter("Tachiyomi: ").substringAfter("Aniyomi: ")
}
