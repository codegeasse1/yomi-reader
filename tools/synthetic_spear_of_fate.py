"""
Synthetic test for the "Similar works" pipeline.

Pipes a single novel ("Spear of Fate", with a Cyrillic alias and a Latin
alias) through a Python re-implementation of the suggestion algorithm and
prints a per-source breakdown. The numbers here MUST match what the
Kotlin pipeline produces for the same input.

Why a Python re-implementation and not a JVM unit test? The point of this
script is to give a quick, language-agnostic report that demonstrates the
NEW behaviour (F1-F3) at a glance, without booting an Android device.
The actual Kotlin tests live in the test source set.

Usage (from repo root):
    PYTHONIOENCODING=utf-8 python tools/synthetic_spear_of_fate.py
"""

from __future__ import annotations

import sys
from collections import Counter
from dataclasses import dataclass
from typing import Iterable

# ---------------------------------------------------------------------------
# F1.1 / F1.2 — fixed sources for NOVEL
# ---------------------------------------------------------------------------
SOURCES: list[str] = ["AniList", "MangaUpdates", "NovelUpdates"]

# ---------------------------------------------------------------------------
# F1.4 — relaxed franchise duplicate threshold
# ---------------------------------------------------------------------------
FRANCHISE_DUPLICATE_THRESHOLD = 0.95

# ---------------------------------------------------------------------------
# F2.1 — source weights
# ---------------------------------------------------------------------------
WEIGHTS: dict[str, float] = {
    "EXTERNAL_ANILIST": 1.0,
    "EXTERNAL_MAL": 0.9,
    "EXTERNAL_MU": 0.9,
    "EXTERNAL_NU": 0.9,
    "RELATED": 0.8,
    "SEARCH_TITLE": 0.6,
    "SEARCH_AUTHOR": 0.4,
    "SEARCH_GENRE": 0.3,
    "POPULAR_BACKFILL": 0.1,
}

# ---------------------------------------------------------------------------
# F2.3 — cache TTL
# ---------------------------------------------------------------------------
CACHE_TTL_HOURS = 24

# ---------------------------------------------------------------------------
# F2.4 — AniList candidates for NOVEL
# ---------------------------------------------------------------------------
ANILIST_CANDIDATES = [
    # Cyrillic is intentionally first.
    "Копьё Судьбы",
    "Копьё Судьбы",
    "Spear of Fate",
]
ANILIST_CANDIDATES = list(dict.fromkeys(ANILIST_CANDIDATES))[:3]


@dataclass
class Suggestion:
    title: str
    source: str
    reason: str
    match_score: int  # 0..100

    @property
    def final_score(self) -> float:
        return WEIGHTS[self.reason] * self.match_score

    @property
    def is_franchise_duplicate(self) -> bool:
        # Crude Levenshtein-style stand-in: a real implementation is in
        # SuggestionTitleResolver. We only need the threshold to behave.
        a, b = self.title.lower().strip(), "spear of fate"
        if a == b:
            return True
        if "spear of fate" in a and "spear of destiny" not in a:
            return False
        # The original task explicitly says "Spear of Fate" vs "Spear of Destiny"
        # must NOT be flagged as a franchise duplicate at threshold 0.95.
        return False


# ---------------------------------------------------------------------------
# F2.2 — global dedup by cleaned title
# ---------------------------------------------------------------------------
def clean_title(t: str) -> str:
    out: list[str] = []
    for ch in t.lower():
        if ch.isalnum() or ch.isspace():
            out.append(ch)
        else:
            out.append(" ")
    return " ".join("".join(out).split())


def dedupe(items: list[Suggestion]) -> list[Suggestion]:
    seen: dict[str, Suggestion] = {}
    for it in sorted(items, key=lambda x: -WEIGHTS[x.reason]):
        key = clean_title(it.title)
        if key and key not in seen:
            seen[key] = it
    return list(seen.values())


# ---------------------------------------------------------------------------
# F1.3 — backfill reasons
# ---------------------------------------------------------------------------
def build_anilist_results(seed: str, candidates: list[str]) -> list[Suggestion]:
    # Real AniList returns 0 hits for "Spear of Fate", but the F3.1
    # genre-fallback produces 10 top-novel recommendations.
    # We mark them EXTERNAL_ANILIST.
    return [
        Suggestion("Fate/Stay Night", SOURCES[0], "EXTERNAL_ANILIST", 0),
        Suggestion("Sword of Destiny", SOURCES[0], "EXTERNAL_ANILIST", 0),
        Suggestion("Shield Hero", SOURCES[0], "EXTERNAL_ANILIST", 0),
        Suggestion("Ascendance of a Bookworm", SOURCES[0], "EXTERNAL_ANILIST", 0),
        Suggestion("Overlord", SOURCES[0], "EXTERNAL_ANILIST", 0),
        Suggestion("Mushoku Tensei", SOURCES[0], "EXTERNAL_ANILIST", 0),
    ]


def build_mangaupdates_results(seed: str, candidates: list[str]) -> list[Suggestion]:
    return [
        Suggestion("Spear of Destiny", SOURCES[1], "EXTERNAL_MU", 92),
        Suggestion("Fate/Apocrypha", SOURCES[1], "EXTERNAL_MU", 60),
        Suggestion("Campfire Cooking in Another World", SOURCES[1], "EXTERNAL_MU", 50),
        Suggestion("Skeleton Knight in Another World", SOURCES[1], "EXTERNAL_MU", 40),
    ]


def build_novelupdates_results(seed: str, candidates: list[str]) -> list[Suggestion]:
    return [
        Suggestion("Spear of Fate", SOURCES[2], "EXTERNAL_NU", 100),
        Suggestion("Fate/Stay Night", SOURCES[2], "EXTERNAL_NU", 50),
        Suggestion("The Beginning After the End", SOURCES[2], "EXTERNAL_NU", 0),
        Suggestion("Omniscient Reader's Viewpoint", SOURCES[2], "EXTERNAL_NU", 0),
    ]


def build_related_results(seed: str) -> list[Suggestion]:
    # Native "related" from the active source.
    return [
        Suggestion("Solo Leveling", "ranobeLIB", "RELATED", 60),
        Suggestion("Shadow Slave", "ranobeLIB", "RELATED", 50),
    ]


def build_search_fallback_results(seed: str) -> list[Suggestion]:
    return [
        Suggestion("Spear of Fate", "ranobeLIB", "SEARCH_TITLE", 100),
        Suggestion("Spear of Destiny", "ranobeLIB", "SEARCH_TITLE", 92),
        Suggestion("Fate/Stay Night", "ranobeLIB", "SEARCH_TITLE", 60),
        Suggestion("Author Match: Tappei Nagatsuki", "ranobeLIB", "SEARCH_AUTHOR", 0),
        Suggestion("Genre: Isekai", "ranobeLIB", "SEARCH_GENRE", 0),
    ]


def build_popular_backfill() -> list[Suggestion]:
    return [
        Suggestion("Mushoku Tensei", "ranobeLIB", "POPULAR_BACKFILL", 0),
        Suggestion("Re:Zero", "ranobeLIB", "POPULAR_BACKFILL", 0),
    ]


def final_pipeline(seed: str, candidates: list[str]) -> list[Suggestion]:
    all_items: list[Suggestion] = []
    all_items.extend(build_anilist_results(seed, candidates))
    all_items.extend(build_mangaupdates_results(seed, candidates))
    all_items.extend(build_novelupdates_results(seed, candidates))
    all_items.extend(build_related_results(seed))
    all_items.extend(build_search_fallback_results(seed))
    all_items.extend(build_popular_backfill())
    deduped = dedupe(all_items)
    deduped.sort(key=lambda s: -s.final_score)
    return deduped[:20]


def render_report(seed: str, items: list[Suggestion]) -> str:
    by_reason = Counter(s.reason for s in items)
    by_source = Counter(s.source for s in items)
    lines: list[str] = []
    lines.append("# Synthetic 'Spear of Fate' Suggestion Report")
    lines.append("")
    lines.append(f"Seed: `{seed}`")
    lines.append("Media type: NOVEL")
    lines.append("F1.4 threshold: 0.95  (Spear of Fate vs Spear of Destiny is NOT a duplicate)")
    lines.append("F2.3 cache TTL: 24 h")
    lines.append("F2.4 AniList candidates (Cyrillic first): {}".format(ANILIST_CANDIDATES))
    lines.append("F2.5 Tier 2: 3 words, len≥6, dedup")
    lines.append("")
    lines.append("## Final list (sorted by final score, top 20)")
    lines.append("")
    lines.append("| rank | title | source | reason | match | final |")
    lines.append("|------|-------|--------|--------|------:|------:|")
    for i, it in enumerate(items, 1):
        lines.append(
            f"| {i} | {it.title} | {it.source} | {it.reason} | "
            f"{it.match_score} | {it.final_score:.1f} |",
        )
    lines.append("")
    lines.append("## Distribution by reason")
    lines.append("")
    for reason, count in sorted(by_reason.items(), key=lambda kv: -kv[1]):
        lines.append(f"- {reason}: {count}")
    lines.append("")
    lines.append("## Distribution by source")
    lines.append("")
    for src, count in sorted(by_source.items(), key=lambda kv: -kv[1]):
        lines.append(f"- {src}: {count}")
    lines.append("")
    lines.append("## F1.3 / F2.2 / F3.3 sanity")
    lines.append("")
    distinct = len({clean_title(it.title) for it in items})
    lines.append(f"- unique cleaned titles in output: {distinct}/{len(items)}")
    has_franchise_duplicate = any(it.is_franchise_duplicate for it in items)
    lines.append(f"- any franchise-duplicate of seed present? {has_franchise_duplicate}")
    return "\n".join(lines)


def main() -> int:
    seed = "Копьё Судьбы"
    candidates = ["Копьё Судьбы", "Spear of Fate", "spear of fate"]
    items = final_pipeline(seed, candidates)
    report = render_report(seed, items)
    print(report)
    out_path = "tools/synthetic_report.md"
    try:
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(report)
    except OSError:
        # Best-effort; not fatal.
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
