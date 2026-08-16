package mihon.domain.extensionrepo.model

data class ExtensionRepo(
    val baseUrl: String,
    val name: String,
    val shortName: String?,
    val website: String,
    val signingKeyFingerprint: String,
    val discord: String? = null,
    /**
     * The url the store is actually indexed by. Legacy repos, store indexes and novel plugin repos
     * use different file names, so it cannot be derived from [baseUrl].
     */
    val indexUrl: String? = null,
)
