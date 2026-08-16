package eu.kanade.tachiyomi.ui.reader.novel.replace

import eu.kanade.tachiyomi.ui.reader.novel.NovelBookAnchoredNativeBlock

/**
 * Applies [rules] to the stored native blocks of a compiled book (the path where no HTML is
 * parsed). Styles live per segment, so replacing text inside a segment never corrupts formatting.
 *
 * Scope mirrors [applyReplaceRulesToHtml]: blocks carrying the baked-in chapter title
 * ([eu.kanade.tachiyomi.data.book.novel.NovelBookNativeBlock.isChapterHeading]) receive title- and
 * content-scoped rules, every other block receives content-scoped rules only. Falls back to the
 * input on any failure.
 */
fun applyReplaceRulesToNativeBlocks(
    blocks: List<NovelBookAnchoredNativeBlock>,
    rules: List<ReplaceRule>,
): List<NovelBookAnchoredNativeBlock> {
    if (blocks.isEmpty() || rules.isEmpty()) return blocks
    val enabled = rules.filter { it.isEnabled && it.isValid() }
    if (enabled.isEmpty()) return blocks
    val titleRules = enabled.filter { it.scopeTitle }
    val contentRules = enabled.filter { it.scopeContent }
    return runCatching {
        blocks.map { anchored ->
            val elementRules = if (anchored.block.isChapterHeading) {
                (titleRules + contentRules).distinct()
            } else {
                contentRules
            }
            if (elementRules.isEmpty()) {
                anchored
            } else {
                anchored.copy(
                    block = anchored.block.copy(
                        segments = anchored.block.segments.map { segment ->
                            val replaced = applyReplaceRulesToText(segment.t, elementRules)
                            if (replaced == segment.t) segment else segment.copy(t = replaced)
                        },
                    ),
                )
            }
        }
    }.getOrDefault(blocks)
}
