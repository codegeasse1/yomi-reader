package mihon.domain.extensionstore.model

data class ExtensionStore(
    val indexUrl: String,
    val name: String,
    val badgeLabel: String,
    val signingKey: String,
    val contact: Contact,
    val isLegacy: Boolean,
    val extensionListUrl: String?,
    /** Name the user gave this store; survives metadata refreshes, unlike [name]. */
    val customName: String? = null,
) {

    /** What the UI should show: the user's name when there is one, the remote name otherwise. */
    val displayName: String get() = customName ?: name
    data class Contact(
        val website: String,
        val discord: String?,
    )
}

/** Base URL used by legacy plugin listing (manga/anime index.min.json, novel plugin repos). */
fun ExtensionStore.legacyBaseUrl(): String = when {
    indexUrl.isExtensionStoreIndexUrl() -> indexUrl.toExtensionStoreBaseUrl()
    indexUrl.endsWith(".json") -> indexUrl.substringBeforeLast("/")
    else -> indexUrl.trimEnd('/')
}
