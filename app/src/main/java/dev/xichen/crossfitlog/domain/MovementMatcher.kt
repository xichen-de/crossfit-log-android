package dev.xichen.crossfitlog.domain

import org.apache.commons.text.similarity.JaroWinklerSimilarity

data class MovementMatch(
    val movement: String,
    val score: Double,
    val exact: Boolean,
    val prefix: Boolean,
    val wordCount: Int,
)

/** Shared movement-name ranking used by editor autocomplete and whiteboard OCR. */
class MovementMatcher {
    private val similarity = JaroWinklerSimilarity()

    private class Candidate(val movement: String, val normalized: String, val compact: String, val spaces: Int, val wordCount: Int)

    // Callers rank many queries against the same list instance (every OCR fragment, every
    // keystroke), so normalizing candidates once per list avoids redundant work.
    @Volatile private var prepared: Pair<Collection<String>, List<Candidate>>? = null

    private fun prepare(candidates: Collection<String>): List<Candidate> {
        prepared?.let { (source, result) -> if (source === candidates) return result }
        val result = candidates.asSequence()
            .filter { it.isNotBlank() }
            .distinctBy(::normalizeMovementName)
            .map { candidate ->
                val normalized = normalizeForMatching(candidate)
                val spaces = normalized.count { it == ' ' }
                Candidate(candidate, normalized, normalized.replace(" ", ""), spaces, if (normalized.isEmpty()) 0 else spaces + 1)
            }
            .toList()
        prepared = candidates to result
        return result
    }

    fun rank(query: String, candidates: Collection<String>): List<MovementMatch> {
        val normalizedQuery = normalizeForMatching(query)
        if (normalizedQuery.isBlank()) return emptyList()
        val queryCompact = normalizedQuery.replace(" ", "")
        val queryVariants = listOf(normalizedQuery, normalizedQuery.replace('i', 'l')).distinct()
        return prepare(candidates).asSequence()
            .map { candidate ->
                val exact = normalizedQuery == candidate.normalized || queryCompact == candidate.compact
                val score = if (exact) 1.0 else queryVariants.maxOf { variant ->
                    val spaced = similarity.apply(variant, candidate.normalized)
                    val compact = if (variant.count { it == ' ' } == candidate.spaces) {
                        similarity.apply(variant.replace(" ", ""), candidate.compact)
                    } else 0.0
                    maxOf(spaced, compact)
                }
                MovementMatch(
                    movement = candidate.movement,
                    score = score,
                    exact = exact,
                    prefix = candidate.normalized.startsWith(normalizedQuery) || candidate.compact.startsWith(queryCompact),
                    wordCount = candidate.wordCount,
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
