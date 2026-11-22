package com.voicerpg.engine.engine

/**
 * 15-entry rolling buffer of recent player chants.
 * Prevents spamming identical high-value chants by decaying repeated phrases by 50%.
 */
class NoveltyCache(private val maxEntries: Int = 15) {

    private val history = ArrayDeque<String>(maxEntries)

    @Synchronized
    fun evaluateAndRecord(rawUtterance: String): NoveltyResult {
        val normalized = normalize(rawUtterance)
        if (normalized.isBlank()) {
            return NoveltyResult(isNovel = true, decayFactor = 1.0f)
        }

        // Count occurrences of identical or high-similarity chants in recent history
        val recentRepeats = history.count { it == normalized || isHighSimilarity(it, normalized) }

        val decayFactor = when (recentRepeats) {
            0 -> 1.0f
            1 -> 0.5f  // 50% penalty for 1st immediate repeat
            2 -> 0.25f // 75% penalty for 2nd repeat
            else -> 0.10f
        }

        val isNovel = recentRepeats == 0

        // Maintain rolling size
        if (history.size >= maxEntries) {
            history.removeFirst()
        }
        history.addLast(normalized)

        return NoveltyResult(
            isNovel = isNovel,
            decayFactor = decayFactor,
            repeatCount = recentRepeats
        )
    }

    @Synchronized
    fun clear() {
        history.clear()
    }

    private fun normalize(text: String): String {
        return text.lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim().replace(Regex("\\s+"), " ")
    }

    private fun isHighSimilarity(a: String, b: String): Boolean {
        if (a == b) return true
        val wordsA = a.split(" ").toSet()
        val wordsB = b.split(" ").toSet()
        val intersection = wordsA.intersect(wordsB).size
        val union = wordsA.union(wordsB).size
        return if (union == 0) false else (intersection.toFloat() / union) >= 0.75f
    }

    data class NoveltyResult(
        val isNovel: Boolean,
        val decayFactor: Float,
        val repeatCount: Int = 0
    )
}
