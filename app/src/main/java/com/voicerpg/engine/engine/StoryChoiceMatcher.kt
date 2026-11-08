package com.voicerpg.engine.engine

import com.voicerpg.engine.model.DialogueChoice

object StoryChoiceMatcher {

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "to", "of", "in", "on", "at", "for", "with",
        "from", "by", "about", "as", "into", "like", "through", "after", "over",
        "between", "out", "against", "during", "without", "before", "under", "around",
        "among", "is", "are", "was", "were", "be", "been", "being", "have", "has",
        "had", "do", "does", "did", "i", "we", "you", "they", "he", "she", "it",
        "my", "our", "your", "their", "his", "her", "its", "me", "us", "them",
        "him", "that", "this", "these", "those", "what", "which", "who", "whom",
        "whose", "let", "lets", "shall", "will", "would", "could", "should",
        "can", "must", "there", "here", "just", "now", "so", "then", "if", "but"
    )

    private val SEARCH_CLUSTER = setOf(
        "examine", "inspect", "investigate", "study", "look", "search", "explore",
        "scout", "check", "peer", "observe", "view", "watch", "scan", "read",
        "gaze", "survey", "find", "tracks", "trail", "ruins", "stones"
    )

    private val COMBAT_CLUSTER = setOf(
        "attack", "fight", "strike", "charge", "assault", "battle", "slay", "destroy",
        "kill", "smite", "blast", "confront", "rush", "breach", "clash",
        "rend", "combat", "draw", "weapon", "blade", "challenge"
    )

    private val TALK_CLUSTER = setOf(
        "ask", "talk", "speak", "inquire", "question", "demand", "proclaim", "shout",
        "call", "greet", "converse", "tell", "discuss", "who", "why", "how", "identity"
    )

    private val MAGIC_CLUSTER = setOf(
        "chant", "cast", "sing", "incantation", "spell", "prayer", "radiance", "harmonic",
        "mana", "flame", "spark", "magic", "holy", "sacred", "resonate", "resonance",
        "elemental"
    )

    private val REST_CLUSTER = setOf(
        "rest", "sleep", "meditate", "vigil", "campfire", "camp", "restore", "relax",
        "breathe", "heal", "peace"
    )

    private val MOVE_CLUSTER = setOf(
        "advance", "descend", "ascend", "enter", "leave", "hurry", "march", "proceed",
        "go", "move", "cross", "climb", "venture", "step", "outside", "forward",
        "southwest", "head", "onward"
    )

    private val RESCUE_CLUSTER = setOf(
        "rescue", "save", "free", "aid", "help", "release", "unshackle", "liberate", "cage"
    )

    private val HARVEST_CLUSTER = setOf(
        "harvest", "gather", "collect", "spores", "cleanse", "sample", "pick"
    )

    private val ALL_CLUSTERS = listOf(
        SEARCH_CLUSTER, COMBAT_CLUSTER, TALK_CLUSTER, MAGIC_CLUSTER,
        REST_CLUSTER, MOVE_CLUSTER, RESCUE_CLUSTER, HARVEST_CLUSTER
    )

    /**
     * Landmark boost: distinctive multi-syllable tokens that also appear verbatim in a
     * choice's text or keywords score extra. Derived dynamically from content — no
     * hardcoded proper nouns.
     */
    private fun isProperNounToken(token: String): Boolean =
        token.length >= 5 && token !in STOP_WORDS

    /**
     * Scores all available choices against the spoken utterance and returns the best matching
     * choice. Allows loud, nerdy, spell-like roleplay sentences as well as casual phrasing.
     * Supports matching in the player's active language by dynamically testing translated choice text.
     */
    fun matchChoice(
        utterance: String,
        choices: List<DialogueChoice>,
        completedFlags: Map<String, Boolean> = emptyMap(),
        locale: String = com.voicerpg.engine.localization.TranslationManager.currentLanguage.value
    ): DialogueChoice? {
        val lower = utterance.lowercase().trim()
        val unaccentedLower = com.voicerpg.engine.model.CommandCatalog.stripDiacritics(lower)
        if (lower.isBlank() || choices.isEmpty()) return null

        val tokens = lower.split(Regex("[^\\p{L}0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in STOP_WORDS }

        // Filter out choices that have already been completed if there are other uncompleted choices
        val validChoices = choices.filter { choice ->
            choice.completionFlag == null || completedFlags[choice.completionFlag] != true
        }.ifEmpty { choices }

        val isNonEnglish = locale != "en" && locale.isNotBlank()

        val scoredChoices = validChoices.map { choice ->
            var score = 0
            val choiceTextLower = choice.text.lowercase()
            val translatedChoiceText = if (isNonEnglish) {
                com.voicerpg.engine.localization.TranslationManager.translate(choice.text, locale).lowercase()
            } else ""

            // 1. Direct whole-text match (original or translated)
            if (lower.contains(choiceTextLower)) {
                score += 150
            }
            if (isNonEnglish && translatedChoiceText.isNotBlank() && (lower.contains(translatedChoiceText) || unaccentedLower.contains(com.voicerpg.engine.model.CommandCatalog.stripDiacritics(translatedChoiceText)))) {
                score += 160
            }

            for (keyword in choice.voiceKeywords) {
                val kwLower = keyword.lowercase()
                if (lower.contains(kwLower)) {
                    score += if (kwLower.contains(" ")) 120 else 100
                }
                if (isNonEnglish) {
                    val transKw = com.voicerpg.engine.localization.TranslationManager.translate(keyword, locale).lowercase()
                    if (transKw.isNotBlank() && (lower.contains(transKw) || unaccentedLower.contains(com.voicerpg.engine.model.CommandCatalog.stripDiacritics(transKw)))) {
                        score += if (transKw.contains(" ")) 130 else 110
                    }
                }
            }

            val choiceWords = choiceTextLower.split(Regex("[^\\p{L}0-9]+"))
                .map { it.trim() }
                .filter { it.length >= 2 && it !in STOP_WORDS }

            val translatedWords = if (isNonEnglish && translatedChoiceText.isNotBlank()) {
                translatedChoiceText.split(Regex("[^\\p{L}0-9]+"))
                    .map { it.trim() }
                    .filter { it.length >= 2 }
            } else emptyList()

            val keywordWords = choice.voiceKeywords.flatMap { kw ->
                kw.lowercase().split(Regex("[^\\p{L}0-9]+"))
            }.filter { it.length >= 2 && it !in STOP_WORDS }

            // 2. Token overlap with choice text and keywords
            for (token in tokens) {
                if (choiceWords.contains(token)) {
                    score += if (token.length >= 5) 35 else 25
                } else if (choiceWords.any { it.contains(token) || (token.contains(it) && it.length >= 4) }) {
                    score += 20
                }

                if (keywordWords.contains(token)) {
                    score += 30
                }

                // 3. Distinctive landmark token boost
                if (isProperNounToken(token) && (choiceWords.contains(token) || keywordWords.contains(token))) {
                    score += 45
                }

                // 4. Action / semantic cluster match
                for (cluster in ALL_CLUSTERS) {
                    if (token in cluster) {
                        val matchesClusterInChoice = choiceWords.any { it in cluster } || keywordWords.any { it in cluster }
                        if (matchesClusterInChoice) {
                            score += 20
                        }
                    }
                }
            }

            choice to score
        }

        val best = scoredChoices.maxByOrNull { it.second }
        // Minimum score threshold of 25 guarantees a meaningful match while tolerating nerdy additions
        return if (best != null && best.second >= 25) best.first else null
    }

    /**
     * Determines whether an utterance represents a progression command when in a non-branching node.
     */
    fun isProgressionUtterance(
        utterance: String,
        locale: String = com.voicerpg.engine.localization.TranslationManager.currentLanguage.value
    ): Boolean {
        val lower = utterance.lowercase().trim()
        val unaccentedLower = com.voicerpg.engine.model.CommandCatalog.stripDiacritics(lower)
        if (lower.isBlank()) return false

        val catalog = com.voicerpg.engine.content.GameContent.commandCatalogFor(locale)
        val defaultCatalog = com.voicerpg.engine.content.GameContent.DEFAULT_COMMAND_CATALOG
        val progressionPhrases = (catalog.progressionPhrases + defaultCatalog.progressionPhrases).distinct()

        if (progressionPhrases.any { phrase ->
            val clean = phrase.lowercase().trim()
            val unacc = com.voicerpg.engine.model.CommandCatalog.stripDiacritics(clean)
            clean.isNotBlank() && (lower.contains(clean) || unaccentedLower.contains(unacc))
        }) return true

        // Roleplay utterances in a progression node (e.g. "We must be careful", "Onward, friends")
        val words = lower.split(Regex("[^\\p{L}0-9]+")).filter { it.isNotBlank() }
        val isNonMetaRoleplay = words.size >= 2 && !lower.contains("options") && !lower.contains("settings") &&
                !lower.contains("status") && !lower.contains("help") && !lower.contains("menu")

        return isNonMetaRoleplay
    }

    /**
     * Determines whether an utterance in the Battle Victory/Defeat screen means "Commence Story" or "Restart Battle".
     */
    fun parseBattleConclusionIntent(
        utterance: String,
        locale: String = com.voicerpg.engine.localization.TranslationManager.currentLanguage.value
    ): ConclusionAction {
        val lower = utterance.lowercase().trim()
        val unaccentedLower = com.voicerpg.engine.model.CommandCatalog.stripDiacritics(lower)
        if (lower.isBlank()) return ConclusionAction.NONE

        val catalog = com.voicerpg.engine.content.GameContent.commandCatalogFor(locale)
        val defaultCatalog = com.voicerpg.engine.content.GameContent.DEFAULT_COMMAND_CATALOG

        val continueKeywords = (catalog.continueStoryPhrases + defaultCatalog.continueStoryPhrases).distinct()
        val restartKeywords = (catalog.restartBattlePhrases + defaultCatalog.restartBattlePhrases).distinct()

        var continueScore = 0
        var restartScore = 0

        for (kw in continueKeywords) {
            val clean = kw.lowercase().trim()
            val unacc = com.voicerpg.engine.model.CommandCatalog.stripDiacritics(clean)
            if (clean.isNotBlank() && (lower.contains(clean) || unaccentedLower.contains(unacc))) {
                continueScore += if (clean.contains(" ")) 100 else 40
            }
        }

        for (kw in restartKeywords) {
            val clean = kw.lowercase().trim()
            val unacc = com.voicerpg.engine.model.CommandCatalog.stripDiacritics(clean)
            if (clean.isNotBlank() && (lower.contains(clean) || unaccentedLower.contains(unacc))) {
                restartScore += if (clean.contains(" ")) 100 else 40
            }
        }

        return when {
            continueScore > restartScore && continueScore >= 40 -> ConclusionAction.CONTINUE_STORY
            restartScore > continueScore && restartScore >= 40 -> ConclusionAction.RESTART_BATTLE
            else -> ConclusionAction.NONE
        }
    }

    enum class ConclusionAction {
        NONE,
        CONTINUE_STORY,
        RESTART_BATTLE
    }
}
