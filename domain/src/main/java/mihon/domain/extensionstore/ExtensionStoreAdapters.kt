package mihon.domain.extensionstore

import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionstore.model.ExtensionStore
import mihon.domain.extensionstore.model.legacyBaseUrl
import mihon.domain.extensionstore.model.toLegacyExtensionRepoUrl

fun ExtensionStore.toExtensionRepo(): ExtensionRepo {
    return ExtensionRepo(
        baseUrl = legacyBaseUrl(),
        name = displayName,
        shortName = (customName ?: badgeLabel).takeIf { it != displayName },
        website = contact.website,
        signingKeyFingerprint = signingKey,
        discord = contact.discord,
        indexUrl = indexUrl,
    )
}

fun ExtensionRepo.toLegacyExtensionStore(): ExtensionStore {
    return ExtensionStore(
        indexUrl = baseUrl.toLegacyExtensionRepoUrl(),
        name = name,
        badgeLabel = shortName ?: name,
        signingKey = signingKeyFingerprint,
        contact = ExtensionStore.Contact(
            website = website,
            discord = discord,
        ),
        isLegacy = true,
        extensionListUrl = null,
    )
}
