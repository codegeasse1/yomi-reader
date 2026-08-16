package mihon.data.extension.repository

import mihon.domain.extensionstore.model.ExtensionStore

internal fun extensionStoreMapper(
    indexUrl: String,
    name: String,
    badgeLabel: String,
    signingKey: String,
    contactWebsite: String,
    contactDiscord: String?,
    isLegacy: Boolean,
    extensionListUrl: String?,
    customName: String?,
): ExtensionStore = ExtensionStore(
    indexUrl = indexUrl,
    name = name,
    badgeLabel = badgeLabel,
    signingKey = signingKey,
    contact = ExtensionStore.Contact(
        website = contactWebsite,
        discord = contactDiscord,
    ),
    isLegacy = isLegacy,
    extensionListUrl = extensionListUrl,
    customName = customName,
)
