package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.domain.extensionstore.model.ExtensionStore

/**
 * Fork-local backup shape, read and written only at Backup fields 650-652.
 *
 * Despite the shared name it is NOT wire-compatible with mihon's BackupExtensionStore: mihon aligned
 * its numbering with the old BackupExtensionRepos (4 = website, 5 = fingerprint) so that a
 * pre-migration field-106 payload still decodes, while ours uses 4 = signingKey, 5 = contactWebsite.
 * Pointing this class at field 106 would therefore swap signing keys with websites - map a separate
 * mihon-shaped model instead.
 */
@Serializable
class BackupExtensionStore(
    @ProtoNumber(1) var indexUrl: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var badgeLabel: String,
    @ProtoNumber(4) var signingKey: String,
    @ProtoNumber(5) var contactWebsite: String,
    @ProtoNumber(6) var contactDiscord: String?,
    @ProtoNumber(7) var isLegacy: Boolean,
    @ProtoNumber(8) var extensionListUrl: String?,
)

val backupExtensionStoreMapper = { store: ExtensionStore ->
    BackupExtensionStore(
        indexUrl = store.indexUrl,
        name = store.name,
        badgeLabel = store.badgeLabel,
        signingKey = store.signingKey,
        contactWebsite = store.contact.website,
        contactDiscord = store.contact.discord,
        isLegacy = store.isLegacy,
        extensionListUrl = store.extensionListUrl,
    )
}

fun BackupExtensionStore.toExtensionStore(): ExtensionStore {
    return ExtensionStore(
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
    )
}
