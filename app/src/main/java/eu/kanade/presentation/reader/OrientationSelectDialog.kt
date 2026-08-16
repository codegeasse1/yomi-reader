package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.entries.manga.model.readerOrientation
import eu.kanade.presentation.reader.components.AuroraReaderSheet
import eu.kanade.presentation.reader.components.ModeSelectionDialog
import eu.kanade.presentation.reader.settings.AuroraGlassSection
import eu.kanade.presentation.reader.settings.AuroraMiniOption
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle

private val ReaderOrientationsWithoutDefault = ReaderOrientation.entries - ReaderOrientation.DEFAULT

@Composable
fun OrientationSelectDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
    onChange: (StringResource) -> Unit,
) {
    val manga by screenModel.mangaFlow.collectAsStateWithLifecycle()
    val defaultOrientation by screenModel.preferences.defaultOrientationType().collectAsStateWithLifecycle()
    val storedOrientation = remember(manga) {
        ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt())
    }
    // Series flags win, otherwise show the resolved global orientation so the active tile is marked.
    val orientation = remember(storedOrientation, defaultOrientation) {
        if (storedOrientation != ReaderOrientation.DEFAULT) {
            storedOrientation
        } else {
            ReaderOrientation.fromPreference(defaultOrientation)
        }
    }

    AuroraReaderSheet(onDismissRequest = onDismissRequest) {
        DialogContent(
            orientation = orientation,
            hasSeriesOverride = storedOrientation != ReaderOrientation.DEFAULT,
            onChangeOrientation = {
                screenModel.onChangeOrientation(it)
                onChange(it.stringRes)
                onDismissRequest()
            },
        )
    }
}

@Composable
private fun DialogContent(
    orientation: ReaderOrientation,
    hasSeriesOverride: Boolean,
    onChangeOrientation: (ReaderOrientation) -> Unit,
) {
    var selected by remember { mutableStateOf(orientation) }

    ModeSelectionDialog(
        onUseDefault = { onChangeOrientation(ReaderOrientation.DEFAULT) }.takeIf { hasSeriesOverride },
        onApply = { onChangeOrientation(selected) },
    ) {
        AuroraGlassSection(title = stringResource(MR.strings.rotation_type)) {
            ReaderOrientationsWithoutDefault.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { item ->
                        AuroraMiniOption(
                            selected = item == selected,
                            onClick = { selected = item },
                            label = stringResource(item.stringRes),
                            icon = item.icon,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
