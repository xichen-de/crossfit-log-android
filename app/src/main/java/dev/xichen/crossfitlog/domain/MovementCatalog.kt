package dev.xichen.crossfitlog.domain

/** Canonical CrossFit and functional-fitness movement names available before history exists. */
object MovementCatalog {
    val names: List<String> = listOf(
        // Squats and lower body
        "Squat", "Air squat", "Back squat", "Front squat", "Overhead squat", "Goblet squat",
        "Zercher squat", "Single-leg squat", "Split squat", "Cossack squat", "Wall sit",
        "Lunge", "Walking lunge", "Front-rack lunge", "Overhead lunge", "Reverse lunge",
        "Step-up", "Box step-up",
        "Pistol squat", "Calf raise", "Glute bridge", "Hip thrust",

        // Pressing and upper body
        "Press", "Jerk", "Shoulder press", "Push press", "Push jerk", "Split jerk", "Bench press",
        "Dumbbell bench press", "Floor press", "Strict press", "Handstand push-up",
        "Deficit handstand push-up", "Push-up", "Ring push-up", "Dip", "Ring dip",

        // Pulling, hanging, and gymnastics
        "Pull-up", "Strict pull-up", "Kipping pull-up", "Butterfly pull-up",
        "Chest-to-bar pull-up", "L pull-up", "Chin-up", "Ring row", "Bar muscle-up",
        "Ring muscle-up", "Strict muscle-up", "Pull-over", "Rope climb", "Legless rope climb",
        "Toes-to-bar", "Toes-to-rings", "Knees-to-elbows", "Hanging knee raise",
        "Handstand", "Handstand walk", "Wall walk", "Bear crawl", "Crab walk",
        "L-sit", "Skin the cat", "Back extension", "GHD back extension", "GHD hip extension",

        // Weightlifting and barbell
        "Deadlift", "Sumo deadlift", "Romanian deadlift", "Sumo deadlift high pull",
        "Clean", "Power clean", "Squat clean", "Hang clean", "Hang power clean",
        "Clean and jerk", "Clean and push jerk", "Medicine-ball clean",
        "Snatch", "Power snatch", "Squat snatch", "Hang snatch", "Hang power snatch",
        "Muscle snatch", "Split snatch", "Snatch balance", "Thruster", "Cluster",
        "Good morning", "Sots press", "Barbell row", "Bent-over row",

        // Dumbbell and kettlebell
        "Dumbbell clean", "Dumbbell power clean", "Dumbbell hang clean",
        "Dumbbell hang power clean", "Dumbbell deadlift", "Dumbbell front squat",
        "Dumbbell overhead squat", "Dumbbell push press", "Dumbbell push jerk",
        "Dumbbell snatch", "Dumbbell power snatch", "Dumbbell squat snatch",
        "Dumbbell thruster", "Dumbbell lunge", "Devil press", "Man maker",
        "Turkish get-up", "Kettlebell swing", "American kettlebell swing",
        "Russian kettlebell swing", "Kettlebell snatch", "Kettlebell clean",
        "Kettlebell clean and jerk", "Kettlebell goblet squat",

        // Jumps, conditioning, and bodyweight
        "Burpee", "Bar-facing burpee", "Burpee box jump-over", "Burpee pull-up",
        "Box jump", "Box jump-over", "Broad jump", "Tuck jump", "Jumping jack",
        "Jump rope", "Single-under", "Double-under", "Triple-under", "Mountain climber",
        "Sit-up", "AbMat sit-up", "GHD sit-up", "V-up", "Hollow rock", "Plank",

        // Balls, carries, odd objects, and sleds
        "Wall ball", "Wall-ball shot", "Slam ball", "Medicine-ball throw", "Medicine-ball sit-up",
        "Farmers carry", "Suitcase carry", "Front-rack carry", "Overhead carry",
        "Yoke carry", "Sandbag carry", "Sandbag clean", "Sandbag squat",
        "Sandbag over shoulder", "D-ball clean", "D-ball over shoulder",
        "Sled push", "Sled pull", "Sled drag", "Tire flip",

        // Monostructural
        "Run", "Row", "Bike", "Assault bike", "Echo bike", "Bike erg",
        "Ski erg", "Swim", "Shuttle run", "Hill sprint", "Stair climb",
    )
}

/**
 * Resolves clear history aliases to catalog spelling while retaining genuinely custom movements.
 * This only shapes suggestion candidates; it never rewrites saved workout records.
 */
fun mergeMovementCandidates(
    preferred: Collection<String>,
    fallback: Collection<String> = MovementCatalog.names,
    matcher: MovementMatcher = MovementMatcher(),
    aliasThreshold: Double = 0.94,
    ambiguityMargin: Double = 0.05,
): List<String> {
    val catalog = fallback.filter(String::isNotBlank).distinctBy(::normalizeMovementName)
    val exactCatalog = catalog.associateBy(::normalizeMovementName)
    val seen = mutableSetOf<String>()
    val resolvedHistory = preferred.mapNotNull { historyName ->
        val normalized = normalizeMovementName(historyName)
        if (normalized.isBlank()) return@mapNotNull null
        exactCatalog[normalized] ?: resolveCatalogAlias(
            historyName = historyName,
            catalog = catalog,
            matcher = matcher,
            aliasThreshold = aliasThreshold,
            ambiguityMargin = ambiguityMargin,
        ) ?: historyName
    }
    return (resolvedHistory.asSequence() + catalog.asSequence()).filter { name ->
        val normalized = normalizeMovementName(name)
        normalized.isNotBlank() && seen.add(normalized)
    }.toList()
}

private fun resolveCatalogAlias(
    historyName: String,
    catalog: Collection<String>,
    matcher: MovementMatcher,
    aliasThreshold: Double,
    ambiguityMargin: Double,
): String? {
    val historyNormalized = normalizeForMatching(historyName)
    val historyCompact = historyNormalized.replace(" ", "")
    val historyWordCount = historyNormalized.split(' ').count(String::isNotBlank)
    val ranked = matcher.rank(historyName, catalog)
        .filter { match ->
            val candidateNormalized = normalizeForMatching(match.movement)
            match.exact || candidateNormalized.split(' ').count(String::isNotBlank) == historyWordCount
        }
        .sortedWith(compareByDescending<MovementMatch> { it.exact }.thenByDescending { it.score })
    val best = ranked.firstOrNull() ?: return null
    if (best.exact) return best.movement
    val candidateCompact = normalizeForMatching(best.movement).replace(" ", "")
    if (historyCompact.length <= 3 || candidateCompact.length <= 3) return null
    val runnerUp = ranked.drop(1).firstOrNull()
    val unambiguous = runnerUp == null || best.score - runnerUp.score >= ambiguityMargin
    return best.movement.takeIf { best.score >= aliasThreshold && unambiguous }
}
