package mihon.data.extension.model

import mihon.domain.extensionstore.model.ExtensionStore

interface BaseNetworkExtensionStore {
    fun toExtensionStore(indexUrl: String): ExtensionStore
}
