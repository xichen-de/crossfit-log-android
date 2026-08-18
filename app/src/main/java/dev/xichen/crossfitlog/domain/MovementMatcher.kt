package dev.xichen.crossfitlog.domain

import org.apache.commons.text.similarity.JaroWinklerSimilarity

data class MovementMatch(
    val movement: String,
    val score: Double,
    val exact: Boolean,
    val prefix: Boolean,
)

/** Shared movement-name ranking used by editor autocomplete and whiteboard OCR. */
class MovementMatcher {
    private val similarity = JaroWinklerSimilarity()

    fun rank(query: String, candidates: Collection<String>): List<MovementMatch> {
        val normalizedQuery = normalizeForMatching(query)
        if (normalizedQuery.isBlank()) return emptyList()
        val queryCompact = normalizedQuery.replace(" ", "")
        return candidates.asSequence()
            .filter { it.isNotBlank() }
            .distinctBy(::normalizeMovementName)
            .map { candidate ->
                val normalizedCandidate = normalizeForMatching(candidate)
                val candidateCompact = normalizedCandidate.replace(" ", "")
                val exact = normalizedQuery == normalizedCandidate || queryCompact == candidateCompact
                val queryVariants = listOf(normalizedQuery, normalizedQuery.replace('i', 'l')).distinct()
                val score = queryVariants.maxOf { variant ->
                    val spaced = similarity.apply(variant, normalizedCandidate)
                    val compact = if (variant.count { it == ' ' } == normalizedCandidate.count { it == ' ' }) {
                        similarity.apply(variant.replace(" ", ""), candidateCompact)
                    } else 0.0
                    maxOf(spaced, compact)
                }
                MovementMatch(
                    movement = candidate,
                    score = if (exact) 1.0 else score,
                    exact = exact,
                    prefix = normalizedCandidate.startsWith(normalizedQuery) || candidateCompact.startsWith(queryCompact),
                )
            }
            .sortedWith(
                compareByDescending<MovementMatch> { it.exact }
                    .thenByDescending { it.prefix }
                    .thenByDescending { it.score }
                    .thenBy { it.movement.lowercase() }
            )
            .toList()
    }
}

fun rankMovementSuggestions(
    query: String,
    candidates: Collection<String>,
    matcher: MovementMatcher = MovementMatcher(),
    limit: Int = 3,
): List<String> {
    val normalizedQuery = normalizeMovementName(query)
    val compactLength = normalizedQuery.replace(" ", "").length
    if (compactLength < 2) return emptyList()
    val ranked = matcher.rank(query, candidates)
    // Once the field contains a known movement, autocomplete has nothing left to complete.
    // Hiding the row also avoids repeating that movement alongside weaker fuzzy matches.
    if (ranked.any { it.exact }) return emptyList()
    return ranked
        .filter { match ->
            match.exact || match.prefix || when {
                compactLength >= 4 -> match.score >= 0.84
                compactLength == 3 -> match.score >= 0.90
                else -> false
            }
        }
        .take(limit)
        .map { it.movement }
}

internal fun normalizeForMatching(value: String): String = normalizeMovementName(value)
    .split(' ')
    .filter(String::isNotBlank)
    .joinToString(" ") { token ->
        if (
            token.length > 2 && token.endsWith('s') &&
            !token.endsWith("ss") && !token.endsWith("sh") &&
            !token.endsWith("us") && !token.endsWith("is")
        ) token.dropLast(1) else token
    }
