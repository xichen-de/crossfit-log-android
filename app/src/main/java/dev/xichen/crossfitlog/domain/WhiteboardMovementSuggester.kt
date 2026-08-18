package dev.xichen.crossfitlog.domain

data class RecognizedWhiteboardText(val lines: List<RecognizedWhiteboardLine>)
data class RecognizedWhiteboardLine(val text: String, val elements: List<String> = emptyList())

class WhiteboardMovementSuggester(
    private val matcher: MovementMatcher = MovementMatcher(),
    private val confidenceThreshold: Double = 0.90,
    private val ambiguityMargin: Double = 0.04,
) {
    fun suggest(
        recognized: RecognizedWhiteboardText,
        candidates: Collection<String>,
        alreadyPresent: Collection<String> = emptyList(),
    ): List<String> {
        val existing = alreadyPresent.map(::normalizeMovementName).filter(String::isNotBlank).toSet()
        val accepted = linkedMapOf<String, Pair<String, Double>>()
        val fragments = recognized.lines.flatMap { line ->
            buildList {
                add(line.text)
                addAll(line.elements)
                val tokens = normalizeMovementName(line.text).split(' ').filter(String::isNotBlank)
                for (size in 1..minOf(5, tokens.size)) {
                    for (start in 0..tokens.size - size) add(tokens.subList(start, start + size).joinToString(" "))
                }
            }
        }.distinct()

        fragments.forEach { fragment ->
            val fragmentWords = normalizeForMatching(fragment).split(' ').count(String::isNotBlank)
            val ranked = matcher.rank(fragment, candidates)
                .filter { match ->
                    match.exact || normalizeForMatching(match.movement).split(' ').count(String::isNotBlank) == fragmentWords
                }
                .sortedWith(compareByDescending<MovementMatch> { it.exact }.thenByDescending { it.score })
            val best = ranked.firstOrNull() ?: return@forEach
            val normalizedBest = normalizeMovementName(best.movement)
            val isShort = normalizeForMatching(best.movement).replace(" ", "").length <= 3
            val runnerUp = ranked.firstOrNull { normalizeMovementName(it.movement) != normalizedBest }
            val unambiguous = best.exact || runnerUp == null || best.score - runnerUp.score >= ambiguityMargin
            if ((best.exact || best.score >= confidenceThreshold) && (!isShort || best.exact) && unambiguous && normalizedBest !in existing) {
                val previous = accepted[normalizedBest]
                if (previous == null || best.score > previous.second) accepted[normalizedBest] = best.movement to best.score
            }
        }
        return accepted.values.sortedByDescending { it.second }.map { it.first }
    }
}

fun newMovementSuggestions(existing: Collection<String>, selected: Collection<String>): List<String> {
    val seen = existing.map(::normalizeMovementName).filter(String::isNotBlank).toMutableSet()
    return selected.filter { value ->
        val normalized = normalizeMovementName(value)
        normalized.isNotBlank() && seen.add(normalized)
    }
}
