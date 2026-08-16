package eu.kanade.tachiyomi.extension.novel.runtime

data class NovelPluginCapabilities(
    val hasParsePage: Boolean = false,
    val hasResolveUrl: Boolean = false,
    val hasFetchImage: Boolean = false,
    val latestListingMethod: String? = null,
    val hasPluginSettings: Boolean = false,
    val usesWebStorage: Boolean = false,
    val hasCustomJs: Boolean = false,
    val hasCustomCss: Boolean = false,
    val hasRelatedNovels: Boolean = false,
)
