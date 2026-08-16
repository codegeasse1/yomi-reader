package eu.kanade.tachiyomi.ui.download

import android.app.Application
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.DownloadNetworkStatus
import eu.kanade.tachiyomi.data.download.engine.DownloadEngineFacade
import eu.kanade.tachiyomi.data.download.engine.DownloadEngineSnapshot
import eu.kanade.tachiyomi.data.download.toDownloadNetworkStatus
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.networkStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import tachiyomi.domain.download.service.DownloadPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Adapts [DownloadEngineFacade] for Compose consumption.
 * Contains no download business rules; delegates everything to the facade.
 */
class DownloadEngineScreenModel(
    private val facade: DownloadEngineFacade,
    context: Application = Injekt.get(),
    downloadPreferences: DownloadPreferences = Injekt.get(),
) : ScreenModel {

    /** Aggregated engine state exposed to the shared engine card. */
    val state: StateFlow<DownloadEngineSnapshot> = facade.state

    val networkStatus: StateFlow<DownloadNetworkStatus> = combine(
        context.networkStateFlow()
            .onStart { emit(context.activeNetworkState()) },
        downloadPreferences.downloadOnlyOverWifi().changes()
            .onStart { emit(downloadPreferences.downloadOnlyOverWifi().get()) },
    ) { networkState, requireWifi ->
        networkState.toDownloadNetworkStatus(requireWifi)
    }
        .distinctUntilChanged()
        .stateIn(
            screenModelScope,
            SharingStarted.WhileSubscribed(5000),
            context.activeNetworkState().toDownloadNetworkStatus(downloadPreferences.downloadOnlyOverWifi().get()),
        )

    fun pauseAll() = facade.pauseAll()
    fun resumeAll() = facade.resumeAll()
    fun cancelAll() = facade.cancelAll()

    override fun onDispose() {
        facade.close()
    }
}
