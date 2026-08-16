package eu.kanade.presentation.download

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.DropdownMenu
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Shared overflow action menu for queue items: move, cancel, retry, folder.
 *
 * Renders a dropdown anchored to its parent composable via [expanded] state.
 * Callers manage the trigger (e.g. IconButton) and pass callbacks.
 */
@Composable
fun DownloadQueueActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMoveToTop: (() -> Unit)? = null,
    onMoveToBottom: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        onMoveToTop?.let { moveToTop ->
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.download_queue_move_to_top)) },
                onClick = {
                    moveToTop()
                    onDismiss()
                },
            )
        }
        onMoveToBottom?.let { moveToBottom ->
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.download_queue_move_to_bottom)) },
                onClick = {
                    moveToBottom()
                    onDismiss()
                },
            )
        }
        onCancel?.let { cancel ->
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.download_queue_cancel)) },
                onClick = {
                    cancel()
                    onDismiss()
                },
            )
        }
    }
}
