package eu.kanade.presentation.entries.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.entries.novel.TranslationBatchScope
import eu.kanade.tachiyomi.ui.entries.novel.resolveTranslationBatchChapterIds
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun NovelTranslationBatchSheet(
    onDismissRequest: () -> Unit,
    chapterTitle: String,
    anchorChapterId: Long,
    chapters: List<NovelChapter>,
    selectedChapterIds: Set<Long>,
    downloadedChapterIds: Set<Long>,
    onStartBatch: (TranslationBatchScope, Int, Int, Int, Boolean) -> Unit,
) {
    var selectedScope by remember { mutableStateOf(TranslationBatchScope.SELECTED) }
    var customCount by remember { mutableStateOf("10") }
    var rangeStart by remember { mutableStateOf("1") }
    var rangeEnd by remember { mutableStateOf("10") }
    var forceRetranslate by remember { mutableStateOf(false) }

    val effectiveSelectedChapterIds = remember(selectedChapterIds, anchorChapterId) {
        selectedChapterIds.ifEmpty { setOf(anchorChapterId) }
    }
    val resolvedLimit = customCount.toIntOrNull()?.coerceAtLeast(1) ?: 10
    val resolvedRangeStart = rangeStart.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val resolvedRangeEnd = rangeEnd.toIntOrNull()?.coerceAtLeast(1) ?: 10
    val previewChapterIds = remember(
        selectedScope,
        resolvedLimit,
        resolvedRangeStart,
        resolvedRangeEnd,
        chapters,
        effectiveSelectedChapterIds,
        downloadedChapterIds,
    ) {
        resolveTranslationBatchChapterIds(
            scope = selectedScope,
            limit = resolvedLimit,
            chapters = chapters,
            selectedChapterIds = effectiveSelectedChapterIds,
            downloadedChapterIds = downloadedChapterIds,
            rangeStart = resolvedRangeStart,
            rangeEnd = resolvedRangeEnd,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(MR.strings.novel_batch_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            if (chapterTitle.isNotBlank()) {
                Text(
                    text = stringResource(MR.strings.novel_batch_current_chapter, chapterTitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(MR.strings.novel_batch_select_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(MR.strings.novel_batch_mode),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                ScopeChipRow(
                    selected = selectedScope,
                    onSelected = { selectedScope = it },
                )
            }

            if (selectedScope == TranslationBatchScope.FIRST_N_VISIBLE) {
                OutlinedTextField(
                    value = customCount,
                    onValueChange = { customCount = it.filter(Char::isDigit) },
                    label = { Text(stringResource(MR.strings.novel_batch_first_n_chapters)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (selectedScope == TranslationBatchScope.RANGE) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = rangeStart,
                        onValueChange = { rangeStart = it.filter(Char::isDigit) },
                        label = { Text(stringResource(MR.strings.novel_batch_from_chapter)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = rangeEnd,
                        onValueChange = { rangeEnd = it.filter(Char::isDigit) },
                        label = { Text(stringResource(MR.strings.novel_batch_to_chapter)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = forceRetranslate,
                    onCheckedChange = { forceRetranslate = it },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(MR.strings.novel_batch_force_retranslate),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(MR.strings.novel_batch_force_retranslate_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = stringResource(MR.strings.novel_batch_queue_count, previewChapterIds.size),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!forceRetranslate) {
                Text(
                    text = stringResource(MR.strings.novel_batch_skip_translated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = {
                    onStartBatch(
                        selectedScope,
                        resolvedLimit,
                        resolvedRangeStart,
                        resolvedRangeEnd,
                        forceRetranslate,
                    )
                },
                enabled = previewChapterIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(MR.strings.novel_batch_start),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        }
    }
}

@Composable
private fun ScopeChipRow(
    selected: TranslationBatchScope,
    onSelected: (TranslationBatchScope) -> Unit,
) {
    val scopes = listOf(
        TranslationBatchScope.SELECTED to stringResource(MR.strings.novel_batch_selected),
        TranslationBatchScope.DOWNLOADED to stringResource(MR.strings.label_downloaded),
        TranslationBatchScope.UNREAD to stringResource(MR.strings.unread),
        TranslationBatchScope.FIRST_N_VISIBLE to stringResource(MR.strings.novel_batch_first_n),
        TranslationBatchScope.RANGE to stringResource(MR.strings.novel_batch_range),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        scopes.chunked(2).forEach { rowScopes ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowScopes.forEach { (scope, label) ->
                    FilterChip(
                        selected = selected == scope,
                        onClick = { onSelected(scope) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
                if (rowScopes.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
