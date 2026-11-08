package com.voicerpg.engine.model

/**
 * Data-driven voice command catalog loaded from JSON (assets/game/commands/commands_<locale>.json).
 * Supports accessibility, screenless pocket mode, targeting ordinals, and combat actions
 * without hardcoding language-specific keywords into engine code.
 */
data class CommandCatalog(
    val locale: String = "en",
    val metaCommands: Map<MetaCommand, List<String>> = emptyMap(),
    val ordinals: Map<Int, List<String>> = emptyMap(),
    val positionals: Map<String, List<String>> = emptyMap(),
    val groups: Map<String, List<String>> = emptyMap(),
    val actions: Map<String, List<String>> = emptyMap(),
    val progressionPhrases: List<String> = emptyList(),
    val continueStoryPhrases: List<String> = emptyList(),
    val restartBattlePhrases: List<String> = emptyList(),
    val systemConfirmations: Map<String, String> = emptyMap()
) {

    companion object {
        fun stripDiacritics(input: String): String {
            return java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
        }
    }

    /**
     * Resolves a voice utterance into a [MetaCommand].
     * Matches exact phrases or multi-word substring phrases (diacritic-insensitive).
     */
    fun resolveMetaCommand(utterance: String): MetaCommand {
        val lower = utterance.trim().lowercase()
        val unaccentedUtterance = stripDiacritics(lower)
        for ((meta, phrases) in metaCommands) {
            for (phrase in phrases) {
                val cleanPhrase = phrase.trim().lowercase()
                if (cleanPhrase.isBlank()) continue
                val unaccentedPhrase = stripDiacritics(cleanPhrase)
                if (lower == cleanPhrase || unaccentedUtterance == unaccentedPhrase) return meta
                if (cleanPhrase.contains(' ') && (lower.contains(cleanPhrase) || unaccentedUtterance.contains(unaccentedPhrase))) return meta
            }
        }
        return MetaCommand.NONE
    }

    /**
     * Resolves target ordinal index (0 to 5 for 1st to 6th enemy/ally).
     * Returns -1 if no ordinal matches (diacritic-insensitive).
     */
    fun resolveOrdinal(utterance: String): Int {
        val lower = utterance.trim().lowercase()
        val unaccentedUtterance = stripDiacritics(lower)
        for ((index, phrases) in ordinals) {
            for (phrase in phrases) {
                val cleanPhrase = phrase.trim().lowercase()
                val unaccentedPhrase = stripDiacritics(cleanPhrase)
                if (cleanPhrase.isNotBlank() && (lower.contains(cleanPhrase) || unaccentedUtterance.contains(unaccentedPhrase))) {
                    return index
                }
            }
        }
        return -1
    }

    /**
     * Checks if the utterance contains an action keyword (e.g. "heal", "defend", "attack").
     */
    fun hasAction(utterance: String, actionName: String): Boolean {
        val lower = utterance.trim().lowercase()
        val unaccentedUtterance = stripDiacritics(lower)
        val words = lower.split(Regex("[^\\p{L}0-9]+")).filter { it.isNotBlank() }.toSet()
        val unaccentedWords = words.map { stripDiacritics(it) }.toSet()
        val phrases = actions[actionName] ?: return false
        return phrases.any { phrase ->
            val clean = phrase.trim().lowercase()
            val unaccented = stripDiacritics(clean)
            if (clean.isBlank()) false
            else if (clean.contains(' ')) lower.contains(clean) || unaccentedUtterance.contains(unaccented)
            else clean in words || unaccented in unaccentedWords || (clean.length >= 4 && (lower.contains(clean) || unaccentedUtterance.contains(unaccented)))
        }
    }

    /**
     * Checks if the utterance contains a group keyword (e.g. "all", "party", "self").
     * Whole-word token matching is enforced for single words to prevent collisions
     * (e.g. "all" matching inside "Fireball", or "me" matching inside "Flame" or "Mending").
     */
    fun hasGroup(utterance: String, groupName: String): Boolean {
        val lower = utterance.trim().lowercase()
        val unaccentedUtterance = stripDiacritics(lower)
        val words = lower.split(Regex("[^\\p{L}0-9]+")).filter { it.isNotBlank() }.toSet()
        val unaccentedWords = words.map { stripDiacritics(it) }.toSet()
        val phrases = groups[groupName] ?: return false
        return phrases.any { phrase ->
            val clean = phrase.trim().lowercase()
            val unaccented = stripDiacritics(clean)
            if (clean.isBlank()) false
            else if (clean.contains(' ')) lower.contains(clean) || unaccentedUtterance.contains(unaccented)
            else clean in words || unaccented in unaccentedWords
        }
    }
}
